/*
 * Copyright 2022 storch.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//> using scala "3.2"
//> using repository "sonatype-s01:snapshots"
//> using repository "sonatype:snapshots"
//> using lib "dev.storch::vision:0.0-baeeb21-SNAPSHOT"
//> using lib "com.lihaoyi::os-lib:0.9.0"
//> using lib "me.tongfei:progressbar:0.9.5"
//> using lib "com.github.alexarchambault::case-app:2.1.0-M24"
//> using lib "org.bytedeco:pytorch-platform-gpu:1.13.1-1.5.9-SNAPSHOT"

import Commands.*
import ImageClassifier.{Prediction, predict, train}
import caseapp.*
import caseapp.core.app.CommandsEntryPoint
import com.sksamuel.scrimage.{ImmutableImage, ScaleMethod}
import me.tongfei.progressbar.{ProgressBar, ProgressBarBuilder}
import org.bytedeco.javacpp.PointerScope
import org.bytedeco.pytorch.{InputArchive, OutputArchive}
import os.Path
import torch.*
import torch.Device.{CPU, CUDA}
import torch.optim.Adam
import torchvision.models.resnet.{ResNet101Weights, resnet101}

import java.nio.file.Paths
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try, Using}

/** Example script for training an image-classification model on your own images */
object ImageClassifier extends CommandsEntryPoint:

  case class Metrics(loss: Float, accuracy: Float)

  torch.manualSeed(0)
  val random = new Random(seed = 0)

  val transforms = ResNet101Weights.IMAGENET1K_V1.transforms

  extension (number: Double) def format: String = "%1.5f".format(number)

  def train(options: TrainOptions): Unit =
    val device = if torch.cuda.isAvailable then CUDA else CPU
    println(s"Using device: $device")

    val datasetDir = os.Path(options.datasetDir, base = os.pwd)
    val classes = os.list(datasetDir).filter(os.isDir).map(_.last).sorted
    val classIndices = classes.zipWithIndex.toMap
    println(s"Found ${classIndices.size} classes: ${classIndices.mkString("[", ", ", "]")}")
    val pathsWithLabel = classes.flatMap { label =>
      os
        .list(datasetDir / label)
        .filter(_.ext == "jpg")
        .map(path => path -> label)
    }
    println(s"Found ${pathsWithLabel.size} examples")

    val sample = random.shuffle(pathsWithLabel).take(options.take.getOrElse(pathsWithLabel.length))
    val (trainData, testData) = sample.splitAt((sample.size * 0.9).toInt)
    println(s"Train size: ${trainData.size}")
    println(s"Eval size:  ${testData.size}")

    val model = resnet101(numClasses = classes.length)
    for weightsDir <- options.weightsDir do
      val weights = torch.pickleLoad(Paths.get(weightsDir))
      model.loadStateDict(
        weights.filterNot((k, v) => Set("fc.weight", "fc.bias").contains(k))
      )
    model.to(device)

    val optimizer = Adam(model.parameters, lr = options.learningRate)
    val lossFn = torch.nn.loss.CrossEntropyLoss()
    val numEpochs = options.epochs
    val batchSize = options.batchSize
    val trainSteps = (trainData.size / batchSize.toFloat).ceil.toInt
    val evalSteps = (testData.size / options.batchSize.toFloat).ceil.toInt

    // Lazily loads inputs and transforms them into batches of tensors in the shape the model expects.
    def dataLoader(
        dataset: Seq[(Path, String)],
        shuffle: Boolean,
        batchSize: Int
    ): Iterator[(Tensor[Float32], Tensor[Int64])] =
      val loader = ImmutableImage.loader()
      (if shuffle then random.shuffle(dataset) else dataset)
        .grouped(batchSize)
        .map { batch =>
          val (inputs, labels) = batch.unzip
          val transformedInputs =
            Await.result(
              Future.traverse(inputs)(path => // parallelize loading to improve GPU utilization
                Future(transforms.transforms(loader.fromPath(path.toNIO)))
              ),
              10.seconds
            )
          assert(transformedInputs.forall(t => !t.isnan.any.item))
          (
            transforms.batchTransforms(torch.stack(transformedInputs)),
            torch.stack(labels.map(label => Tensor(classIndices(label)).to(dtype = int64)))
          )
        }

    def trainDL = dataLoader(trainData, shuffle = true, batchSize)

    def evaluate(): Metrics =
      val testDL = dataLoader(testData, shuffle = false, batchSize = batchSize)
      val evalPB =
        ProgressBarBuilder().setTaskName(s"Evaluating        ").setInitialMax(evalSteps).build()
      evalPB.setExtraMessage(s" " * 36)
      val isTraining = model.isTraining
      if isTraining then model.eval()
      val (loss, correct) = testDL
        .map { (inputBatch, labelBatch) =>
          Using.resource(new PointerScope()) { p =>
            val pred = model(inputBatch.to(device))
            val label = labelBatch.to(device)
            val loss = lossFn(pred, label).item
            val correct = pred.argmax(dim = 1).eq(label).sum.item
            evalPB.step()
            (loss, correct)
          }
        }
        .toSeq
        .unzip
      val metrics = Metrics(
        Tensor(loss).mean.item,
        Tensor(correct).sum.item / testData.size.toFloat
      )
      evalPB.setExtraMessage(
        s"    Loss: ${metrics.loss.format}, Accuracy: ${metrics.accuracy.format}"
      )
      evalPB.close()
      if isTraining then model.train()
      metrics

    for epoch <- 1 to numEpochs do
      val trainPB = ProgressBarBuilder()
        .setTaskName(s"Training epoch $epoch/$numEpochs")
        .setInitialMax(trainSteps)
        .build()
      var runningLoss = 0.0
      var step = 0
      var evalMetrics: Metrics = Metrics(Float.NaN, accuracy = 0)
      for (input, label) <- trainDL do {
        optimizer.zeroGrad()
        // Using PointerScope ensures that all intermediate tensors are deallocated in time
        Using.resource(new PointerScope()) { p =>
          val pred = model(input.to(device))
          val loss = lossFn(pred, label.to(device))
          loss.backward()
          // add a few sanity checks
          assert(
            model.parameters.forall(t => !t.isnan.any.item),
            "Parameters containing nan values"
          )
          assert(
            model.parameters.forall(t => !t.grad.isnan.any.item),
            "Gradients containing nan values"
          )
          optimizer.step()
          runningLoss += loss.item
        }
        trainPB.setExtraMessage(" " * 21 + s"Loss: ${(runningLoss / step).format}")
        trainPB.step()
        if ((step + 1) % (trainSteps / 4.0)).toInt == 0 then
          evalMetrics = evaluate()
          runningLoss = 0.0
        step += 1
      }
      trainPB.close()
      println(
        s"Epoch $epoch/$numEpochs, Training loss: ${(runningLoss / step).format}, Evaluation loss: ${evalMetrics.loss.format}, Accuracy: ${evalMetrics.accuracy.format}"
      )
      val checkpointDir = os.Path(options.checkpointDir, os.pwd) / "%02d".format(epoch)
      os.makeDir.all(checkpointDir)
      val oa = OutputArchive()
      model.to(CPU).save(oa)
      oa.save_to((checkpointDir / "model.pt").toString)
      os.write(checkpointDir / "classes.txt", classes.mkString("\n"))

  def cleanup(datasetDir: String): Unit =
    os.walk(os.Path(datasetDir, base = os.pwd)).filter(_.ext == "jpg").foreach { path =>
      Try(ImmutableImage.loader().fromPath(path.toNIO)).recover { _ =>
        println(s"Cleaning up broken image $path")
        os.move(path, path / os.up / (path.last + ".bak"))
      }
    }

  case class Prediction(label: String, confidence: Double)

  def predict(options: PredictOptions): Prediction =
    val classes = os.read.lines(os.Path(options.modelDir, os.pwd) / "classes.txt")
    val model = resnet101(numClasses = classes.length)
    val ia = InputArchive()
    ia.load_from((os.Path(options.modelDir, os.pwd) / "model.pt").toString)
    model.load(ia)
    model.eval()
    val image = ImmutableImage.loader().fromPath(Paths.get(options.imagePath))
    val transformedImage =
      transforms.batchTransforms(transforms.transforms(image)).unsqueeze(dim = 0)
    val prediction = model(transformedImage)
    val TensorTuple(confidence, index) =
      torch.nn.functional.softmax(prediction, dim = 1)().max(dim = 1)
    val predictedLabel = classes(index.item.toInt)
    Prediction(predictedLabel, confidence.item)

  override def commands: Seq[Command[?]] = Seq(Train, Predict)
  override def progName: String = "image-classifier"

