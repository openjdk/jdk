# Setup

## QEMU

You most likely want to setup QEMU user-space emulation at the OS level to transparently execute RISC-V binaries on an x86-64 host.

To do so, first install `rivos-sdk-qemu`:
```
sudo apt-get update
sudo apt-get install -y rivos-sdk-qemu
```

Then, setup binfmt_mist with:
```
docker run --privileged --rm -v /rivos:/rivos -e QEMU_BINARY_PATH=/rivos/qemu/bin tonistiigi/binfmt --install riscv64
```
