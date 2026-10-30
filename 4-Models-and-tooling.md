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

The earlier question of how `llama3.1:8b` came to name today's date as its training cutoff has an answer. Asked "What is today's date?" through the API, with no system prompt, the model says it has no access to the current date. Asked the same in an Open WebUI chat, it answers correctly. So Open WebUI supplies the date, and the model confused it with its cutoff. How it supplies it is described under "Layers of prompting": the model calls a built-in tool.


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

The prompt for the second question contained only the question itself, so nothing in it hinted at the count. "5 sections" is correct, but the model listed only two, and where the number came from is unclear. It may be a lucky guess.

### The retrieval settings

Open WebUI's document settings show how retrieval is set up:

- **Embedding model:** `sentence-transformers/all-MiniLM-L6-v2`, a small model that runs inside the container. The embedding engine is "Default (SentenceTransformers)", and the other choices are Ollama, OpenAI and Azure OpenAI, so the embedding model could be a different one.
- **Full Context Mode:** a switch that sends the whole document to the model instead of retrieved chunks.
- **A RAG template:** the instructions wrapped around the retrieved text. The retrieved chunks replace `{{CONTEXT}}`.

<details>
<summary>The default RAG template</summary>

```
### Task:
Respond to the user query using the provided context, incorporating inline citations in the format [id] **only when the <source> tag includes an explicit id attribute** (e.g., <source id="1">).

### Guidelines:
- If you don't know the answer, clearly state that.
- If uncertain, ask the user for clarification.
- Respond in the same language as the user's query.
- If the context is unreadable or of poor quality, inform the user and provide the best possible answer.
- If the answer isn't present in the context but you possess the knowledge, explain this to the user and provide the answer using your own understanding.
- **Only include inline citations using [id] (e.g., [1], [2]) when the <source> tag includes an id attribute.**
- Do not cite if the <source> tag does not contain an id attribute.
- Do not use XML tags in your response.
- Ensure citations are concise and directly related to the information provided.

### Example of Citation:
If the user asks about a specific topic and the information is found in a source with a provided id attribute, the response should include the citation like in the following example:
* "According to the study, the proposed method increases efficiency by 20% [1]."

### Output:
Provide a clear and direct response to the user's query, including inline citations in the format [id] only when the <source> tag with id attribute is present in the context.

<context>
{{CONTEXT}}
</context>
```

</details>

The template explains the earlier behaviour. "If you don't know the answer, clearly state that" is why the price question got an honest answer. Citations are only added when a source carries an `id`, which may be why the answers had no inline citations. One line points the other way: the model may answer from its own knowledge if the document lacks the answer, as long as it says so.

### Full Context Mode

I switched on Full Context Mode, uploaded `0-Unboxing.md` again in a new chat and repeated the three whole-document questions with `llama3.1:8b`:

| Question | Retrieval (chunks) | Full context |
| --- | --- | --- |
| How many sections, and which? | "5", but listed only 2 | Correct: all five headings, in order |
| What is mentioned last? | The end of the Unboxing section | The right section, "First look at the machine", and a fair description of it |
| Summarise in five bullet points | Only the Introduction and Unboxing | Better, with a last bullet covering the setup and SSH |

The summary was still front-loaded: four of the five bullets came from the Introduction and Unboxing, and the more distinctive findings (no LEDs, the hotspot that only starts without a network, the memory results) were missing. A small model summarising a long text may lean on the start, but this single test does not prove that.

The replies were slower by a couple of seconds, which I did not time. That fits the size of the file, about 9 KB or roughly 2,500 to 3,000 tokens, at the 650 to 1,160 tokens/s prompt processing speed seen earlier.

The trade-off: full context sends the whole file with every message, so it only works while the document is small compared with the context window. For a large collection of documents, retrieval is the only option, with the weaknesses shown above.

## Layers of prompting

Between a chat message and the model there are several layers that add text.

**Open WebUI.** All the prompt settings I could find in the UI were empty (the chat controls, the personal system prompt, the per-model settings and the task prompts under the admin interface settings), so the defaults apply. The defaults are in the application code inside the container:

```
docker exec open-webui grep -rn "CURRENT_DATE" /app/backend/open_webui --include=*.py
```

This finds `config.py:2399`, `- Today's date is: {{CURRENT_DATE}}.`, in one of the default prompts, and `utils/task.py`, which substitutes it with `template.replace('{{CURRENT_DATE}}', formatted_date)`. That is plain string replacement, not a template engine.

**Ollama.** Each model has a template that turns the messages into the exact text the model sees. It is written in Go's `text/template` language, since Ollama is written in Go. For `llama3.1:8b`:

