FROM jupyter/base-notebook:python-3.7.6

USER root

# Instalar pacotes necessários para XFCE e TurboVNC
RUN apt-get -y update && apt-get install -y \
    dbus-x11 \
    firefox \
    xfce4 \
    xfce4-panel \
    xfce4-session \
    xfce4-settings \
    xorg \
    xubuntu-icon-theme \
    wget && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Instalar TurboVNC
ARG TURBOVNC_VERSION=2.2.6
RUN wget -q "https://sourceforge.net/projects/turbovnc/files/${TURBOVNC_VERSION}/turbovnc_${TURBOVNC_VERSION}_amd64.deb/download" -O turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    apt-get install -y ./turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    rm ./turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    ln -s /opt/TurboVNC/bin/* /usr/local/bin/

# Ajustar permissões
RUN chown -R $NB_UID:$NB_GID $HOME

# Adicionar arquivos de instalação
ADD . /opt/install
RUN fix-permissions /opt/install

USER $NB_USER

# Instalar dependências do Python
COPY requirements.txt /opt/install/requirements.txt
RUN pip install --no-cache-dir -r /opt/install/requirements.txt
