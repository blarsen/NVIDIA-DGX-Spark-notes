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
