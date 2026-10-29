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

## First power-up

I connected an Ethernet cable and the power supply, then pressed the power button.

The Spark has no LEDs anywhere. There's no light on the power button or at the Ethernet socket, and none on the case. The only sign it's on is a very faint fan noise. Without a monitor you can't tell it's running except by seeing it appear on the network.

The Quick Start guide describes two ways to do the first-boot setup:

- **Desktop setup:** plug in a monitor, keyboard and mouse and run the setup on the Spark itself.
- **Hotspot setup:** the Spark creates its own Wi-Fi network, and you join it from another device to run the setup. A sticker on the setup guide has the hotspot SSID (`sparc-d66c`), its password and the setup URL (`http://sparc-d66c.local`).

### Finding it on the network

Despite the lack of lights, the Spark had booted and picked up an address by DHCP. My router lists it as a client named `sparc-d66c`, here `192.168.84.68`.

The hotspot network wasn't visible, probably because the Spark started with a wired network attached.

Trying the setup page over the LAN failed:

- `http://sparc-d66c.local` didn't resolve.
- `http://192.168.84.68/` gave "connection refused".

A full port scan showed only one open port:

```
$ nmap -p- 192.168.84.68
PORT   STATE SERVICE
22/tcp open  ssh
```

So the Spark is fully booted and its SSH server is running, but the setup web page isn't served on the LAN. The sticker URL most likely only works while connected to the Spark's own hotspot. SSH is of no use yet, because a factory-fresh Spark has no user account until first-boot setup creates one. After setup it should be the way to work with the Spark headless.

Next step: shut the Spark down, unplug the Ethernet cable, power it on again and look for the hotspot.
