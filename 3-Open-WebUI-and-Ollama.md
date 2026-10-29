# 3 - Open WebUI and Ollama

The first local LLM setup follows NVIDIA's playbook [Chat with LLMs Using Open WebUI and Ollama](https://build.nvidia.com/spark/open-webui). It runs [Open WebUI](https://github.com/open-webui/open-webui), a browser-based chat interface, together with [Ollama](https://ollama.com/), which downloads and runs the models. Both are bundled in one container image, `ghcr.io/open-webui/open-webui:ollama`, so Ollama is not installed separately on the host.

The playbook estimates 15 to 20 minutes. It needs about 7 GB of disk for the image, plus about 15 GB for the suggested model, `gpt-oss:20b`.

## Starting the container

The playbook's `docker run` command, saved as a script (`~/.local/bin/run-ollama`):

```
docker run -d -p 8080:8080 --gpus=all \
  -v open-webui:/app/backend/data \
  -v open-webui-ollama:/root/.ollama \
  --name open-webui ghcr.io/open-webui/open-webui:ollama
```

- `-d` runs the container in the background.
- `-p 8080:8080` publishes the web UI on port 8080.
- `--gpus=all` gives the container access to the GPU.
- The two named volumes hold the application data and the downloaded models, so they survive removing and recreating the container.

The script works once. Running it again fails because the container name is taken, so later runs use `docker stop open-webui` and `docker start open-webui`. The container has no restart policy, so it stays stopped after a reboot. `docker update --restart unless-stopped open-webui` changes that.

## Reaching the web UI

The playbook only mentions `http://localhost:8080`, which means a browser on the Spark itself. With the Spark running headless, the port is also reachable from my laptop at `http://spark-d66c.local:8080`, which redirects to a login page with a **Get started** button.

The port is open to the whole home network, and the first account to register becomes the administrator, so the admin account should be created straight away. An SSH tunnel (`ssh -L 8080:localhost:8080 spark`) is the alternative that exposes nothing beyond SSH.
