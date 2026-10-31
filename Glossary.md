# Glossary

Terms I ran into while setting up local LLMs, explained for a developer who knows Java but not LLM internals. Where a figure comes from this machine, the document that recorded it is linked.

**Model, weights, parameters.** A model is a very large set of numbers, the *weights* or *parameters*, plus the code that applies them to an input. "20B" or "120B" is the number of parameters, in billions. The weights are the bulk of the file you download, so parameter count times bits per parameter gives roughly the file size.

**Inference.** Running a trained model to get output, as opposed to training it. Everything on this machine so far is inference.

**Token.** The unit a model reads and writes: a word, part of a word, or punctuation. Models measure input, output and memory in tokens, not characters or words.

**Tokens per second (tokens/s).** How fast the model generates output. About 58 tokens/s for `gpt-oss:20b` and 39 for `gpt-oss:120b` on this machine ([3-Open-WebUI-and-Ollama.md](3-Open-WebUI-and-Ollama.md)).

**Context window.** The number of tokens the model can take into account at once: the conversation so far plus the answer it is writing. `gpt-oss` has a 128K-token window. It is a hard limit, a bit like a fixed-size buffer.

**KV cache.** Memory that holds intermediate results for the tokens already processed, so the model doesn't recompute them for every new token. It grows with the length of the conversation, which is part of why a model needs more memory than its file size.

**Prefill and decode.** The two phases of a response. *Prefill* processes your prompt. *Decode* generates the answer one token at a time. The tokens/s figure above is decode speed.

**Quantization.** Storing the weights with fewer bits each, to cut memory and file size at a small cost in quality. `gpt-oss` uses **MXFP4**, about 4.25 bits per parameter, instead of the 16 bits a full-precision model would use.

**Mixture of experts (MoE).** An architecture where the model contains many specialist sub-networks, the *experts*, and uses only a few of them for each token. Total parameters are what must fit in memory. *Active parameters* are what is actually used per token: 3.6B of 21B for `gpt-oss:20b`, and 5.1B of 117B for `gpt-oss:120b`. That is why a large MoE model can generate faster than its size suggests.

**Dense model.** The opposite of a mixture-of-experts model: every parameter is used for every token. Speed then falls roughly in proportion to the model's size ([3-Open-WebUI-and-Ollama.md](3-Open-WebUI-and-Ollama.md)).

**Reasoning model, thinking tokens.** A model that writes out its reasoning before the final answer. Those tokens cost time, which is why a reply can take several seconds to start. Open WebUI shows it as "Thought for N seconds". `gpt-oss` has three effort levels: low, medium and high.

**Cold start and warm start.** The first request after a model has been unloaded must read the model from disk into memory. Later requests, while it is still loaded, skip that. Ollama unloads an idle model after a timeout, 5 minutes by default.

**Unified memory.** The Spark's CPU and GPU share one pool of memory, so a model that fits in it can be used by the GPU without a separate copy, and there is no separate VRAM figure to report ([0-Unboxing.md](0-Unboxing.md)).

**Ollama.** A program that downloads models and runs them behind a simple local API.

**Open WebUI.** A browser-based chat interface that can talk to Ollama and other model servers.

**System prompt.** Instructions placed at the start of the conversation that set the model's behaviour, and often give it facts such as the date. The user normally doesn't see it.

**Embedding model.** A separate, small model that turns text into a list of numbers so that texts with similar meaning end up close together. It is not used for chat.

**Retrieval (RAG).** Retrieval-augmented generation: instead of giving the model a whole document, the application finds the chunks relevant to the question, using an embedding model, and pastes only those into the prompt.

**Tools (function calling).** A model that supports tools can answer with a structured request to run a function instead of ordinary text. The application runs the function and sends the result back to the model.

**Agent.** A model used in a loop: it decides on a step, calls a tool, reads the result and decides the next step, until it can answer.

**GGUF.** The single-file model format used by llama.cpp and Ollama. It holds metadata (architecture, context length, tokenizer) and the weights in a chosen quantization.

