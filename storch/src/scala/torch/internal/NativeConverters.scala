package torch
package internal

import org.bytedeco.pytorch
import org.bytedeco.pytorch.ScalarTypeOptional
import org.bytedeco.pytorch.LayoutOptional
import org.bytedeco.pytorch.DeviceOptional
import org.bytedeco.pytorch.BoolOptional
import org.bytedeco.pytorch.LongOptional
import org.bytedeco.pytorch.TensorOptional

import scala.reflect.Typeable
import org.bytedeco.javacpp.LongPointer
import org.bytedeco.pytorch.GenericDict
import org.bytedeco.pytorch.GenericDictIterator

private[torch] object NativeConverters:

  inline def toOptional[T, U <: T | Option[T], V >: Null](i: U, f: T => V): V = i match
    case i: Option[T] => i.map(f(_)).orNull
    case i: T         => f(i)

  def toOptional(l: Long | Option[Long]): LongOptional = toOptional(l, pytorch.LongOptional(_))

  def toOptional[D <: DType](t: Tensor[D] | Option[Tensor[D]]): TensorOptional =
    toOptional(t, t => pytorch.TensorOptional(t.native))

  def toArray(i: Long | (Long, Long)) = i match
    case i: Long              => Array(i)
    case (i, j): (Long, Long) => Array(i, j)

  def toNative(input: Int | (Int, Int)) = input match
    case (h, w): (Int, Int) => LongPointer(Array(h.toLong, w.toLong)*)
    case x: Int             => LongPointer(Array(x.toLong, x.toLong)*)

  def tensorOptions(
      dtype: DType,
      layout: Layout,
      device: Device,
      requiresGrad: Boolean,
      pinMemory: Boolean = false
  ): pytorch.TensorOptions =
    pytorch
      .TensorOptions()
      .dtype(ScalarTypeOptional(dtype.toScalarType))
      .layout(LayoutOptional(layout.toNative))
      .device(DeviceOptional(device.toNative))
      .requires_grad(BoolOptional(requiresGrad))
      .pinned_memory(BoolOptional(pinMemory))

  def tensorOptions(
      dtype: Option[DType],
      layout: Option[Layout],
      device: Option[Device],
      requiresGrad: Boolean
  ): pytorch.TensorOptions =
    pytorch
      .TensorOptions()
      .dtype(dtype.fold(ScalarTypeOptional())(d => ScalarTypeOptional(d.toScalarType)))
      .layout(layout.fold(LayoutOptional())(l => LayoutOptional(l.toNative)))
      .device(device.fold(DeviceOptional())(d => DeviceOptional(d.toNative)))
      .requires_grad(BoolOptional(requiresGrad))

  class NativeIterable[Container, NativeIterator, Item](
      container: Container,
      containerSize: Container => Long,
      begin: Container => NativeIterator,
      increment: NativeIterator => NativeIterator,
      access: NativeIterator => Item
  ) extends scala.collection.Iterable[Item]:

    override def iterator: Iterator[Item] = new Iterator[Item] {
      val it = begin(container)
      val len = containerSize(container)
      var index = 0

      override def next(): Item =
        val item = access(it)
        index += 1
        increment(it)
        item

      override def hasNext: Boolean = index < len
    }

  class GenericDictIterable(d: GenericDict)
      extends NativeIterable(
        container = d,
        containerSize = d => d.size(),
        begin = d => d.begin(),
        increment = it => it.increment(),
        access = it => it.access()
      )