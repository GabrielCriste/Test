FROM jupyter/base-notebook:python-3.7.6

USER root

# Instalar dependências do sistema
RUN apt-get -y update && apt-get install -y \
    dbus-x11 \
    firefox \
    xfce4 \
    xfce4-panel \
    xfce4-session \
    xfce4-settings \
    xorg \
    xubuntu-icon-theme && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Instalar o TurboVNC
ARG TURBOVNC_VERSION=2.2.6
RUN wget -q "https://sourceforge.net/projects/turbovnc/files/${TURBOVNC_VERSION}/turbovnc_${TURBOVNC_VERSION}_amd64.deb/download" -O turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    apt-get install -y ./turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    apt-get remove -y light-locker && \
    rm ./turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    ln -s /opt/TurboVNC/bin/* /usr/local/bin/

# Corrigir permissões no diretório do usuário
RUN chown -R $NB_UID:$NB_GID $HOME

# Adicionar arquivos ao container
ADD . /opt/install
RUN fix-permissions /opt/install

# Trocar para o usuário padrão
USER $NB_USER

# Instalar dependências do Python
RUN pip install --no-cache-dir -r /opt/install/requirements.txt
