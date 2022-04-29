```
Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

The Universal Permissive License (UPL), Version 1.0

Subject to the condition set forth below, permission is hereby granted to
any person obtaining a copy of this software, associated documentation
and/or data (collectively the "Software"), free of charge and under any
and all copyright rights in the Software, and any and all patent rights
owned or freely licensable by each licensor hereunder covering either (i)
the unmodified Software as contributed to or provided by such licensor,
or (ii) the Larger Works (as defined below), to deal in both

(a) the Software, and

(b) any piece of software and/or hardware listed in the lrgrwrks.txt file
if one is included with the Software (each a "Larger Work" to which the
Software is contributed by such licensors),

without restriction, including without limitation the rights to copy,
create derivative works of, display, perform, and distribute the Software
and make, use, sell, offer for sale, import, export, have made, and have
sold the Software and the Larger Work(s), and to sublicense the foregoing
rights on either these or other terms.

This license is subject to the following condition:

The above copyright notice and either this complete permission notice or
at a minimum a reference to the UPL must be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
USE OR OTHER DEALINGS IN THE SOFTWARE.

Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
or visit www.oracle.com if you need additional information or have any
questions.
```

---

# hsdis - a HotSpot plugin for disassembling dynamically generated code

The files in this directory are built independently of the HotSpot JVM.

hsdis is an interface exposed by Hotspot. There are several backends that
implement this interface, using different disassembly engines. Included in the
JDK is support for building hsdis with Capstone, LLVM or GNU binutils. The
interface is fairly straightforward and easy to implement using other backends.

## Building and installing

To compile hsdis, you need to activate hsdis support, and select the proper
backend to use. This is done with the configure switch `--with-hsdis=<backend>`,
where `<backend>` is either `capstone`, `llvm` or `binutils`. For details, see
the sections on the respective backends below.

To build the hsdis library, run `make build-hsdis`. This will build the library
in a separate directory, but not make it available to the JDK in the
configuration. To actually install it in the JDK, run `make install-hsdis`.

**NOTE:** If you do this using the binutils backend, the resulting build may not
be distributable. Please get legal advice if you intend to distribute the result
of your build.

## Using the library

The hsdis library will be automatically loaded by Hotspot when you use the
diagnostic option `-XX:+PrintAssembly`. Note that since this is a diagnostic
option, you need to unlock these first, so in practice you activate it using
`-XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly`.

If using the LLVM backend on Windows, you need to be sure that the LLVM DLL file
(or files) can be found by hsdis. In practice, this means that you either need
to copy `LLVM-C.DLL` to a place on your `PATH` or the JDK `bin` directory, or
you need to augment your `PATH` variable to also point to where you installed
LLVM (like `C:\LLVM\bin`).

More information is available at the [HotSpot
wiki](https://wiki.openjdk.java.net/display/HotSpot/PrintAssembly).

## Building with Capstone

To build this project using Capstone you need to have Capstone installed.
Typical ways of installation can be `sudo apt install libcapstone-dev` (on
Debian and derivatives), or `brew install capstone` (on macOS with Homebrew).
For Windows, you need to download the "Core Engine", and unzip it. See the
[Capstone Download
page](https://www.capstone-engine.org/download.html#windows---core-engine-) for
up-to-date download links.

This has been tested with Capstone v4.0.2, but earlier (and later) versions are
also likely to work.

To build hsdis using Capstone, you must enable it in configure by `bash
configure --with-hsdis=capstone`.

On Linux and macOS, the location Capstone can most often be auto-detected. If
this fails, or if you are building on Windows, you need to specify where
Capstone is located using `--with-capstone=<path>`. This path should point to
where you have extracted the Core Engine zip file.

## Building with LLVM

To build this project using LLVM you need to have LLVM installed. Typical ways
of installation can be `sudo apt install llvm` (on Debian and derivatives), or
`brew install llvm` (on macOS with Homebrew). For Windows, see below.

This has been tested with LLVM v13.0.0, but earlier (and later) versions are
also likely to work.

To build hsdis using LLVM, you must enable it in configure by `bash configure
--with-hsdis=llvm`.

If `llvm-config` is not in your path, you will need to specify the LLVM home using
`--with-llvm=<LLVM home>`. Example: If your `llvm-config` is in `~/my-llvm/bin`,
then you should use `--with-llvm=~/my-llvm`.

### Building with LLVM on Windows

Getting a usable installation on Windows is trickier than on the other
platforms. You can download (and patch) the official distribution, or you can
build it yourself.

Links to the latest version of the official build is available at [LLVMs
download page](https://releases.llvm.org/download.html). Download the file
*LLVM-nn.n.n-win64.exe*, and run it to let it install itself. The default
installation location is `C:\Program Files\LLVM`. This is not ideal due to the
spaces in the path, so it is recommended to put it elsewhere (e.g. `C:\LLVM`).

For very unclear reasons, the official Windows build is missing almost all LLVM
include files. (At least this was the case up to and including LLVM 13.) You
will need to complement your installation with the proper include files. One way
to do this is to install LLVM in Cygwin. This will give you (apart from the Cygwin-based dll
files which are unusable with Visual Studio) a complete set of the
headers. These are located in `/usr/include/llvm` and `/usr/include/llvm-c`. Copy
these directories, with all their content, into `$LLVM_HOME/include`.

Alternatively, you can build LLVM yourself from source. This process is
documented at the [LLVM Visual Studio
page](https://llvm.org/docs/GettingStartedVS.html).

Either which way, you must tell configure the location of your LLVM installation
using `--with-llvm=<path to LLVM home>`.

The `llvm-config` tool, which configure uses on other platforms to get the
proper compile and link flags to use, is unfortunately not usable on Windows. In
the official distribution, it is just missing. And the self-built version tend
to give broken and unusable output. Therefore configure uses heuristics to setup
proper flags to the compiler and linker. This was verified to work for LLVM v13,
but might be incorrect for other versions. Manual override of `HSDIS_CFLAGS`,
`HSDIS_LDFLAGS` and/or `HSDIS_LIBS` on the make command line might be needed in
that case.

## Building with binutils

To build this project using binutils you need a copy of GNU binutils to build
against. It is known to work with binutils 2.37. Building against versions older
than 2.29 is not supported. Download a copy of the software from [FSF binutils
page](http://directory.fsf.org/project/binutils) or one of its mirrors.

To build this library, you must enable building in configure by `bash configure
--with-hsdis=binutils`.

You must also specify where binutils is located. To facilitate building, you can
point to a place where the (unpacked) binutils sources are located using
`--with-binutils-src=<location>`, and configure will build binutils for you. On
repeated runs, you can keep this command line option, since configure will
figure out that the binutils binaries are already present and skip building, or
you can replace it with `--with-binutils=<location>`.

If you have pre-built binutils binaries, you can point to them directly using
`--with-binutils=<location>`.

If you want to build hsdis with binutils provided by system (e.g. binutils-devel
from Fedora, binutils-dev from Ubuntu), you can pass `--with-binutils=system`.
`system` is available on Linux only.

### Building with binutils on Windows

On Windows, the normal Microsoft Visual Studio toolchain cannot build binutils.
Instead we need to use the mingw compiler. This is available as a cygwin
package. You need to install the `gcc-core` and `mingw64-x86_64-gcc-core`
packages (or `mingw64-i686-gcc-core`, if you want the 32-bit version) and
`mingw64-x86_64-glib2.0`.
