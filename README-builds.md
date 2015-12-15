![OpenJDK](http://openjdk.java.net/images/openjdk.png)
# OpenJDK Build README

*****

<a name="introduction"></a>
## Introduction

This README file contains build instructions for the
[OpenJDK](http://openjdk.java.net). Building the source code for the OpenJDK
requires a certain degree of technical expertise.

### !!!!!!!!!!!!!!! THIS IS A MAJOR RE-WRITE of this document. !!!!!!!!!!!!!

Some Headlines:

 * The build is now a "`configure && make`" style build
 * Any GNU make 3.81 or newer should work, except on Windows where 4.0 or newer
   is recommended.
 * The build should scale, i.e. more processors should cause the build to be
   done in less wall-clock time
 * Nested or recursive make invocations have been significantly reduced,
   as has the total fork/exec or spawning of sub processes during the build
 * Windows MKS usage is no longer supported
 * Windows Visual Studio `vsvars*.bat` and `vcvars*.bat` files are run
   automatically
 * Ant is no longer used when building the OpenJDK
 * Use of ALT_* environment variables for configuring the build is no longer
   supported

*****

## Contents

  * [Introduction](#introduction)
  * [Use of Mercurial](#hg)
    * [Getting the Source](#get_source)
    * [Repositories](#repositories)
  * [Building](#building)
    * [System Setup](#setup)
      * [Linux](#linux)
      * [Solaris](#solaris)
      * [Mac OS X](#macosx)
      * [Windows](#windows)
    * [Configure](#configure)
    * [Make](#make)
  * [Testing](#testing)

*****

  * [Appendix A: Hints and Tips](#hints)
    * [FAQ](#faq)
    * [Build Performance Tips](#performance)
    * [Troubleshooting](#troubleshooting)
  * [Appendix B: GNU Make Information](#gmake)
  * [Appendix C: Build Environments](#buildenvironments)

*****

<a name="hg"></a>
## Use of Mercurial

The OpenJDK sources are maintained with the revision control system
[Mercurial](http://mercurial.selenic.com/wiki/Mercurial). If you are new to
Mercurial, please see the [Beginner Guides](http://mercurial.selenic.com/wiki/
BeginnersGuides) or refer to the [Mercurial Book](http://hgbook.red-bean.com/).
The first few chapters of the book provide an excellent overview of Mercurial,
what it is and how it works.

For using Mercurial with the OpenJDK refer to the [Developer Guide: Installing
and Configuring Mercurial](http://openjdk.java.net/guide/
repositories.html#installConfig) section for more information.

<a name="get_source"></a>
### Getting the Source

To get the entire set of OpenJDK Mercurial repositories use the script
`get_source.sh` located in the root repository:

      hg clone http://hg.openjdk.java.net/jdk9/jdk9 YourOpenJDK
      cd YourOpenJDK
      bash ./get_source.sh

Once you have all the repositories, keep in mind that each repository is its
own independent repository. You can also re-run `./get_source.sh` anytime to
pull over all the latest changesets in all the repositories. This set of
nested repositories has been given the term "forest" and there are various
ways to apply the same `hg` command to each of the repositories. For
example, the script `make/scripts/hgforest.sh` can be used to repeat the
same `hg` command on every repository, e.g.

      cd YourOpenJDK
      bash ./make/scripts/hgforest.sh status

<a name="repositories"></a>
### Repositories

The set of repositories and what they contain:

 * **. (root)** contains common configure and makefile logic
 * **hotspot** contains source code and make files for building the OpenJDK
   Hotspot Virtual Machine
 * **langtools** contains source code for the OpenJDK javac and language tools
 * **jdk** contains source code and make files for building the OpenJDK runtime
   libraries and misc files
 * **jaxp** contains source code for the OpenJDK JAXP functionality
 * **jaxws** contains source code for the OpenJDK JAX-WS functionality
 * **corba** contains source code for the OpenJDK Corba functionality
 * **nashorn** contains source code for the OpenJDK JavaScript implementation

### Repository Source Guidelines

There are some very basic guidelines:

 * Use of whitespace in source files (.java, .c, .h, .cpp, and .hpp files) is
   restricted. No TABs, no trailing whitespace on lines, and files should not
   terminate in more than one blank line.
 * Files with execute permissions should not be added to the source
   repositories.
 * All generated files need to be kept isolated from the files maintained or
   managed by the source control system. The standard area for generated files
   is the top level `build/` directory.
 * The default build process should be to build the product and nothing else,
   in one form, e.g. a product (optimized), debug (non-optimized, -g plus
   assert logic), or fastdebug (optimized, -g plus assert logic).
 * The `.hgignore` file in each repository must exist and should include
   `^build/`, `^dist/` and optionally any `nbproject/private` directories. **It
   should NEVER** include anything in the `src/` or `test/` or any managed
   directory area of a repository.
 * Directory names and file names should never contain blanks or non-printing
   characters.
 * Generated source or binary files should NEVER be added to the repository
   (that includes `javah` output). There are some exceptions to this rule, in
   particular with some of the generated configure scripts.
 * Files not needed for typical building or testing of the repository should
   not be added to the repository.

*****

<a name="building"></a>
## Building

The very first step in building the OpenJDK is making sure the system itself
has everything it needs to do OpenJDK builds. Once a system is setup, it
generally doesn't need to be done again.

Building the OpenJDK is now done with running a `configure` script which will
try and find and verify you have everything you need, followed by running
`make`, e.g.

>  **`bash ./configure`**  
>  **`make all`**

Where possible the `configure` script will attempt to located the various
components in the default locations or via component specific variable
settings. When the normal defaults fail or components cannot be found,
additional `configure` options may be necessary to help `configure` find the
necessary tools for the build, or you may need to re-visit the setup of your
system due to missing software packages.

**NOTE:** The `configure` script file does not have execute permissions and
will need to be explicitly run with `bash`, see the source guidelines.

*****

<a name="setup"></a>
### System Setup

Before even attempting to use a system to build the OpenJDK there are some very
basic system setups needed. For all systems:

 * Be sure the GNU make utility is version 3.81 (4.0 on windows) or newer, e.g.
   run "`make -version`"

   <a name="bootjdk"></a>
 * Install a Bootstrap JDK. All OpenJDK builds require access to a previously
   released JDK called the _bootstrap JDK_ or _boot JDK._ The general rule is
   that the bootstrap JDK must be an instance of the previous major release of
   the JDK. In addition, there may be a requirement to use a release at or
   beyond a particular update level.

   **_Building JDK 9 requires JDK 8. JDK 9 developers should not use JDK 9 as
   the boot JDK, to ensure that JDK 9 dependencies are not introduced into the
   parts of the system that are built with JDK 8._**

   The JDK 8 binaries can be downloaded from Oracle's [JDK 8 download
   site](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
   For build performance reasons it is very important that this bootstrap JDK
   be made available on the local disk of the machine doing the build. You
   should add its `bin` directory to the `PATH` environment variable. If
   `configure` has any issues finding this JDK, you may need to use the
   `configure` option `--with-boot-jdk`.

 * Ensure that GNU make, the Bootstrap JDK, and the compilers are all in your
   PATH environment variable.

And for specific systems:

 * **Linux**

   Install all the software development packages needed including
   [alsa](#alsa), [freetype](#freetype), [cups](#cups), and
   [xrender](#xrender). See [specific system packages](#SDBE).

 * **Solaris**

   Install all the software development packages needed including [Studio
   Compilers](#studio), [freetype](#freetype), [cups](#cups), and
   [xrender](#xrender). See [specific system packages](#SDBE).

 * **Windows**

   * Install one of [CYGWIN](#cygwin) or [MinGW/MSYS](#msys)
   * Install [Visual Studio 2013](#vs2013)

 * **Mac OS X**

   Install [XCode 6.3](https://developer.apple.com/xcode/)

<a name="linux"></a>
#### Linux

With Linux, try and favor the system packages over building your own or getting
packages from other areas. Most Linux builds should be possible with the
system's available packages.

Note that some Linux systems have a habit of pre-populating your environment
variables for you, for example `JAVA_HOME` might get pre-defined for you to
refer to the JDK installed on your Linux system. You will need to unset
`JAVA_HOME`. It's a good idea to run `env` and verify the environment variables
you are getting from the default system settings make sense for building the
OpenJDK.

<a name="solaris"></a>
#### Solaris

<a name="studio"></a>
##### Studio Compilers

At a minimum, the [Studio 12 Update 4 Compilers](http://www.oracle.com/
technetwork/server-storage/solarisstudio/downloads/index.htm) (containing
version 5.13 of the C and C++ compilers) is required, including specific
patches.

The Solaris Studio installation should contain at least these packages:

>  <table border="1">
     <thead>
       <tr>
         <td>**Package**</td>
         <td>**Version**</td>
       </tr>
     </thead>
     <tbody>
       <tr>
         <td>developer/solarisstudio-124/backend</td>
         <td>12.4-1.0.6.0</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/c++</td>
         <td>12.4-1.0.10.0</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/cc</td>
         <td>12.4-1.0.4.0</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/library/c++-libs</td>
         <td>12.4-1.0.10.0</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/library/math-libs</td>
         <td>12.4-1.0.0.1</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/library/studio-gccrt</td>
         <td>12.4-1.0.0.1</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/studio-common</td>
         <td>12.4-1.0.0.1</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/studio-ja</td>
         <td>12.4-1.0.0.1</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/studio-legal</td>
         <td>12.4-1.0.0.1</td>
       </tr>
       <tr>
         <td>developer/solarisstudio-124/studio-zhCN</td>
         <td>12.4-1.0.0.1</td>
       </tr>
     </tbody>
   </table>

In particular backend 12.4-1.0.6.0 contains a critical patch for the sparc
version.

Place the `bin` directory in `PATH`.

The Oracle Solaris Studio Express compilers at: [Oracle Solaris Studio Express
Download site](http://www.oracle.com/technetwork/server-storage/solarisstudio/
downloads/index-jsp-142582.html) are also an option, although these compilers
have not been extensively used yet.

<a name="windows"></a>
#### Windows

##### Windows Unix Toolkit

Building on Windows requires a Unix-like environment, notably a Unix-like
shell. There are several such environments available of which
[Cygwin](http://www.cygwin.com/) and
[MinGW/MSYS](http://www.mingw.org/wiki/MSYS) are currently supported for the
OpenJDK build. One of the differences of these systems from standard Windows
tools is the way they handle Windows path names, particularly path names which
contain spaces, backslashes as path separators and possibly drive letters.
Depending on the use case and the specifics of each environment these path
problems can be solved by a combination of quoting whole paths, translating
backslashes to forward slashes, escaping backslashes with additional
backslashes and translating the path names to their ["8.3"
version](http://en.wikipedia.org/wiki/8.3_filename).

<a name="cygwin"></a>
###### CYGWIN

CYGWIN is an open source, Linux-like environment which tries to emulate a
complete POSIX layer on Windows. It tries to be smart about path names and can
usually handle all kinds of paths if they are correctly quoted or escaped
although internally it maps drive letters `<drive>:` to a virtual directory
`/cygdrive/<drive>`.

You can always use the `cygpath` utility to map pathnames with spaces or the
backslash character into the `C:/` style of pathname (called 'mixed'), e.g.
`cygpath -s -m "<path>"`.

Note that the use of CYGWIN creates a unique problem with regards to setting
[`PATH`](#path). Normally on Windows the `PATH` variable contains directories
separated with the ";" character (Solaris and Linux use ":"). With CYGWIN, it
uses ":", but that means that paths like "C:/path" cannot be placed in the
CYGWIN version of `PATH` and instead CYGWIN uses something like
`/cygdrive/c/path` which CYGWIN understands, but only CYGWIN understands.

The OpenJDK build requires CYGWIN version 1.7.16 or newer. Information about
CYGWIN can be obtained from the CYGWIN website at
[www.cygwin.com](http://www.cygwin.com).

By default CYGWIN doesn't install all the tools required for building the
OpenJDK. Along with the default installation, you need to install the following
tools.

>  <table border="1">
     <thead>
       <tr>
         <td>Binary Name</td>
         <td>Category</td>
         <td>Package</td>
         <td>Description</td>
      </tr>
     </thead>
     <tbody>
       <tr>
         <td>ar.exe</td>
         <td>Devel</td>
         <td>binutils</td>
         <td>The GNU assembler, linker and binary utilities</td>
       </tr>
       <tr>
         <td>make.exe</td>
         <td>Devel</td>
         <td>make</td>
         <td>The GNU version of the 'make' utility built for CYGWIN</td>
       </tr>
       <tr>
         <td>m4.exe</td>
         <td>Interpreters</td>
         <td>m4</td>
         <td>GNU implementation of the traditional Unix macro processor</td>
       </tr>
       <tr>
         <td>cpio.exe</td>
         <td>Utils</td>
         <td>cpio</td>
         <td>A program to manage archives of files</td>
       </tr>
       <tr>
         <td>gawk.exe</td>
         <td>Utils</td>
         <td>awk</td>
         <td>Pattern-directed scanning and processing language</td>
       </tr>
       <tr>
         <td>file.exe</td>
         <td>Utils</td>
         <td>file</td>
         <td>Determines file type using 'magic' numbers</td>
       </tr>
       <tr>
         <td>zip.exe</td>
         <td>Archive</td>
         <td>zip</td>
         <td>Package and compress (archive) files</td>
       </tr>
       <tr>
         <td>unzip.exe</td>
         <td>Archive</td>
         <td>unzip</td>
         <td>Extract compressed files in a ZIP archive</td>
       </tr>
       <tr>
         <td>free.exe</td>
         <td>System</td>
         <td>procps</td>
         <td>Display amount of free and used memory in the system</td>
       </tr>
     </tbody>
   </table>

Note that the CYGWIN software can conflict with other non-CYGWIN software on
your Windows system. CYGWIN provides a [FAQ](http://cygwin.com/faq/
faq.using.html) for known issues and problems, of particular interest is the
section on [BLODA (applications that interfere with
CYGWIN)](http://cygwin.com/faq/faq.using.html#faq.using.bloda).

<a name="msys"></a>
###### MinGW/MSYS

MinGW ("Minimalist GNU for Windows") is a collection of free Windows specific
header files and import libraries combined with GNU toolsets that allow one to
produce native Windows programs that do not rely on any 3rd-party C runtime
DLLs. MSYS is a supplement to MinGW which allows building applications and
programs which rely on traditional UNIX tools to be present. Among others this
includes tools like `bash` and `make`. See [MinGW/MSYS](http://www.mingw.org/
wiki/MSYS) for more information.

Like Cygwin, MinGW/MSYS can handle different types of path formats. They are
internally converted to paths with forward slashes and drive letters
`<drive>:` replaced by a virtual directory `/<drive>`. Additionally, MSYS
automatically detects binaries compiled for the MSYS environment and feeds them
with the internal, Unix-style path names. If native Windows applications are
called from within MSYS programs their path arguments are automatically
converted back to Windows style path names with drive letters and backslashes
as path separators. This may cause problems for Windows applications which use
forward slashes as parameter separator (e.g. `cl /nologo /I`) because MSYS may
wrongly [replace such parameters by drive letters](http://mingw.org/wiki/
Posix_path_conversion).

In addition to the tools which will be installed by default, you have to
manually install the `msys-zip` and `msys-unzip` packages. This can be easily
done with the MinGW command line installer:

      mingw-get.exe install msys-zip
      mingw-get.exe install msys-unzip

<a name="vs2013"></a>
##### Visual Studio 2013 Compilers

The 32-bit and 64-bit OpenJDK Windows build requires Microsoft Visual Studio
C++ 2013 (VS2013) Professional Edition or Express compiler. The compiler and
other tools are expected to reside in the location defined by the variable
`VS120COMNTOOLS` which is set by the Microsoft Visual Studio installer.

Only the C++ part of VS2013 is needed. Try to let the installation go to the
default install directory. Always reboot your system after installing VS2013.
The system environment variable VS120COMNTOOLS should be set in your
environment.

Make sure that TMP and TEMP are also set in the environment and refer to
Windows paths that exist, like `C:\temp`, not `/tmp`, not `/cygdrive/c/temp`,
and not `C:/temp`. `C:\temp` is just an example, it is assumed that this area
is private to the user, so by default after installs you should see a unique
user path in these variables.

<a name="macosx"></a>
#### Mac OS X

Make sure you get the right XCode version.

*****

<a name="configure"></a>
### Configure

The basic invocation of the `configure` script looks like:

>  **`bash ./configure [options]`**

This will create an output directory containing the "configuration" and setup
an area for the build result. This directory typically looks like:

>  **`build/linux-x64-normal-server-release`**

`configure` will try to figure out what system you are running on and where all
necessary build components are. If you have all prerequisites for building
installed, it should find everything. If it fails to detect any component
automatically, it will exit and inform you about the problem. When this
happens, read more below in [the `configure` options](#configureoptions).

Some examples:

>  **Windows 32bit build with freetype specified:**  
>  `bash ./configure --with-freetype=/cygdrive/c/freetype-i586 --with-target-
bits=32`

>  **Debug 64bit Build:**  
>  `bash ./configure --enable-debug --with-target-bits=64`

<a name="configureoptions"></a>
#### Configure Options

Complete details on all the OpenJDK `configure` options can be seen with:

>  **`bash ./configure --help=short`**

Use `-help` to see all the `configure` options available. You can generate any
number of different configurations, e.g. debug, release, 32, 64, etc.

Some of the more commonly used `configure` options are:

>  **`--enable-debug`**  
>  set the debug level to fastdebug (this is a shorthand for `--with-debug-
   level=fastdebug`)

<a name="alsa"></a>
>  **`--with-alsa=`**_path_  
>  select the location of the Advanced Linux Sound Architecture (ALSA)

>  Version 0.9.1 or newer of the ALSA files are required for building the
   OpenJDK on Linux. These Linux files are usually available from an "alsa" of
   "libasound" development package, and it's highly recommended that you try
   and use the package provided by the particular version of Linux that you are
   using.

>  **`--with-boot-jdk=`**_path_  
>  select the [Bootstrap JDK](#bootjdk)

>  **`--with-boot-jdk-jvmargs=`**"_args_"  
>  provide the JVM options to be used to run the [Bootstrap JDK](#bootjdk)

>  **`--with-cacerts=`**_path_  
>  select the path to the cacerts file.

>  See [Certificate Authority on Wikipedia](http://en.wikipedia.org/wiki/
   Certificate_Authority) for a better understanding of the Certificate
   Authority (CA). A certificates file named "cacerts" represents a system-wide
   keystore with CA certificates. In JDK and JRE binary bundles, the "cacerts"
   file contains root CA certificates from several public CAs (e.g., VeriSign,
   Thawte, and Baltimore). The source contain a cacerts file without CA root
   certificates. Formal JDK builders will need to secure permission from each
   public CA and include the certificates into their own custom cacerts file.
   Failure to provide a populated cacerts file will result in verification
   errors of a certificate chain during runtime. By default an empty cacerts
   file is provided and that should be fine for most JDK developers.

<a name="cups"></a>
>  **`--with-cups=`**_path_  
>  select the CUPS install location

>  The Common UNIX Printing System (CUPS) Headers are required for building the
   OpenJDK on Solaris and Linux. The Solaris header files can be obtained by
   installing the package **SFWcups** from the Solaris Software Companion
   CD/DVD, these often will be installed into the directory `/opt/sfw/cups`.

>  The CUPS header files can always be downloaded from
   [www.cups.org](http://www.cups.org).

>  **`--with-cups-include=`**_path_  
>  select the CUPS include directory location

>  **`--with-debug-level=`**_level_  
>  select the debug information level of release, fastdebug, or slowdebug

>  **`--with-dev-kit=`**_path_  
>  select location of the compiler install or developer install location

<a name="freetype"></a>
>  **`--with-freetype=`**_path_  
>  select the freetype files to use.

>  Expecting the freetype libraries under `lib/` and the headers under
   `include/`.

>  Version 2.3 or newer of FreeType is required. On Unix systems required files
   can be available as part of your distribution (while you still may need to
   upgrade them). Note that you need development version of package that
   includes both the FreeType library and header files.

>  You can always download latest FreeType version from the [FreeType
   website](http://www.freetype.org). Building the freetype 2 libraries from
   scratch is also possible, however on Windows refer to the [Windows FreeType
   DLL build instructions](http://freetype.freedesktop.org/wiki/FreeType_DLL).

>  Note that by default FreeType is built with byte code hinting support
   disabled due to licensing restrictions. In this case, text appearance and
   metrics are expected to differ from Sun's official JDK build. See the
   [SourceForge FreeType2 Home Page](http://freetype.sourceforge.net/freetype2)
   for more information.

>  **`--with-import-hotspot=`**_path_  
>  select the location to find hotspot binaries from a previous build to avoid
   building hotspot

>  **`--with-target-bits=`**_arg_  
>  select 32 or 64 bit build

>  **`--with-jvm-variants=`**_variants_  
>  select the JVM variants to build from, comma separated list that can
   include: server, client, kernel, zero and zeroshark

>  **`--with-memory-size=`**_size_  
>  select the RAM size that GNU make will think this system has

>  **`--with-msvcr-dll=`**_path_  
>  select the `msvcr100.dll` file to include in the Windows builds (C/C++
   runtime library for Visual Studio).

>  This is usually picked up automatically from the redist directories of
   Visual Studio 2013.

>  **`--with-num-cores=`**_cores_  
>  select the number of cores to use (processor count or CPU count)

<a name="xrender"></a>
>  **`--with-x=`**_path_  
>  select the location of the X11 and xrender files.

>  The XRender Extension Headers are required for building the OpenJDK on
   Solaris and Linux. The Linux header files are usually available from a
   "Xrender" development package, it's recommended that you try and use the
   package provided by the particular distribution of Linux that you are using.
   The Solaris XRender header files is included with the other X11 header files
   in the package **SFWxwinc** on new enough versions of Solaris and will be
   installed in `/usr/X11/include/X11/extensions/Xrender.h` or
   `/usr/openwin/share/include/X11/extensions/Xrender.h`

*****

<a name="make"></a>
### Make

The basic invocation of the `make` utility looks like:

>  **`make all`**

This will start the build to the output directory containing the
"configuration" that was created by the `configure` script. Run `make help` for
more information on the available targets.

There are some of the make targets that are of general interest:

>  _empty_  
>  build everything but no images

>  **`all`**  
>  build everything including images

>  **`all-conf`**  
>  build all configurations

>  **`images`**  
>  create complete j2sdk and j2re images

>  **`install`**  
>  install the generated images locally, typically in `/usr/local`

>  **`clean`**  
>  remove all files generated by make, but not those generated by `configure`

>  **`dist-clean`**  
>  remove all files generated by both and `configure` (basically killing the
   configuration)

>  **`help`**  
>  give some help on using `make`, including some interesting make targets

*****

<a name="testing"></a>
## Testing

When the build is completed, you should see the generated binaries and
associated files in the `j2sdk-image` directory in the output directory. In
particular, the `build/*/images/j2sdk-image/bin` directory should contain
executables for the OpenJDK tools and utilities for that configuration. The
testing tool `jtreg` will be needed and can be found at: [the jtreg
site](http://openjdk.java.net/jtreg/). The provided regression tests in the
repositories can be run with the command:

>  **``cd test && make PRODUCT_HOME=`pwd`/../build/*/images/j2sdk-image all``**

*****

<a name="hints"></a>
## Appendix A: Hints and Tips

<a name="faq"></a>
### FAQ

**Q:** The `generated-configure.sh` file looks horrible! How are you going to
edit it?  
**A:** The `generated-configure.sh` file is generated (think "compiled") by the
autoconf tools. The source code is in `configure.ac` and various .m4 files in
common/autoconf, which are much more readable.

**Q:** Why is the `generated-configure.sh` file checked in, if it is 
generated?  
**A:** If it was not generated, every user would need to have the autoconf
tools installed, and re-generate the `configure` file as the first step. Our
goal is to minimize the work needed to be done by the user to start building
OpenJDK, and to minimize the number of external dependencies required.

**Q:** Do you require a specific version of autoconf for regenerating
`generated-configure.sh`?  
**A:** Yes, version 2.69 is required and should be easy enough to aquire on all
supported operating systems. The reason for this is to avoid large spurious
changes in `generated-configure.sh`.

**Q:** How do you regenerate `generated-configure.sh` after making changes to
the input files?  
**A:** Regnerating `generated-configure.sh` should always be done using the
script `common/autoconf/autogen.sh` to ensure that the correct files get
updated. This script should also be run after mercurial tries to merge
`generated-configure.sh` as a merge of the generated file is not guaranteed to
be correct.

**Q:** What are the files in `common/makefiles/support/*` for? They look like
gibberish.  
**A:** They are a somewhat ugly hack to compensate for command line length
limitations on certain platforms (Windows, Solaris). Due to a combination of
limitations in make and the shell, command lines containing too many files will
not work properly. These helper files are part of an elaborate hack that will
compress the command line in the makefile and then uncompress it safely. We're
not proud of it, but it does fix the problem. If you have any better
suggestions, we're all ears! :-)

**Q:** I want to see the output of the commands that make runs, like in the old
build. How do I do that?  
**A:** You specify the `LOG` variable to make. There are several log levels:

 * **`warn`** -- Default and very quiet.
 * **`info`** -- Shows more progress information than warn.
 * **`debug`** -- Echos all command lines and prints all macro calls for
   compilation definitions.
 * **`trace`** -- Echos all $(shell) command lines as well.

**Q:** When do I have to re-run `configure`?  
**A:** Normally you will run `configure` only once for creating a
configuration. You need to re-run configuration only if you want to change any
configuration options, or if you pull down changes to the `configure` script.

**Q:** I have added a new source file. Do I need to modify the makefiles?  
**A:** Normally, no. If you want to create e.g. a new native library, you will
need to modify the makefiles. But for normal file additions or removals, no
changes are needed. There are certan exceptions for some native libraries where
the source files are spread over many directories which also contain sources
for other libraries. In these cases it was simply easier to create include
lists rather than excludes.

**Q:** When I run `configure --help`, I see many strange options, like
`--dvidir`. What is this?  
**A:** Configure provides a slew of options by default, to all projects that
use autoconf. Most of them are not used in OpenJDK, so you can safely ignore
them. To list only OpenJDK specific features, use `configure --help=short`
instead.

**Q:** `configure` provides OpenJDK-specific features such as `--with-
builddeps-server` that are not described in this document. What about those?  
**A:** Try them out if you like! But be aware that most of these are
experimental features. Many of them don't do anything at all at the moment; the
option is just a placeholder. Others depend on pieces of code or infrastructure
that is currently not ready for prime time.

**Q:** How will you make sure you don't break anything?  
**A:** We have a script that compares the result of the new build system with
the result of the old. For most part, we aim for (and achieve) byte-by-byte
identical output. There are however technical issues with e.g. native binaries,
which might differ in a byte-by-byte comparison, even when building twice with
the old build system. For these, we compare relevant aspects (e.g. the symbol
table and file size). Note that we still don't have 100% equivalence, but we're
close.

**Q:** I noticed this thing X in the build that looks very broken by design.
Why don't you fix it?  
**A:** Our goal is to produce a build output that is as close as technically
possible to the old build output. If things were weird in the old build, they
will be weird in the new build. Often, things were weird before due to
obscurity, but in the new build system the weird stuff comes up to the surface.
The plan is to attack these things at a later stage, after the new build system
is established.

**Q:** The code in the new build system is not that well-structured. Will you
fix this?  
**A:** Yes! The new build system has grown bit by bit as we converted the old
system. When all of the old build system is converted, we can take a step back
and clean up the structure of the new build system. Some of this we plan to do
before replacing the old build system and some will need to wait until after.

**Q:** Is anything able to use the results of the new build's default make
target?  
**A:** Yes, this is the minimal (or roughly minimal) set of compiled output
needed for a developer to actually execute the newly built JDK. The idea is
that in an incremental development fashion, when doing a normal make, you
should only spend time recompiling what's changed (making it purely
incremental) and only do the work that's needed to actually run and test your
code. The packaging stuff that is part of the `images` target is not needed for
a normal developer who wants to test his new code. Even if it's quite fast,
it's still unnecessary. We're targeting sub-second incremental rebuilds! ;-)
(Or, well, at least single-digit seconds...)

**Q:** I usually set a specific environment variable when building, but I can't
find the equivalent in the new build. What should I do?  
**A:** It might very well be that we have neglected to add support for an
option that was actually used from outside the build system. Email us and we
will add support for it!

<a name="performance"></a>
### Build Performance Tips

Building OpenJDK requires a lot of horsepower. Some of the build tools can be
adjusted to utilize more or less of resources such as parallel threads and
memory. The `configure` script analyzes your system and selects reasonable
values for such options based on your hardware. If you encounter resource
problems, such as out of memory conditions, you can modify the detected values
with:

 * **`--with-num-cores`** -- number of cores in the build system, e.g.
   `--with-num-cores=8`
 * **`--with-memory-size`** -- memory (in MB) available in the build system,
    e.g. `--with-memory-size=1024`

It might also be necessary to specify the JVM arguments passed to the Bootstrap
JDK, using e.g. `--with-boot-jdk-jvmargs="-Xmx8G -enableassertions"`. Doing
this will override the default JVM arguments passed to the Bootstrap JDK.

One of the top goals of the new build system is to improve the build
performance and decrease the time needed to build. This will soon also apply to
the java compilation when the Smart Javac wrapper is fully supported.

At the end of a successful execution of `configure`, you will get a performance
summary, indicating how well the build will perform. Here you will also get
performance hints. If you want to build fast, pay attention to those!

#### Building with ccache

The OpenJDK build supports building with ccache when using gcc or clang. Using
ccache can radically speed up compilation of native code if you often rebuild
the same sources. Your milage may vary however so we recommend evaluating it
for yourself. To enable it, make sure it's on the path and configure with
`--enable-ccache`.

#### Building on local disk

If you are using network shares, e.g. via NFS, for your source code, make sure
the build directory is situated on local disk. The performance penalty is
extremely high for building on a network share, close to unusable.

#### Building only one JVM

The old build builds multiple JVMs on 32-bit systems (client and server; and on
Windows kernel as well). In the new build we have changed this default to only
build server when it's available. This improves build times for those not
interested in multiple JVMs. To mimic the old behavior on platforms that
support it, use `--with-jvm-variants=client,server`.

#### Selecting the number of cores to build on

By default, `configure` will analyze your machine and run the make process in
parallel with as many threads as you have cores. This behavior can be
overridden, either "permanently" (on a `configure` basis) using
`--with-num-cores=N` or for a single build only (on a make basis), using
`make JOBS=N`.

If you want to make a slower build just this time, to save some CPU power for
other processes, you can run e.g. `make JOBS=2`. This will force the makefiles
to only run 2 parallel processes, or even `make JOBS=1` which will disable
parallelism.

If you want to have it the other way round, namely having slow builds default
and override with fast if you're impatient, you should call `configure` with
`--with-num-cores=2`, making 2 the default. If you want to run with more cores,
run `make JOBS=8`

<a name="troubleshooting"></a>
### Troubleshooting

#### Solving build problems

If the build fails (and it's not due to a compilation error in a source file
you've changed), the first thing you should do is to re-run the build with more
verbosity. Do this by adding `LOG=debug` to your make command line.

The build log (with both stdout and stderr intermingled, basically the same as
you see on your console) can be found as `build.log` in your build directory.

You can ask for help on build problems with the new build system on either the
[build-dev](http://mail.openjdk.java.net/mailman/listinfo/build-dev) or the
[build-infra-dev](http://mail.openjdk.java.net/mailman/listinfo/build-infra-dev)
mailing lists. Please include the relevant parts of the build log.

A build can fail for any number of reasons. Most failures are a result of
trying to build in an environment in which all the pre-build requirements have
not been met. The first step in troubleshooting a build failure is to recheck
that you have satisfied all the pre-build requirements for your platform.
Scanning the `configure` log is a good first step, making sure that what it
found makes sense for your system. Look for strange error messages or any
difficulties that `configure` had in finding things.

Some of the more common problems with builds are briefly described below, with
suggestions for remedies.

 * **Corrupted Bundles on Windows:**  
   Some virus scanning software has been known to corrupt the downloading of
   zip bundles. It may be necessary to disable the 'on access' or 'real time'
   virus scanning features to prevent this corruption. This type of 'real time'
   virus scanning can also slow down the build process significantly.
   Temporarily disabling the feature, or excluding the build output directory
   may be necessary to get correct and faster builds.

 * **Slow Builds:**  
   If your build machine seems to be overloaded from too many simultaneous C++
   compiles, try setting the `JOBS=1` on the `make` command line. Then try
   increasing the count slowly to an acceptable level for your system. Also:

   Creating the javadocs can be very slow, if you are running javadoc, consider
   skipping that step.

   Faster CPUs, more RAM, and a faster DISK usually helps. The VM build tends
   to be CPU intensive (many C++ compiles), and the rest of the JDK will often
   be disk intensive.

   Faster compiles are possible using a tool called
   [ccache](http://ccache.samba.org/).

 * **File time issues:**  
   If you see warnings that refer to file time stamps, e.g.

   > _Warning message:_ ` File 'xxx' has modification time in the future.`  
   > _Warning message:_ ` Clock skew detected. Your build may be incomplete.`

   These warnings can occur when the clock on the build machine is out of sync
   with the timestamps on the source files. Other errors, apparently unrelated
   but in fact caused by the clock skew, can occur along with the clock skew
   warnings. These secondary errors may tend to obscure the fact that the true
   root cause of the problem is an out-of-sync clock.

   If you see these warnings, reset the clock on the build machine, run
   "`gmake clobber`" or delete the directory containing the build output, and
   restart the build from the beginning.

 * **Error message: `Trouble writing out table to disk`**  
   Increase the amount of swap space on your build machine. This could be
   caused by overloading the system and it may be necessary to use:

   > `make JOBS=1`

   to reduce the load on the system.

 * **Error Message: `libstdc++ not found`:**  
   This is caused by a missing libstdc++.a library. This is installed as part
   of a specific package (e.g. libstdc++.so.devel.386). By default some 64-bit
   Linux versions (e.g. Fedora) only install the 64-bit version of the
   libstdc++ package. Various parts of the JDK build require a static link of
   the C++ runtime libraries to allow for maximum portability of the built
   images.

 * **Linux Error Message: `cannot restore segment prot after reloc`**  
   This is probably an issue with SELinux (See [SELinux on
   Wikipedia](http://en.wikipedia.org/wiki/SELinux)). Parts of the VM is built
   without the `-fPIC` for performance reasons.

   To completely disable SELinux:

   1. `$ su root`
   2. `# system-config-securitylevel`
   3. `In the window that appears, select the SELinux tab`
   4. `Disable SELinux`

   Alternatively, instead of completely disabling it you could disable just
   this one check.

   1. Select System->Administration->SELinux Management
   2. In the SELinux Management Tool which appears, select "Boolean" from the
      menu on the left
   3. Expand the "Memory Protection" group
   4. Check the first item, labeled "Allow all unconfined executables to use
      libraries requiring text relocation ..."

 * **Windows Error Messages:**  
   `*** fatal error - couldn't allocate heap, ... `  
   `rm fails with "Directory not empty"`  
   `unzip fails with "cannot create ... Permission denied"`  
   `unzip fails with "cannot create ... Error 50"`

   The CYGWIN software can conflict with other non-CYGWIN software. See the
   CYGWIN FAQ section on [BLODA (applications that interfere with
   CYGWIN)](http://cygwin.com/faq/faq.using.html#faq.using.bloda).

 * **Windows Error Message: `spawn failed`**  
   Try rebooting the system, or there could be some kind of issue with the disk
   or disk partition being used. Sometimes it comes with a "Permission Denied"
   message.

*****

<a name="gmake"></a>
## Appendix B: GNU make

The Makefiles in the OpenJDK are only valid when used with the GNU version of
the utility command `make` (usually called `gmake` on Solaris). A few notes
about using GNU make:

 * You need GNU make version 3.81 or newer. On Windows 4.0 or newer is
   recommended. If the GNU make utility on your systems is not of a suitable
   version, see "[Building GNU make](#buildgmake)".
 * Place the location of the GNU make binary in the `PATH`.
 * **Solaris:** Do NOT use `/usr/bin/make` on Solaris. If your Solaris system
   has the software from the Solaris Developer Companion CD installed, you
   should try and use `gmake` which will be located in either the `/usr/bin`,
   `/opt/sfw/bin` or `/usr/sfw/bin` directory.
 * **Windows:** Make sure you start your build inside a bash shell.
 * **Mac OS X:** The XCode "command line tools" must be installed on your Mac.

Information on GNU make, and access to ftp download sites, are available on the
[GNU make web site ](http://www.gnu.org/software/make/make.html). The latest
source to GNU make is available at
[ftp.gnu.org/pub/gnu/make/](http://ftp.gnu.org/pub/gnu/make/).

<a name="buildgmake"></a>
### Building GNU make

First step is to get the GNU make 3.81 or newer source from
[ftp.gnu.org/pub/gnu/make/](http://ftp.gnu.org/pub/gnu/make/). Building is a
little different depending on the OS but is basically done with:

      bash ./configure
      make

*****

<a name="buildenvironments"></a>
## Appendix C: Build Environments

### Minimum Build Environments

This file often describes specific requirements for what we call the "minimum
build environments" (MBE) for this specific release of the JDK. What is listed
below is what the Oracle Release Engineering Team will use to build the Oracle
JDK product. Building with the MBE will hopefully generate the most compatible
bits that install on, and run correctly on, the most variations of the same
base OS and hardware architecture. In some cases, these represent what is often
called the least common denominator, but each Operating System has different
aspects to it.

In all cases, the Bootstrap JDK version minimum is critical, we cannot
guarantee builds will work with older Bootstrap JDK's. Also in all cases, more
RAM and more processors is better, the minimums listed below are simply
recommendations.

With Solaris and Mac OS X, the version listed below is the oldest release we
can guarantee builds and works, and the specific version of the compilers used
could be critical.

With Windows the critical aspect is the Visual Studio compiler used, which due
to it's runtime, generally dictates what Windows systems can do the builds and
where the resulting bits can be used.

**NOTE: We expect a change here off these older Windows OS releases and to a
'less older' one, probably Windows 2008R2 X64.**

With Linux, it was just a matter of picking a stable distribution that is a
good representative for Linux in general.

It is understood that most developers will NOT be using these specific
versions, and in fact creating these specific versions may be difficult due to
the age of some of this software. It is expected that developers are more often
using the more recent releases and distributions of these operating systems.

Compilation problems with newer or different C/C++ compilers is a common
problem. Similarly, compilation problems related to changes to the
`/usr/include` or system header files is also a common problem with older,
newer, or unreleased OS versions. Please report these types of problems as bugs
so that they can be dealt with accordingly.

>  <table border="1">
     <thead>
       <tr>
         <th>Base OS and Architecture</th>
         <th>OS</th>
         <th>C/C++ Compiler</th>
         <th>Bootstrap JDK</th>
         <th>Processors</th>
         <th>RAM Minimum</th>
         <th>DISK Needs</th>
       </tr>
     </thead>
     <tbody>
       <tr>
         <td>Linux X86 (32-bit) and X64 (64-bit)</td>
         <td>Oracle Enterprise Linux 6.4</td>
         <td>gcc 4.9.2 </td>
         <td>JDK 8</td>
         <td>2 or more</td>
         <td>1 GB</td>
         <td>6 GB</td>
       </tr>
       <tr>
         <td>Solaris SPARCV9 (64-bit)</td>
         <td>Solaris 11 Update 1</td>
         <td>Studio 12 Update 4 + patches</td>
         <td>JDK 8</td>
         <td>4 or more</td>
         <td>4 GB</td>
         <td>8 GB</td>
       </tr>
       <tr>
         <td>Solaris X64 (64-bit)</td>
         <td>Solaris 11 Update 1</td>
         <td>Studio 12 Update 4 + patches</td>
         <td>JDK 8</td>
         <td>4 or more</td>
         <td>4 GB</td>
         <td>8 GB</td>
       </tr>
       <tr>
         <td>Windows X86 (32-bit)</td>
         <td>Windows Server 2012 R2 x64</td>
         <td>Microsoft Visual Studio C++ 2013 Professional Edition</td>
         <td>JDK 8</td>
         <td>2 or more</td>
         <td>2 GB</td>
         <td>6 GB</td>
       </tr>
       <tr>
         <td>Windows X64 (64-bit)</td>
         <td>Windows Server 2012 R2 x64</td>
         <td>Microsoft Visual Studio C++ 2013 Professional Edition</td>
         <td>JDK 8</td>
         <td>2 or more</td>
         <td>2 GB</td>
         <td>6 GB</td>
       </tr>
       <tr>
         <td>Mac OS X X64 (64-bit)</td>
         <td>Mac OS X 10.9 "Mavericks"</td>
         <td>Xcode 6.3 or newer</td>
         <td>JDK 8</td>
         <td>2 or more</td>
         <td>4 GB</td>
         <td>6 GB</td>
       </tr>
     </tbody>
   </table>

*****

<a name="SDBE"></a>
### Specific Developer Build Environments

We won't be listing all the possible environments, but we will try to provide
what information we have available to us.

**NOTE: The community can help out by updating this part of the document.**

#### Fedora

After installing the latest [Fedora](http://fedoraproject.org) you need to
install several build dependencies. The simplest way to do it is to execute the
following commands as user `root`:

      yum-builddep java-1.7.0-openjdk
      yum install gcc gcc-c++

In addition, it's necessary to set a few environment variables for the build:

      export LANG=C
      export PATH="/usr/lib/jvm/java-openjdk/bin:${PATH}"

#### CentOS 5.5

After installing [CentOS 5.5](http://www.centos.org/) you need to make sure you
have the following Development bundles installed:

 * Development Libraries
 * Development Tools
 * Java Development
 * X Software Development (Including XFree86-devel)

Plus the following packages:

 * cups devel: Cups Development Package
 * alsa devel: Alsa Development Package
 * Xi devel: libXi.so Development Package

The freetype 2.3 packages don't seem to be available, but the freetype 2.3
sources can be downloaded, built, and installed easily enough from [the
freetype site](http://downloads.sourceforge.net/freetype). Build and install
with something like:

      bash ./configure
      make
      sudo -u root make install

Mercurial packages could not be found easily, but a Google search should find
ones, and they usually include Python if it's needed.

#### Debian 5.0 (Lenny)

After installing [Debian](http://debian.org) 5 you need to install several
build dependencies. The simplest way to install the build dependencies is to
execute the following commands as user `root`:

      aptitude build-dep openjdk-7
      aptitude install openjdk-7-jdk libmotif-dev

In addition, it's necessary to set a few environment variables for the build:

      export LANG=C
      export PATH="/usr/lib/jvm/java-7-openjdk/bin:${PATH}"

#### Ubuntu 12.04

After installing [Ubuntu](http://ubuntu.org) 12.04 you need to install several
build dependencies. The simplest way to do it is to execute the following
commands:

      sudo aptitude build-dep openjdk-7
      sudo aptitude install openjdk-7-jdk

In addition, it's necessary to set a few environment variables for the build:

      export LANG=C
      export PATH="/usr/lib/jvm/java-7-openjdk/bin:${PATH}"

#### OpenSUSE 11.1

After installing [OpenSUSE](http://opensuse.org) 11.1 you need to install
several build dependencies. The simplest way to install the build dependencies
is to execute the following commands:

      sudo zypper source-install -d java-1_7_0-openjdk
      sudo zypper install make

In addition, it is necessary to set a few environment variables for the build:

      export LANG=C
      export PATH="/usr/lib/jvm/java-1.7.0-openjdk/bin:$[PATH}"

Finally, you need to unset the `JAVA_HOME` environment variable:

      export -n JAVA_HOME`

#### Mandriva Linux One 2009 Spring

After installing [Mandriva](http://mandriva.org) Linux One 2009 Spring you need
to install several build dependencies. The simplest way to install the build
dependencies is to execute the following commands as user `root`:

      urpmi java-1.7.0-openjdk-devel make gcc gcc-c++ freetype-devel zip unzip
        libcups2-devel libxrender1-devel libalsa2-devel libstc++-static-devel
        libxtst6-devel libxi-devel

In addition, it is necessary to set a few environment variables for the build:

      export LANG=C
      export PATH="/usr/lib/jvm/java-1.7.0-openjdk/bin:${PATH}"

#### OpenSolaris 2009.06

After installing [OpenSolaris](http://opensolaris.org) 2009.06 you need to
install several build dependencies. The simplest way to install the build
dependencies is to execute the following commands:

      pfexec pkg install SUNWgmake SUNWj7dev sunstudioexpress SUNWcups SUNWzip
        SUNWunzip SUNWxwhl SUNWxorg-headers SUNWaudh SUNWfreetype2

In addition, it is necessary to set a few environment variables for the build:

      export LANG=C
      export PATH="/opt/SunStudioExpress/bin:${PATH}"

*****

End of the OpenJDK build README document.

Please come again!
