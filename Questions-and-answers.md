# Questions and answers

Questions that came up along the way, with short answers. The details are in the document each answer links to.

## Setting up

**Why doesn't the Spark's setup hotspot appear?**
The hotspot only starts when the Spark boots with no network connected. With an Ethernet cable plugged in, there is no hotspot and no setup page. See [0-Unboxing.md](0-Unboxing.md).

**Why did `spark-d66c.local` not resolve?**
A typo in the hostname, more than once. The network was fine. See [0-Unboxing.md](0-Unboxing.md).

**Do I need to install Ollama separately?**
Not with NVIDIA's Open WebUI playbook. Ollama is bundled in the same container image as Open WebUI. See [3-Open-WebUI-and-Ollama.md](3-Open-WebUI-and-Ollama.md).

## Running models

**Why is the first answer so slow, and later ones fast?**
The first request loads the model from disk into memory. Ollama unloads an idle model after about 5 minutes, so the cost returns after a pause. With reasoning models, much of the remaining wait is the model thinking. See [3-Open-WebUI-and-Ollama.md](3-Open-WebUI-and-Ollama.md).

**Why does a 4.9 GB model use 22 GB of memory?**
Mostly the context buffer (the KV cache), which is reserved for the whole context window, 131072 tokens here. A shorter context would use far less. See [3-Open-WebUI-and-Ollama.md](3-Open-WebUI-and-Ollama.md).

**Why is a 120B model faster than a 70B one?**
The 120B model is a mixture-of-experts model, which uses only about 5B of its parameters per token. The 70B model is dense and reads all of its weights for every token. See [3-Open-WebUI-and-Ollama.md](3-Open-WebUI-and-Ollama.md).

**Why did the model repeat one sentence forever?**
A repetition loop: once a sentence has been repeated, repeating it is the most likely continuation. Nothing stops it by default, so set a maximum number of tokens. See [4-Models-and-tooling.md](4-Models-and-tooling.md).

**What file format does a model downloaded with Ollama have?**
GGUF, a single-file format from the llama.cpp project. The `/api/models` output lists `"format":"gguf"` for every model, with the quantization (`Q4_K_M` or `MXFP4`). See [4-Models-and-tooling.md](4-Models-and-tooling.md).

**Is GGUF also used for training, or only for using models?**
Only for using and distributing them, as far as I know. Training works on full-precision weights in the framework's own checkpoint formats (PyTorch files or safetensors), with gradients and optimizer state that GGUF has no room for. GGUF holds quantized weights, which loses information. The usual route for a fine-tuned model is to train on the original weights, then convert to GGUF with llama.cpp's scripts and quantize.

## What the model does, and what the application does

**Why does the model say "I'll remember that" when it can't?**
Its replies are generated, not reported. Whether the promise is true depends on the application, not the model. See [4-Models-and-tooling.md](4-Models-and-tooling.md).

**How does the model know today's date?**
In an Open WebUI chat it calls a built-in tool, `get_current_timestamp`. Through the plain API it has no tools and does not know. See [4-Models-and-tooling.md](4-Models-and-tooling.md).

**What is the odd JSON with function names in a reply? Is it hidden reasoning leaking out?**
No. It is the model writing a tool call as ordinary text, in a form the application did not recognise. Small models do this when tools are offered. See [4-Models-and-tooling.md](4-Models-and-tooling.md).

**What template language does Ollama use?**
Go's `text/template`. Open WebUI's `{{CURRENT_DATE}}` is a different thing, plain string replacement. See [4-Models-and-tooling.md](4-Models-and-tooling.md).

**Which Function Calling mode should I use?**
It depends on the model. In **Native** mode the model chooses tools itself, which `gpt-oss:20b` handles well. Small models do better in **Legacy**, where the application runs a fixed search pipeline. See [4-Models-and-tooling.md](4-Models-and-tooling.md).

**Why does a model with no tool support fail in Native mode? Is that in the weights?**
The "does not support tools" message is an error from Ollama, relayed by Open WebUI, not something the model said, and it came back almost instantly. Ollama keeps capability metadata for each model (`completion`, `tools`, `thinking`; see `/api/models`), and I believe the tools capability is derived from the model's chat template. Training decides how good a model is at tools, while the template and metadata decide whether Ollama allows them. `ollama show <model>` lists the capabilities. See [5-Model-survey.md](5-Model-survey.md).
