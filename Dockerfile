# Imagem base
FROM jupyter/base-notebook:python-3.7.6

# Atualizar e instalar dependências de sistema
USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
    dbus-x11 \
    firefox \
    xfce4 \
    xfce4-panel \
    xfce4-session \
    xfce4-settings \
    xorg \
    xubuntu-icon-theme \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

# Configurar TurboVNC
ARG TURBOVNC_VERSION=2.2.6
RUN wget -q "https://sourceforge.net/projects/turbovnc/files/${TURBOVNC_VERSION}/turbovnc_${TURBOVNC_VERSION}_amd64.deb/download" -O turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    apt-get install -y ./turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    rm ./turbovnc_${TURBOVNC_VERSION}_amd64.deb && \
    ln -s /opt/TurboVNC/bin/* /usr/local/bin/

# Copiar arquivos para o contêiner
COPY . /opt/install

# Configurar permissões
RUN chown -R $NB_UID:$NB_GID /opt/install

# Instalar dependências Python com pip
USER $NB_USER
WORKDIR /opt/install
RUN pip install --no-cache-dir -r requirements.txt

# Configurar CMD padrão
CMD ["start.sh"]
