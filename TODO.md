# To do

Where the project stands and what could come next. The future items are ordered roughly from simple to advanced.

## Done

- [x] Unboxing, first power-up and the hotspot setup ([0](0-Unboxing.md))
- [x] First-boot setup, SSH login, wired network with a DHCP reservation ([0](0-Unboxing.md))
- [x] First look at the machine: GPU, OS, memory, disk ([0](0-Unboxing.md))
- [x] SSH keys and an SSH alias ([1](1-Remote-access.md))
- [x] Docker with GPU access from containers ([2](2-Docker-and-housekeeping.md))
- [x] System updates, a held package, telemetry check, the OTA check tool ([2](2-Docker-and-housekeeping.md))
- [x] Open WebUI and Ollama from NVIDIA's playbook ([3](3-Open-WebUI-and-Ollama.md))
- [x] Benchmarks of four models: speed, memory, cold and warm starts ([3](3-Open-WebUI-and-Ollama.md))
- [x] Dense against mixture-of-experts models, the KV cache, reasoning models, repetition loops ([3](3-Open-WebUI-and-Ollama.md))
- [x] API access with a key, and a dependency-free Java client ([4](4-Models-and-tooling.md))
- [x] What the model does against what the application adds: memory, system prompts, hidden requests ([4](4-Models-and-tooling.md))
- [x] Document upload, retrieval and Full Context Mode ([4](4-Models-and-tooling.md))
- [x] Web search in three function calling modes ([4](4-Models-and-tooling.md))
- [x] Code interpreter and arithmetic ([4](4-Models-and-tooling.md))
- [x] Survey of four more models on Java, Norwegian and tool use ([5](5-Model-survey.md))
- [x] Glossary and Questions and answers

## In progress

- [ ] Connect the Continue extension in VS Code to the Spark, and try `qwen3-coder:30b` and `qwen3.6` on a Java class ([4](4-Models-and-tooling.md))
- [ ] Check how Ollama stores a model (blobs and manifests) and confirm the GGUF header
- [ ] Check `ollama show gemma3:27b` for its capabilities and template, to confirm that tool support is a metadata gate

## Next

- [ ] Learn more about Ollama: the command line, the `Modelfile`, the native API, parameters such as context length and keep-alive, and how models are stored
- [ ] Reduce the context length and measure the memory and speed effect on a large model
- [ ] Try `gemma3:27b` in Legacy mode with web search, to combine its Norwegian with search
- [ ] Steer the 8b model's tool choice with a system prompt in Native mode
- [ ] Set up system prompts and function calling defaults per model in Open WebUI
- [ ] Task-based comparison of the models, with a fixed set of tasks (code, summaries, instructions, Norwegian)
- [ ] Compare quantizations of the same model, such as Q4 and Q8, for quality, speed and size
- [ ] Structured output (JSON) and a tool-calling loop written by hand in Java
- [ ] Knowledge bases and embedding models: retrieval over my own notes, and a different embedding model through Ollama
- [ ] Vision models: ask questions about an image
- [ ] Coding assistants beyond Continue: autocomplete, JetBrains, and agents such as OpenCode
- [ ] Open WebUI extras: memory, custom tools and functions
- [ ] Run llama.cpp directly, and compare it with Ollama for speed and features
- [ ] Other inference engines from NVIDIA's playbooks (vLLM, TensorRT-LLM) and a speed comparison
- [ ] NVIDIA Sync and the DGX Dashboard
- [ ] Agents and the Model Context Protocol (MCP)
- [ ] Fine-tuning a model with LoRA from one of NVIDIA's playbooks, and converting the result to GGUF
- [ ] Security and operations: turn off SSH password login, back up the Docker volumes, monitor temperature, power and memory
- [ ] Clustering two Sparks over the QSFP ports, if a second one ever appears

## Parked questions

- [ ] Why `free` shows 121 GiB when the Spark is marketed with 128 GB (probably explained in the release notes)
- [ ] Why `ollama ps` reports 85 GB for the 70b model while `free` showed less
- [ ] Why the 8b model's cold start took 30 seconds

## When wrapping up

- [ ] Number the Questions and answers note and the Glossary, and review all documents for repetition