```
docker exec open-webui ollama show llama3.1:8b --template
```

- `{{ .System }}`, `{{- ... }}` (whitespace trimming), `range`, `if` and `eq` are Go template syntax. `<|start_header_id|>` and similar are special tokens of the Llama 3 chat format that mark who is speaking.
- A system block is only written when there is a system message or tools. `ollama show llama3.1:8b --system` is empty, so the model has no built-in system prompt.
- "Cutting Knowledge Date: December 2023" appears only in the tool-calling branch, together with the instruction to answer tool results.
- The official Llama 3.1 format has a "Today Date" line, which Ollama's template doesn't include.

**Hidden requests.** The line `- Today's date is: {{CURRENT_DATE}}.` at `config.py:2399` is not in the chat prompt. It is in `DEFAULT_QUERY_GENERATION_PROMPT_TEMPLATE`, a separate request in which Open WebUI asks the model to read the recent chat history (`{{MESSAGES:END:6}}`, the last six messages) and answer only with JSON such as `{ "queries": ["query1", "query2"] }`. Those queries drive web search and document retrieval. The flags just above it, `ENABLE_TITLE_GENERATION`, `ENABLE_SEARCH_QUERY_GENERATION` and `ENABLE_RETRIEVAL_QUERY_GENERATION`, all default to True. So a chat can involve extra requests to the model besides the visible reply: for titles, and for search or retrieval queries.

**The date comes from a tool call.** The date reached the model in an ordinary chat, with all the prompt settings empty, in the following way. Searching the container found the built-in tools `get_current_timestamp` and `calculate_timestamp` in `tools/builtin.py`, added to the available functions in `utils/tools.py:697`:

```
docker exec open-webui grep -rn "current_timestamp\|get_current_time" /app/backend/open_webui --include=*.py
```

The reply to "What is today's date?" in the chat then showed the call: a collapsible "View Result from get_current_timestamp" above the answer, with this output.

```
{
  "current_timestamp": 1793339644,
  "current_iso": "2026-10-30T05:54:04.045236+00:00",
  "user_local_iso": "2026-10-30T06:54:04.045236+01:00",
  "user_timezone": "Europe/Oslo"
}
```

So the model asked for the date itself, and the answer, "Today's date is October 30th, 2026.", came from the result. This is tool calling in miniature, and it happened with no setup on my part:

1. Open WebUI sends the question to the model together with the available tools.
2. The model answers with a structured call instead of text. In Llama's template (see above) that is `{"name": function name, "parameters": {...}}`.
3. Open WebUI runs the function and sends the result back as a `tool` message, which the template labels `ipython`.
4. The model writes the final reply from the result.

One chat question therefore became two model calls with a function run in between. The API request from my Java client offers no tools, which is why it answered that it could not know the date.

## Web search

I set up web search with the DuckDuckGo option (DDGS), which ships inside Open WebUI and needs no API key. Its **Auto (Random)** backend chooses between providers such as Bing, Brave, Google and Yahoo, so queries may go to any of them, from my home connection. The model writes the queries from the chat, so they can contain what I typed.

Setup in Open WebUI:

1. **Admin Settings** → **Web Search**: switch web search on, choose **DDGS** as the engine and leave the backend on Auto. I also switched on **Web Search Confirmation**, which asks before each search.
2. **Admin Settings** → **Models**: under the model's **Capabilities**, enable **Web Search**. This was needed before the option appeared in a chat.
3. In a new chat, **Web Search** is under the **Integrations** button next to the **+** button.

### First attempt

The question was "What is the latest DGX Spark OTA update?" to `llama3.1:8b`, which cannot know the answer, since its training ended in 2023. I confirmed the search prompt. The reply was "Unfortunately, I was unable to find any information on the latest DGX Spark OTA update." The tool call shown was not a web search. Its input was `pattern DGX Spark OTA update` with `case_insensitive true`, which looks like a file search, and its output was `{"error": "No accessible files found"}`.

My reading is that recent Open WebUI versions can let the model choose among several built-in tools (native function calling), and that the small 8b model chose the wrong one. That is an interpretation, not something I have verified.

### Second attempt: gpt-oss:20b

The same question to `gpt-oss:20b` worked. The reply's log showed the model choosing its own steps:

1. `search_knowledge_files`, which found nothing useful.
2. `search_web` with the query "latest DGX Spark OTA update" and 5 results. Three NVIDIA pages came back, including the DGX Spark release notes and a forum post about the October 2026 release.
3. `fetch_url` to read a page, followed by 5 seconds of thinking, and then the answer.

