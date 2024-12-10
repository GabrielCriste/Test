# Utilizando uma imagem base oficial do Ubuntu
FROM ubuntu:22.04

# Evitar prompts interativos e configurar o ambiente para instalação
ENV DEBIAN_FRONTEND=noninteractive

# Atualizando os pacotes e instalando dependências necessárias
RUN apt-get update && apt-get upgrade -y && \
    apt-get install -y \
    wget \
    curl \
    libplist3 \
    hicolor-icon-theme \
    xdg-user-dirs \
    libexo-common \
    libvisual-0.4-0 \
    libyaml-0-2 \
    libglib2.0-0 \
    distro-info-data \
    manpages \
    libglvnd0 \
    libwnck-3-common \
    libsnmp-base \
    libtdb1 \
    libmaxminddb0 \
    fonts-ubuntu \
    tumbler-common \
    libdbusmenu-glib4 \
    libbrotli1 \
    libsqlite3-0 \
    libsasl2-modules \
    libxfce4util-common \
    libgdk-pixbuf2.0-common \
    dosfstools \
    binutils-common \
    x11-common \
    libsensors-config \
    libnghttp2-14 \
    libdeflate0 \
    libwebrtc-audio-processing1 \
    linux-libc-dev \
    libctf-nobfd0 \
    libnss-systemd \
    xkb-data \
    liblzo2-2 \
    libnpth0 \
    libntfs-3g89 \
    libassuan0 \
    libgomp1 \
    perl-modules-5.34 \
    bzip2 \
    libldap-common \
    libunwind8 \
    libgphoto2-l10n \
    libpthread-stubs0-dev \
    apport-symptoms \
    libjbig0 \
    libcolord2 \
    xxd \
    ntfs-3g \
    libopengl0 \
    libfakeroot \
    colord-data \
    libasan6 \
    libflac8 \
    poppler-data \
    libgarcon-common \
    acl \
    libsasl2-modules-db \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

# Configure the timezone (replace "Europe/Lisbon" with your timezone)
RUN echo "Europe/Lisbon" > /etc/timezone && \
    dpkg-reconfigure -f noninteractive tzdata

# Adicionar seu código-fonte ou outros arquivos necessários para a imagem
COPY . /app

# Definir o diretório de trabalho
WORKDIR /app

# Expor a porta desejada
EXPOSE 8080

# Comando para rodar o seu aplicativo
CMD ["your-command-here"]

