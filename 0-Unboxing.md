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
- **Hotspot setup:** the Spark creates its own Wi-Fi network, and you join it from another device to run the setup. A sticker on the setup guide has the hotspot SSID (`spark-d66c`), its password and the setup URL (`http://spark-d66c.local`).

### Finding it on the network

Despite the lack of lights, the Spark had booted and picked up an address by DHCP. My router lists it as a client named `spark-d66c`, here `192.168.84.68`.

The hotspot network wasn't visible, probably because the Spark started with a wired network attached.

Trying the setup page over the LAN at `http://192.168.84.68/` gave "connection refused". A full port scan showed only one open port:

```
$ nmap -p- 192.168.84.68
PORT   STATE SERVICE
22/tcp open  ssh
```

So the Spark is fully booted and its SSH server is running, but the setup web page isn't served on the LAN. The sticker URL most likely only works while connected to the Spark's own hotspot. SSH is of no use yet, because a factory-fresh Spark has no user account until first-boot setup creates one. After setup it should be the way to work with the Spark headless.

### Shutting down and starting the hotspot

A short press of the power button did not shut the Spark down. Cool air kept coming from the fan at the front. Holding the button for about three seconds did shut it down.

I unplugged the Ethernet cable and powered it on again. The `spark-d66c` Wi-Fi network then appeared. So **the hotspot only starts when the Spark boots with no network connected**. With a cable plugged in, you won't get the setup page.

### Reaching the setup page

I joined the hotspot from a laptop running Ubuntu 26.04. A captive portal appeared and showed the setup URL from the sticker. Entering `http://spark-d66c.local` in the browser opened the setup.

An iPad did not work for this. It kept switching back to its default Wi-Fi network, so the setup page never loaded and just hung on "Loading". If you try it from a phone or tablet, make sure the device stays on the Spark's network. Turning off auto-join for your usual network helps. A laptop avoids the problem.

## First-boot setup

The setup is a sequence of screens:

1. **Welcome:** **Get Started**.
2. **Language and timezone:** left at the defaults, US - English and Europe/Oslo.
3. **Terms of Use:** accepted.
4. **Create account:** username, password and password confirmation. This is the first user on the device.
5. **Analytics:** two checkboxes, *Analytics and usage data* (checked by default) and *Crash and failure data* (unchecked by default). I unchecked both. The box belongs to my employer, so opting it into data collection isn't my call.
6. **Wi-Fi:** the screen lists nearby networks. I picked my home network and entered its password, and it joined. The Ethernet cable stays unplugged during setup.
7. **System updates:** the setup shows "Preparing system updates" and starts downloading. This takes a while.
8. **Setup Complete!** The system updates took about 12 minutes over Wi-Fi. **Get Started** then forwards to `build.nvidia.com/spark`, NVIDIA's playbooks site.

When I picked my home network, the Spark shut down its hotspot. My laptop rejoined my normal home Wi-Fi by itself, and the setup page carried on at `http://spark-d66c.local`.

### Logging in over SSH

After setup, the Spark was no longer at the address it had during the first boot, and I couldn't find it in the router's DHCP list. A scan of the subnet found it:

```
nmap -p 22 --open 192.168.84.0/24
```

The Spark was at `192.168.84.74` on Wi-Fi, a different address from the `192.168.84.68` it had on Ethernet during the first boot. SSH login to that address worked.

### Moving to the wired network

The Spark had two addresses, one per interface (`ip -br addr`):

- Ethernet (`enP7s7`): `192.168.84.68`, route metric 100.
- Wi-Fi (`wlP9s9`): `192.168.84.74`, route metric 600.

Linux uses the lowest metric, so traffic already went over the cable. I turned Wi-Fi off with `nmcli radio wifi off` and reserved `192.168.84.68` for the Ethernet MAC address in the router's DHCP settings. The Spark now has one stable address.

The home network is `192.168.84.0/22`, which covers 192.168.84.0 to 192.168.87.255. The earlier `/24` scan was lucky to find it. Scan the whole `/22` in future.

The `.local` name works too: `ping spark-d66c.local` resolves to `192.168.84.68`. My earlier failures were typos in the hostname.

## First look at the machine

After logging in over SSH, I ran a few commands to see what I have.

**GPU** (`nvidia-smi`):

- The GPU is an **NVIDIA GB10**, driver **580.178.04**, **CUDA 13.0**.
- It idles at 35 °C, drawing 4 W.
- GPU memory usage shows "Not Supported". That's expected on this machine. The CPU and GPU share one pool of memory, so there's no separate VRAM to report. For a Java developer, think of it as one big heap that both sides use, with no copying between "host" and "device" memory.
- The only GPU processes are `Xorg` and `gnome-shell`, using 18 MiB and 6 MiB. DGX OS includes a desktop environment even though I'm using the machine headless.

**OS, kernel and architecture** (`/etc/os-release`, `uname -a`):

- The OS is **Ubuntu 24.04.5 LTS (Noble Numbat)**. This is DGX OS, NVIDIA's customised Ubuntu, so everything Ubuntu applies: `apt`, `systemd` and so on.
- Kernel `7.0.0-1019-nvidia`, built for **aarch64**. This confirms the Arm CPU: binaries and containers need arm64 builds.

**Memory** (`free -h`):

- **121 GiB** total, with 2.6 GiB used and 119 GiB available.
- 15 GiB of swap, unused.
- The Spark is marketed as 128 GB, so why 121 GiB shows up is an open question. Part of the memory may be reserved by firmware or the hardware.

**Disk** (`df -h /`):

- A **3.7 TB** NVMe drive, with 38 GB used and 3.5 TB free.
