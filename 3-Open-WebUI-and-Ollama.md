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

The script is for the one-time setup. Running it again fails because the container name is taken, so later runs use `docker stop open-webui` and `docker start open-webui`. The container starts without a restart policy, so it would stay stopped after a reboot. I set one on the existing container:

```
docker update --restart unless-stopped open-webui
```

## Reaching the web UI

The playbook only mentions `http://localhost:8080`, which means a browser on the Spark itself. With the Spark running headless, the port is also reachable from my laptop at `http://spark-d66c.local:8080`, which redirects to a login page with a **Get started** button.

The port is open to the whole home network, and the first account to register becomes the administrator, so the admin account should be created straight away. An SSH tunnel (`ssh -L 8080:localhost:8080 spark`) is the alternative that exposes nothing beyond SSH.

## Getting a model

After creating the admin account I landed straight in a chat, but the model dropdown said "No models available" and offered no way to download one, which the playbook says it should. Ollama itself was running inside the container, with an empty model list:

```
docker exec open-webui ollama list
```

So I pulled the model from the command line instead:

```
docker exec open-webui ollama pull gpt-oss:20b
```

It was 13 GB and took about 4 minutes over the wired connection. After reloading the web page, `gpt-oss:20b` was selected in the dropdown.

## First conversation

The prompt was "list the top 100 largest countries in the world, by population with population and population density", with `gpt-oss:20b`. The answer looked correct.

| Measurement | Result |
| --- | --- |
| Time until the answer started | 61 seconds |
| Generation speed | 58.6 tokens/s |
| `ollama ps` | 13 GB, 100% GPU, context 131072 |
| Memory used, idle | 2.6 GiB |
| Memory used, answering | 22 GiB (121 GiB total, 99 GiB available) |
| Page cache | 24 GiB |

Notes on the numbers:

- **The 61-second wait** is probably a cold start: the 13 GB model is read from disk into memory on the first request. `ollama ps` shows when the model will be unloaded again (`UNTIL`). Ollama unloads an idle model after a timeout, 5 minutes by default, so a request after a pause pays the start-up cost again.
- **Tokens/s** is the generation speed once the answer starts. 58.6 is comfortable to read.
- **100% GPU** means the whole model runs on the GPU.
- **Memory:** loading the model took roughly 19 GiB, more than the 13 GB model file. The large context window (131072 tokens) and its KV cache, which holds the conversation so far, probably account for part of that. The 24 GiB page cache is Linux caching the model file, which the kernel gives back when needed.
- **Measuring:** `watch -n1 free -h` only shows the current value. A baseline before and a reading at the peak, or `vmstat 1` for a scrolling history, work better.

### Warm start

A second prompt, "Show the top 10 countries by population density.", sent while the model was still loaded, started answering after about 7 seconds. So roughly 54 of the first prompt's 61 seconds were spent loading the model.

The remaining wait wasn't loading. `gpt-oss` is a reasoning model, so it generates thinking tokens before the visible answer, and Open WebUI labelled the reply "Thought for 6 seconds". So about 6 of the 7 seconds were the model thinking.

## A bigger model: gpt-oss:120b

The Spark's main selling point is its memory, so the next test was `gpt-oss:120b`:

```
docker exec open-webui ollama pull gpt-oss:120b
```

The pull reported 65 GB, which is about 60 GiB and fits comfortably in the 121 GiB of memory. The download took 17 minutes 30 seconds, about 62 MB/s.

I stopped the 20b model first (`docker exec open-webui ollama stop gpt-oss:20b`), then asked the same population question as before.

| Measurement | gpt-oss:20b | gpt-oss:120b |
| --- | --- | --- |
| Model size | 13 GB | 65 GB |
| Cold: time until the answer started | 61 s | 61 s, of which 42 s thinking |
| Warm: time until the answer started | 7 s (6 s thinking) | 9 s (6 s thinking) |
| Generation speed | 58.6 tokens/s | 39.1 tokens/s |
| Memory used, idle / answering | 2.6 / 22 GiB | 3.9 / 71 GiB |

Notes on the numbers:

- **Memory:** loading the 120b model cost about 67 GiB, against a model size of roughly 60 GiB, so about 7 GiB went to the context and runtime overhead. During the answer `free` showed only 939 MiB free and 50 GiB available, with 51 GiB of page cache. Swap use was 140 KiB.
- **Cold start:** if the 61 seconds includes the 42 seconds of thinking, as the warm numbers suggest, loading took about 19 seconds, quicker than for the smaller model. The 120b files were probably still in the page cache from the download (85 GiB of cache at the baseline). That is a guess.
- **Thinking time** is the model's choice, not the hardware's. The bigger model thought much longer about the same question.
- **Speed:** the model is five times larger on disk but only about a third slower. Both are, I believe, mixture-of-experts models that use only part of their weights per token. I haven't verified that.
