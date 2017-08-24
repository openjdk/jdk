% Building OpenJDK

## TL;DR (Instructions for the Impatient)

If you are eager to try out building OpenJDK, these simple steps works most of
the time. They assume that you have installed Mercurial (and Cygwin if running
on Windows) and cloned the top-level OpenJDK repository that you want to build.

 1. [Get the complete source code](#getting-the-source-code): \
    `bash get_source.sh`

 2. [Run configure](#running-configure): \
    `bash configure`

    If `configure` fails due to missing dependencies (to either the
    [toolchain](#native-compiler-toolchain-requirements), [external libraries](
    #external-library-requirements) or the [boot JDK](#boot-jdk-requirements)),
    most of the time it prints a suggestion on how to resolve the situation on
    your platform. Follow the instructions, and try running `bash configure`
    again.

 3. [Run make](#running-make): \
    `make images`

 4. Verify your newly built JDK: \
    `./build/*/images/jdk/bin/java -version`

 5. [Run basic tests](##running-tests): \
    `make run-test-tier1`

If any of these steps failed, or if you want to know more about build
requirements or build functionality, please continue reading this document.

## Introduction

OpenJDK is a complex software project. Building it requires a certain amount of
technical expertise, a fair number of dependencies on external software, and
reasonably powerful hardware.

If you just want to use OpenJDK and not build it yourself, this document is not
for you. See for instance [OpenJDK installation](
http://openjdk.java.net/install) for some methods of installing a prebuilt
OpenJDK.

## Getting the Source Code

OpenJDK uses [Mercurial](http://www.mercurial-scm.org) for source control. The
source code is contained not in a single Mercurial repository, but in a tree
("forest") of interrelated repositories. You will need to check out all of the
repositories to be able to build OpenJDK. To assist you in dealing with this
somewhat unusual arrangement, there are multiple tools available, which are
explained below.

In any case, make sure you are getting the correct version. At the [OpenJDK
Mercurial server](http://hg.openjdk.java.net/) you can see a list of all
available forests. If you want to build an older version, e.g. JDK 8, it is
recommended that you get the `jdk8u` forest, which contains incremental
updates, instead of the `jdk8` forest, which was frozen at JDK 8 GA.

If you are new to Mercurial, a good place to start is the [Mercurial Beginner's
Guide](http://www.mercurial-scm.org/guide). The rest of this document assumes a
working knowledge of Mercurial.

### Special Considerations

For a smooth building experience, it is recommended that you follow these rules
on where and how to check out the source code.

  * Do not check out the source code in a path which contains spaces. Chances
    are the build will not work. This is most likely to be an issue on Windows
    systems.

  * Do not check out the source code in a path which has a very long name or is
    nested many levels deep. Chances are you will hit an OS limitation during
    the build.

  * Put the source code on a local disk, not a network share. If possible, use
    an SSD. The build process is very disk intensive, and having slow disk
    access will significantly increase build times. If you need to use a
    network share for the source code, see below for suggestions on how to keep
    the build artifacts on a local disk.

  * On Windows, extra care must be taken to make sure the [Cygwin](#cygwin)
    environment is consistent. It is recommended that you follow this
    procedure:

      * Create the directory that is going to contain the top directory of the
        OpenJDK clone by using the `mkdir` command in the Cygwin bash shell.
        That is, do *not* create it using Windows Explorer. This will ensure
        that it will have proper Cygwin attributes, and that it's children will
        inherit those attributes.

      * Do not put the OpenJDK clone in a path under your Cygwin home
        directory. This is especially important if your user name contains
        spaces and/or mixed upper and lower case letters.

      * Clone the OpenJDK repository using the Cygwin command line `hg` client
        as instructed in this document. That is, do *not* use another Mercurial
        client such as TortoiseHg.

    Failure to follow this procedure might result in hard-to-debug build
    problems.

### Using get\_source.sh

The simplest way to get the entire forest is probably to clone the top-level
repository and then run the `get_source.sh` script, like this:

```
hg clone http://hg.openjdk.java.net/jdk9/jdk9
cd jdk9
bash get_source.sh
```

The first time this is run, it will clone all the sub-repositories. Any
subsequent execution of the script will update all sub-repositories to the
latest revision.

### Using hgforest.sh

The `hgforest.sh` script is more expressive than `get_source.sh`. It takes any
number of arguments, and runs `hg` with those arguments on each sub-repository
in the forest. The `get_source.sh` script is basically a simple wrapper that
runs either `hgforest.sh clone` or `hgforest.sh pull -u`.

  * Cloning the forest:
    ```
    hg clone http://hg.openjdk.java.net/jdk9/jdk9
    cd jdk9
    bash common/bin/hgforest.sh clone
    ```

  * Pulling and updating the forest:
    ```
    bash common/bin/hgforest.sh pull -u
    ```

  * Merging over the entire forest:
    ```
    bash common/bin/hgforest.sh merge
    ```

### Using the Trees Extension

The trees extension is a Mercurial add-on that helps you deal with the forest.
More information is available on the [Code Tools trees page](
http://openjdk.java.net/projects/code-tools/trees).

#### Installing the Extension

Install the extension by cloning `http://hg.openjdk.java.net/code-tools/trees`
and updating your `.hgrc` file. Here's one way to do this:

```
cd ~
mkdir hg-ext
cd hg-ext
hg clone http://hg.openjdk.java.net/code-tools/trees
cat << EOT >> ~/.hgrc
[extensions]
trees=~/hg-ext/trees/trees.py
EOT
```

#### Initializing the Tree

The trees extension needs to know the structure of the forest. If you have
already cloned the entire forest using another method, you can initialize the
forest like this:

```
hg tconf --set --walk --depth
```

Or you can clone the entire forest at once, if you substitute `clone` with
`tclone` when cloning the top-level repository, e.g. like this:

```
hg tclone http://hg.openjdk.java.net/jdk9/jdk9
```

In this case, the forest will be properly initialized from the start.

#### Other Operations

The trees extensions supplement many common operations with a trees version by
prefixing a `t` to the normal Mercurial command, e.g. `tcommit`, `tstatus` or
`tmerge`. For instance, to update the entire forest:

```
hg tpull -u
```

## Build Hardware Requirements

OpenJDK is a massive project, and require machines ranging from decent to
powerful to be able to build in a reasonable amount of time, or to be able to
complete a build at all.

We *strongly* recommend usage of an SSD disk for the build, since disk speed is
one of the limiting factors for build performance.

### Building on x86

At a minimum, a machine with 2-4 cores is advisable, as well as 2-4 GB of RAM.
(The more cores to use, the more memory you need.) At least 6 GB of free disk
space is required (8 GB minimum for building on Solaris).

Even for 32-bit builds, it is recommended to use a 64-bit build machine, and
instead create a 32-bit target using `--with-target-bits=32`.

### Building on sparc

At a minimum, a machine with 4 cores is advisable, as well as 4 GB of RAM. (The
more cores to use, the more memory you need.) At least 8 GB of free disk space
is required.

### Building on arm/aarch64

This is not recommended. Instead, see the section on [Cross-compiling](
#cross-compiling).

## Operating System Requirements

The mainline OpenJDK project supports Linux, Solaris, macOS, AIX and Windows.
Support for other operating system, e.g. BSD, exists in separate "port"
projects.

In general, OpenJDK can be built on a wide range of versions of these operating
systems, but the further you deviate from what is tested on a daily basis, the
more likely you are to run into problems.

This table lists the OS versions used by Oracle when building JDK 9. Such
information is always subject to change, but this table is up to date at the
time of writing.

 Operating system   Vendor/version used
 -----------------  -------------------------------------------------------
 Linux              Oracle Enterprise Linux 6.4 / 7.1 (using kernel 3.8.13)
 Solaris            Solaris 11.1 SRU 21.4.1 / 11.2 SRU 5.5
 macOS              Mac OS X 10.9 (Mavericks) / 10.10 (Yosemite)
 Windows            Windows Server 2012 R2

The double version numbers for Linux, Solaris and macOS is due to the hybrid
model used at Oracle, where header files and external libraries from an older
version is used when building on a more modern version of the OS.

The Build Group has a wiki page with [Supported Build Platforms](
https://wiki.openjdk.java.net/display/Build/Supported+Build+Platforms). From
time to time, this is updated by the community to list successes or failures of
building on different platforms.

### Windows

Windows XP is not a supported platform, but all newer Windows should be able to
build OpenJDK.

On Windows, it is important that you pay attention to the instructions in the
[Special Considerations](#special-considerations).

Windows is the only non-POSIX OS supported by OpenJDK, and as such, requires
some extra care. A POSIX support layer is required to build on Windows. For
OpenJDK 9, the only supported such layer is Cygwin. (Msys is no longer
supported due to a too old bash; msys2 and the new Windows Subsystem for Linux
(WSL) would likely be possible to support in a future version but that would
require a community effort to implement.)

Internally in the build system, all paths are represented as Unix-style paths,
e.g. `/cygdrive/c/hg/jdk9/Makefile` rather than `C:\hg\jdk9\Makefile`. This
rule also applies to input to the build system, e.g. in arguments to
`configure`. So, use `--with-freetype=/cygdrive/c/freetype` rather than
`--with-freetype=c:\freetype`. For details on this conversion, see the section
on [Fixpath](#fixpath).

#### Cygwin

A functioning [Cygwin](http://www.cygwin.com/) environment is thus required for
building OpenJDK on Windows. If you have a 64-bit OS, we strongly recommend
using the 64-bit version of Cygwin.

**Note:** Cygwin has a model of continuously updating all packages without any
easy way to install or revert to a specific version of a package. This means
that whenever you add or update a package in Cygwin, you might (inadvertently)
update tools that are used by the OpenJDK build process, and that can cause
unexpected build problems.

OpenJDK requires GNU Make 4.0 or greater on Windows. This is usually not a
problem, since Cygwin currently only distributes GNU Make at a version above
4.0.

Apart from the basic Cygwin installation, the following packages must also be
installed:

  * `make`
  * `zip`
  * `unzip`

Often, you can install these packages using the following command line:
```
<path to Cygwin setup>/setup-x86_64 -q -P make -P unzip -P zip
```

Unfortunately, Cygwin can be unreliable in certain circumstances. If you
experience build tool crashes or strange issues when building on Windows,
please check the Cygwin FAQ on the ["BLODA" list](
https://cygwin.com/faq/faq.html#faq.using.bloda) and the section on [fork()
failures](https://cygwin.com/faq/faq.html#faq.using.fixing-fork-failures).

### Solaris

See `make/devkit/solaris11.1-package-list.txt` for a list of recommended
packages to install when building on Solaris. The versions specified in this
list is the versions used by the daily builds at Oracle, and is likely to work
properly.

Older versions of Solaris shipped a broken version of `objcopy`. At least
version 2.21.1 is needed, which is provided by Solaris 11 Update 1. Objcopy is
needed if you want to have external debug symbols. Please make sure you are
using at least version 2.21.1 of objcopy, or that you disable external debug
symbols.

### macOS

Apple is using a quite aggressive scheme of pushing OS updates, and coupling
these updates with required updates of Xcode. Unfortunately, this makes it
difficult for a project like OpenJDK to keep pace with a continuously updated
machine running macOS. See the section on [Apple Xcode](#apple-xcode) on some
strategies to deal with this.

It is recommended that you use at least Mac OS X 10.9 (Mavericks). At the time
of writing, OpenJDK has been successfully compiled on macOS versions up to
10.12.5 (Sierra), using XCode 8.3.2 and `--disable-warnings-as-errors`.

The standard macOS environment contains the basic tooling needed to build, but
for external libraries a package manager is recommended. OpenJDK uses
[homebrew](https://brew.sh/) in the examples, but feel free to use whatever
manager you want (or none).

### Linux

It is often not much problem to build OpenJDK on Linux. The only general advice
is to try to use the compilers, external libraries and header files as provided
by your distribution.

The basic tooling is provided as part of the core operating system, but you
will most likely need to install developer packages.

For apt-based distributions (Debian, Ubuntu, etc), try this:
```
sudo apt-get install build-essential
```

For rpm-based distributions (Fedora, Red Hat, etc), try this:
```
sudo yum groupinstall "Development Tools"
```

### AIX

The regular builds by SAP is using AIX version 7.1, but AIX 5.3 is also
supported. See the [OpenJDK PowerPC Port Status Page](
http://cr.openjdk.java.net/~simonis/ppc-aix-port) for details.

## Native Compiler (Toolchain) Requirements

Large portions of OpenJDK consists of native code, that needs to be compiled to
be able to run on the target platform. In theory, toolchain and operating
system should be independent factors, but in practice there's more or less a
one-to-one correlation between target operating system and toolchain.

 Operating system   Supported toolchain
 ------------------ -------------------------
 Linux              gcc, clang
 macOS              Apple Xcode (using clang)
 Solaris            Oracle Solaris Studio
 AIX                IBM XL C/C++
 Windows            Microsoft Visual Studio

Please see the individual sections on the toolchains for version
recommendations. As a reference, these versions of the toolchains are used, at
the time of writing, by Oracle for the daily builds of OpenJDK. It should be
possible to compile OpenJDK with both older and newer versions, but the closer
you stay to this list, the more likely you are to compile successfully without
issues.

 Operating system   Toolchain version
 ------------------ -------------------------------------------------------
 Linux              gcc 4.9.2
 macOS              Apple Xcode 6.3 (using clang 6.1.0)
 Solaris            Oracle Solaris Studio 12.4 (with compiler version 5.13)
 Windows            Microsoft Visual Studio 2013 update 4

### gcc

The minimum accepted version of gcc is 4.7. Older versions will generate a warning 
by `configure` and are unlikely to work.

OpenJDK 9 includes patches that should allow gcc 6 to compile, but this should
be considered experimental.

In general, any version between these two should be usable.

### clang

The minimum accepted version of clang is 3.2. Older versions will not be
accepted by `configure`.

To use clang instead of gcc on Linux, use `--with-toolchain-type=clang`.

### Apple Xcode

The oldest supported version of Xcode is 5.

You will need the Xcode command lines developers tools to be able to build
OpenJDK. (Actually, *only* the command lines tools are needed, not the IDE.)
The simplest way to install these is to run:
```
xcode-select --install
```

It is advisable to keep an older version of Xcode for building OpenJDK when
updating Xcode. This [blog page](
http://iosdevelopertips.com/xcode/install-multiple-versions-of-xcode.html) has
good suggestions on managing multiple Xcode versions. To use a specific version
of Xcode, use `xcode-select -s` before running `configure`, or use
`--with-toolchain-path` to point to the version of Xcode to use, e.g.
`configure --with-toolchain-path=/Applications/Xcode5.app/Contents/Developer/usr/bin`

If you have recently (inadvertently) updated your OS and/or Xcode version, and
OpenJDK can no longer be built, please see the section on [Problems with the
Build Environment](#problems-with-the-build-environment), and [Getting
Help](#getting-help) to find out if there are any recent, non-merged patches
available for this update.

### Oracle Solaris Studio

The minimum accepted version of the Solaris Studio compilers is 5.13
(corresponding to Solaris Studio 12.4). Older versions will not be accepted by
configure.

The Solaris Studio installation should contain at least these packages:

 Package                                            Version
 -------------------------------------------------- -------------
 developer/solarisstudio-124/backend                12.4-1.0.6.0
 developer/solarisstudio-124/c++                    12.4-1.0.10.0
 developer/solarisstudio-124/cc                     12.4-1.0.4.0
 developer/solarisstudio-124/library/c++-libs       12.4-1.0.10.0
 developer/solarisstudio-124/library/math-libs      12.4-1.0.0.1
 developer/solarisstudio-124/library/studio-gccrt   12.4-1.0.0.1
 developer/solarisstudio-124/studio-common          12.4-1.0.0.1
 developer/solarisstudio-124/studio-ja              12.4-1.0.0.1
 developer/solarisstudio-124/studio-legal           12.4-1.0.0.1
 developer/solarisstudio-124/studio-zhCN            12.4-1.0.0.1

Compiling with Solaris Studio can sometimes be finicky. This is the exact
version used by Oracle, which worked correctly at the time of writing:
```
$ cc -V
cc: Sun C 5.13 SunOS_i386 2014/10/20
$ CC -V
CC: Sun C++ 5.13 SunOS_i386 151846-10 2015/10/30
```

### Microsoft Visual Studio

The minimum accepted version of Visual Studio is 2010. Older versions will not
be accepted by `configure`. The maximum accepted version of Visual Studio is
2013.

If you have multiple versions of Visual Studio installed, `configure` will by
default pick the latest. You can request a specific version to be used by
setting `--with-toolchain-version`, e.g. `--with-toolchain-version=2010`.

If you get `LINK: fatal error LNK1123: failure during conversion to COFF: file
invalid` when building using Visual Studio 2010, you have encountered
[KB2757355](http://support.microsoft.com/kb/2757355), a bug triggered by a
specific installation order. However, the solution suggested by the KB article
does not always resolve the problem. See [this stackoverflow discussion](
https://stackoverflow.com/questions/10888391) for other suggestions.

### IBM XL C/C++

The regular builds by SAP is using version 12.1, described as `IBM XL C/C++ for
AIX, V12.1 (5765-J02, 5725-C72) Version: 12.01.0000.0017`.

See the [OpenJDK PowerPC Port Status Page](
http://cr.openjdk.java.net/~simonis/ppc-aix-port) for details.

## Boot JDK Requirements

Paradoxically, building OpenJDK requires a pre-existing JDK. This is called the
"boot JDK". The boot JDK does not have to be OpenJDK, though. If you are
porting OpenJDK to a new platform, chances are that there already exists
another JDK for that platform that is usable as boot JDK.

The rule of thumb is that the boot JDK for building JDK major version *N*
should be an JDK of major version *N-1*, so for building JDK 9 a JDK 8 would be
suitable as boot JDK. However, OpenJDK should be able to "build itself", so an
up-to-date build of the current OpenJDK source is an acceptable alternative. If
you are following the *N-1* rule, make sure you got the latest update version,
since JDK 8 GA might not be able to build JDK 9 on all platforms.

If the Boot JDK is not automatically detected, or the wrong JDK is picked, use
`--with-boot-jdk` to point to the JDK to use.

### JDK 8 on Linux

On apt-based distros (like Debian and Ubuntu), `sudo apt-get install
openjdk-8-jdk` is typically enough to install OpenJDK 8. On rpm-based distros
(like Fedora and Red Hat), try `sudo yum install java-1.8.0-openjdk-devel`.

### JDK 8 on Windows

No pre-compiled binaries of OpenJDK 8 are readily available for Windows at the
time of writing. An alternative is to download the [Oracle JDK](
http://www.oracle.com/technetwork/java/javase/downloads). Another is the [Adopt
OpenJDK Project](https://adoptopenjdk.net/), which publishes experimental
prebuilt binaries for Windows.

### JDK 8 on macOS

No pre-compiled binaries of OpenJDK 8 are readily available for macOS at the
time of writing. An alternative is to download the [Oracle JDK](
http://www.oracle.com/technetwork/java/javase/downloads), or to install it
using `brew cask install java`. Another option is the [Adopt OpenJDK Project](
https://adoptopenjdk.net/), which publishes experimental prebuilt binaries for
macOS.

### JDK 8 on AIX

No pre-compiled binaries of OpenJDK 8 are readily available for AIX at the
time of writing. A starting point for working with OpenJDK on AIX is
the [PowerPC/AIX Port Project](http://openjdk.java.net/projects/ppc-aix-port/).

## External Library Requirements

Different platforms require different external libraries. In general, libraries
are not optional - that is, they are either required or not used.

If a required library is not detected by `configure`, you need to provide the
path to it. There are two forms of the `configure` arguments to point to an
external library: `--with-<LIB>=<path>` or `--with-<LIB>-include=<path to
include> --with-<LIB>-lib=<path to lib>`. The first variant is more concise,
but require the include files an library files to reside in a default hierarchy
under this directory. In most cases, it works fine.

As a fallback, the second version allows you to point to the include directory
and the lib directory separately.

### FreeType

FreeType2 from [The FreeType Project](http://www.freetype.org/) is required on
all platforms. At least version 2.3 is required.

  * To install on an apt-based Linux, try running `sudo apt-get install
    libcups2-dev`.
  * To install on an rpm-based Linux, try running `sudo yum install
    cups-devel`.
  * To install on Solaris, try running `pkg install system/library/freetype-2`.
  * To install on macOS, try running `brew install freetype`.
  * To install on Windows, see [below](#building-freetype-on-windows).

Use `--with-freetype=<path>` if `configure` does not properly locate your
FreeType files.

#### Building FreeType on Windows

On Windows, there is no readily available compiled version of FreeType. OpenJDK
can help you compile FreeType from source. Download the FreeType sources and
unpack them into an arbitrary directory:

```
wget http://download.savannah.gnu.org/releases/freetype/freetype-2.5.3.tar.gz
tar -xzf freetype-2.5.3.tar.gz
```

Then run `configure` with `--with-freetype-src=<freetype_src>`. This will
automatically build the freetype library into `<freetype_src>/lib64` for 64-bit
builds or into `<freetype_src>/lib32` for 32-bit builds. Afterwards you can
always use `--with-freetype-include=<freetype_src>/include` and
`--with-freetype-lib=<freetype_src>/lib[32|64]` for other builds.

Alternatively you can unpack the sources like this to use the default
directory:

```
tar --one-top-level=$HOME/freetype --strip-components=1 -xzf freetype-2.5.3.tar.gz
```

### CUPS

CUPS, [Common UNIX Printing System](http://www.cups.org) header files are
required on all platforms, except Windows. Often these files are provided by
your operating system.

  * To install on an apt-based Linux, try running `sudo apt-get install
    libcups2-dev`.
  * To install on an rpm-based Linux, try running `sudo yum install
    cups-devel`.
  * To install on Solaris, try running `pkg install print/cups`.

Use `--with-cups=<path>` if `configure` does not properly locate your CUPS
files.

### X11

Certain [X11](http://www.x.org/) libraries and include files are required on
Linux and Solaris.

  * To install on an apt-based Linux, try running `sudo apt-get install
    libx11-dev libxext-dev libxrender-dev libxtst-dev libxt-dev`.
  * To install on an rpm-based Linux, try running `sudo yum install
    libXtst-devel libXt-devel libXrender-devel libXi-devel`.
  * To install on Solaris, try running `pkg install x11/header/x11-protocols
    x11/library/libice x11/library/libpthread-stubs x11/library/libsm
    x11/library/libx11 x11/library/libxau x11/library/libxcb
    x11/library/libxdmcp x11/library/libxevie x11/library/libxext
    x11/library/libxrender x11/library/libxscrnsaver x11/library/libxtst
    x11/library/toolkit/libxt`.

Use `--with-x=<path>` if `configure` does not properly locate your X11 files.

### ALSA

ALSA, [Advanced Linux Sound Architecture](https://www.alsa-project.org/) is
required on Linux. At least version 0.9.1 of ALSA is required.

  * To install on an apt-based Linux, try running `sudo apt-get install
    libasound2-dev`.
  * To install on an rpm-based Linux, try running `sudo yum install
    alsa-lib-devel`.

Use `--with-alsa=<path>` if `configure` does not properly locate your ALSA
files.

### libffi

libffi, the [Portable Foreign Function Interface Library](
http://sourceware.org/libffi) is required when building the Zero version of
Hotspot.

  * To install on an apt-based Linux, try running `sudo apt-get install
    libffi-dev`.
  * To install on an rpm-based Linux, try running `sudo yum install
    libffi-devel`.

Use `--with-libffi=<path>` if `configure` does not properly locate your libffi
files.

### libelf

libelf from the [elfutils project](http://sourceware.org/elfutils) is required
when building the AOT feature of Hotspot.

  * To install on an apt-based Linux, try running `sudo apt-get install
    libelf-dev`.
  * To install on an rpm-based Linux, try running `sudo yum install
    elfutils-libelf-devel`.

Use `--with-libelf=<path>` if `configure` does not properly locate your libelf
files.

## Other Tooling Requirements

### GNU Make

OpenJDK requires [GNU Make](http://www.gnu.org/software/make). No other flavors
of make are supported.

At least version 3.81 of GNU Make must be used. For distributions supporting
GNU Make 4.0 or above, we strongly recommend it. GNU Make 4.0 contains useful
functionality to handle parallel building (supported by `--with-output-sync`)
and speed and stability improvements.

Note that `configure` locates and verifies a properly functioning version of
`make` and stores the path to this `make` binary in the configuration. If you
start a build using `make` on the command line, you will be using the version
of make found first in your `PATH`, and not necessarily the one stored in the
configuration. This initial make will be used as "bootstrap make", and in a
second stage, the make located by `configure` will be called. Normally, this
will present no issues, but if you have a very old `make`, or a non-GNU Make
`make` in your path, this might cause issues.

If you want to override the default make found by `configure`, use the `MAKE`
configure variable, e.g. `configure MAKE=/opt/gnu/make`.

On Solaris, it is common to call the GNU version of make by using `gmake`.

### GNU Bash

OpenJDK requires [GNU Bash](http://www.gnu.org/software/bash). No other shells
are supported.

At least version 3.2 of GNU Bash must be used.

### Autoconf

If you want to modify the build system itself, you need to install [Autoconf](
http://www.gnu.org/software/autoconf).

However, if you only need to build OpenJDK or if you only edit the actual
OpenJDK source files, there is no dependency on autoconf, since the source
distribution includes a pre-generated `configure` shell script.

See the section on [Autoconf Details](#autoconf-details) for details on how
OpenJDK uses autoconf. This is especially important if you plan to contribute
changes to OpenJDK that modifies the build system.

## Running Configure

To build OpenJDK, you need a "configuration", which consists of a directory
where to store the build output, coupled with information about the platform,
the specific build machine, and choices that affect how OpenJDK is built.

The configuration is created by the `configure` script. The basic invocation of
the `configure` script looks like this:

```
bash configure [options]
```

This will create an output directory containing the configuration and setup an
area for the build result. This directory typically looks like
`build/linux-x64-normal-server-release`, but the actual name depends on your
specific configuration. (It can also be set directly, see [Using Multiple
Configurations](#using-multiple-configurations)). This directory is referred to
as `$BUILD` in this documentation.

`configure` will try to figure out what system you are running on and where all
necessary build components are. If you have all prerequisites for building
installed, it should find everything. If it fails to detect any component
automatically, it will exit and inform you about the problem.

Some command line examples:

  * Create a 32-bit build for Windows with FreeType2 in `C:\freetype-i586`:
    ```
    bash configure --with-freetype=/cygdrive/c/freetype-i586 --with-target-bits=32
    ```

  * Create a debug build with the `server` JVM and DTrace enabled:
    ```
    bash configure --enable-debug --with-jvm-variants=server --enable-dtrace
    ```

### Common Configure Arguments

Here follows some of the most common and important `configure` argument.

To get up-to-date information on *all* available `configure` argument, please
run:
```
bash configure --help
```

(Note that this help text also include general autoconf options, like
`--dvidir`, that is not relevant to OpenJDK. To list only OpenJDK specific
features, use `bash configure --help=short` instead.)

#### Configure Arguments for Tailoring the Build

  * `--enable-debug` - Set the debug level to `fastdebug` (this is a shorthand
    for `--with-debug-level=fastdebug`)
  * `--with-debug-level=<level>` - Set the debug level, which can be `release`,
    `fastdebug`, `slowdebug` or `optimized`. Default is `release`. `optimized`
    is variant of `release` with additional Hotspot debug code.
  * `--with-native-debug-symbols=<method>` - Specify if and how native debug
    symbols should be built. Available methods are `none`, `internal`,
    `external`, `zipped`. Default behavior depends on platform. See [Native
    Debug Symbols](#native-debug-symbols) for more details.
  * `--with-version-string=<string>` - Specify the version string this build
    will be identified with.
  * `--with-version-<part>=<value>` - A group of options, where `<part>` can be
    any of `pre`, `opt`, `build`, `major`, `minor`, `security` or `patch`. Use
    these options to modify just the corresponding part of the version string
    from the default, or the value provided by `--with-version-string`.
  * `--with-jvm-variants=<variant>[,<variant>...]` - Build the specified variant
    (or variants) of Hotspot. Valid variants are: `server`, `client`,
    `minimal`, `core`, `zero`, `zeroshark`, `custom`. Note that not all
    variants are possible to combine in a single build.
  * `--with-jvm-features=<feature>[,<feature>...]` - Use the specified JVM
    features when building Hotspot. The list of features will be enabled on top
    of the default list. For the `custom` JVM variant, this default list is
    empty. A complete list of available JVM features can be found using `bash
    configure --help`.
  * `--with-target-bits=<bits>` - Create a target binary suitable for running
    on a `<bits>` platform. Use this to create 32-bit output on a 64-bit build
    platform, instead of doing a full cross-compile. (This is known as a
    *reduced* build.)

#### Configure Arguments for Native Compilation

  * `--with-devkit=<path>` - Use this devkit for compilers, tools and resources
  * `--with-sysroot=<path>` - Use this directory as sysroot
  * `--with-extra-path=<path>[;<path>]` - Prepend these directories to the
    default path when searching for all kinds of binaries
  * `--with-toolchain-path=<path>[;<path>]` - Prepend these directories when
    searching for toolchain binaries (compilers etc)
  * `--with-extra-cflags=<flags>` - Append these flags when compiling JDK C
    files
  * `--with-extra-cxxflags=<flags>` - Append these flags when compiling JDK C++
    files
  * `--with-extra-ldflags=<flags>` - Append these flags when linking JDK
    libraries

#### Configure Arguments for External Dependencies

  * `--with-boot-jdk=<path>` - Set the path to the [Boot JDK](
    #boot-jdk-requirements)
  * `--with-freetype=<path>` - Set the path to [FreeType](#freetype)
  * `--with-cups=<path>` - Set the path to [CUPS](#cups)
  * `--with-x=<path>` - Set the path to [X11](#x11)
  * `--with-alsa=<path>` - Set the path to [ALSA](#alsa)
  * `--with-libffi=<path>` - Set the path to [libffi](#libffi)
  * `--with-libelf=<path>` - Set the path to [libelf](#libelf)
  * `--with-jtreg=<path>` - Set the path to JTReg. See [Running Tests](
    #running-tests)

Certain third-party libraries used by OpenJDK (libjpeg, giflib, libpng, lcms
and zlib) are included in the OpenJDK repository. The default behavior of the
OpenJDK build is to use this version of these libraries, but they might be
replaced by an external version. To do so, specify `system` as the `<source>`
option in these arguments. (The default is `bundled`).

  * `--with-libjpeg=<source>` - Use the specified source for libjpeg
  * `--with-giflib=<source>` - Use the specified source for giflib
  * `--with-libpng=<source>` - Use the specified source for libpng
  * `--with-lcms=<source>` - Use the specified source for lcms
  * `--with-zlib=<source>` - Use the specified source for zlib

On Linux, it is possible to select either static or dynamic linking of the C++
runtime. The default is static linking, with dynamic linking as fallback if the
static library is not found.

  * `--with-stdc++lib=<method>` - Use the specified method (`static`, `dynamic`
    or `default`) for linking the C++ runtime.

### Configure Control Variables

It is possible to control certain aspects of `configure` by overriding the
value of `configure` variables, either on the command line or in the
environment.

Normally, this is **not recommended**. If used improperly, it can lead to a
broken configuration. Unless you're well versed in the build system, this is
hard to use properly. Therefore, `configure` will print a warning if this is
detected.

However, there are a few `configure` variables, known as *control variables*
that are supposed to be overriden on the command line. These are variables that
describe the location of tools needed by the build, like `MAKE` or `GREP`. If
any such variable is specified, `configure` will use that value instead of
trying to autodetect the tool. For instance, `bash configure
MAKE=/opt/gnumake4.0/bin/make`.

If a configure argument exists, use that instead, e.g. use `--with-jtreg`
instead of setting `JTREGEXE`.

Also note that, despite what autoconf claims, setting `CFLAGS` will not
accomplish anything. Instead use `--with-extra-cflags` (and similar for
`cxxflags` and `ldflags`).

## Running Make

When you have a proper configuration, all you need to do to build OpenJDK is to
run `make`. (But see the warning at [GNU Make](#gnu-make) about running the
correct version of make.)

When running `make` without any arguments, the default target is used, which is
the same as running `make default` or `make jdk`. This will build a minimal (or
roughly minimal) set of compiled output (known as an "exploded image") needed
for a developer to actually execute the newly built JDK. The idea is that in an
incremental development fashion, when doing a normal make, you should only
spend time recompiling what's changed (making it purely incremental) and only
do the work that's needed to actually run and test your code.

The output of the exploded image resides in `$BUILD/jdk`. You can test the
newly built JDK like this: `$BUILD/jdk/bin/java -version`.

### Common Make Targets

Apart from the default target, here are some common make targets:

  * `hotspot` - Build all of hotspot (but only hotspot)
  * `hotspot-<variant>` - Build just the specified jvm variant
  * `images` or `product-images` - Build the JRE and JDK images
  * `docs` or `docs-image` - Build the documentation image
  * `test-image` - Build the test image
  * `all` or `all-images` - Build all images (product, docs and test)
  * `bootcycle-images` - Build images twice, second time with newly built JDK
    (good for testing)
  * `clean` - Remove all files generated by make, but not those generated by
    configure
  * `dist-clean` - Remove all files, including configuration

Run `make help` to get an up-to-date list of important make targets and make
control variables.

It is possible to build just a single module, a single phase, or a single phase
of a single module, by creating make targets according to these followin
patterns. A phase can be either of `gensrc`, `gendata`, `copy`, `java`,
`launchers`, `libs` or `rmic`. See [Using Fine-Grained Make Targets](
#using-fine-grained-make-targets) for more details about this functionality.

  * `<phase>` - Build the specified phase and everything it depends on
  * `<module>` - Build the specified module and everything it depends on
  * `<module>-<phase>` - Compile the specified phase for the specified module
    and everything it depends on

Similarly, it is possible to clean just a part of the build by creating make
targets according to these patterns:

  * `clean-<outputdir>` - Remove the subdir in the output dir with the name
  * `clean-<phase>` - Remove all build results related to a certain build
    phase
  * `clean-<module>` - Remove all build results related to a certain module
  * `clean-<module>-<phase>` - Remove all build results related to a certain
    module and phase

### Make Control Variables

It is possible to control `make` behavior by overriding the value of `make`
variables, either on the command line or in the environment.

Normally, this is **not recommended**. If used improperly, it can lead to a
broken build. Unless you're well versed in the build system, this is hard to
use properly. Therefore, `make` will print a warning if this is detected.

However, there are a few `make` variables, known as *control variables* that
are supposed to be overriden on the command line. These make up the "make time"
configuration, as opposed to the "configure time" configuration.

#### General Make Control Variables

  * `JOBS` - Specify the number of jobs to build with. See [Build
    Performance](#build-performance).
  * `LOG` - Specify the logging level and functionality. See [Checking the
    Build Log File](#checking-the-build-log-file)
  * `CONF` and `CONF_NAME` - Selecting the configuration(s) to use. See [Using
    Multiple Configurations](#using-multiple-configurations)

#### Test Make Control Variables

These make control variables only make sense when running tests. Please see
[Testing OpenJDK](testing.html) for details.

  * `TEST`
  * `TEST_JOBS`
  * `JTREG`
  * `GTEST`

#### Advanced Make Control Variables

These advanced make control variables can be potentially unsafe. See [Hints and
Suggestions for Advanced Users](#hints-and-suggestions-for-advanced-users) and
[Understanding the Build System](#understanding-the-build-system) for details.

  * `SPEC`
  * `CONF_CHECK`
  * `COMPARE_BUILD`
  * `JDK_FILTER`

## Running Tests

Most of the OpenJDK tests are using the [JTReg](http://openjdk.java.net/jtreg)
test framework. Make sure that your configuration knows where to find your
installation of JTReg. If this is not picked up automatically, use the
`--with-jtreg=<path to jtreg home>` option to point to the JTReg framework.
Note that this option should point to the JTReg home, i.e. the top directory,
containing `lib/jtreg.jar` etc.

To execute the most basic tests (tier 1), use:
```
make run-test-tier1
```

For more details on how to run tests, please see the [Testing
OpenJDK](testing.html) document.

## Cross-compiling

Cross-compiling means using one platform (the *build* platform) to generate
output that can ran on another platform (the *target* platform).

The typical reason for cross-compiling is that the build is performed on a more
powerful desktop computer, but the resulting binaries will be able to run on a
different, typically low-performing system. Most of the complications that
arise when building for embedded is due to this separation of *build* and
*target* systems.

This requires a more complex setup and build procedure. This section assumes
you are familiar with cross-compiling in general, and will only deal with the
particularities of cross-compiling OpenJDK. If you are new to cross-compiling,
please see the [external links at Wikipedia](
https://en.wikipedia.org/wiki/Cross_compiler#External_links) for a good start
on reading materials.

Cross-compiling OpenJDK requires you to be able to build both for the build
platform and for the target platform. The reason for the former is that we need
to build and execute tools during the build process, both native tools and Java
tools.

If all you want to do is to compile a 32-bit version, for the same OS, on a
64-bit machine, consider using `--with-target-bits=32` instead of doing a
full-blown cross-compilation. (While this surely is possible, it's a lot more
work and will take much longer to build.)

### Boot JDK and Build JDK

When cross-compiling, make sure you use a boot JDK that runs on the *build*
system, and not on the *target* system.

To be able to build, we need a "Build JDK", which is a JDK built from the
current sources (that is, the same as the end result of the entire build
process), but able to run on the *build* system, and not the *target* system.
(In contrast, the Boot JDK should be from an older release, e.g. JDK 8 when
building JDK 9.)

The build process will create a minimal Build JDK for you, as part of building.
To speed up the build, you can use `--with-build-jdk` to `configure` to point
to a pre-built Build JDK. Please note that the build result is unpredictable,
and can possibly break in subtle ways, if the Build JDK does not **exactly**
match the current sources.

### Specifying the Target Platform

You *must* specify the target platform when cross-compiling. Doing so will also
automatically turn the build into a cross-compiling mode. The simplest way to
do this is to use the `--openjdk-target` argument, e.g.
`--openjdk-target=arm-linux-gnueabihf`. or `--openjdk-target=aarch64-oe-linux`.
This will automatically set the `--build`, `--host` and `--target` options for
autoconf, which can otherwise be confusing. (In autoconf terminology, the
"target" is known as "host", and "target" is used for building a Canadian
cross-compiler.)

### Toolchain Considerations

You will need two copies of your toolchain, one which generates output that can
run on the target system (the normal, or *target*, toolchain), and one that
generates output that can run on the build system (the *build* toolchain). Note
that cross-compiling is only supported for gcc at the time being. The gcc
standard is to prefix cross-compiling toolchains with the target denominator.
If you follow this standard, `configure` is likely to pick up the toolchain
correctly.

The *build* toolchain will be autodetected just the same way the normal
*build*/*target* toolchain will be autodetected when not cross-compiling. If
this is not what you want, or if the autodetection fails, you can specify a
devkit containing the *build* toolchain using `--with-build-devkit` to
`configure`, or by giving `BUILD_CC` and `BUILD_CXX` arguments.

It is often helpful to locate the cross-compilation tools, headers and
libraries in a separate directory, outside the normal path, and point out that
directory to `configure`. Do this by setting the sysroot (`--with-sysroot`) and
appending the directory when searching for cross-compilations tools
(`--with-toolchain-path`). As a compact form, you can also use `--with-devkit`
to point to a single directory, if it is correctly setup. (See `basics.m4` for
details.)

If you are unsure what toolchain and versions to use, these have been proved
working at the time of writing:

  * [aarch64](
https://releases.linaro.org/archive/13.11/components/toolchain/binaries/gcc-linaro-aarch64-linux-gnu-4.8-2013.11_linux.tar.xz)
  * [arm 32-bit hardware floating  point](
https://launchpad.net/linaro-toolchain-unsupported/trunk/2012.09/+download/gcc-linaro-arm-linux-gnueabihf-raspbian-2012.09-20120921_linux.tar.bz2)

### Native Libraries

You will need copies of external native libraries for the *target* system,
present on the *build* machine while building.

Take care not to replace the *build* system's version of these libraries by
mistake, since that can render the *build* machine unusable.

Make sure that the libraries you point to (ALSA, X11, etc) are for the
*target*, not the *build*, platform.

#### ALSA

You will need alsa libraries suitable for your *target* system. For most cases,
using Debian's pre-built libraries work fine.

Note that alsa is needed even if you only want to build a headless JDK.

  * Go to [Debian Package Search](https://www.debian.org/distrib/packages) and
    search for the `libasound2` and `libasound2-dev` packages for your *target*
    system. Download them to /tmp.

  * Install the libraries into the cross-compilation toolchain. For instance:
```
cd /tools/gcc-linaro-arm-linux-gnueabihf-raspbian-2012.09-20120921_linux/arm-linux-gnueabihf/libc
dpkg-deb -x /tmp/libasound2_1.0.25-4_armhf.deb .
dpkg-deb -x /tmp/libasound2-dev_1.0.25-4_armhf.deb .
```

  * If alsa is not properly detected by `configure`, you can point it out by
    `--with-alsa`.

#### X11

You will need X11 libraries suitable for your *target* system. For most cases,
using Debian's pre-built libraries work fine.

Note that X11 is needed even if you only want to build a headless JDK.

  * Go to [Debian Package Search](https://www.debian.org/distrib/packages),
    search for the following packages for your *target* system, and download them
    to /tmp/target-x11:
      * libxi
      * libxi-dev
      * x11proto-core-dev
      * x11proto-input-dev
      * x11proto-kb-dev
      * x11proto-render-dev
      * x11proto-xext-dev
      * libice-dev
      * libxrender
      * libxrender-dev
      * libsm-dev
      * libxt-dev
      * libx11
      * libx11-dev
      * libxtst
      * libxtst-dev
      * libxext
      * libxext-dev

  * Install the libraries into the cross-compilation toolchain. For instance:
    ```
    cd /tools/gcc-linaro-arm-linux-gnueabihf-raspbian-2012.09-20120921_linux/arm-linux-gnueabihf/libc/usr
    mkdir X11R6
    cd X11R6
    for deb in /tmp/target-x11/*.deb ; do dpkg-deb -x $deb . ; done
    mv usr/* .
    cd lib
    cp arm-linux-gnueabihf/* .
    ```

    You can ignore the following messages. These libraries are not needed to
    successfully complete a full JDK build.
    ```
    cp: cannot stat `arm-linux-gnueabihf/libICE.so': No such file or directory
    cp: cannot stat `arm-linux-gnueabihf/libSM.so': No such file or directory
    cp: cannot stat `arm-linux-gnueabihf/libXt.so': No such file or directory
    ```

  * If the X11 libraries are not properly detected by `configure`, you can
    point them out by `--with-x`.

### Building for ARM/aarch64

A common cross-compilation target is the ARM CPU. When building for ARM, it is
useful to set the ABI profile. A number of pre-defined ABI profiles are
available using `--with-abi-profile`: arm-vfp-sflt, arm-vfp-hflt, arm-sflt,
armv5-vfp-sflt, armv6-vfp-hflt. Note that soft-float ABIs are no longer
properly supported on OpenJDK.

OpenJDK contains two different ports for the aarch64 platform, one is the
original aarch64 port from the [AArch64 Port Project](
http://openjdk.java.net/projects/aarch64-port) and one is a 64-bit version of
the Oracle contributed ARM port. When targeting aarch64, by the default the
original aarch64 port is used. To select the Oracle ARM 64 port, use
`--with-cpu-port=arm64`. Also set the corresponding value (`aarch64` or
`arm64`) to --with-abi-profile, to ensure a consistent build.

### Verifying the Build

The build will end up in a directory named like
`build/linux-arm-normal-server-release`.

Inside this build output directory, the `images/jdk` and `images/jre` will
contain the newly built JDK and JRE, respectively, for your *target* system.

Copy these folders to your *target* system. Then you can run e.g.
`images/jdk/bin/java -version`.

## Build Performance

Building OpenJDK requires a lot of horsepower. Some of the build tools can be
adjusted to utilize more or less of resources such as parallel threads and
memory. The `configure` script analyzes your system and selects reasonable
values for such options based on your hardware. If you encounter resource
problems, such as out of memory conditions, you can modify the detected values
with:

  * `--with-num-cores` -- number of cores in the build system, e.g.
    `--with-num-cores=8`.

  * `--with-memory-size` -- memory (in MB) available in the build system, e.g.
    `--with-memory-size=1024`

You can also specify directly the number of build jobs to use with
`--with-jobs=N` to `configure`, or `JOBS=N` to `make`. Do not use the `-j` flag
to `make`. In most cases it will be ignored by the makefiles, but it can cause
problems for some make targets.

It might also be necessary to specify the JVM arguments passed to the Boot JDK,
using e.g. `--with-boot-jdk-jvmargs="-Xmx8G"`. Doing so will override the
default JVM arguments passed to the Boot JDK.

At the end of a successful execution of `configure`, you will get a performance
summary, indicating how well the build will perform. Here you will also get
performance hints. If you want to build fast, pay attention to those!

If you want to tweak build performance, run with `make LOG=info` to get a build
time summary at the end of the build process.

### Disk Speed

If you are using network shares, e.g. via NFS, for your source code, make sure
the build directory is situated on local disk (e.g. by `ln -s
/localdisk/jdk-build $JDK-SHARE/build`). The performance penalty is extremely
high for building on a network share; close to unusable.

Also, make sure that your build tools (including Boot JDK and toolchain) is
located on a local disk and not a network share.

As has been stressed elsewhere, do use SSD for source code and build directory,
as well as (if possible) the build tools.

### Virus Checking

The use of virus checking software, especially on Windows, can *significantly*
slow down building of OpenJDK. If possible, turn off such software, or exclude
the directory containing the OpenJDK source code from on-the-fly checking.

### Ccache

The OpenJDK build supports building with ccache when using gcc or clang. Using
ccache can radically speed up compilation of native code if you often rebuild
the same sources. Your milage may vary however, so we recommend evaluating it
for yourself. To enable it, make sure it's on the path and configure with
`--enable-ccache`.

### Precompiled Headers

By default, the Hotspot build uses preccompiled headers (PCH) on the toolchains
were it is properly supported (clang, gcc, and Visual Studio). Normally, this
speeds up the build process, but in some circumstances, it can actually slow
things down.

You can experiment by disabling precompiled headers using
`--disable-precompiled-headers`.

### Icecc / icecream

[icecc/icecream](http://github.com/icecc/icecream) is a simple way to setup a
distributed compiler network. If you have multiple machines available for
building OpenJDK, you can drastically cut individual build times by utilizing
it.

To use, setup an icecc network, and install icecc on the build machine. Then
run `configure` using `--enable-icecc`.

### Using sjavac

To speed up Java compilation, especially incremental compilations, you can try
the experimental sjavac compiler by using `--enable-sjavac`.

### Building the Right Target

Selecting the proper target to build can have dramatic impact on build time.
For normal usage, `jdk` or the default target is just fine. You only need to
build `images` for shipping, or if your tests require it.

See also [Using Fine-Grained Make Targets](#using-fine-grained-make-targets) on
how to build an even smaller subset of the product.

## Troubleshooting

If your build fails, it can sometimes be difficult to pinpoint the problem or
find a proper solution.

### Locating the Source of the Error

When a build fails, it can be hard to pinpoint the actual cause of the error.
In a typical build process, different parts of the product build in parallel,
with the output interlaced.

#### Build Failure Summary

To help you, the build system will print a failure summary at the end. It looks
like this:

```
ERROR: Build failed for target 'hotspot' in configuration 'linux-x64' (exit code 2)

=== Output from failing command(s) repeated here ===
* For target hotspot_variant-server_libjvm_objs_psMemoryPool.o:
/localhome/hg/jdk9-sandbox/hotspot/src/share/vm/services/psMemoryPool.cpp:1:1: error: 'failhere' does not name a type
   ... (rest of output omitted)

* All command lines available in /localhome/hg/jdk9-sandbox/build/linux-x64/make-support/failure-logs.
=== End of repeated output ===

=== Make failed targets repeated here ===
lib/CompileJvm.gmk:207: recipe for target '/localhome/hg/jdk9-sandbox/build/linux-x64/hotspot/variant-server/libjvm/objs/psMemoryPool.o' failed
make/Main.gmk:263: recipe for target 'hotspot-server-libs' failed
=== End of repeated output ===

Hint: Try searching the build log for the name of the first failed target.
Hint: If caused by a warning, try configure --disable-warnings-as-errors.
```

Let's break it down! First, the selected configuration, and the top-level
target you entered on the command line that caused the failure is printed.

Then, between the `Output from failing command(s) repeated here` and `End of
repeated output` the first lines of output (stdout and stderr) from the actual
failing command is repeated. In most cases, this is the error message that
caused the build to fail. If multiple commands were failing (this can happen in
a parallel build), output from all failed commands will be printed here.

The path to the `failure-logs` directory is printed. In this file you will find
a `<target>.log` file that contains the output from this command in its
entirety, and also a `<target>.cmd`, which contain the complete command line
used for running this command. You can re-run the failing command by executing
`. <path to failure-logs>/<target>.cmd` in your shell.

Another way to trace the failure is to follow the chain of make targets, from
top-level targets to individual file targets. Between `Make failed targets
repeated here` and `End of repeated output` the output from make showing this
chain is repeated. The first failed recipe will typically contain the full path
to the file in question that failed to compile. Following lines will show a
trace of make targets why we ended up trying to compile that file.

Finally, some hints are given on how to locate the error in the complete log.
In this example, we would try searching the log file for "`psMemoryPool.o`".
Another way to quickly locate make errors in the log is to search for "`]
Error`" or "`***`".

Note that the build failure summary will only help you if the issue was a
compilation failure or similar. If the problem is more esoteric, or is due to
errors in the build machinery, you will likely get empty output logs, and `No
indication of failed target found` instead of the make target chain.

#### Checking the Build Log File

The output (stdout and stderr) from the latest build is always stored in
`$BUILD/build.log`. The previous build log is stored as `build.log.old`. This
means that it is not necessary to redirect the build output yourself if you
want to process it.

You can increase the verbosity of the log file, by the `LOG` control variable
to `make`. If you want to see the command lines used in compilations, use
`LOG=cmdlines`. To increase the general verbosity, use `LOG=info`, `LOG=debug`
or `LOG=trace`. Both of these can be combined with `cmdlines`, e.g.
`LOG=info,cmdlines`. The `debug` log level will show most shell commands
executed by make, and `trace` will show all. Beware that both these log levels
will produce a massive build log!

### Fixing Unexpected Build Failures

Most of the time, the build will fail due to incorrect changes in the source
code.

Sometimes the build can fail with no apparent changes that have caused the
failure. If this is the first time you are building OpenJDK on this particular
computer, and the build fails, the problem is likely with your build
environment. But even if you have previously built OpenJDK with success, and it
now fails, your build environment might have changed (perhaps due to OS
upgrades or similar). But most likely, such failures are due to problems with
the incremental rebuild.

#### Problems with the Build Environment

Make sure your configuration is correct. Re-run `configure`, and look for any
warnings. Warnings that appear in the middle of the `configure` output is also
repeated at the end, after the summary. The entire log is stored in
`$BUILD/configure.log`.

Verify that the summary at the end looks correct. Are you indeed using the Boot
JDK and native toolchain that you expect?

By default, OpenJDK has a strict approach where warnings from the compiler is
considered errors which fail the build. For very new or very old compiler
versions, this can trigger new classes of warnings, which thus fails the build.
Run `configure` with `--disable-warnings-as-errors` to turn of this behavior.
(The warnings will still show, but not make the build fail.)

#### Problems with Incremental Rebuilds

Incremental rebuilds mean that when you modify part of the product, only the
affected parts get rebuilt. While this works great in most cases, and
significantly speed up the development process, from time to time complex
interdependencies will result in an incorrect build result. This is the most
common cause for unexpected build problems, together with inconsistencies
between the different Mercurial repositories in the forest.

Here are a suggested list of things to try if you are having unexpected build
problems. Each step requires more time than the one before, so try them in
order. Most issues will be solved at step 1 or 2.

 1. Make sure your forest is up-to-date

    Run `bash get_source.sh` to make sure you have the latest version of all
    repositories.

 2. Clean build results

    The simplest way to fix incremental rebuild issues is to run `make clean`.
    This will remove all build results, but not the configuration or any build
    system support artifacts. In most cases, this will solve build errors
    resulting from incremental build mismatches.

 3. Completely clean the build directory.

    If this does not work, the next step is to run `make dist-clean`, or
    removing the build output directory (`$BUILD`). This will clean all
    generated output, including your configuration. You will need to re-run
    `configure` after this step. A good idea is to run `make
    print-configuration` before running `make dist-clean`, as this will print
    your current `configure` command line. Here's a way to do this:

    ```
    make print-configuration > current-configuration
    make dist-clean
    bash configure $(cat current-configuration)
    make
    ```

 4. Re-clone the Mercurial forest

    Sometimes the Mercurial repositories themselves gets in a state that causes
    the product to be un-buildable. In such a case, the simplest solution is
    often the "sledgehammer approach": delete the entire forest, and re-clone
    it. If you have local changes, save them first to a different location
    using `hg export`.

### Specific Build Issues

#### Clock Skew

If you get an error message like this:
```
File 'xxx' has modification time in the future.
Clock skew detected. Your build may be incomplete.
```
then the clock on your build machine is out of sync with the timestamps on the
source files. Other errors, apparently unrelated but in fact caused by the
clock skew, can occur along with the clock skew warnings. These secondary
errors may tend to obscure the fact that the true root cause of the problem is
an out-of-sync clock.

If you see these warnings, reset the clock on the build machine, run `make
clean` and restart the build.

#### Out of Memory Errors

On Solaris, you might get an error message like this:
```
Trouble writing out table to disk
```
To solve this, increase the amount of swap space on your build machine.

On Windows, you might get error messages like this:
```
fatal error - couldn't allocate heap
cannot create ... Permission denied
spawn failed
```
This can be a sign of a Cygwin problem. See the information about solving
problems in the [Cygwin](#cygwin) section. Rebooting the computer might help
temporarily.

### Getting Help

If none of the suggestions in this document helps you, or if you find what you
believe is a bug in the build system, please contact the Build Group by sending
a mail to [build-dev@openjdk.java.net](mailto:build-dev@openjdk.java.net).
Please include the relevant parts of the configure and/or build log.

If you need general help or advice about developing for OpenJDK, you can also
contact the Adoption Group. See the section on [Contributing to OpenJDK](
#contributing-to-openjdk) for more information.

## Hints and Suggestions for Advanced Users

### Setting Up a Forest for Pushing Changes (defpath)

To help you prepare a proper push path for a Mercurial repository, there exists
a useful tool known as [defpath](
http://openjdk.java.net/projects/code-tools/defpath). It will help you setup a
proper push path for pushing changes to OpenJDK.

Install the extension by cloning
`http://hg.openjdk.java.net/code-tools/defpath` and updating your `.hgrc` file.
Here's one way to do this:

```
cd ~
mkdir hg-ext
cd hg-ext
hg clone http://hg.openjdk.java.net/code-tools/defpath
cat << EOT >> ~/.hgrc
[extensions]
defpath=~/hg-ext/defpath/defpath.py
EOT
```

You can now setup a proper push path using:
```
hg defpath -d -u <your OpenJDK username>
```

If you also have the `trees` extension installed in Mercurial, you will
automatically get a `tdefpath` command, which is even more useful. By running
`hg tdefpath -du <username>` in the top repository of your forest, all repos
will get setup automatically. This is the recommended usage.

### Bash Completion

The `configure` and `make` commands tries to play nice with bash command-line
completion (using `<tab>` or `<tab><tab>`). To use this functionality, make
sure you enable completion in your `~/.bashrc` (see instructions for bash in
your operating system).

Make completion will work out of the box, and will complete valid make targets.
For instance, typing `make jdk-i<tab>` will complete to `make jdk-image`.

The `configure` script can get completion for options, but for this to work you
need to help `bash` on the way. The standard way of running the script, `bash
configure`, will not be understood by bash completion. You need `configure` to
be the command to run. One way to achieve this is to add a simple helper script
to your path:

```
cat << EOT > /tmp/configure
#!/bin/bash
if [ \$(pwd) = \$(cd \$(dirname \$0); pwd) ] ; then
  echo >&2 "Abort: Trying to call configure helper recursively"
  exit 1
fi

bash \$PWD/configure "\$@"
EOT
chmod +x /tmp/configure
sudo mv /tmp/configure /usr/local/bin
```

Now `configure --en<tab>-dt<tab>` will result in `configure --enable-dtrace`.

### Using Multiple Configurations

You can have multiple configurations for a single source forest. When you
create a new configuration, run `configure --with-conf-name=<name>` to create a
configuration with the name `<name>`. Alternatively, you can create a directory
under `build` and run `configure` from there, e.g. `mkdir build/<name> && cd
build/<name> && bash ../../configure`.

Then you can build that configuration using `make CONF_NAME=<name>` or `make
CONF=<pattern>`, where `<pattern>` is a substring matching one or several
configurations, e.g. `CONF=debug`. The special empty pattern (`CONF=`) will
match *all* available configuration, so `make CONF= hotspot` will build the
`hotspot` target for all configurations. Alternatively, you can execute `make`
in the configuration directory, e.g. `cd build/<name> && make`.

### Handling Reconfigurations

If you update the forest and part of the configure script has changed, the
build system will force you to re-run `configure`.

Most of the time, you will be fine by running `configure` again with the same
arguments as the last time, which can easily be performed by `make
reconfigure`. To simplify this, you can use the `CONF_CHECK` make control
variable, either as `make CONF_CHECK=auto`, or by setting an environment
variable. For instance, if you add `export CONF_CHECK=auto` to your `.bashrc`
file, `make` will always run `reconfigure` automatically whenever the configure
script has changed.

You can also use `CONF_CHECK=ignore` to skip the check for a needed configure
update. This might speed up the build, but comes at the risk of an incorrect
build result. This is only recommended if you know what you're doing.

From time to time, you will also need to modify the command line to `configure`
due to changes. Use `make print-configure` to show the command line used for
your current configuration.

### Using Fine-Grained Make Targets

The default behavior for make is to create consistent and correct output, at
the expense of build speed, if necessary.

If you are prepared to take some risk of an incorrect build, and know enough of
the system to understand how things build and interact, you can speed up the
build process considerably by instructing make to only build a portion of the
product.

#### Building Individual Modules

The safe way to use fine-grained make targets is to use the module specific
make targets. All source code in JDK 9 is organized so it belongs to a module,
e.g. `java.base` or `jdk.jdwp.agent`. You can build only a specific module, by
giving it as make target: `make jdk.jdwp.agent`. If the specified module
depends on other modules (e.g. `java.base`), those modules will be built first.

You can also specify a set of modules, just as you can always specify a set of
make targets: `make jdk.crypto.cryptoki jdk.crypto.ec jdk.crypto.mscapi
jdk.crypto.ucrypto`

#### Building Individual Module Phases

The build process for each module is divided into separate phases. Not all
modules need all phases. Which are needed depends on what kind of source code
and other artifact the module consists of. The phases are:

  * `gensrc` (Generate source code to compile)
  * `gendata` (Generate non-source code artifacts)
  * `copy` (Copy resource artifacts)
  * `java` (Compile Java code)
  * `launchers` (Compile native executables)
  * `libs` (Compile native libraries)
  * `rmic` (Run the `rmic` tool)

You can build only a single phase for a module by using the notation
`$MODULE-$PHASE`. For instance, to build the `gensrc` phase for `java.base`,
use `make java.base-gensrc`.

Note that some phases may depend on others, e.g. `java` depends on `gensrc` (if
present). Make will build all needed prerequisites before building the
requested phase.

#### Skipping the Dependency Check

When using an iterative development style with frequent quick rebuilds, the
dependency check made by make can take up a significant portion of the time
spent on the rebuild. In such cases, it can be useful to bypass the dependency
check in make.

> **Note that if used incorrectly, this can lead to a broken build!**

To achieve this, append `-only` to the build target. For instance, `make
jdk.jdwp.agent-java-only` will *only* build the `java` phase of the
`jdk.jdwp.agent` module. If the required dependencies are not present, the
build can fail. On the other hand, the execution time measures in milliseconds.

A useful pattern is to build the first time normally (e.g. `make
jdk.jdwp.agent`) and then on subsequent builds, use the `-only` make target.

#### Rebuilding Part of java.base (JDK\_FILTER)

If you are modifying files in `java.base`, which is the by far largest module
in OpenJDK, then you need to rebuild all those files whenever a single file has
changed. (This inefficiency will hopefully be addressed in JDK 10.)

As a hack, you can use the make control variable `JDK_FILTER` to specify a
pattern that will be used to limit the set of files being recompiled. For
instance, `make java.base JDK_FILTER=javax/crypto` (or, to combine methods,
`make java.base-java-only JDK_FILTER=javax/crypto`) will limit the compilation
to files in the `javax.crypto` package.

### Learn About Mercurial

To become an efficient OpenJDK developer, it is recommended that you invest in
learning Mercurial properly. Here are some links that can get you started:

  * [Mercurial for git users](http://www.mercurial-scm.org/wiki/GitConcepts)
  * [The official Mercurial tutorial](http://www.mercurial-scm.org/wiki/Tutorial)
  * [hg init](http://hginit.com/)
  * [Mercurial: The Definitive Guide](http://hgbook.red-bean.com/read/)

## Understanding the Build System

This section will give you a more technical description on the details of the
build system.

### Configurations

The build system expects to find one or more configuration. These are
technically defined by the `spec.gmk` in a subdirectory to the `build`
subdirectory. The `spec.gmk` file is generated by `configure`, and contains in
principle the configuration (directly or by files included by `spec.gmk`).

You can, in fact, select a configuration to build by pointing to the `spec.gmk`
file with the `SPEC` make control variable, e.g. `make SPEC=$BUILD/spec.gmk`.
While this is not the recommended way to call `make` as a user, it is what is
used under the hood by the build system.

### Build Output Structure

The build output for a configuration will end up in `build/<configuration
name>`, which we refer to as `$BUILD` in this document. The `$BUILD` directory
contains the following important directories:

```
buildtools/
configure-support/
hotspot/
images/
jdk/
make-support/
support/
test-results/
test-support/
```

This is what they are used for:

  * `images`: This is the directory were the output of the `*-image` make
    targets end up. For instance, `make jdk-image` ends up in `images/jdk`.

  * `jdk`: This is the "exploded image". After `make jdk`, you will be able to
    launch the newly built JDK by running `$BUILD/jdk/bin/java`.

  * `test-results`: This directory contains the results from running tests.

  * `support`: This is an area for intermediate files needed during the build,
    e.g. generated source code, object files and class files. Some noteworthy
    directories in `support` is `gensrc`, which contains the generated source
    code, and the `modules_*` directories, which contains the files in a
    per-module hierarchy that will later be collapsed into the `jdk` directory
    of the exploded image.

  * `buildtools`: This is an area for tools compiled for the build platform
    that are used during the rest of the build.

  * `hotspot`: This is an area for intermediate files needed when building
    hotspot.

  * `configure-support`, `make-support` and `test-support`: These directories
    contain files that are needed by the build system for `configure`, `make`
    and for running tests.

### Fixpath

Windows path typically look like `C:\User\foo`, while Unix paths look like
`/home/foo`. Tools with roots from Unix often experience issues related to this
mismatch when running on Windows.

In the OpenJDK build, we always use Unix paths internally, and only just before
calling a tool that does not understand Unix paths do we convert them to
Windows paths.

This conversion is done by the `fixpath` tool, which is a small wrapper that
modifies unix-style paths to Windows-style paths in command lines. Fixpath is
compiled automatically by `configure`.

### Native Debug Symbols

Native libraries and executables can have debug symbol (and other debug
information) associated with them. How this works is very much platform
dependent, but a common problem is that debug symbol information takes a lot of
disk space, but is rarely needed by the end user.

The OpenJDK supports different methods on how to handle debug symbols. The
method used is selected by `--with-native-debug-symbols`, and available methods
are `none`, `internal`, `external`, `zipped`.

  * `none` means that no debug symbols will be generated during the build.

  * `internal` means that debug symbols will be generated during the build, and
    they will be stored in the generated binary.

  * `external` means that debug symbols will be generated during the build, and
    after the compilation, they will be moved into a separate `.debuginfo` file.
    (This was previously known as FDS, Full Debug Symbols).

  * `zipped` is like `external`, but the .debuginfo file will also be zipped
    into a `.diz` file.

When building for distribution, `zipped` is a good solution. Binaries built
with `internal` is suitable for use by developers, since they facilitate
debugging, but should be stripped before distributed to end users.

### Autoconf Details

The `configure` script is based on the autoconf framework, but in some details
deviate from a normal autoconf `configure` script.

The `configure` script in the top level directory of OpenJDK is just a thin
wrapper that calls `common/autoconf/configure`. This in turn provides
functionality that is not easily expressed in the normal Autoconf framework,
and then calls into the core of the `configure` script, which is the
`common/autoconf/generated-configure.sh` file.

As the name implies, this file is generated by Autoconf. It is checked in after
regeneration, to alleviate the common user to have to install Autoconf.

The build system will detect if the Autoconf source files have changed, and
will trigger a regeneration of `common/autoconf/generated-configure.sh` if
needed. You can also manually request such an update by `bash
common/autoconf/autogen.sh`.

If you make changes to the build system that requires a re-generation, note the
following:

  * You must use *exactly* version 2.69 of autoconf for your patch to be
    accepted. This is to avoid spurious changes in the generated file. Note
    that Ubuntu 16.04 ships a patched version of autoconf which claims to be
    2.69, but is not.

  * You do not need to include the generated file in reviews.

  * If the generated file needs updating, the Oracle JDK closed counter-part
    will also need to be updated. It is very much appreciated if you ask for an
    Oracle engineer to sponsor your push so this can be made in tandem.

### Developing the Build System Itself

This section contains a few remarks about how to develop for the build system
itself. It is not relevant if you are only making changes in the product source
code.

While technically using `make`, the make source files of the OpenJDK does not
resemble most other Makefiles. Instead of listing specific targets and actions
(perhaps using patterns), the basic modus operandi is to call a high-level
function (or properly, macro) from the API in `make/common`. For instance, to
compile all classes in the `jdk.internal.foo` package in the `jdk.foo` module,
a call like this would be made:

```
$(eval $(call SetupJavaCompilation, BUILD_FOO_CLASSES, \
    SETUP := GENERATE_OLDBYTECODE, \
    SRC := $(JDK_TOPDIR)/src/jkd.foo/share/classes, \
    INCLUDES := jdk/internal/foo, \
    BIN := $(SUPPORT_OUTPUTDIR)/foo_classes, \
))
```

By encapsulating and expressing the high-level knowledge of *what* should be
done, rather than *how* it should be done (as is normal in Makefiles), we can
build a much more powerful and flexible build system.

Correct dependency tracking is paramount. Sloppy dependency tracking will lead
to improper parallelization, or worse, race conditions.

To test for/debug race conditions, try running `make JOBS=1` and `make
JOBS=100` and see if it makes any difference. (It shouldn't).

To compare the output of two different builds and see if, and how, they differ,
run `$BUILD1/compare.sh -o $BUILD2`, where `$BUILD1` and `$BUILD2` are the two
builds you want to compare.

To automatically build two consecutive versions and compare them, use
`COMPARE_BUILD`. The value of `COMPARE_BUILD` is a set of variable=value
assignments, like this:
```
make COMPARE_BUILD=CONF=--enable-new-hotspot-feature:MAKE=hotspot
```
See `make/InitSupport.gmk` for details on how to use `COMPARE_BUILD`.

To analyze build performance, run with `LOG=trace` and check `$BUILD/build-trace-time.log`.
Use `JOBS=1` to avoid parallelism.

Please check that you adhere to the [Code Conventions for the Build System](
http://openjdk.java.net/groups/build/doc/code-conventions.html) before
submitting patches. Also see the section in [Autoconf Details](
#autoconf-details) about the generated configure script.

## Contributing to OpenJDK

So, now you've build your OpenJDK, and made your first patch, and want to
contribute it back to the OpenJDK community.

First of all: Thank you! We gladly welcome your contribution to the OpenJDK.
However, please bear in mind that OpenJDK is a massive project, and we must ask
you to follow our rules and guidelines to be able to accept your contribution.

The official place to start is the ['How to contribute' page](
http://openjdk.java.net/contribute/). There is also an official (but somewhat
outdated and skimpy on details) [Developer's Guide](
http://openjdk.java.net/guide/).

If this seems overwhelming to you, the Adoption Group is there to help you! A
good place to start is their ['New Contributor' page](
https://wiki.openjdk.java.net/display/Adoption/New+Contributor), or start
reading the comprehensive [Getting Started Kit](
https://adoptopenjdk.gitbooks.io/adoptopenjdk-getting-started-kit/en/). The
Adoption Group will also happily answer any questions you have about
contributing. Contact them by [mail](
http://mail.openjdk.java.net/mailman/listinfo/adoption-discuss) or [IRC](
http://openjdk.java.net/irc/).

---
# Override styles from the base CSS file that are not ideal for this document.
header-includes:
 - '<style type="text/css">pre, code, tt { color: #1d6ae5; }</style>'
---
