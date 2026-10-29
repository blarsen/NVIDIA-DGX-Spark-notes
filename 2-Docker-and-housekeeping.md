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

The `nvidia-dgx-telemetry` package is installed. I unchecked both analytics boxes during setup, so I wanted to know whether it respects that choice.

`dpkg -L nvidia-dgx-telemetry` shows two systemd services, `nvidia-dgx-telemetry` and `nvidia-dgx-sol` ("Sign of Life Telemetry"), plus a library, two command-line tools and two JSON config files. Neither service is running, and `systemctl is-enabled` reports `disabled` for both. The package's preset would enable them, so something disabled them after install. The setup wizard honouring my opt-out is the likely explanation, but nothing proves it. The config in `/opt/nvidia/dgx-telemetry/TelemetryConfig.json` has no readable endpoints, only `!localized` placeholders.

The upgrade moves the package from 4.11 to 8.13, and package scripts can re-enable services. So the check is repeated after the upgrade, and `sudo systemctl mask nvidia-dgx-telemetry nvidia-dgx-sol` makes it permanent.

### The upgrade

With `nvidia-modprobe` held, `sudo apt upgrade` upgraded 16 packages, installed one new one (`nvidia-spark-cloud-init`) and kept `nvidia-modprobe` back. No reboot was needed, because the running kernel is current. Restarts of `dgx-dashboard` and `dgx-dashboard-admin` were deferred.

- **Telemetry stayed off.** The new `nvidia-dgx-telemetry` pre-install script printed "Sign of Life service stopped and disabled" and "Telemetry service stopped and disabled". Both services still report `disabled` afterwards, so masking looks unnecessary.
- **`nvidia-spark-cloud-init`** removed several files from `/etc/cloud/` that the Ubuntu installer had left behind, including the one that disabled cloud-init after the first boot. It is NVIDIA's own package and looks deliberate, but it changes how cloud-init behaves.
- **The ConnectX-7 firmware manager** removed its old firmware files and flag files while upgrading. I don't yet know whether the Spark fetches and applies new firmware by itself, or whether that is left to NVIDIA's OTA update.
- A harmless warning came from the old `nvidia-disable-aqc-nic` package: `postrm called with unknown argument 'upgrade'`. dpkg fell back to the new package's script, which worked.

`apt-mark showhold` confirms that `nvidia-modprobe` is held. I rebooted afterwards, to restart the deferred dashboard services and to check the machine comes back cleanly after an upgrade.

After the reboot, `nvidia-smi` and `docker run --rm --gpus all ubuntu nvidia-smi` both still show the GB10 with driver 580.178.04 and CUDA 13.0.

### The OTA check tool

`nvidia-spark-ota-check` compares the installed system with NVIDIA's released OTA updates. Its sub-commands are `is-ota-available`, `torn-score` (how incomplete the current OTA update is, 0 meaning fully applied), `installed-name`, `installed-versions`, `ota-versions` and `summary`. According to its help, `is-ota-available` first updates the tool itself unless the DGX Dashboard is handling automatic updates, so it isn't purely read-only.

### The desktop environment

I'm leaving the desktop environment installed. It uses little memory, and I may want to try it later.
