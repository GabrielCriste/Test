# Etapa 1: Usar uma imagem base com Ubuntu
FROM ubuntu:22.04

# Etapa 2: Atualizar pacotes e instalar dependências
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    vim \
    python3-pip \
    python3-dev \
    python3-setuptools \
    python3-venv \
    libgtk2.0-0 \
    libx11-dev \
    libxtst6 \
    libnss3 \
    libxss1 \
    libgconf-2-4 \
    x11vnc \
    xvfb \
    xfce4 \
    tightvncserver \
    && apt-get clean

# Etapa 3: Instalar pacotes do Python necessários
RUN pip3 install --upgrade pip && \
    pip3 install pyvirtualdisplay websocket-client webcolors uri-template tzdata \
    types-python-dateutil traitlets threadpoolctl simpervisor Send2Trash rpds-py rfc3986-validator \
    rfc3339-validator pyyaml python-json-logger propcache platformdirs pillow packaging overrides \
    numpy kiwisolver jsonpointer joblib frozenlist fqdn fonttools cycler attrs async-timeout \
    aiohappyeyeballs scipy referencing redis pandas multidict jupyter-server-terminals jupyter-core \
    contourpy arrow aiosignal yarl scikit-learn matplotlib jwcrypto jupyter-client jsonschema-specifications \
    isoduration websockify seaborn jsonschema aiohttp jupyter-events jupyter-server jupyter-server-proxy

# Etapa 4: Criar script para iniciar o ambiente
RUN echo '#!/bin/bash\nexport DISPLAY=:1\nvncserver :1 -geometry 1280x720 -depth 24\nstartxfce4 &\nexec jupyter notebook --NotebookApp.token="" --NotebookApp.allow_origin="*"' > /usr/local/bin/start.sh \
    && chmod +x /usr/local/bin/start.sh

# Etapa 5: Expor as portas para VNC e Jupyter
EXPOSE 8888  # Porta do Jupyter
EXPOSE 5901  # Porta do VNC

# Etapa 6: Definir o comando de inicialização
CMD ["/usr/local/bin/start.sh"]

