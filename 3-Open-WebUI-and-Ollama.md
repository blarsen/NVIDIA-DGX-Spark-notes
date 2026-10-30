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
- **Unload timer:** `ollama ps` while the 120b model was answering showed 64 GB, 100% GPU, context 131072, and `UNTIL 4 minutes from now`. That is consistent with the 5-minute default timeout for idle models.
- **Cold start:** the 61 seconds includes the 42 seconds of thinking, so loading took about 19 seconds, quicker than for the smaller model. The 120b files were probably still in the page cache from the download (85 GiB of cache at the baseline). That is a guess.
- **Thinking time** is the model's choice, not the hardware's. The bigger model thought much longer about the same question.
- **Speed:** the 120b model is five times larger on disk but only about a third slower. Both are mixture-of-experts models, which use only part of their weights for each token (see the next section).

### Checking the facts

The model cards (Ollama's page for `gpt-oss` and the Hugging Face model card) give:

| | gpt-oss:20b | gpt-oss:120b |
| --- | --- | --- |
| Total parameters | 21B | 117B |
| Active parameters per token | 3.6B | 5.1B |
| File size, per Ollama | 14GB (my pull reported 13 GB) | 65GB |
| Context window | 128K | 128K |

- Both are mixture-of-experts (MoE) models, with weights quantized to MXFP4 at 4.25 bits per parameter. The expert weights are over 90% of the parameters.
- Reasoning effort has three levels, low, medium and high, set in the system prompt. The models are licensed under Apache 2.0.
- NVIDIA's DGX Spark is not what the models were sized for: they are designed to fit in 16 GB and 80 GB of memory respectively. The Spark has room to spare.

A back-of-envelope check on the speed: generating a token means reading the active weights from memory once. For the 120b model that is 5.1B parameters at 4.25 bits, about 2.7 GB per token, and at 39.1 tokens/s that is roughly 106 GB/s. For the 20b model it is 3.6B parameters, about 1.9 GB per token, and at 58.6 tokens/s roughly 112 GB/s. I first read the closeness of the two figures as a sign that generation is limited by memory bandwidth. The `llama3.1:8b` test below disproves that reading: it reached at least 187 GB/s, so 106 to 112 GB/s is not the ceiling, and the `gpt-oss` models must lose time to something besides reading weights.

Still unexplained: why loading the 120b model took about 67 GiB when the model is about 60 GiB, and why `free` showed only 50 GiB available with 51 GiB of page cache.

## A non-reasoning model: llama3.1:8b

To compare with the reasoning models I tried a dense, non-reasoning one: `llama3.1:8b`. A dense model uses all of its weights for every token. I stopped the previous model first and asked the same population question.

```
docker exec open-webui ollama pull llama3.1:8b
```

| Measurement | Result |
| --- | --- |
| Model size | 4.9 GB |
| Cold: time until the answer started | 30 s |
| Warm: time until the answer started | 2 s |
| Generation speed | 38.2 tokens/s |
| Memory used, idle / answering | 3.8 / 27 GiB |

- **The speed prediction was wrong.** From the `gpt-oss` figures I expected about 20 tokens/s. Reading 4.9 GB per token at 38.2 tokens/s means at least about 187 GB/s of effective bandwidth, well above the 106 to 112 GB/s I had computed for `gpt-oss`.
- **Memory:** loading cost about 23 GiB for a 4.9 GB model. My explanation is the KV cache. Llama 3.1 8B has 32 layers, 8 key/value heads and a head size of 128, which makes 128 KiB per token, and at a 131072-token context that is 16 GiB. With the model itself that is close to the observed figure. `ollama ps` confirmed it: context 131072 and a size of 22 GB for a 4.9 GB model, 100% GPU. Most of the memory goes to the context buffer, not the model, and a shorter context would use far less.
- **Cold start:** 30 seconds for a small model is odd, since the 120b model took about 19 seconds to load. I don't know why. Setting up the large KV cache may be part of it.
- **No thinking:** the warm start of 2 seconds shows how much of the `gpt-oss` wait was the model thinking.

### A model that doesn't know its own cutoff

Asked about itself, `llama3.1:8b` claimed "My training data includes information up through October 29, 2026." That is today's date, which can't be right: Meta gives December 2023 as the knowledge cutoff for Llama 3.1. A model's statements about itself are not reliable.

My first guess was that the chat template supplies today's date. It doesn't: the template shown by `docker exec open-webui ollama show llama3.1:8b --template` contains no date line. Its "Cutting Knowledge Date: December 2023" line only appears in tool-calling requests, not in ordinary chats. The date reaches the model another way: in an Open WebUI chat the model calls a built-in tool that returns the current time, and it mixed that up with its cutoff. See [4-Models-and-tooling.md](4-Models-and-tooling.md#where-the-date-comes-from).

## A large dense model: llama3.3:70b

The last model was a big dense one, `llama3.3:70b`, to see what a model that reads all of its weights for every token costs. The pull reported 42 GB.

```
docker exec open-webui ollama pull llama3.3:70b
```

| Measurement | Result |
| --- | --- |
| Model size | 42 GB |
| Cold: time until the answer started | 70 s |
| Generation speed | 4.66 tokens/s |
| Memory used, idle / answering | 4.0 / 54 GiB |

- **Speed:** 4.66 tokens/s matched the prediction of at most 4 to 5 tokens/s. Reading 42 GB per token at that speed is about 196 GB/s, in line with the at least 187 GB/s from the 8b model. Both dense models land at roughly 190 to 200 GB/s, which looks like the practical ceiling for reading weights on this machine.
- **Memory:** `ollama ps` showed 85 GB at a context of 131072, 100% GPU, matching my prediction of 80 to 85 GB from the model plus a KV cache of about 40 GiB (80 layers, 8 key/value heads, head size 128). But `free` showed used memory rising by only about 50 GiB, and available memory falling from 117 to 66 GiB. So what Ollama reserves is more than what `free` showed at that moment. Possible reasons are that part of the reserved context buffer had not been touched yet, or that some of the memory is counted as cache. I haven't established which.
- **Usability:** at under 5 tokens/s it is slow for chat, though it works.

## Summary of the benchmarks

All four models were asked the same population question.

| Model | Type | File | Cold start | Warm start | Tokens/s | Memory used (rise) |
| --- | --- | --- | --- | --- | --- | --- |
| `gpt-oss:20b` | MoE, reasoning | 13 GB | 61 s | 7 s | 58.6 | 19 GiB |
| `gpt-oss:120b` | MoE, reasoning | 65 GB | 61 s (42 s thinking) | 9 s | 39.1 | 67 GiB |
| `llama3.1:8b` | dense | 4.9 GB | 30 s | 2 s | 38.2 | 23 GiB |
| `llama3.3:70b` | dense | 42 GB | 70 s | not measured | 4.66 | 50 GiB |

What I take from it:

- **Dense models are limited by reading their weights.** The 8b and 70b models both run at about 190 to 200 GB/s of effective bandwidth, so the speed falls in proportion to the file size.
- **Mixture-of-experts models are much faster than their size suggests.** The 120b model, with more than 1.5 times the file size of the 70b, generates about 8 times as fast, because it uses only 5.1B parameters per token. That is what the Spark's large memory is good for: holding a big MoE model that runs at a usable speed.
- **The context buffer can dominate memory** for small models. The 8b model took 22 GB in `ollama ps` for a 4.9 GB file, because of the 131072-token context.
- **The cold start is a one-off cost** per model load, and it varies in ways I can't fully explain. Warm starts are quick unless the model thinks first.
- **Open questions:** why `free` shows less memory used than `ollama ps` reports for the 70b, the 120b's 67 GiB, and the odd 30 seconds for the 8b cold start.

The benchmarks are enough for now. Next I want to compare what the models are good for, and the apps and tooling that work with them.
