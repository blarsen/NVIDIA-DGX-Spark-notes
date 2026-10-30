# 5 - Model survey

A comparison of four models from other families, run through Open WebUI with the same three requests, one trial each:

1. "Explain what a Java virtual thread is."
2. "Gi meg en oppskrift på cookies" (a cookie recipe, asked in Norwegian, with no system prompt).
3. "What is the latest DGX Spark OTA update?", with web search on and Function Calling set to Native.

The numbers come from the chat exports (Open WebUI's `usage` data). The judgements of the Java content come from my own knowledge of Java, and of the Norwegian from the reading of an AI that is not a native speaker, so both are open to correction.

| Model | Type (as far as I know) | Supports tools |
| --- | --- | --- |
| `gemma3:27b` | dense | No |
| `mistral-small:24b` | dense | Yes |
| `qwen3-coder:30b` | mixture of experts, coding | Yes |
| `qwen3.6:latest` | probably mixture of experts, with thinking | Yes |

## Speed

Generation speed for the Java question, with the total time for the reply:

| Model | Tokens/s | Reply | Total time |
| --- | --- | --- | --- |
| `gemma3:27b` | 12.3 | 1469 tokens | 122 s |
| `mistral-small:24b` | 13.8 | 476 tokens | 47 s |
| `qwen3-coder:30b` | 59.2 | 303 tokens | 14 s (6 s loading) |
| `qwen3.6:latest` | 77.0 | 1018 tokens | 44 s (16 s loading, 5 s thinking) |

The pattern matches the earlier benchmarks. The two dense models run at 12 to 14 tokens/s, which is why `gemma3:27b` felt laggy. The two models that are fast for their size are, I believe, mixture-of-experts models, which use only part of their weights per token. The 30B `qwen3-coder` is faster than the dense 8B Llama (38 tokens/s).

If the files are around 17 GB for `gemma3:27b` and 14 GB for `mistral-small:24b` (sizes from memory, not checked), 12.3 and 13.8 tokens/s correspond to about 200 GB/s of weights read, the same level as the dense Llama models.

## The cost of tools

The first message, a seven-word question, was processed as this many prompt tokens:

| Model | Prompt tokens |
| --- | --- |
| `gemma3:27b` | 18 |
| `mistral-small:24b` | 5,985 |
| `qwen3.6:latest` | 6,846 |
| `qwen3-coder:30b` | 7,754 |

`gemma3:27b` does not support tools, and in Native mode Open WebUI failed both the first Java question and the OTA question with "registry.ollama.ai/library/gemma3:27b does not support tools". The Java question worked on a retry with a different setting (the export does not say which). The 18 tokens are the question itself. The other three models received thousands of extra tokens, which I take to be the tool definitions and instructions that Native mode adds to every request. Tools are not free: they cost a few seconds of prompt processing, and room in the context window, on every message.

## Java

| Model | Verdict |
| --- | --- |
| `qwen3-coder:30b` | Short and correct. It says "introduced in Java 21", which skips the preview in Java 19 and 20, and it leaves out carrier threads and how to create one. |
| `qwen3.6:latest` | The best answer: JDK 21, carrier threads in a `ForkJoinPool`, many-to-many mapping, a comparison with `CompletableFuture` and reactive frameworks. A small mismatch: the text says `Thread.ofVirtual()` and the example uses `Thread.startVirtualThread`. |
| `mistral-small:24b` | Generic and mostly harmless. It says "introduced in Java 19", the preview version, and does not mention carrier threads. |
| `gemma3:27b` | Long, well structured and wrong in several places: see below. |

The `gemma3:27b` answer shows how fluent text can be wrong:

- It calls virtual threads "stackless" and says they work by "continuation passing style". They do have stacks, kept on the heap, and are implemented with continuations.
- It says the mechanism is structured concurrency, which is a separate API.
- It says `BlockingQueue` negates the benefits of virtual threads. It doesn't: blocking operations are what virtual threads handle well.
- Its example does not compile. It uses the loop variable `i` inside a lambda, and `i` is not effectively final.
- It leaves out the real pitfalls, such as pinning.

## Norwegian

My reading, and I am not a native speaker:

| Model | Verdict |
| --- | --- |
| `gemma3:27b` | The best: natural recipe language (`natron`, `stekebrett`, `bakepapir`, `sjokoladebiter`) and plausible proportions in grams. |
| `mistral-small:24b` | Readable, with plausible proportions, but with words that look invented or wrong, such as `baktøy` for baking powder and `teaske`. |
| `qwen3.6:latest` | A usable recipe with oddities (`chokladkjeks`, `Stem opp ovnen`, `bunne`). The flour amount is wrong: 2½ dl is roughly 150 g, not 300 g. |
| `qwen3-coder:30b` | Poor, with nonsense words such as `tørket jæsrød` for baking soda and `røysukker`, and 3 tsp of herbs in a cookie recipe. This is a coding model. |

The `gemma3:27b` recipe was the one I would trust, and it is also the model that cannot use tools in Native mode.

## The OTA question

| Model | What it did | Result |
| --- | --- | --- |
| `gemma3:27b` | Failed at once: no tool support | Error |
| `mistral-small:24b` | One search, no page fetched | Vague: "improvements in performance, stability" |
| `qwen3-coder:30b` | One search, 5 seconds | Correct October 2026 Release, with links, but no version numbers |
| `qwen3.6:latest` | Four searches, then `fetch_url`, 62 seconds | The best: the October 2026 Release with DGX OS 7.6.0, driver 580.178.04 and CUDA 13.0.3, a citation, and the details of the release |

The `qwen3.6` answer matches this machine (driver 580.178.04). Its notes show something worth keeping in mind. The model's training knowledge is stale, so it first doubted the search results: it took the "2026" dates for typos and believed the DGX Spark was announced in January 2024. It kept going anyway, found the primary source with `fetch_url` and trusted that. The `mistral-small` reply also contained a stray `[REF]3[/REF]` marker, a citation format that Open WebUI did not translate.

## Summary

- No model won everything. `qwen3.6` was best at Java and at searching, `gemma3:27b` at Norwegian, `qwen3-coder` was fast and accurate on a short question.
- Tool support is a capability that decides what a model can do in the application, and it is listed in the model's metadata.
- Dense models of 24 to 27B run at 12 to 14 tokens/s on this machine, while the mixture-of-experts models run at 59 to 77 tokens/s.
- A longer answer gave the model more room to be wrong.
