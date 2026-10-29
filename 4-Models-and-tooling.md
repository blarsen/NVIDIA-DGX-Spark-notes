# 4 - Models and tooling

With a few models running, the questions are what each is good for, and which apps and tools can use them. This document covers the tooling first, then the model families.

## API access through Open WebUI

Only Open WebUI's port, 8080, is published from the container, so Ollama's own API isn't reachable from outside. Open WebUI has an OpenAI-compatible API of its own, which works without changing the container.

API keys are off by default. I found the switch in the admin settings under **System** → **Authentication**. After enabling it, **Settings** → **Account** offers **API Keys**, and I generated a key for my user. The key is a secret, so it stays out of these notes.

Listing the models from my laptop:

```
curl http://spark-d66c.local:8080/api/models -H "Authorization: Bearer <key>"
```

The response includes metadata for each model:

| Model | Parameters | Quantization | Capabilities |
| --- | --- | --- | --- |
| `gpt-oss:20b` | 20.9B | MXFP4 | completion, tools, thinking |
| `gpt-oss:120b` | 116.8B | MXFP4 | completion, tools, thinking |
| `llama3.1:8b` | 8.0B | Q4_K_M | completion, tools |
| `llama3.3:70b` | 70.6B | Q4_K_M | completion, tools |

All four report a context length of 131072 tokens. *Tools* means the model can ask the calling program to run a function, which coding assistants and agent frameworks rely on. Only the `gpt-oss` models have *thinking*.

### A chat request

A chat request to the OpenAI-compatible endpoint, with the key read from a file kept outside this repository:

```
curl http://spark-d66c.local:8080/api/chat/completions \
  -H "Authorization: Bearer $(cat apikey.secret)" \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3.1:8b","messages":[{"role":"user","content":"Say hello in one sentence."}]}'
```

The response is JSON in OpenAI's format, with the answer in `choices[0].message.content` ("Hello, how are you today?"). Besides the usual token counts, `usage` includes Ollama's timings: 16 prompt tokens processed at 649 tokens/s, 8 tokens generated at 50 tokens/s, 189 ms in total, and a load time of about 1 ms, so the model was already loaded.

The generation speed here, 50 tokens/s, is higher than the 38.2 measured earlier. With only 8 tokens generated, the sample is too small to compare.
