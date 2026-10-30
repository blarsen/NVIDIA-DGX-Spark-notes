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

### Where the date comes from

The earlier question of how `llama3.1:8b` came to name today's date as its training cutoff has an answer. Asked "What is today's date?" through the API, with no system prompt, the model says it has no access to the current date. Asked the same in an Open WebUI chat, it answers correctly. So Open WebUI supplies the date, and the model confused it with its cutoff. I haven't looked up exactly how Open WebUI inserts it.


### Experiment: the model has no memory

Two separate requests with `llama3.1:8b`:

```
java java/ChatClient.java llama3.1:8b "My favourite colour is teal. Remember that."
java java/ChatClient.java llama3.1:8b "What is my favourite colour?"
```

The first reply was "I'll remember that your favourite colour is teal.", which is a promise the model cannot keep. The second reply said it has no information about my preferences and that it is "a new conversation each time".

Then one request that carries the earlier exchange in `messages`:

```
curl -s http://spark-d66c.local:8080/api/chat/completions \
  -H "Authorization: Bearer $(cat $SPARK_API_KEY_FILE)" \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3.1:8b","messages":[
        {"role":"user","content":"My favourite colour is teal. Remember that."},
        {"role":"assistant","content":"Got it."},
        {"role":"user","content":"What is my favourite colour?"}]}'
```

The reply was "Your favourite colour is teal! I remember!". Two things to note:

- The assistant's "Got it." was written by me, not by the model. The model accepts whatever history it is given as its own past, so an application can edit, summarise or invent earlier turns. Memory features work this way.
- The prompt was 38 tokens, against 16 for a single short message. Every turn resends and reprocesses the whole conversation, so long chats get slower, until they hit the context limit.

### Why the model promised to remember

The false "I'll remember that" is not lying, since that would need the model to know the truth and intend to deceive. As I understand it, a model's reply is generated, not reported: it produces the most plausible continuation of the conversation, and "I'll remember that" is a very likely answer to "remember that", especially after tuning to be an agreeable assistant.

Whether the promise is true depends on the application, not the model. With a memory feature, the application stores the fact and inserts it into later prompts, so the sentence is true. In a bare API call there is no such layer, so it is false. The model cannot inspect its surroundings unless a system prompt describes them, and its self-descriptions are not consistent: in the very next request it correctly said it was "a new conversation each time".

So what a model says about its own capabilities or history is not a source of facts. What the application actually does is. The same applies to the training cutoff claim earlier.

## Document upload

I uploaded `0-Unboxing.md` in an Open WebUI chat with `llama3.1:8b` and asked two questions:

- "Which USB-C port is the power input?" The answer was "The left-most USB-C port is the power input.", which is what the file says.
- A question the file doesn't answer, about the price of the DGX Spark: "Unfortunately, the provided context does not mention the price of the DGX Spark."

The wording "the provided context" shows the retrieval at work. Open WebUI pasted passages from the file into the prompt together with an instruction to answer from them, and the model reported that the price was not among them. Without that framing, a model asked the price of a DGX Spark would be likely to guess.

### Questions about the whole document

Retrieval pastes only a few chunks of the file into the prompt, so questions about the document as a whole are a weak spot. In the same chat I asked three such questions about `0-Unboxing.md`, which has five `##` sections (Introduction, Unboxing, First power-up, First-boot setup, First look at the machine) plus `###` subsections.

| Question | Answer | Actual |
| --- | --- | --- |
| Summarise in five bullet points | Five accurate bullets, all from the Introduction and Unboxing sections | Nothing from the first power-up, the setup, the SSH login or the GPU, about three quarters of the file |
| How many sections, and their headings? | "5 sections", but listed only 2, and presented lines such as "Key facts" and "The box is spartan." as subheadings | The real subheadings, such as "Finding it on the network", were missing |
| What is mentioned last? | The plan to do the setup from another machine | That is the end of the Unboxing section. The file ends with the disk figures |

The model only saw the beginning of the file, and its answers presented that part as the whole. Each answer was fluent and nothing in it was false in itself, but it was incomplete in a way the reply did not admit. The answers carry a "1 Source" label that expands to the file name.

The prompt for the second question contained only the question itself, so the "5 sections" count came from the retrieved text, not from anything in the prompt. The count is right, but the model could list only two of them.
