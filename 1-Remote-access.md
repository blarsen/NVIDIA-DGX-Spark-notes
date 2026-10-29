# 1 - Remote access

The Spark will mostly run headless, so this stage covers reaching it comfortably from my laptop. The network setup (wired connection, fixed address, working hostname) is in [0-Unboxing.md](0-Unboxing.md).

## SSH keys

I use key-based SSH login from my Ubuntu laptop:

```
ssh-keygen -t ed25519
ssh-copy-id <username>@spark-d66c.local
```

After `ssh-copy-id`, logging in no longer asks for a password.

## SSH alias

An entry in `~/.ssh/config` on the laptop gives the Spark a short name:

```
Host spark
    HostName spark-d66c.local
    User blarsen
```

`ssh spark` now logs in.

## Password login

Password login over SSH is still enabled alongside the key. On a home network that is an acceptable trade-off for now. Turning it off later means setting `PasswordAuthentication no` in a file under `/etc/ssh/sshd_config.d/`, and keeping an existing session open while testing with a new one.