@HelpMessage("Train an image classification model")
case class TrainOptions(
    @HelpMessage(
      "Path to images. Images are expected to be stored in one directory per class i.e. cats/cat1.jpg cats/cat2.jpg dogs/dog1.jpg ..."
    )
    datasetDir: String,
    @HelpMessage("Path to load pre-trained weights from")
    weightsDir: Option[String] = None,
    @HelpMessage("Where to save model checkpoints")
    checkpointDir: String = "checkpoints",
    @HelpMessage("The maximum number of images to take for training")
    take: Option[Int] = None,
    batchSize: Int = 8,
    @HelpMessage("How many epochs (iterations over the input data) to train")
    epochs: Int = 1,
    learningRate: Double = 1e-5
)
@HelpMessage("Predict which class an image belongs to")
case class PredictOptions(
    @HelpMessage("Path to an image whose class we want to predict")
    imagePath: String,
    @HelpMessage("Path to to the serialized model created by running 'train'")
    modelDir: String
)

object Commands:
  object Train extends Command[TrainOptions]:
    override def run(options: TrainOptions, remainingArgs: RemainingArgs): Unit = train(options)
  object Predict extends Command[PredictOptions]:
    override def run(options: PredictOptions, remainingArgs: RemainingArgs): Unit =
      val Prediction(label, confidence) = predict(options)
      println(s"Class: $label, confidence: $confidence")