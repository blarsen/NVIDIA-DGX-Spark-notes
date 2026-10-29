# 2 - Docker and housekeeping

## Docker

Docker comes preinstalled on DGX OS: Docker Engine 29.6.2 (Docker's own build, not the Ubuntu package), with the buildx and compose plugins.

My user is not in the `docker` group out of the box, so `docker info` and `docker run` fail with "permission denied while trying to connect to the docker API at unix:///var/run/docker.sock".

The fix is to add the user to the group, then log in again:

```
sudo usermod -aG docker $USER
```

Membership in the `docker` group is effectively root access, because anyone in it can mount the host's filesystem into a container. On a single-user machine that is the usual trade-off, and the setup most playbooks assume. Rootless Docker is the stricter alternative.

Afterwards both checks worked:

- `docker run --rm hello-world` pulled the `arm64v8` image and ran it.
- `docker run --rm --gpus all ubuntu nvidia-smi` showed the GB10 from inside a container, with the same driver (580.178.04) and CUDA 13.0 as the host. So NVIDIA's container runtime is already configured, and containers can use the GPU without further setup.

## System updates

`sudo apt update` showed 17 upgradable packages, none of them the kernel or the GPU driver:

- **DGX system tools:** `dgx-dashboard`, `dgx-oobe*`, `dgx-spark-ota-update-meta`, `nvidia-spark-ota-check`.
- **Firmware helpers:** `nvidia-spark-mlnx-firmware-manager`, `nvidia-spark-wifi-fw-ppa`, `nvidia-disable-aqc-nic`.
- **Other NVIDIA software:** `nvidia-ai-workbench`, `nvidia-modprobe`, `nvidia-dgx-telemetry`.
- **Ubuntu security fixes:** `libfreerdp*`, `libwinpr2`, `python3-pip*`, `python3-wheel`.

### A package to hold back

`nvidia-modprobe` was offered at 615.71.10, while the installed driver is in the 580 series. `apt policy nvidia-modprobe` shows why: NVIDIA's CUDA repository carries every driver series from 535 to 615, and `apt` offers the highest version number as the candidate. That is an artefact of the repo, not a recommendation for the Spark, so I held the package at its installed version:

```
sudo apt-mark hold nvidia-modprobe
```

### Telemetry

The `nvidia-dgx-telemetry` package is installed. I unchecked both analytics boxes during setup, so I want to find out what it does and whether it respects that choice.
