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

**Why does a model with no tool support fail in Native mode?**
Open WebUI sends tool definitions with every request. A model without the *tools* capability, such as `gemma3:27b`, rejects them with "does not support tools". See [5-Model-survey.md](5-Model-survey.md).