The answer named the October 2026 Release and listed its software stack, with the single source `docs.nvidia.com`. The driver (580.178.04) and the kernel (7.0.0-1019) in it match what this Spark runs, and the October 2026 release matches the OTA2610 reported by `nvidia-spark-ota-check`. I did not verify the other versions listed (DGX OS 7.6.0, CUDA 13.0.3 and so on).

The model decided the sequence itself: think, call a tool, read the result, decide the next step. That loop is what people call an *agent*. The 8b model picked a file tool and gave up after the error, while the 20b recovered and carried on. Choosing among several tools is a place where model quality matters.

The release features included "Dynamic Display Reserved Memory". It may relate to the open question of why `free` shows 121 GiB, which I have not checked.

### Third attempt: llama3.1:8b with Function Calling set to Default

I expected **Default** function calling to make the app run the search itself, instead of the model choosing a tool. That expectation was wrong. The setting was already **Default**, and the model still received tools and called one of them, `search_knowledge_bases` (input `query DGX Spark OTA update`, `skip 0`, `count 1`), which returned `[]`. The model then answered: "Based on the provided tool call output, it appears that the output was empty. Unfortunately, I am unable to provide an answer to the user's question."

Open WebUI's documentation mentions a separate **Legacy** function calling mode, which may be the classic pipeline. I have not tried it.

What the three attempts show:

- The 8b model chose a file or knowledge-base tool both times and never `search_web`. The 20b model also tried `search_knowledge_files` first.
- After an empty result, the 20b model carried on and found the web search, while the 8b model gave up after one step. The difference is largely persistence.

### Fourth attempt: llama3.1:8b with Function Calling set to Legacy

The Function Calling setting offers **Default**, **Native** and **Legacy**. With **Legacy**, the same question to `llama3.1:8b` worked. The interface showed the pipeline stage by stage:

1. **Searching:** the model wrote two search queries, "DGX Spark OTA update October 2026" and "latest DGX Spark update news 2026". This is the query generation prompt found earlier.
2. **Searched 6 sites:** Open WebUI ran the searches and loaded the pages, and listed the sources.
3. **Querying:** a second pair of queries, "DGX Spark OTA update 2026" and "latest DGX Spark update 2026-10". I take these to be the retrieval queries used to pick relevant chunks from the loaded pages with the embedding model, which fits the second query-generation flag found in the config.
4. **The answer**, starting "Based on the provided context", the phrase from the document upload test, so the RAG template is in use.

The answer named the October 2026 Release, announced October 21, 2026, and listed its features and fixed issues, with a note that the context gave no version number. The sources included NVIDIA's release notes and a forum thread on the release. One source was a forum thread about a kernel panic after the update, with a missing initrd for `7.0.0-1019-nvidia`, the kernel this Spark runs. My reboot after the upgrade was fine. The OTA tool's release date for OTA2610 is 2026-10-15, while the answer says October 21, which may be a different event.

In this mode the model does two jobs: it writes queries and summarises what it is given. The application finds, loads, selects and pastes.

### Comparing the modes

| Function calling | Model | Result |
| --- | --- | --- |
| Default | `llama3.1:8b` | Chose a knowledge tool, got `[]`, and gave up |
| Native | `gpt-oss:20b` | Multi-step loop; found and read the right page |
| Legacy | `llama3.1:8b` | The pipeline did the work; a good, sourced answer |

With native tool calling the model decides what to do and can take several steps, but it must be good at choosing. With the legacy pipeline the application does the same fixed steps every time, which suits a small model but is less flexible: it cannot read one chosen page more closely or follow a link.

### Tool calls written out as text

I attached the web page `https://docs.docker.com/desktop/release-notes/` to a new chat with `llama3.1:8b` and asked for the latest minor release of Docker Desktop and the three most important fixes in it. The reply started sensibly ("Based on the provided context, the latest minor release of Docker Desktop is 4.97.0", which I did not verify), then said the fixes were not in the context and went on to write out tool calls in the message:

```
{"name": "list_knowledge_bases", "parameters": {"count": 10, "skip": 0}}
{"name": "search_knowledge_files", "parameters": {"count": 10, "query": "Docker Desktop 4.97.0 fixes", "skip": 0}}
```

This is not hidden reasoning leaking out. It is the model's visible reply. A tool call is text in a specific format that the application must recognise, and here the model wrapped the JSON in a chatty explanation and listed several calls, so the application did not treat it as a call, nothing was executed, and the text was shown as the answer. The model knew about the tools because tools were offered in this chat. The `gpt-oss` models send their thinking through a separate channel, which appears as "Thought" in the interface.
