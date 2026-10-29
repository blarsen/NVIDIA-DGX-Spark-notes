# 0 - Unboxing

## Introduction

The NVIDIA DGX Spark is a small desktop AI computer, roughly the size of a thick paperback, aimed at developers who want to run, fine-tune and experiment with AI models locally instead of renting cloud GPUs.

Key facts:

- **Chip:** NVIDIA GB10 Grace Blackwell Superchip. A 20-core Arm CPU and a Blackwell GPU sit on one package.
- **Memory:** 128 GB of unified memory shared between CPU and GPU. This is the main selling point. Large models that wouldn't fit in the VRAM of a typical consumer GPU can be loaded here. The trade-off is that memory bandwidth is lower than on high-end discrete GPUs, so token generation speed is more modest.
- **Software:** Ships with DGX OS, NVIDIA's Ubuntu-based Linux distribution, preconfigured with the NVIDIA driver, CUDA and AI tooling.
- **Architecture note:** The CPU is Arm (aarch64), not x86. Most things work, but some prebuilt binaries and containers need an arm64 build.

### Useful resources

- [DGX Spark product page](https://www.nvidia.com/en-us/products/workstations/dgx-spark/): official overview and specifications.
- [DGX Spark documentation](https://docs.nvidia.com/dgx/dgx-spark/index.html): the user guide covering setup, hardware and DGX OS.
- [DGX Spark Playbooks (build.nvidia.com/spark)](https://build.nvidia.com/spark): NVIDIA's step-by-step guides for common tasks such as running LLMs, fine-tuning and setting up tools.
- [NVIDIA developer forum, DGX Spark / GB10](https://forums.developer.nvidia.com/c/accelerated-computing/dgx-spark-gb10/719): community Q&A, known issues and tips.
- [llama.cpp](https://github.com/ggml-org/llama.cpp): a widely used engine for running LLMs locally. Useful background for how local inference works.
- [Hugging Face LLM Course](https://huggingface.co/learn/llm-course): a free introduction to LLM concepts and terminology.

## Unboxing

The box is spartan. It contains:

- The DGX Spark itself, a small unit in a gold finish.
- A power supply.
- A support guide sheet.
- A minimal Quick Start guide.

The rear has a single button, assumed to be power, and these connectors:

- **Four USB-C ports.** The left-most one is the power input, so three remain for peripherals.
- **HDMI.**
- **Ethernet.**
- **Two QSFP ports.** These are the high-speed ConnectX-7 network ports, intended for linking Sparks together or for very fast networking.

The Spark is a complete computer in its own right. A monitor, keyboard and mouse can be plugged in and used directly. I expect to run it mostly headless, so the plan is to connect power and network only and do the setup from another machine.
