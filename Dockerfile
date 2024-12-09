FROM jupyter/base-notebook:python-3.7.6

USER root

# Atualiza os pacotes do sistema e instala dependências gráficas e outras necessárias
RUN apt-get -y update \
 && apt-get install -y \
    dbus-x11 \
    firefox \
    xfce4 \
    xfce4-panel \
    xfce4-session \
    xfce4-settings \
    xorg \
    xubuntu-icon-theme \
 && apt-get clean

# Instala o TurboVNC
ARG TURBOVNC_VERSION=2.2.6
RUN wget -q "https://sourceforge.net/projects/turbovnc/files/${TURBOVNC_VERSION}/turbovnc_${TURBOVNC_VERSION}_amd64.deb/download" -O turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
   apt-get install -y -q ./turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
   apt-get remove -y -q light-locker && \
   rm ./turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
   ln -s /opt/TurboVNC/bin/* /usr/local/bin/

# Ajusta permissões dos diretórios
RUN chown -R $NB_UID:$NB_GID $HOME

# Copia o código de instalação do seu diretório local para o contêiner
ADD . /opt/install
RUN fix-permissions /opt/install

# Volta para o usuário padrão do Jupyter
USER $NB_USER

# Atualiza o ambiente Conda
RUN cd /opt/install && \
    conda env update -n base --file environment.yml


