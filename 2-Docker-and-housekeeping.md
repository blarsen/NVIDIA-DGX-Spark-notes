# 2 - Docker and housekeeping

## Docker

Docker comes preinstalled on DGX OS: Docker Engine 29.6.2 (Docker's own build, not the Ubuntu package), with the buildx and compose plugins.

My user is not in the `docker` group out of the box, so `docker info` and `docker run` fail with "permission denied while trying to connect to the docker API at unix:///var/run/docker.sock".
