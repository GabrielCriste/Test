# Use a imagem base leve do Jupyter
FROM jupyter/base-notebook:python-3.9

USER root

# Atualizar o sistema e instalar dependências para XFCE e TurboVNC
RUN apt-get update && apt-get install -y \
    dbus-x11 \
    xfce4 \
    xfce4-terminal \
    xfce4-session \
    xfce4-panel \
    xfce4-settings \
    xorg \
    firefox \
    xubuntu-icon-theme \
    wget \
    && apt-get clean

# Instalar o TurboVNC
ARG TURBOVNC_VERSION=2.2.6
RUN wget -q https://sourceforge.net/projects/turbovnc/files/${TURBOVNC_VERSION}/turbovnc_${TURBOVNC_VERSION}_amd64.deb/download -O turbovnc.deb \
    && dpkg -i turbovnc.deb \
    && apt-get install -f -y \
    && rm turbovnc.deb

# Copiar o arquivo requirements.txt e instalar as dependências Python
COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt

# Criar um script de inicialização para o ambiente de desktop
RUN echo '#!/bin/bash\n\
export DISPLAY=:1\n\
vncserver :1 -geometry 1280x720 -depth 24\n\
startxfce4 &\n\
exec jupyter notebook --NotebookApp.token="" --NotebookApp.allow_origin="*"' > /usr/local/bin/start.sh \
    && chmod +x /usr/local/bin/start.sh

# Expor a porta para o Jupyter e o VNC
EXPOSE 8888

# Comando inicial
CMD ["/usr/local/bin/start.sh"]
