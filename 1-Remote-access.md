# 1 - Remote access

The Spark will mostly run headless, so this stage covers reaching it comfortably from my laptop. The network setup (wired connection, fixed address, working hostname) is in [0-Unboxing.md](0-Unboxing.md).

## SSH keys

I use key-based SSH login from my Ubuntu laptop:

```
ssh-keygen -t ed25519
ssh-copy-id <username>@spark-d66c.local
```

After `ssh-copy-id`, logging in no longer asks for a password.
