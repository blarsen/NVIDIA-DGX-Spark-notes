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

## A Java client

[`java/ChatClient.java`](java/ChatClient.java) sends a chat request from Java, using only the JDK's `HttpClient` (JDK 25, run directly as a source file, with no build step). The JDK has no JSON parser, so a few small methods pick out the fields needed by hand. That is enough for a demo, and a real project should use a JSON library such as Jackson.

```
export SPARK_API_KEY_FILE=~/.config/spark-apikey.secret
java java/ChatClient.java llama3.1:8b "Say hello in one sentence."
```

The answer goes to stdout and a line of statistics to stderr:

```
Hello!

[3 tokens, 59.55 tokens/s, 4.1 s in total]
```

The 4.1 seconds in total is mostly the model being loaded, since the generation speed was 59.55 tokens/s. The key comes from `SPARK_API_KEY` or from the file named in `SPARK_API_KEY_FILE`, and the address can be changed with `SPARK_URL`.

A prompt with quotes and line breaks checks the escaping in both directions:

```
java java/ChatClient.java llama3.1:8b $'Repeat this back exactly, including the quotes:\n"foo" and "bar"\nthen say OK.'
```

The reply came back as `"foo" and "bar"` and `OK.` on separate lines, so the quotes and line breaks survived the JSON round trip. The model left out the first line of the prompt, which is the model's behaviour and not a client problem. This run took 0.2 s in total at 49 tokens/s, with the model already loaded.

## What the model does, and what the application adds

Chat products like ChatGPT and Claude feel like one thing. Running models locally separates the two layers.

**The model by itself** takes a sequence of tokens and continues it. Each request is independent:

- It has no memory between requests. The request to the API carries the whole conversation in its `messages` list, and the application resends it every time. The model "remembers" because the history is pasted into each request.
- It knows nothing after its training cutoff, and does not know the date unless it is told.
- It cannot read files, browse the web or run code. It can only produce text.
- A model with the *tools* capability can write a structured request, such as "call `search` with this query". The application has to run it and send the result back as more text.

**The application** (here Open WebUI) adds most of what the commercial products are known for:

- Chat history, accounts, and switching models mid-chat.
- A *system prompt* that sets behaviour and may include the current date.
- *Retrieval* from uploaded files: the document is split into chunks, a separate *embedding* model finds the chunks most relevant to the question, and only those are pasted into the prompt.
- Web search, with the results pasted into the prompt.
- Running tools and code, and feeding the results back.
- Memory across chats, which is stored text inserted into the prompt.

Part of what feels like a model getting smarter in a commercial product is the application layer getting better.
