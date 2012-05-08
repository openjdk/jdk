#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

AC_PREREQ([2.61])
AC_INIT(openjdk, version-0.1, build-infra-dev@openjdk.java.net)

AC_CONFIG_AUX_DIR([build-aux])

m4_include([build-aux/pkg.m4])
m4_include([builddeps.m4])
m4_include([platform.m4])
m4_include([cores.m4])
m4_include([help.m4])

# Save the command line. Do this very early, before it is lost by calling autoconf macros.
CONFIGURE_COMMAND_LINE="$@"
AC_SUBST(CONFIGURE_COMMAND_LINE)

# Locate the directory of this script.
SCRIPT="[$]0"
REMOVE_SYMBOLIC_LINKS(SCRIPT)        
AUTOCONF_DIR=`dirname [$]0`

# Start with tools that do not need have cross compilation support
# and can be expected to be found in the default PATH. These tools are
# used by configure. Nor are these tools expected to be found in the
# devkit from the builddeps server either, since they are
# needed to download the devkit. 
AC_PROG_AWK
CHECK_NONEMPTY(AWK)
AC_PATH_PROG(CAT, cat)
CHECK_NONEMPTY(CAT)
AC_PATH_PROG(CHMOD, chmod)
CHECK_NONEMPTY(CHMOD)
AC_PATH_PROG(CP, cp)
CHECK_NONEMPTY(CP)
AC_PATH_PROG(CPIO, cpio)
CHECK_NONEMPTY(CPIO)
AC_PATH_PROG(CUT, cut)
CHECK_NONEMPTY(CUT)
AC_PATH_PROG(DATE, date)
CHECK_NONEMPTY(DATE)
AC_PATH_PROG(DF, df)
CHECK_NONEMPTY(DF)
AC_PATH_PROG(DIFF, diff)
CHECK_NONEMPTY(DIFF)
# Warning echo is really, really unportable!!!!! Different
# behaviour in bash and dash and in a lot of other shells!
# Use printf for serious work! 
AC_PATH_PROG(ECHO, echo)
CHECK_NONEMPTY(ECHO)
AC_PROG_EGREP
CHECK_NONEMPTY(EGREP)
AC_PROG_FGREP
CHECK_NONEMPTY(FGREP)

AC_PATH_PROG(FIND, find)
CHECK_NONEMPTY(FIND)
CHECK_FIND_DELETE
AC_SUBST(FIND_DELETE)

AC_PROG_GREP
CHECK_NONEMPTY(GREP)
AC_PATH_PROG(HEAD, head)
CHECK_NONEMPTY(HEAD)
AC_PATH_PROG(LN, ln)
CHECK_NONEMPTY(LN)
AC_PATH_PROG(LS, ls)
CHECK_NONEMPTY(LS)
AC_PATH_PROGS(MAKE, [gmake make])
CHECK_NONEMPTY(MAKE)
MAKE_VERSION=`$MAKE --version | head -n 1 | grep '3.8[[12346789]]'`
if test "x$MAKE_VERSION" = x; then
    AC_ERROR([You must use GNU make 3.81 or newer! Please put it in the path.])
fi
AC_PATH_PROG(MKDIR, mkdir)
CHECK_NONEMPTY(MKDIR)
AC_PATH_PROG(MV, mv)
CHECK_NONEMPTY(MV)
AC_PATH_PROGS(NAWK, [nawk gawk awk])
CHECK_NONEMPTY(NAWK)
AC_PATH_PROG(PRINTF, printf)
CHECK_NONEMPTY(PRINTF)
AC_PATH_PROG(THEPWDCMD, pwd)
AC_PATH_PROG(RM, rm)
CHECK_NONEMPTY(RM)
RM="$RM -f"
AC_PROG_SED
CHECK_NONEMPTY(SED)
AC_PATH_PROG(SH, sh)
CHECK_NONEMPTY(SH)
AC_PATH_PROG(SORT, sort)
CHECK_NONEMPTY(SORT)
AC_PATH_PROG(TAR, tar)
CHECK_NONEMPTY(TAR)
AC_PATH_PROG(TAIL, tail)
CHECK_NONEMPTY(TAIL)
AC_PATH_PROG(TEE, tee)
CHECK_NONEMPTY(TEE)
AC_PATH_PROG(TR, tr)
CHECK_NONEMPTY(TR)
AC_PATH_PROG(TOUCH, touch)
CHECK_NONEMPTY(TOUCH)
AC_PATH_PROG(WC, wc)
CHECK_NONEMPTY(WC)
AC_PATH_PROG(XARGS, xargs)
CHECK_NONEMPTY(XARGS)
AC_PATH_PROG(ZIP, zip)
CHECK_NONEMPTY(ZIP)
AC_PATH_PROG(UNZIP, unzip)
CHECK_NONEMPTY(UNZIP)
AC_PATH_PROG(LDD, ldd)
if test "x$LDD" = "x"; then
    # List shared lib dependencies is used for
    # debug output and checking for forbidden dependencies.
    # We can build without it.
    LDD="true"
fi
AC_PATH_PROG(OTOOL, otool)
if test "x$OTOOL" = "x"; then
   OTOOL="true"
fi
AC_PATH_PROG(READELF, readelf)
AC_PATH_PROG(EXPR, expr)
CHECK_NONEMPTY(EXPR)
AC_PATH_PROG(FILE, file)
CHECK_NONEMPTY(FILE)
# Figure out the build and host system.
AC_CANONICAL_BUILD
AC_CANONICAL_HOST

AC_ARG_WITH(data-model, [AS_HELP_STRING([--with-data-model],
   [build 32-bit or 64-bit binaries (for platforms that support it), e.g. --with-data-model=32 @<:@guessed@:>@])])

if test "x$with_data_model" != x && \
   test "x$with_data_model" != x32 && \
   test "x$with_data_model" != x64 ; then
    AC_ERROR([The data model can only be 32 or 64!])
fi
# Translate the standard cpu-vendor-kernel-os quadruplets into
# the new HOST_.... and BUILD_... and the legacy names used by
# the openjdk build.
# It uses $host_os $host_cpu $build_os $build_cpu and $with_data_model
EXTRACT_HOST_AND_BUILD_AND_LEGACY_VARS
# Now the following vars are defined.
# HOST_OS=aix,bsd,hpux,linux,macosx,solaris,windows
# HOST_OS_FAMILY=bsd,gnu,sysv,win32,wince
# HOST_OS_API=posix,winapi
#
# HOST_CPU=ia32,x64,sparc,sparcv9,arm,arm64,ppc,ppc64
# HOST_CPU_ARCH=x86,sparc,pcc,arm
# HOST_CPU_BITS=32,64
# HOST_CPU_ENDIAN=big,little
#
# There is also a:
# LEGACY_HOST_CPU1=i586,amd64,....  # used to set the old var ARCH
# LEGACY_HOST_CPU2=i386,amd64,.... # used to set the old var LIBARCH
# LEGACY_HOST_CPU3=only sparcv9,amd64 # used to set the ISA_DIR on Solaris
# There was also a BUILDARCH that had i486,amd64,... but we do not use that
# in the new build.
# LEGACY_HOST_OS_API=solaris,windows # used to select source roots

DATE_WHEN_CONFIGURED=`LANG=C date`
AC_SUBST(DATE_WHEN_CONFIGURED)

# How many cores do we have on this build system?
AC_ARG_WITH(num-cores, [AS_HELP_STRING([--with-num-cores],
    [number of cores in the build system, e.g. --with-num-cores=8 @<:@probed@:>@])])
if test "x$with_num_cores" = x; then
    # The number of cores were not specified, try to probe them.
    CHECK_CORES
else
    NUM_CORES=$with_num_cores
    CONCURRENT_BUILD_JOBS=`expr $NUM_CORES \* 2`
fi
AC_SUBST(NUM_CORES)
AC_SUBST(CONCURRENT_BUILD_JOBS)

# How many cores do we have on this build system?
AC_ARG_WITH(memory-size, [AS_HELP_STRING([--with-memory-size],
    [memory (in MB) available in the build system, e.g. --with-memory-size=1024 @<:@probed@:>@])])
if test "x$with_memory_size" = x; then
    # The memory size was not specified, try to probe it.
    CHECK_MEMORY_SIZE
else
    MEMORY_SIZE=$with_memory_size
fi
AC_SUBST(MEMORY_SIZE)

# Where is the source? It is located two levels above the configure script.
CURDIR="$PWD"
cd "$AUTOCONF_DIR/../.."
SRC_ROOT="`pwd`"
AC_SUBST(SRC_ROOT)
cd "$CURDIR"

# For cygwin we need cygpath first, since it is used everywhere.
AC_PATH_PROG(CYGPATH, cygpath)
PATH_SEP=":"
if test "x$BUILD_OS" = "xwindows"; then
    if test "x$CYGPATH" = x; then
        AC_ERROR([Something is wrong with your cygwin installation since I cannot find cygpath.exe in your path])
    fi
    PATH_SEP=";"
fi
AC_SUBST(PATH_SEP)

SPACESAFE(SRC_ROOT,[the path to the source root])
SPACESAFE(CURDIR,[the path to the current directory])

if test "x$BUILD_OS" = "xsolaris"; then
    # Also search in /usr/ccs/bin for utilities like ar and as.
    PATH="/usr/ccs/bin:$PATH"
fi

###############################################################################
#
# OpenJDK or closed
#
AC_ARG_ENABLE([openjdk], [AS_HELP_STRING([--enable-openjdk],
    [build OpenJDK regardless of the presence of closed repositories @<:@disabled@:>@])],,)

if test "x$enable_openjdk" = "xyes"; then
    OPENJDK=true
elif test "x$enable_openjdk" = "xno"; then
    OPENJDK=false
elif test -d "$SRC_ROOT/jdk/src/closed"; then
    OPENJDK=false
else
    OPENJDK=true
fi

if test "x$OPENJDK" = "xtrue"; then
    SET_OPENJDK=OPENJDK=true
fi

AC_SUBST(SET_OPENJDK)

###############################################################################
#
# JIGSAW or not.  The JIGSAW variable is used during the intermediate
# stage when we are building both the old style JDK and the new style modularized JDK.
# When the modularized JDK is finalized, this option will go away.
#
AC_ARG_ENABLE([jigsaw], [AS_HELP_STRING([--enable-jigsaw],
    [build Jigsaw images (not yet available) @<:@disabled@:>@])],,)

if test "x$enable_jigsaw" = "xyes"; then
    JIGSAW=true
else
    JIGSAW=false
fi
AC_SUBST(JIGSAW)

###############################################################################
#
# Check which variant of the JDK that we want to build.
# Currently we have:
#    normal:   standard edition   
#    embedded: cut down to a smaller footprint
#
# Effectively the JDK variant gives a name to a specific set of
# modules to compile into the JDK. In the future, these modules
# might even be Jigsaw modules.
#
AC_MSG_CHECKING([which variant of the JDK to build])
AC_ARG_WITH([jdk-variant], [AS_HELP_STRING([--with-jdk-variant],
	[JDK variant to build (normal, embedded) @<:@normal@:>@])])

if test "x$with_jdk_variant" = xnormal || test "x$with_jdk_variant" = x; then
    JAVASE_EMBEDDED=""
    MINIMIZE_RAM_USAGE=""
    JDK_VARIANT="normal"
elif test "x$with_jdk_variant" = xembedded; then
    JAVASE_EMBEDDED="JAVASE_EMBEDDED:=true"
    MINIMIZE_RAM_USAGE="MINIMIZE_RAM_USAGE:=true"
    JDK_VARIANT="embedded"
else
    AC_ERROR([The available JDK variants are: normal, embedded])
fi
                              
AC_SUBST(JAVASE_EMBEDDED)
AC_SUBST(MINIMIZE_RAM_USAGE)
AC_SUBST(JDK_VARIANT)

AC_MSG_RESULT([$JDK_VARIANT])

###############################################################################
#
# Should we build a JDK/JVM with head support (ie a graphical ui)?
# We always build headless support.
#
AC_MSG_CHECKING([headful support])
AC_ARG_ENABLE([headful], [AS_HELP_STRING([--disable-headful],
	[build headful support (graphical UI support) @<:@enabled@:>@])],
    [SUPPORT_HEADFUL=${enable_headful}], [SUPPORT_HEADFUL=yes])

SUPPORT_HEADLESS=yes
BUILD_HEADLESS="BUILD_HEADLESS:=true"

if test "x$SUPPORT_HEADFUL" = xyes; then
    # We are building both headful and headless.
    BUILD_HEADLESS_ONLY=""
    headful_msg="inlude support for both headful and headless"
fi

if test "x$SUPPORT_HEADFUL" = xno; then
    # Thus we are building headless only.
    BUILD_HEADLESS="BUILD_HEADLESS:=true"
    BUILD_HEADLESS_ONLY="BUILD_HEADLESS_ONLY:=true"
    headful_msg="headless only"
fi

AC_MSG_RESULT([$headful_msg])

AC_SUBST(SUPPORT_HEADLESS)
AC_SUBST(SUPPORT_HEADFUL)
AC_SUBST(BUILD_HEADLESS)
AC_SUBST(BUILD_HEADLESS_ONLY)

###############################################################################
#
# Check which variants of the JVM that we want to build.
# Currently we have:
#    server: normal interpreter and a tiered C1/C2 compiler
#    client: normal interpreter and C1 (no C2 compiler) (only 32-bit platforms)
#    kernel: kernel footprint JVM that passes the TCK without major performance problems,
#             ie normal interpreter and C1, only the serial GC, kernel jvmti etc
#    zero: no machine code interpreter, no compiler
#    zeroshark: zero interpreter and shark/llvm compiler backend
AC_MSG_CHECKING([which variants of the JVM that should be built])
AC_ARG_WITH([jvm-variants], [AS_HELP_STRING([--with-jvm-variants],
	[JVM variants (separated by commas) to build (server, client, kernel, zero, zeroshark) @<:@server@:>@])])

if test "x$with_jvm_variants" = x; then
    if test "x$JDK_VARIANT" = xembedded; then
        with_jvm_variants="kernel"
    elif test "x$HOST_CPU_BITS" = x64; then
        with_jvm_variants="server"
    else
        # Yes, on 32-bit machines, the same source (almost) is compiled twice
        # to generate two libjvm.so files. Fun, fun.
        with_jvm_variants="client,server"
    fi
fi

JVM_VARIANTS=",$with_jvm_variants,"
TEST_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/server,//' -e 's/client,//' -e 's/kernel,//' -e 's/zero,//' -e 's/zeroshark,//'`

if test "x$TEST_VARIANTS" != "x,"; then
   AC_ERROR([The available JVM variants are: server, client, kernel, zero, zeroshark])
fi   
AC_MSG_RESULT([$with_jvm_variants])

JVM_VARIANT_SERVER=`$ECHO "$JVM_VARIANTS" | $SED -e '/,server,/!s/.*/false/g' -e '/,server,/s/.*/true/g'`
JVM_VARIANT_CLIENT=`$ECHO "$JVM_VARIANTS" | $SED -e '/,client,/!s/.*/false/g' -e '/,client,/s/.*/true/g'` 
JVM_VARIANT_KERNEL=`$ECHO "$JVM_VARIANTS" | $SED -e '/,kernel,/!s/.*/false/g' -e '/,kernel,/s/.*/true/g'`
JVM_VARIANT_ZERO=`$ECHO "$JVM_VARIANTS" | $SED -e '/,zero,/!s/.*/false/g' -e '/,zero,/s/.*/true/g'`
JVM_VARIANT_ZEROSHARK=`$ECHO "$JVM_VARIANTS" | $SED -e '/,zeroshark,/!s/.*/false/g' -e '/,zeroshark,/s/.*/true/g'`

if test "x$JVM_VARIANT_CLIENT" = xtrue; then
    if test "x$HOST_CPU_BITS" = x64; then
        AC_ERROR([You cannot build a client JVM for a 64-bit machine.])
    fi
fi
if test "x$JVM_VARIANT_KERNEL" = xtrue; then
    if test "x$HOST_CPU_BITS" = x64; then
        AC_ERROR([You cannot build a kernel JVM for a 64-bit machine.])
    fi
fi

COUNT_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/server,/1/' -e 's/client,/1/' -e 's/kernel,/1/' -e 's/zero,/1/' -e 's/zeroshark,/1/'`
# Replace the commas with AND for use in the build directory name.
ANDED_JVM_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/^,//' -e 's/,$//' -e 's/,/AND/'`
if test "x$COUNT_VARIANTS" != "x,1"; then
    AC_MSG_NOTICE([NOTE! Longer build time since we are building more than one version of the libjvm! $with_jvm_variants])
fi

AC_SUBST(JVM_VARIANTS)
AC_SUBST(JVM_VARIANT_SERVER)
AC_SUBST(JVM_VARIANT_CLIENT)
AC_SUBST(JVM_VARIANT_KERNEL)
AC_SUBST(JVM_VARIANT_ZERO)
AC_SUBST(JVM_VARIANT_ZEROSHARK)

###############################################################################
#
# Set the debug level
#    release: no debug information, all optimizations, no asserts.
#    fastdebug: debug information (-g), all optimizations, all asserts
#    slowdebug: debug information (-g), no optimizations, all asserts
#
DEBUG_LEVEL="release"              
AC_MSG_CHECKING([which debug level to use])
AC_ARG_ENABLE([debug], [AS_HELP_STRING([--enable-debug],
	[set the debug level to fastdebug (shorthand for --with-debug-level=fastdebug) @<:@disabled@:>@])],
	[
        ENABLE_DEBUG="${enableval}"
        DEBUG_LEVEL="fastdebug"
    ], [ENABLE_DEBUG="no"])

AC_ARG_WITH([debug-level], [AS_HELP_STRING([--with-debug-level],
	[set the debug level (release, fastdebug, slowdebug) @<:@release@:>@])],
	[
        DEBUG_LEVEL="${withval}"
        if test "x$ENABLE_DEBUG" = xyes; then
			AC_ERROR([You cannot use both --enable-debug and --with-debug-level at the same time.])
        fi
    ])
AC_MSG_RESULT([$DEBUG_LEVEL])

if test "x$DEBUG_LEVEL" != xrelease && \
   test "x$DEBUG_LEVEL" != xfastdebug && \
   test "x$DEBUG_LEVEL" != xslowdebug; then
   AC_ERROR([Allowed debug levels are: release, fastdebug and slowdebug])
fi

case $DEBUG_LEVEL in
      release )
          VARIANT="OPT"
          FASTDEBUG="false"
          DEBUG_CLASSFILES="false"            
          BUILD_VARIANT_RELEASE=""             
           ;;
      fastdebug )
          VARIANT="DBG"
          FASTDEBUG="true"
          DEBUG_CLASSFILES="true"            
          BUILD_VARIANT_RELEASE="-fastdebug"
           ;;
      slowdebug )
          VARIANT="DBG"
          FASTDEBUG="false"
          DEBUG_CLASSFILES="true"            
          BUILD_VARIANT_RELEASE="-debug"             
           ;;
esac

AC_SUBST(DEBUG_LEVEL)
AC_SUBST(VARIANT)
AC_SUBST(FASTDEBUG)
AC_SUBST(DEBUG_CLASSFILES)
AC_SUBST(BUILD_VARIANT_RELEASE)

# Source the version numbers
. $AUTOCONF_DIR/version.numbers
if test "x$OPENJDK" = "xfalse"; then
    . $AUTOCONF_DIR/closed.version.numbers
fi
# Now set the JDK version, milestone, build number etc.
AC_SUBST(JDK_MAJOR_VERSION)
AC_SUBST(JDK_MINOR_VERSION)
AC_SUBST(JDK_MICRO_VERSION)
AC_SUBST(JDK_UPDATE_VERSION)
AC_SUBST(JDK_BUILD_NUMBER)
AC_SUBST(MILESTONE)
AC_SUBST(LAUNCHER_NAME)
AC_SUBST(PRODUCT_NAME)
AC_SUBST(PRODUCT_SUFFIX)
AC_SUBST(JDK_RC_PLATFORM_NAME)
AC_SUBST(COMPANY_NAME)

COPYRIGHT_YEAR=`date +'%Y'`
AC_SUBST(COPYRIGHT_YEAR)

RUNTIME_NAME="$PRODUCT_NAME $PRODUCT_SUFFIX"
AC_SUBST(RUNTIME_NAME)

if test "x$JDK_UPDATE_VERSION" != x; then
    JDK_VERSION="${JDK_MAJOR_VERSION}.${JDK_MINOR_VERSION}.${JDK_MICRO_VERSION}_${JDK_UPDATE_VERSION}"
else
    JDK_VERSION="${JDK_MAJOR_VERSION}.${JDK_MINOR_VERSION}.${JDK_MICRO_VERSION}"
fi
AC_SUBST(JDK_VERSION)

if test "x$MILESTONE" != x; then
    RELEASE="${JDK_VERSION}-${MILESTONE}${BUILD_VARIANT_RELEASE}"
else
    RELEASE="${JDK_VERSION}${BUILD_VARIANT_RELEASE}"
fi
AC_SUBST(RELEASE)

if test "x$JDK_BUILD_NUMBER" != x; then
    FULL_VERSION="${RELEASE}-${JDK_BUILD_NUMBER}"
else
    JDK_BUILD_NUMBER=b00
    BUILD_DATE=`date '+%Y_%m_%d_%H_%M'`
    # Avoid [:alnum:] since it depends on the locale.
    CLEAN_USERNAME=`echo "$USER" | $TR -d -c 'abcdefghijklmnopqrstuvqxyz0123456789'`
    USER_RELEASE_SUFFIX=`echo "${CLEAN_USERNAME}_${BUILD_DATE}" | $TR 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' 'abcdefghijklmnopqrstuvqxyz'`
    FULL_VERSION="${RELEASE}-${USER_RELEASE_SUFFIX}-${JDK_BUILD_NUMBER}"
fi
AC_SUBST(FULL_VERSION)
COOKED_BUILD_NUMBER=`$ECHO $JDK_BUILD_NUMBER | $SED -e 's/^b//' -e 's/^0//'`
AC_SUBST(COOKED_BUILD_NUMBER)

# Test from where we are running configure, in or outside of src root.
if test "x$CURDIR" = "x$SRC_ROOT" || test "x$CURDIR" = "x$SRC_ROOT/common" || test "x$CURDIR" = "x$SRC_ROOT/common/autoconf" || test "x$CURDIR" = "x$SRC_ROOT/common/makefiles" ; then
    # We are running configure from the src root.
    # Create a default ./build/host-variant-debuglevel output root.
    OUTPUT_ROOT="$SRC_ROOT/build/${HOST_OS}-${HOST_CPU}-${JDK_VARIANT}-${ANDED_JVM_VARIANTS}-${DEBUG_LEVEL}"
    mkdir -p "$OUTPUT_ROOT"
    if test ! -d "$OUTPUT_ROOT"; then
        AC_ERROR([Could not create build directory $OUTPUT_ROOT])
    fi
else
    # We are running configure from outside of the src dir.
    # Then use the current directory as output dir!
    OUTPUT_ROOT="$CURDIR"
fi

SPACESAFE(OUTPUT_ROOT,[the path to the output root])

# Save the arguments given to us
echo "$CONFIGURE_COMMAND_LINE" > $OUTPUT_ROOT/configure-arguments

# Check if the VS env variables were setup prior to running configure.
# If not, then find vcvarsall.bat and run it automatically, and integrate
# the set env variables into the spec file.
SETUPDEVENV="# No special vars"
if test "x$BUILD_OS" = "xwindows"; then
    # If vcvarsall.bat has been run, then VCINSTALLDIR is set.
    if test "x$VCINSTALLDIR" != x; then
        # No further setup is needed. The build will happen from this kind
        # of shell.
        SETUPDEVENV="# This spec file expects that you are running bash from within a VS command prompt."
        # Make sure to remind you, if you forget to run make from a cygwin bash shell
        # that is spawned "bash -l" from a VS command prompt.
        CHECK_FOR_VCINSTALLDIR=yes
        AC_MSG_CHECKING([if you are running from within a VS command prompt])
        AC_MSG_RESULT([yes])
    else
        # Ah, we have not yet run vcvarsall.bat/vsvars32.bat/vsvars64.bat. Lets do that. First find it.
        if test "x$VS100COMNTOOLS" != x; then
            VARSBAT=`find "$VS100COMNTOOLS/../.." -name vcvarsall.bat`
	    SEARCH_ROOT="$VS100COMNTOOLS"
        else
            VARSBAT=`find "$PROGRAMFILES" -name vcvarsall.bat`
	    SEARCH_ROOT="$PROGRAMFILES"
        fi
        VCPATH=`dirname "$VARSBAT"`
        VCPATH=`cygpath -w "$VCPATH"`
	if test "x$VARSBAT" = x || test ! -d "$VCPATH"; then
            AC_MSG_CHECKING([if we can find the VS installation])
            AC_MSG_RESULT([no])
            AC_ERROR([Tried to find a VS installation using both $SEARCH_ROOT but failed. Please run "c:\\cygwin\\bin\\bash.exe -l" from a VS command prompt and then run configure/make from there.])
        fi
        case "$LEGACY_HOST_CPU1" in
          i?86)
            VARSBAT_ARCH=x86
            ;;
          *)
            VARSBAT_ARCH=$LEGACY_HOST_CPU1
            ;;
        esac
        # Lets extract the variables that are set by vcvarsall.bat/vsvars32.bat/vsvars64.bat
        cd $OUTPUT_ROOT
        $SRC_ROOT/common/bin/extractvcvars.sh "$VARSBAT" "$VARSBAT_ARCH"
	cd $CURDIR
	if test ! -s $OUTPUT_ROOT/localdevenv.sh || test ! -s $OUTPUT_ROOT/localdevenv.gmk; then
            AC_MSG_CHECKING([if we can extract the needed env variables])
            AC_MSG_RESULT([no])
            AC_ERROR([Could not succesfully extract the env variables needed for the VS setup. Please run "c:\\cygwin\\bin\\bash.exe -l" from a VS command prompt and then run configure/make from there.])
        fi 
        # Now set all paths and other env variables. This will allow the rest of 
        # the configure script to find and run the compiler in the proper way.
        . $OUTPUT_ROOT/localdevenv.sh
        AC_MSG_CHECKING([if we can find the VS installation])
	if test "x$VCINSTALLDIR" != x; then 
            AC_MSG_RESULT([$VCINSTALLDIR])
        else 
            AC_MSG_RESULT([no])
            AC_ERROR([Could not find VS installation. Please install. If you are sure you have installed VS, then please run "c:\\cygwin\\bin\\bash.exe -l" from a VS command prompt and then run configure/make from there.])
        fi
        CHECK_FOR_VCINSTALLDIR=no
	SETUPDEVENV="include $OUTPUT_ROOT/localdevenv.gmk"

	AC_MSG_CHECKING([for msvcr100.dll])
        AC_ARG_WITH(msvcr100dll, [AS_HELP_STRING([--with-msvcr100dll],
            [copy this msvcr100.dll into the built JDK])])
        if test "x$with_msvcr100dll" != x; then
            MSVCR100DLL="$with_msvcr100dll"
        else
            if test "x$HOST_CPU_BITS" = x64; then
                MSVCR100DLL=`find "$VCINSTALLDIR/.." -name msvcr100.dll | grep x64 | head --lines 1`
            else
                MSVCR100DLL=`find "$VCINSTALLDIR/.." -name msvcr100.dll | grep x86 | grep -v ia64 | grep -v x64 | head --lines 1`
                if test "x$MSVCR100DLL" = x; then
                    MSVCR100DLL=`find "$VCINSTALLDIR/.." -name msvcr100.dll | head --lines 1`
                fi
            fi
        fi
	if test "x$MSVCR100DLL" = x; then
           AC_MSG_RESULT([no])
	   AC_ERROR([Could not find msvcr100.dll !])
        fi
        AC_MSG_RESULT([$MSVCR100DLL])
	SPACESAFE(MSVCR100DLL,[the path to msvcr100.dll])
    fi
fi
AC_SUBST(SETUPDEVENV)
AC_SUBST(CHECK_FOR_VCINSTALLDIR)
AC_SUBST(MSVCR100DLL)

# Most of the probed defines are put into config.h
AC_CONFIG_HEADERS([$OUTPUT_ROOT/config.h:$AUTOCONF_DIR/config.h.in])
# The spec.gmk file contains all variables for the make system.
AC_CONFIG_FILES([$OUTPUT_ROOT/spec.gmk:$AUTOCONF_DIR/spec.gmk.in])
# The generated Makefile knows where the spec.gmk is and where the source is.
# You can run make from the OUTPUT_ROOT. If you have only
# one configured host, then you can also run make from the SRC_ROOT,
# since it will go look for a single spec.gmk file. Or perhaps it
# should instead make all configured hosts it can find?
AC_CONFIG_FILES([$OUTPUT_ROOT/Makefile:$AUTOCONF_DIR/Makefile.in])

AC_SUBST(SPEC, $OUTPUT_ROOT/spec.gmk)
AC_SUBST(OUTPUT_ROOT, $OUTPUT_ROOT)

# Where are the sources. Any of these can be overridden
# using --with-override-corba and the likes.
LANGTOOLS_TOPDIR="$SRC_ROOT/langtools"
CORBA_TOPDIR="$SRC_ROOT/corba"
JAXP_TOPDIR="$SRC_ROOT/jaxp"
JAXWS_TOPDIR="$SRC_ROOT/jaxws"
HOTSPOT_TOPDIR="$SRC_ROOT/hotspot"
JDK_TOPDIR="$SRC_ROOT/jdk"
AC_SUBST(LANGTOOLS_TOPDIR)
AC_SUBST(CORBA_TOPDIR)
AC_SUBST(JAXP_TOPDIR)
AC_SUBST(JAXWS_TOPDIR)
AC_SUBST(HOTSPOT_TOPDIR)
AC_SUBST(JDK_TOPDIR)

# Check if pkg-config is available.
PKG_PROG_PKG_CONFIG

AC_ARG_WITH(builddeps-conf, [AS_HELP_STRING([--with-builddeps-conf],
    [use this configuration file for the builddeps])])

AC_ARG_WITH(builddeps-server, [AS_HELP_STRING([--with-builddeps-server],
    [download and use build dependencies from this server url, e.g. --with-builddeps-server=ftp://example.com/dir])])

AC_ARG_WITH(builddeps-dir, [AS_HELP_STRING([--with-builddeps-dir],
    [store downloaded build dependencies here @<:@d/localhome/builddeps@:>@])],
    [],
    [with_builddeps_dir=/localhome/builddeps])

AC_ARG_WITH(builddeps-group, [AS_HELP_STRING([--with-builddeps-group],
    [chgrp the downloaded build dependencies to this group])])

AC_ARG_ENABLE([list-builddeps], [AS_HELP_STRING([--enable-list-builddeps],
	[list all build dependencies known to the configure script])],
	[LIST_BUILDDEPS="${enableval}"], [LIST_BUILDDEPS='no'])

if test "x$LIST_BUILDDEPS" = xyes; then
    echo
    echo List of build dependencies known to the configure script,
    echo that can be used in builddeps.conf files:
    cat $SRC_ROOT/configure.ac | grep BDEPS_CHECK_MODUL | grep -v configure.ac | cut -f 2 -d ',' | tr -d ' ' | sort
    echo
    exit 1
fi

# If builddeps server or conf file is given. Setup buildeps usage.
BDEPS_SCAN_FOR_BUILDDEPS

###############################################################################
#
# Configure the development tool paths and potential sysroot.
#
AC_LANG(C++)
DEVKIT=
SYS_ROOT=/
AC_SUBST(SYS_ROOT)

# The option used to specify the target .o,.a or .so file.
# When compiling, how to specify the to be created object file.
CC_OUT_OPTION='-o$(SPACE)'
# When linking, how to specify the to be created executable.
EXE_OUT_OPTION='-o$(SPACE)'
# When linking, how to specify the to be created dynamically linkable library.
LD_OUT_OPTION='-o$(SPACE)'
# When archiving, how to specify the to be create static archive for object files.
AR_OUT_OPTION='rcs$(SPACE)'
AC_SUBST(CC_OUT_OPTION)
AC_SUBST(EXE_OUT_OPTION)
AC_SUBST(LD_OUT_OPTION)
AC_SUBST(AR_OUT_OPTION)

# If --build AND --host is set, then the configure script will find any
# cross compilation tools in the PATH. Cross compilation tools
# follows the cross compilation standard where they are prefixed with ${host}.
# For example the binary i686-sun-solaris2.10-gcc
# will cross compile for i686-sun-solaris2.10
# If neither of build and host is not set, then build=host and the
# default compiler found in the path will be used.
# Setting only --host, does not seem to be really supported.
# Please set both --build and --host if you want to cross compile.

DEFINE_CROSS_COMPILE_ARCH=""
HOSTCC=""
HOSTCXX=""
AC_SUBST(DEFINE_CROSS_COMPILE_ARCH)
AC_SUBST(HOST_CC)
AC_SUBST(HOST_CXX)
AC_MSG_CHECKING([if this is a cross compile])
if test "x$build_var" != "x$host_var"; then
    AC_MSG_RESULT([yes, from $build_var to $host_var])   
    # We have detected a cross compile!
    DEFINE_CROSS_COMPILE_ARCH="CROSS_COMPILE_ARCH:=$LEGACY_HOST_CPU1"
    # Now we to find a C/C++ compiler that can build executables for the build
    # platform. We can't use the AC_PROG_CC macro, since it can only be used
    # once.
    AC_PATH_PROGS(HOSTCC, [cl cc gcc])
    WHICHCMD(HOSTCC)
    AC_PATH_PROGS(HOSTCXX, [cl CC g++])
    WHICHCMD(HOSTCXX)
    # Building for the build platform should be easy. Therefore
    # we do not need any linkers or assemblers etc.    
else
    AC_MSG_RESULT([no])
fi

# You can force the sys-root if the sys-root encoded into the cross compiler tools
# is not correct.
AC_ARG_WITH(sys-root, [AS_HELP_STRING([--with-sys-root],
    [pass this sys-root to the compilers and linker (useful if the sys-root encoded in
     the cross compiler tools is incorrect)])])

if test "x$with_sys_root" != x; then
    SYS_ROOT=$with_sys_root
fi
                     
# If a devkit is found on the builddeps server, then prepend its path to the
# PATH variable. If there are cross compilers available in the devkit, these
# will be found by AC_PROG_CC et al.
BDEPS_CHECK_MODULE(DEVKIT, devkit, xxx,
                    [# Found devkit
                     PATH="$DEVKIT/bin:$PATH"
                     SYS_ROOT="$DEVKIT/${rewritten_host}/sys-root"
                     if test "x$x_includes" = "xNONE"; then
                         x_includes="$SYS_ROOT/usr/include/X11"
                     fi
                     if test "x$x_libraries" = "xNONE"; then
                         x_libraries="$SYS_ROOT/usr/lib"
                     fi
                    ],
                    [])

if test "x$SYS_ROOT" != "x/" ; then                    
    CFLAGS="--sysroot=$SYS_ROOT $CFLAGS"
    CXXFLAGS="--sysroot=$SYS_ROOT $CXXFLAGS"
    OBJCFLAGS="--sysroot=$SYS_ROOT $OBJCFLAGS" 
    OBJCXXFLAGS="--sysroot=$SYS_ROOT $OBJCFLAGS" 
    CPPFLAGS="--sysroot=$SYS_ROOT $CPPFLAGS"
    LDFLAGS="--sysroot=$SYS_ROOT $LDFLAGS"
fi

# Store the CFLAGS etal passed to the configure script.
ORG_CFLAGS="$CFLAGS"
ORG_CXXFLAGS="$CXXFLAGS"
ORG_OBJCFLAGS="$OBJCFLAGS"

# gcc is almost always present, but on Windows we
# prefer cl.exe and on Solaris we prefer CC.
# Thus test for them in this order.
AC_PROG_CC([cl cc gcc])
if test "x$CC" = x; then
    help_on_build_dependency devkit
    AC_ERROR([Could not find a compiler. $HELP_MSG])
fi
if test "x$CC" = xcc && test "x$BUILD_OS" = xmacosx; then
    # Do not use cc on MacOSX use gcc instead.
    CC="gcc"
fi
WHICHCMD(CC)

AC_PROG_CXX([cl CC g++])
if test "x$CXX" = xCC && test "x$BUILD_OS" = xmacosx; then
    # The found CC, even though it seems to be a g++ derivate, cannot compile
    # c++ code. Override.
    CXX="g++"
fi
WHICHCMD(CXX)

if test "x$CXX" = x || test "x$CC" = x; then
    help_on_build_dependency devkit
    AC_ERROR([Could not find the needed compilers! $HELP_MSG ])
fi

if test "x$BUILD_OS" != xwindows; then
    AC_PROG_OBJC
    WHICHCMD(OBJC)
else
    OBJC=
fi

# Restore the flags to the user specified values.
# This is necessary since AC_PROG_CC defaults CFLAGS to "-g -O2"
CFLAGS="$ORG_CFLAGS"
CXXFLAGS="$ORG_CXXFLAGS"
OBJCFLAGS="$ORG_OBJCFLAGS"

# If we are not cross compiling, use the same compilers for
# building the build platform executables.
if test "x$DEFINE_CROSS_COMPILE_ARCH" = x; then
    HOSTCC="$CC"
    HOSTCXX="$CXX"
fi

AC_CHECK_TOOL(LD, ld)
WHICHCMD(LD)
LDEXE="$LD"
LDCXX="$LD"
LDEXECXX="$LD"
# LDEXE is the linker to use, when creating executables.
AC_SUBST(LDEXE)
# Linking C++ libraries.
AC_SUBST(LDCXX)
# Linking C++ executables.
AC_SUBST(LDEXECXX)

AC_CHECK_TOOL(AR, ar)
WHICHCMD(AR)
if test "x$BUILD_OS" = xmacosx; then
    ARFLAGS="-r"
else
    ARFLAGS=""
fi
AC_SUBST(ARFLAGS)

COMPILER_NAME=gcc
COMPILER_TYPE=CC
AS_IF([test "x$BUILD_OS" = xwindows], [
    # For now, assume that we are always compiling using cl.exe. 
    CC_OUT_OPTION=-Fo
    EXE_OUT_OPTION=-Fe
    LD_OUT_OPTION=-out:
    AR_OUT_OPTION=-out:
    # On Windows, reject /usr/bin/link, which is a cygwin
    # program for something completely different.
    AC_CHECK_PROG([WINLD], [link],[link],,, [/usr/bin/link])
    # Since we must ignore the first found link, WINLD will contain
    # the full path to the link.exe program.
    WHICHCMD_SPACESAFE([WINLD])
    LD="$WINLD"
    # However creating executables can only be done with cl.exe. 
    LDEXE="$CC"
    LDCXX="$WINLD"
    LDEXECXX="$CC"

    AC_CHECK_PROG([MT], [mt], [mt],,, [/usr/bin/mt])
    WHICHCMD_SPACESAFE([MT])
    # The resource compiler
    AC_CHECK_PROG([RC], [rc], [rc],,, [/usr/bin/rc])
    WHICHCMD_SPACESAFE([RC])

    RC_FLAGS="/l 0x409 /r"
    AS_IF([test "x$VARIANT" = xOPT], [
        RC_FLAGS="$RC_FLAGS -d NDEBUG"
    ])
    JDK_UPDATE_VERSION_NOTNULL=$JDK_UPDATE_VERSION
    AS_IF([test "x$JDK_UPDATE_VERSION" = x], [
        JDK_UPDATE_VERSION_NOTNULL=0
    ])
    RC_FLAGS="$RC_FLAGS -d \"JDK_BUILD_ID=$FULL_VERSION\""
    RC_FLAGS="$RC_FLAGS -d \"JDK_COMPANY=$COMPANY_NAME\""
    RC_FLAGS="$RC_FLAGS -d \"JDK_COMPONENT=$PRODUCT_NAME $JDK_RC_PLATFORM_NAME binary\""
    RC_FLAGS="$RC_FLAGS -d \"JDK_VER=$JDK_MINOR_VERSION.$JDK_MICRO_VERSION.$JDK_UPDATE_VERSION_NOTNULL.$COOKED_BUILD_NUMBER\""
    RC_FLAGS="$RC_FLAGS -d \"JDK_COPYRIGHT=Copyright \xA9 $COPYRIGHT_YEAR\""
    RC_FLAGS="$RC_FLAGS -d \"JDK_NAME=$PRODUCT_NAME $JDK_RC_PLATFORM_NAME $JDK_MINOR_VERSION $JDK_UPDATE_META_TAG\""
    RC_FLAGS="$RC_FLAGS -d \"JDK_FVER=$JDK_MINOR_VERSION,$JDK_MICRO_VERSION,$JDK_UPDATE_VERSION_NOTNULL,$COOKED_BUILD_NUMBER\""

    # lib.exe is used to create static libraries.
    AC_CHECK_PROG([WINAR], [lib],[lib],,,)
    WHICHCMD_SPACESAFE([WINAR])
    AR="$WINAR"
    ARFLAGS="-nologo -NODEFAULTLIB:MSVCRT"

    AC_CHECK_PROG([DUMPBIN], [dumpbin], [dumpbin],,,)
    WHICHCMD_SPACESAFE([DUMPBIN])

    COMPILER_TYPE=CL
    CFLAGS="$CFLAGS -nologo"
    LDFLAGS="$LDFLAGS -nologo -dll -opt:ref -incremental:no "
    if test "x$LEGACY_HOST_CPU1" = xi586; then 
        LDFLAGS="$LDFLAGS -safeseh"
    fi
    if test "x$DEBUG_LEVEL" != xrelease; then
        LDFLAGS="$LDFLAGS -debug"
    fi
])
AC_SUBST(RC_FLAGS)
AC_SUBST(COMPILER_TYPE)

AC_PROG_CPP
WHICHCMD(CPP)

AC_PROG_CXXCPP
WHICHCMD(CXXCPP)

# Find the right assembler.
if test "x$BUILD_OS" = xsolaris; then
    AC_PATH_PROG(AS, as)
    WHICHCMD(AS)
    ASFLAGS=" "
else
    AS="$CC -c"
    ASFLAGS=" "
fi
AC_SUBST(AS)
AC_SUBST(ASFLAGS)

if test "x$HOST_CPU_BITS" = x32 && test "x$HOST_OS" = macosx; then
    # On 32-bit MacOSX the OS requires C-entry points to be 16 byte aligned.
    # While waiting for a better solution, the current workaround is to use -mstackrealign.
    CFLAGS="$CFLAGS -mstackrealign"
    AC_MSG_CHECKING([if 32-bit compiler supports -mstackrealign])
    AC_LINK_IFELSE([AC_LANG_SOURCE([[int main() { return 0; }]])],
                   [
		        AC_MSG_RESULT([yes])
                   ],
	           [
		        AC_MSG_RESULT([no])
	                AC_ERROR([The selected compiler $CXX does not support -mstackrealign! Try to put another compiler in the path.])
	           ])
fi

if test "x$BUILD_OS" = xsolaris; then
    AC_PATH_PROG(NM, nm)
    WHICHCMD(NM)
    AC_PATH_PROG(STRIP, strip)
    WHICHCMD(STRIP)
    AC_PATH_PROG(MCS, mcs)
    WHICHCMD(MCS)
else
    AC_CHECK_TOOL(NM, nm)
    WHICHCMD(NM)
    AC_CHECK_TOOL(STRIP, strip)
    WHICHCMD(STRIP)
fi

# When using cygwin, we need a wrapper binary that renames
# /cygdrive/c/ arguments into c:/ arguments and peeks into
# @files and rewrites these too! This wrapper binary is
# called uncygdrive.exe.
UNCYGDRIVE=
if test "x$BUILD_OS" = xwindows; then
    AC_MSG_CHECKING([if uncygdrive can be created])
    UNCYGDRIVE_SRC=`$CYGPATH -m $SRC_ROOT/common/src/uncygdrive.c`
    rm -f $OUTPUT_ROOT/uncygdrive*
    UNCYGDRIVE=`$CYGPATH -m $OUTPUT_ROOT/uncygdrive.exe`
    cd $OUTPUT_ROOT
    $CC $UNCYGDRIVE_SRC /Fe$UNCYGDRIVE > $OUTPUT_ROOT/uncygdrive1.log 2>&1
    cd $CURDIR

    if test ! -x $OUTPUT_ROOT/uncygdrive.exe; then 
        AC_MSG_RESULT([no])
        cat $OUTPUT_ROOT/uncygdrive1.log
        AC_ERROR([Could not create $OUTPUT_ROOT/uncygdrive.exe])
    fi
    AC_MSG_RESULT([$UNCYGDRIVE])
    AC_MSG_CHECKING([if uncygdrive.exe works])
    cd $OUTPUT_ROOT
    $UNCYGDRIVE $CC $SRC_ROOT/common/src/uncygdrive.c /Fe$OUTPUT_ROOT/uncygdrive2.exe > $OUTPUT_ROOT/uncygdrive2.log 2>&1 
    cd $CURDIR
    if test ! -x $OUTPUT_ROOT/uncygdrive2.exe; then 
        AC_MSG_RESULT([no])
        cat $OUTPUT_ROOT/uncygdrive2.log
        AC_ERROR([Uncygdrive did not work!])
    fi
    AC_MSG_RESULT([yes])
    rm -f $OUTPUT_ROOT/uncygdrive?.??? $OUTPUT_ROOT/uncygdrive.obj
fi

AC_SUBST(UNCYGDRIVE)

TESTFOR_PROG_CCACHE

# Used on GNU/Linux systems, can be empty...
#AC_PATH_PROG(ELFDUMP, elfdump)

# Setup default logging of stdout and stderr to build.log in the output root.
BUILD_LOG='$(OUTPUT_ROOT)/build.log'
BUILD_LOG_WRAPPER='$(SH) $(SRC_ROOT)/common/bin/logger.sh $(BUILD_LOG)'
AC_SUBST(BUILD_LOG)
AC_SUBST(BUILD_LOG_WRAPPER)

###############################################################################
#
# Now we check if libjvm.so will use 32 or 64 bit pointers for the C/C++ code.
# (The JVM can use 32 or 64 bit Java pointers but that decision
# is made at runtime.)
#
AC_CHECK_SIZEOF([int *], [1111])
if test "x$ac_cv_sizeof_int_p" = x0; then 
    # The test failed, lets pick the assumed value.
    ARCH_DATA_MODEL=$HOST_CPU_BITS
else
    ARCH_DATA_MODEL=`expr 8 \* $ac_cv_sizeof_int_p`
fi

if test "x$ARCH_DATA_MODEL" = x64; then
    A_LP64="LP64:="
    ADD_LP64="-D_LP64=1"
fi
AC_MSG_CHECKING([for host address size])
AC_MSG_RESULT([$ARCH_DATA_MODEL bits])
AC_SUBST(LP64,$A_LP64)
AC_SUBST(ARCH_DATA_MODEL)

if test "x$ARCH_DATA_MODEL" != "x$HOST_CPU_BITS"; then
    AC_ERROR([The tested number of bits in the host ($ARCH_DATA_MODEL) differs from the number of bits expected to be found in the host ($HOST_CPU_BITS)])
fi

###############################################################################
#
# Can the C/C++ compiler use precompiled headers?
#
AC_ARG_ENABLE([precompiled-headers], [AS_HELP_STRING([--disable-precompiled-headers],
	[use precompiled headers when compiling C++ @<:@enabled@:>@])],
    [ENABLE_PRECOMPH=${enable_precompiled-headers}], [ENABLE_PRECOMPH=yes])

USE_PRECOMPILED_HEADER=1
if test "x$ENABLE_PRECOMPH" = xno; then
    USE_PRECOMPILED_HEADER=0
fi

if test "x$ENABLE_PRECOMPH" = xyes; then
    # Check that the compiler actually supports precomp headers.
    if test "x$GCC" = xyes; then
         AC_MSG_CHECKING([that precompiled headers work])         
         echo "int alfa();" > conftest.h
         $CXX -x c++-header conftest.h -o conftest.hpp.gch
         if test ! -f conftest.hpp.gch; then
             echo Precompiled header is not working!
             USE_PRECOMPILED_HEADER=0
             AC_MSG_RESULT([no])        
         else
             AC_MSG_RESULT([yes])
         fi
         rm -f conftest.h
    fi
fi

AC_SUBST(USE_PRECOMPILED_HEADER)

###############################################################################
#
# How to compile shared libraries. 
#

if test "x$GCC" = xyes; then
    COMPILER_NAME=gcc
    PICFLAG="-fPIC"
    LIBRARY_PREFIX=lib
    SHARED_LIBRARY='lib$1.so'
    STATIC_LIBRARY='lib$1.a'
    SHARED_LIBRARY_FLAGS="-shared"
    SHARED_LIBRARY_SUFFIX='.so'
    STATIC_LIBRARY_SUFFIX='.a'
    OBJ_SUFFIX='.o'
    EXE_SUFFIX=''
    SET_SHARED_LIBRARY_NAME='-Xlinker -soname=$1'
    SET_SHARED_LIBRARY_MAPFILE='-Xlinker -version-script=$(JDK_TOPDIR)/$1'
    SET_SHARED_LIBRARY_ORIGIN='-Xlinker -z -Xlinker origin -Xlinker -rpath -Xlinker \$$$$ORIGIN/$1'
    LD="$CC"
    LDEXE="$CC"
    LDCXX="$CXX"
    LDEXECXX="$CXX"

    # Linking is different on MacOSX
    if test "x$BUILD_OS" = xmacosx; then
        # Might change in the future to clang.
        COMPILER_NAME=gcc
        SHARED_LIBRARY='lib$1.dylib'
        SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0 $PICFLAG"
        SHARED_LIBRARY_SUFFIX='.dylib'
        EXE_SUFFIX=''
        SET_SHARED_LIBRARY_NAME='-Xlinker -install_name -Xlinker @rpath/$1' 
        SET_SHARED_LIBRARY_MAPFILE=''
        SET_SHARED_LIBRARY_ORIGIN='-Xlinker -rpath -Xlinker @loader_path/.'
    fi
else
    if test "x$BUILD_OS" = xsolaris; then
        # If it is not gcc, then assume it is the Oracle Solaris Studio Compiler
        COMPILER_NAME=ossc
        PICFLAG="-KPIC"
        LIBRARY_PREFIX=lib
        SHARED_LIBRARY='lib$1.so'
        STATIC_LIBRARY='lib$1.a'
        SHARED_LIBRARY_FLAGS="-G"
        SHARED_LIBRARY_SUFFIX='.so'
        STATIC_LIBRARY_SUFFIX='.a'
        OBJ_SUFFIX='.o'
        EXE_SUFFIX=''
        SET_SHARED_LIBRARY_NAME=''
        SET_SHARED_LIBRARY_MAPFILE='-M $(JDK_TOPDIR)/$1'
        SET_SHARED_LIBRARY_ORIGIN='-R \$$$$ORIGIN/$1'
        CFLAGS_JDKLIB_EXTRA='-xstrconst -D__solaris__'
    fi
    if test "x$BUILD_OS" = xwindows; then
        # If it is not gcc, then assume it is the MS Visual Studio compiler
        COMPILER_NAME=cl
        PICFLAG=""
        LIBRARY_PREFIX=
        SHARED_LIBRARY='$1.dll'
        STATIC_LIBRARY='$1.lib'
        SHARED_LIBRARY_FLAGS="-LD"
        SHARED_LIBRARY_SUFFIX='.dll'
        STATIC_LIBRARY_SUFFIX='.lib'
        OBJ_SUFFIX='.obj'
        EXE_SUFFIX='.exe'
        SET_SHARED_LIBRARY_NAME=''
        SET_SHARED_LIBRARY_MAPFILE=''
        SET_SHARED_LIBRARY_ORIGIN=''
    fi
fi

AC_SUBST(OBJ_SUFFIX)
AC_SUBST(SHARED_LIBRARY)
AC_SUBST(STATIC_LIBRARY)
AC_SUBST(LIBRARY_PREFIX)
AC_SUBST(SHARED_LIBRARY_SUFFIX)
AC_SUBST(STATIC_LIBRARY_SUFFIX)
AC_SUBST(EXE_SUFFIX)
AC_SUBST(SHARED_LIBRARY_FLAGS)
AC_SUBST(SET_SHARED_LIBRARY_NAME)
AC_SUBST(SET_SHARED_LIBRARY_MAPFILE)
AC_SUBST(SET_SHARED_LIBRARY_ORIGIN)

# The (cross) compiler is now configured, we can now test capabilities
# of the host platform.

###############################################################################
#
# Is the host little of big endian?
#
AC_C_BIGENDIAN([ENDIAN="big"],[ENDIAN="little"],[ENDIAN="unknown"],[ENDIAN="universal"])

if test "x$ENDIAN" = xuniversal; then
    AC_ERROR([It seems like someone needs to decide how we are to deal with universal binaries on the MacOSX?])
fi
if test "x$ENDIAN" = xunknown; then
    ENDIAN="$HOST_CPU_ENDIAN"
fi
if test "x$ENDIAN" != "x$HOST_CPU_ENDIAN"; then
    AC_WARN([The tested endian in the host ($ENDIAN) differs from the endian expected to be found in the host ($HOST_CPU_ENDIAN)])
    ENDIAN="$HOST_CPU_ENDIAN"
fi
AC_SUBST(ENDIAN)

###############################################################################
#
# We need a Boot JDK to bootstrap the build. 
#
BOOT_JDK_FOUND=no
AC_ARG_WITH(boot-jdk, [AS_HELP_STRING([--with-boot-jdk],
    [path to Boot JDK (used to bootstrap build) @<:@probed@:>@])])
                    
if test "x$with_boot_jdk" != x; then
    BOOT_JDK=$with_boot_jdk
    BOOT_JDK_FOUND=yes
fi
if test "x$BOOT_JDK_FOUND" = xno; then
    BDEPS_CHECK_MODULE(BOOT_JDK, bootjdk, xxx, [BOOT_JDK_FOUND=yes], [BOOT_JDK_FOUND=no])
fi

if test "x$BOOT_JDK_FOUND" = xno; then
    if test "x$JAVA_HOME" != x; then
        if test ! -d "$JAVA_HOME"; then
            AC_ERROR([Your JAVA_HOME points to a non-existing directory!])
        fi
        # Aha, the user has set a JAVA_HOME
        # let us use that as the Boot JDK.
        BOOT_JDK=$JAVA_HOME
        BOOT_JDK_FOUND=yes
        # To be on the safe side, lets check that it is a JDK.
        if test -x $BOOT_JDK/bin/javac && test -x $BOOT_JDK/bin/java; then
            JAVAC=$BOOT_JDK/bin/javac
            JAVA=$BOOT_JDK/bin/java
            BOOT_JDK_FOUND=yes
        else
            AC_ERROR([Your JAVA_HOME points to a JRE! The build needs a JDK! Please point JAVA_HOME to a JDK.])
        fi            
    fi
fi

if test "x$BOOT_JDK_FOUND" = xno; then
    AC_PATH_PROG(JAVAC_CHECK, javac)
    AC_PATH_PROG(JAVA_CHECK, java)
    BINARY="$JAVAC_CHECK"
    if test "x$JAVAC_CHECK" = x; then
        BINARY="$JAVA_CHECK"
    fi
    if test "x$BINARY" != x; then
        # So there is a java(c) binary, it might be part of a JDK.
        # Lets find the JDK/JRE directory by following symbolic links.
        # Linux/GNU systems often have links from /usr/bin/java to 
        # /etc/alternatives/java to the real JDK binary.
	WHICHCMD_SPACESAFE(BINARY,[path to javac])
        REMOVE_SYMBOLIC_LINKS(BINARY)
        BOOT_JDK=`dirname $BINARY`
        BOOT_JDK=`cd $BOOT_JDK/..; pwd`
        if test -x $BOOT_JDK/bin/javac && test -x $BOOT_JDK/bin/java; then
            JAVAC=$BOOT_JDK/bin/javac
            JAVA=$BOOT_JDK/bin/java
            BOOT_JDK_FOUND=yes
        fi
    fi
fi

if test "x$BOOT_JDK_FOUND" = xno; then
    # Try the MacOSX way.
    if test -x /usr/libexec/java_home; then
        BOOT_JDK=`/usr/libexec/java_home`
        if test -x $BOOT_JDK/bin/javac && test -x $BOOT_JDK/bin/java; then
            JAVAC=$BOOT_JDK/bin/javac
            JAVA=$BOOT_JDK/bin/java
            BOOT_JDK_FOUND=yes
        fi
    fi
fi

if test "x$BOOT_JDK_FOUND" = xno; then
    AC_PATH_PROG(JAVA_CHECK, java)
    if test "x$JAVA_CHECK" != x; then
        # There is a java in the path. But apparently we have not found a javac 
        # in the path, since that would have been tested earlier.
        if test "x$HOST_OS" = xwindows; then
            # Now if this is a windows platform. The default installation of a JDK
            # actually puts the JRE in the path and keeps the JDK out of the path!
            # Go look in the default installation location.
            BOOT_JDK=/cygdrive/c/Program\ Files/Java/`ls /cygdrive/c/Program\ Files/Java | grep jdk | sort -r | head --lines 1`
            if test -d "$BOOT_JDK"; then
                BOOT_JDK_FOUND=yes
            fi
        fi
        if test "x$BOOT_JDK_FOUND" = xno; then
            help_on_build_dependency openjdk
            AC_ERROR([Found a JRE, not not a JDK! Please remove the JRE from your path and put a JDK there instead. $HELP_MSG])
        fi
    else
        help_on_build_dependency openjdk
        AC_ERROR([Could not find a JDK. $HELP_MSG])
    fi
fi

WIN_FIX_PATH(BOOT_JDK)

# Now see if we can find the rt.jar, or its nearest equivalent.
BOOT_RTJAR="$BOOT_JDK/jre/lib/rt.jar"
SPACESAFE(BOOT_RTJAR,[the path to the Boot JDK rt.jar (or nearest equivalent)])

BOOT_TOOLSJAR="$BOOT_JDK/lib/tools.jar"
SPACESAFE(BOOT_TOOLSJAR,[the path to the Boot JDK tools.jar (or nearest equivalent)])

if test ! -f $BOOT_RTJAR; then
    # On MacOSX it is called classes.jar
    BOOT_RTJAR=$BOOT_JDK/../Classes/classes.jar
    if test ! -f $BOOT_RTJAR; then
        AC_ERROR([Cannot find the rt.jar or its equivalent!])
    fi
    # Remove the .. 
    BOOT_RTJAR="`cd ${BOOT_RTJAR%/*} && pwd`/${BOOT_RTJAR##*/}"
    # The tools.jar is part of classes.jar
    BOOT_TOOLSJAR="$BOOT_RTJAR"
fi

AC_SUBST(BOOT_JDK)
AC_SUBST(BOOT_RTJAR)
AC_SUBST(BOOT_TOOLSJAR)
AC_MSG_CHECKING([for Boot JDK])
AC_MSG_RESULT([$BOOT_JDK])
AC_MSG_CHECKING([for Boot rt.jar])
AC_MSG_RESULT([$BOOT_RTJAR])
AC_MSG_CHECKING([for Boot tools.jar])
AC_MSG_RESULT([$BOOT_TOOLSJAR])

# Use the java tool from the Boot JDK.
AC_MSG_CHECKING([for java in Boot JDK])
JAVA=$BOOT_JDK/bin/java
if test ! -x $JAVA; then
    AC_ERROR([Could not find a working java])
fi
BOOT_JDK_VERSION=`$JAVA -version 2>&1 | head -n 1`
AC_MSG_RESULT([yes $BOOT_JDK_VERSION])
AC_SUBST(JAVA)

# Extra M4 quote needed to protect [] in grep expression.
[FOUND_VERSION_78=`echo $BOOT_JDK_VERSION | grep  '\"1\.[78]\.'`]
if test "x$FOUND_VERSION_78" = x; then
    help_on_build_dependency openjdk
    AC_ERROR([Your bootjdk must be version 7 or 8. $HELP_MSG])
fi

# When compiling code to be executed by the Boot JDK, force jdk7 compatibility.
BOOT_JDK_SOURCETARGET="-source 7 -target 7"
AC_SUBST(BOOT_JDK_SOURCETARGET)

# Use the javac tool from the Boot JDK.
AC_MSG_CHECKING([for javac in Boot JDK])
JAVAC=$BOOT_JDK/bin/javac
if test ! -x $JAVAC; then
    AC_ERROR([Could not find a working javac])
fi
AC_MSG_RESULT(yes)
AC_SUBST(JAVAC)
AC_SUBST(JAVAC_FLAGS)

# Use the javac tool from the Boot JDK.
AC_MSG_CHECKING([for javah in Boot JDK])
JAVAH=$BOOT_JDK/bin/javah
if test ! -x $JAVAH; then
    AC_ERROR([Could not find a working javah])
fi
AC_MSG_RESULT(yes)
AC_SUBST(JAVAH)

# Use the jar tool from the Boot JDK.
AC_MSG_CHECKING([for jar in Boot JDK])
JAR=$BOOT_JDK/bin/jar
if test ! -x $JAR; then
    AC_ERROR([Could not find a working jar])
fi
AC_SUBST(JAR)
AC_MSG_RESULT(yes)

# Use the rmic tool from the Boot JDK.
AC_MSG_CHECKING([for rmic in Boot JDK])
RMIC=$BOOT_JDK/bin/rmic
if test ! -x $RMIC; then
    AC_ERROR([Could not find a working rmic])
fi
AC_SUBST(RMIC)
AC_MSG_RESULT(yes)

###############################################################################
#
# Pickup additional source for a component from outside of the source root
# or override source for a component. 
#
AC_ARG_WITH(add-source-root, [AS_HELP_STRING([--with-add-source-root],
    [for each and every source directory, look in this additional source root for
     the same directory; if it exists and have files in it, include it in the build])])                             
                             
AC_ARG_WITH(override-source-root, [AS_HELP_STRING([--with-override-source-root],
    [for each and every source directory, look in this override source root for
     the same directory; if it exists, use that directory instead and
     ignore the directory in the original source root])])

AC_ARG_WITH(adds-and-overrides, [AS_HELP_STRING([--with-adds-and-overrides],
    [use the subdirs 'adds' and 'overrides' in the specified directory as
     add-source-root and override-source-root])])

if test "x$with_adds_and_overrides" != x; then
    with_add_source_root="$with_adds_and_overrides/adds"
    with_override_source_root="$with_adds_and_overrides/overrides"
fi

if test "x$with_add_source_root" != x; then
    if ! test -d $with_add_source_root; then
       AC_ERROR([Trying to use a non-existant add-source-root $with_add_source_root])
    fi
    CURDIR="$PWD"
    cd "$with_add_source_root"
    ADD_SRC_ROOT="`pwd`"
    cd "$CURDIR"
    # Verify that the addon source root does not have any root makefiles.
    # If it does, then it is usually an error, prevent this.
    if test -f $with_add_source_root/langtools/makefiles/Makefile || \
       test -f $with_add_source_root/langtools/make/Makefile; then
        AC_ERROR([Your add source root seems to contain a full langtools repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/corba/makefiles/Makefile || \
       test -f $with_add_source_root/corba/make/Makefile; then
        AC_ERROR([Your add source root seems to contain a full corba repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/jaxp/makefiles/Makefile || \
       test -f $with_add_source_root/jaxp/make/Makefile; then
        AC_ERROR([Your add source root seems to contain a full jaxp repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/jaxws/makefiles/Makefile || \
       test -f $with_add_source_root/jaxws/make/Makefile; then
        AC_ERROR([Your add source root seems to contain a full jaxws repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/hotspot/makefiles/Makefile || \
       test -f $with_add_source_root/hotspot/make/Makefile; then
        AC_ERROR([Your add source root seems to contain a full hotspot repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/jdk/makefiles/Makefile || \
       test -f $with_add_source_root/jdk/make/Makefile; then
        AC_ERROR([Your add source root seems to contain a full JDK repo! An add source root should only contain additional sources.])
    fi
fi
AC_SUBST(ADD_SRC_ROOT)

if test "x$with_override_source_root" != x; then
    if ! test -d $with_override_source_root; then
       AC_ERROR([Trying to use a non-existant override-source-root $with_override_source_root])
    fi
    CURDIR="$PWD"
    cd "$with_override_source_root"
    OVERRIDE_SRC_ROOT="`pwd`"
    cd "$CURDIR"
    if test -f $with_override_source_root/langtools/makefiles/Makefile || \
       test -f $with_override_source_root/langtools/make/Makefile; then
        AC_ERROR([Your override source root seems to contain a full langtools repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/corba/makefiles/Makefile || \
       test -f $with_override_source_root/corba/make/Makefile; then
        AC_ERROR([Your override source root seems to contain a full corba repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/jaxp/makefiles/Makefile || \
       test -f $with_override_source_root/jaxp/make/Makefile; then
        AC_ERROR([Your override source root seems to contain a full jaxp repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/jaxws/makefiles/Makefile || \
       test -f $with_override_source_root/jaxws/make/Makefile; then
        AC_ERROR([Your override source root seems to contain a full jaxws repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/hotspot/makefiles/Makefile || \
       test -f $with_override_source_root/hotspot/make/Makefile; then
        AC_ERROR([Your override source root seems to contain a full hotspot repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/jdk/makefiles/Makefile || \
       test -f $with_override_source_root/jdk/make/Makefile; then
        AC_ERROR([Your override source root seems to contain a full JDK repo! An override source root should only contain sources that override.])
    fi
fi
AC_SUBST(OVERRIDE_SRC_ROOT)

###############################################################################
#
# Override a repo completely, this is used for example when you have 3 small
# development sandboxes of the langtools sources and want to avoid having 3 full
# OpenJDK sources checked out on disk.
#
# Assuming that the 3 langtools sandboxes are located here:
# /home/fredrik/sandbox1/langtools
# /home/fredrik/sandbox2/langtools
# /home/fredrik/sandbox3/langtools
#
# From the source root you create build subdirs manually:
#     mkdir -p build1 build2 build3 
# in each build directory run:
#     (cd build1 && ../configure --with-override-langtools=/home/fredrik/sandbox1 && make)
#     (cd build2 && ../configure --with-override-langtools=/home/fredrik/sandbox2 && make)
#     (cd build3 && ../configure --with-override-langtools=/home/fredrik/sandbox3 && make)
#

AC_ARG_WITH(override-langtools, [AS_HELP_STRING([--with-override-langtools],
    [use this langtools dir for the build])])

AC_ARG_WITH(override-corba, [AS_HELP_STRING([--with-override-corba],
    [use this corba dir for the build])])

AC_ARG_WITH(override-jaxp, [AS_HELP_STRING([--with-override-jaxp],
	[use this jaxp dir for the build])])

AC_ARG_WITH(override-jaxws, [AS_HELP_STRING([--with-override-jaxws],
	[use this jaxws dir for the build])])

AC_ARG_WITH(override-hotspot, [AS_HELP_STRING([--with-override-hotspot],
	[use this hotspot dir for the build])])

AC_ARG_WITH(override-jdk, [AS_HELP_STRING([--with-override-jdk],
	[use this jdk dir for the build])])

if test "x$with_override_langtools" != x; then
    CURDIR="$PWD"
    cd "$with_override_langtools"
    LANGTOOLS_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $LANGTOOLS_TOPDIR/makefiles/Makefile; then
        AC_ERROR([You have to override langtools with a full langtools repo!])
    fi
    AC_MSG_CHECKING([if langtools should be overridden])
    AC_MSG_RESULT([yes with $LANGTOOLS_TOPDIR])
fi    
if test "x$with_override_corba" != x; then
    CURDIR="$PWD"
    cd "$with_override_corba"
    CORBA_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $CORBA_TOPDIR/makefiles/Makefile; then
        AC_ERROR([You have to override corba with a full corba repo!])
    fi
    AC_MSG_CHECKING([if corba should be overridden])
    AC_MSG_RESULT([yes with $CORBA_TOPDIR])
fi    
if test "x$with_override_jaxp" != x; then
    CURDIR="$PWD"
    cd "$with_override_jaxp"
    JAXP_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $JAXP_TOPDIR/makefiles/Makefile; then
        AC_ERROR([You have to override jaxp with a full jaxp repo!])
    fi
    AC_MSG_CHECKING([if jaxp should be overridden])
    AC_MSG_RESULT([yes with $JAXP_TOPDIR])
fi    
if test "x$with_override_jaxws" != x; then
    CURDIR="$PWD"
    cd "$with_override_jaxws"
    JAXWS_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $JAXWS_TOPDIR/makefiles/Makefile; then
        AC_ERROR([You have to override jaxws with a full jaxws repo!])
    fi
    AC_MSG_CHECKING([if jaxws should be overridden])
    AC_MSG_RESULT([yes with $JAXWS_TOPDIR])
fi    
if test "x$with_override_hotspot" != x; then
    CURDIR="$PWD"
    cd "$with_override_hotspot"
    HOTSPOT_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $HOTSPOT_TOPDIR/make/Makefile && \
       ! test -f $HOTSPOT_TOPDIR/makefiles/Makefile; then
        AC_ERROR([You have to override hotspot with a full hotspot repo!])
    fi
    AC_MSG_CHECKING([if hotspot should be overridden])
    AC_MSG_RESULT([yes with $HOTSPOT_TOPDIR])
fi    
if test "x$with_override_jdk" != x; then
    CURDIR="$PWD"
    cd "$with_override_jdk"
    JDK_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $JDK_TOPDIR/makefiles/Makefile; then
        AC_ERROR([You have to override JDK with a full JDK repo!])
    fi
    AC_MSG_CHECKING([if JDK should be overridden])
    AC_MSG_RESULT([yes with $JDK_TOPDIR])
fi    

###############################################################################
#
# Specify options for anything that is run with the Boot JDK.
#
AC_ARG_WITH(boot-jdk-jvmargs, [AS_HELP_STRING([--with-boot-jdk-jvmargs],
	[specify JVM arguments to be passed to all invocations of the Boot JDK, overriding the default values,
     e.g --with-boot-jdk-jvmargs="-Xmx8G -enableassertions"])])

if test "x$with_boot_jdk_jvmargs" = x; then
    # Not all JVM:s accept the same arguments on the command line.
    # OpenJDK specific increase in thread stack for JDK build,
    # well more specifically, when running javac.
    if test "x$BUILD_NUM_BITS" = x32; then
       STACK_SIZE=768
    else
       # Running Javac on a JVM on a 64-bit machine, the stack takes more space
       # since 64-bit pointers are pushed on the stach. Apparently, we need
       # to increase the stack space when javacing the JDK....
       STACK_SIZE=1536
    fi

    # Minimum amount of heap memory.
    ADD_JVM_ARG_IF_OK([-Xms64M],boot_jdk_jvmargs,[$JAVA])
    if test "x$HOST_OS" = "xmacosx"; then
        # Why does macosx need more heap? Its the huge JDK batch.
        ADD_JVM_ARG_IF_OK([-Xmx1600M],boot_jdk_jvmargs,[$JAVA])
    else
        ADD_JVM_ARG_IF_OK([-Xmx1100M],boot_jdk_jvmargs,[$JAVA])
    fi
    # When is adding -client something that speeds up the JVM?
    # ADD_JVM_ARG_IF_OK([-client],boot_jdk_jvmargs,[$JAVA])
    ADD_JVM_ARG_IF_OK([-XX:PermSize=32m],boot_jdk_jvmargs,[$JAVA])
    ADD_JVM_ARG_IF_OK([-XX:MaxPermSize=160m],boot_jdk_jvmargs,[$JAVA])
    ADD_JVM_ARG_IF_OK([-XX:ThreadStackSize=$STACK_SIZE],boot_jdk_jvmargs,[$JAVA])
    # Disable special log output when a debug build is used as Boot JDK...
    ADD_JVM_ARG_IF_OK([-XX:-PrintVMOptions -XX:-UnlockDiagnosticVMOptions -XX:-LogVMOutput],boot_jdk_jvmargs,[$JAVA])
fi

AC_SUBST(BOOT_JDK_JVMARGS, $boot_jdk_jvmargs)

AC_ARG_WITH(server-java, [AS_HELP_STRING([--with-server-java],
	[use this java binary for running the javac background server and other long running java tasks in the build process,
     e.g. ---with-server-java="/opt/jrockit/bin/java -server"])])

if test "x$with_server_java" != x; then
    SERVER_JAVA="$with_server_java"
    FOUND_VERSION=`$SERVER_JAVA -version 2>&1 | grep " version \""`
    if test "x$FOUND_VERSION" = x; then
        AC_ERROR([Could not execute server java: $SERVER_JAVA])
    fi
else
    SERVER_JAVA=""
    # Hotspot specific options.
    ADD_JVM_ARG_IF_OK([-XX:+UseParallelOldGC],SERVER_JAVA,[$JAVA])
    ADD_JVM_ARG_IF_OK([-verbosegc],SERVER_JAVA,[$JAVA])
    # JRockit specific options.
    ADD_JVM_ARG_IF_OK([-Xverbose:gc],SERVER_JAVA,[$JAVA])
    SERVER_JAVA="$JAVA $SERVER_JAVA"
fi                    
AC_SUBST(SERVER_JAVA)

AC_MSG_CHECKING([whether to use shared server for javac])
AC_ARG_ENABLE([javac-server], [AS_HELP_STRING([--enable-javac-server],
	[enable the shared javac server during the build process @<:@disabled@:>@])],
	[ENABLE_JAVAC_SERVER="${enableval}"], [ENABLE_JAVAC_SERVER='no'])
AC_MSG_RESULT([$ENABLE_JAVAC_SERVER])
if test "x$ENABLE_JAVAC_SERVER" = xyes; then
    JAVAC_USE_REMOTE=true
    JAVAC_SERVERS="$OUTPUT_ROOT/javacservers"
else
    JAVAC_USE_REMOTE=false
    JAVAC_SERVERS=
fi
AC_SUBST(JAVAC_USE_REMOTE)
AC_SUBST(JAVAC_SERVERS)

AC_ARG_WITH(javac-server-cores, [AS_HELP_STRING([--with-javac-server-cores],
	[use at most this number of concurrent threads on the javac server @<:@probed@:>@])])
if test "x$with_javac_server_cores" != x; then
    JAVAC_SERVER_CORES="$with_javac_server_cores"
else
    if test "$NUM_CORES" -gt 16; then
        # We set this arbitrary limit because we want to limit the heap
        # size of the javac server.
        # In the future we will make the javac compilers in the server
        # share more and more state, thus enabling us to use more and
        # more concurrent threads in the server.
        JAVAC_SERVER_CORES="16"
    else
        JAVAC_SERVER_CORES="$NUM_CORES"
    fi

    if test "$MEMORY_SIZE" -gt "17000"; then
        MAX_HEAP_MEM=10000
        ADD_JVM_ARG_IF_OK([-d64],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms10G -Xmx10G],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn2G],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "10000"; then
        MAX_HEAP_MEM=6000
        ADD_JVM_ARG_IF_OK([-d64],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms6G -Xmx6G],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn1G],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "5000"; then
        MAX_HEAP_MEM=3000
        ADD_JVM_ARG_IF_OK([-d64],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms1G -Xmx3G],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn256M],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "3800"; then
        MAX_HEAP_MEM=2500
        ADD_JVM_ARG_IF_OK([-Xms1G -Xmx2500M],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn256M],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "1900"; then
        MAX_HEAP_MEM=1200
        ADD_JVM_ARG_IF_OK([-Xms700M -Xmx1200M],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn256M],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "1000"; then
        MAX_HEAP_MEM=900
        ADD_JVM_ARG_IF_OK([-Xms400M -Xmx900M],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn128M],SERVER_JAVA,[$SERVER_JAVA])
    else
        MAX_HEAP_MEM=512
        ADD_JVM_ARG_IF_OK([-Xms256M -Xmx512M],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn128M],SERVER_JAVA,[$SERVER_JAVA])
    fi

    MAX_COMPILERS_IN_HEAP=`expr $MAX_HEAP_MEM / 501`
    if test "$JAVAC_SERVER_CORES" -gt "$MAX_COMPILERS_IN_HEAP"; then
        AC_MSG_CHECKING([if number of server cores must be reduced])
        JAVAC_SERVER_CORES="$MAX_COMPILERS_IN_HEAP"
        AC_MSG_RESULT([yes, to $JAVAC_SERVER_CORES with max heap size $MAX_HEAP_MEM MB])
    fi
fi                    
AC_SUBST(JAVAC_SERVER_CORES)

AC_MSG_CHECKING([whether to track dependencies between Java packages])
AC_ARG_ENABLE([javac-deps], [AS_HELP_STRING([--enable-javac-deps],
	[enable the dependency tracking between Java packages @<:@disabled@:>@])],
	[ENABLE_JAVAC_DEPS="${enableval}"], [ENABLE_JAVAC_DEPS='no'])
AC_MSG_RESULT([$ENABLE_JAVAC_DEPS])
if test "x$ENABLE_JAVAC_DEPS" = xyes; then
    JAVAC_USE_DEPS=true
else
    JAVAC_USE_DEPS=false
fi
AC_SUBST(JAVAC_USE_DEPS)

AC_MSG_CHECKING([whether to use multiple cores for javac compilation])
AC_ARG_ENABLE([javac-multi-core], [AS_HELP_STRING([--enable-javac-multi-core],
	[compile Java packages concurrently @<:@disabled@:>@])],
	[ENABLE_JAVAC_MULTICORE="${enableval}"], [ENABLE_JAVAC_MULTICORE='no'])
AC_MSG_RESULT([$ENABLE_JAVAC_MULTICORE])
if test "x$ENABLE_JAVAC_MULTICORE" = xyes; then
    JAVAC_USE_MODE=MULTI_CORE_CONCURRENT
else
    JAVAC_USE_MODE=SINGLE_THREADED_BATCH
    if test "x$ENABLE_JAVAC_DEPS" = xyes; then
        AC_MSG_WARN([Dependency tracking is not supported with single threaded batch compiles of Java source roots. Please add --disable-javac-deps to your configure options.])
        AC_MSG_WARN([Disabling dependency tracking for you now.])
        JAVAC_USE_DEPS=false
    fi
    if test "x$ENABLE_JAVAC_SERVER" = xyes; then
        AC_MSG_WARN([The javac server will not be used since single threaded batch compiles are run within their own JVM. Please add --disable-javac-server to your configure options.])
        AC_MSG_WARN([Disabling javac server for you now.])
        JAVAC_USE_REMOTE=false
    fi
fi
AC_SUBST(JAVAC_USE_MODE)

###############################################################################
#
# OS specific settings that we never will need to probe.
#
if test "x$HOST_OS" = xlinux; then
    AC_MSG_CHECKING([what is not needed on Linux?])
    PULSE_NOT_NEEDED=yes
    AC_MSG_RESULT([pulse])
fi

if test "x$HOST_OS" = xsolaris; then
    AC_MSG_CHECKING([what is not needed on Solaris?])
    ALSA_NOT_NEEDED=yes
    PULSE_NOT_NEEDED=yes
    AC_MSG_RESULT([alsa pulse])
fi

if test "x$HOST_OS" = xwindows; then
    AC_MSG_CHECKING([what is not needed on Windows?])
    CUPS_NOT_NEEDED=yes    
    ALSA_NOT_NEEDED=yes
    PULSE_NOT_NEEDED=yes
    X11_NOT_NEEDED=yes
    AC_MSG_RESULT([alsa cups pulse x11])
fi

if test "x$HOST_OS" = xmacosx; then
    AC_MSG_CHECKING([what is not needed on MacOSX?])
    ALSA_NOT_NEEDED=yes
    PULSE_NOT_NEEDED=yes
    X11_NOT_NEEDED=yes
    FREETYPE2_NOT_NEEDED=yes    
    # If the java runtime framework is disabled, then we need X11.
    # This will be adjusted below.
    AC_MSG_RESULT([alsa pulse x11])
fi

if test "x$HOST_OS" = xbsd; then
    AC_MSG_CHECKING([what is not needed on bsd?])
    ALSA_NOT_NEEDED=yes
    AC_MSG_RESULT([alsa])    
fi

###############################################################################
#
# Check for MacOSX support for OpenJDK. If this exists, try to build a JVM
# that uses this API. 
#
AC_ARG_ENABLE([macosx-runtime-support], [AS_HELP_STRING([--disable-macosx-runtime-support],
	[disable the use of MacOSX Java runtime support framework @<:@enabled@:>@])],
	[MACOSX_RUNTIME_SUPPORT="${enableval}"],[MACOSX_RUNTIME_SUPPORT="no"])

USE_MACOSX_RUNTIME_SUPPORT=no
AC_MSG_CHECKING([for explicit Java runtime support in the OS])
if test -f /System/Library/Frameworks/JavaVM.framework/Frameworks/JavaRuntimeSupport.framework/Headers/JavaRuntimeSupport.h; then
    if test "x$MACOSX_RUNTIME_SUPPORT" != xno; then
        MACOSX_RUNTIME_SUPPORT=yes
        USE_MACOSX_RUNTIME_SUPPORT=yes
        AC_MSG_RESULT([yes, does not need alsa freetype2 pulse and X11])
    else
        AC_MSG_RESULT([yes, but explicitly disabled.])
    fi
else
    AC_MSG_RESULT([no])
fi

if test "x$HOST_OS" = xmacosx && test "x$USE_MACOSX_RUNTIME_SUPPORT" = xno; then
    AC_MSG_CHECKING([what is not needed on an X11 build on MacOSX?])
    X11_NOT_NEEDED=
    FREETYPE2_NOT_NEEDED=
    AC_MSG_RESULT([alsa pulse])
fi

###############################################################################
#
# Check for X Windows
#
AC_PATH_XTRA

if test "x$no_x" = xyes && test "x$X11_NOT_NEEDED" != xyes; then 
    help_on_build_dependency x11
    AC_ERROR([Could not find X11 libraries. $HELP_MSG])
fi

AC_SUBST(X_CFLAGS)
AC_SUBST(X_LIBS)

# Some of the old makefiles require a setting of OPENWIN_HOME
# Since the X11R6 directory has disappeared on later Linuxes,
# we need to probe for it.
if test "x$HOST_OS" = xlinux; then
    if test -d "$SYS_ROOT/usr/X11R6"; then
        OPENWIN_HOME="$SYS_ROOT/usr/X11R6"
    fi
    if test -d "$SYS_ROOT/usr/include/X11"; then
        OPENWIN_HOME="$SYS_ROOT/usr"
    fi
fi
if test "x$HOST_OS" = xsolaris; then
    OPENWIN_HOME="/usr/openwin"
fi
AC_SUBST(OPENWIN_HOME)

AC_LANG_PUSH(C)
OLD_CFLAGS="$CFLAGS"
CFLAGS="$CFLAGS $X_CFLAGS"
AC_CHECK_HEADERS([X11/extensions/shape.h X11/extensions/Xrender.h X11/extensions/XTest.h],
                [X11_A_OK=yes],
                [X11_A_OK=no])
CFLAGS="$OLD_CFLAGS"
AC_LANG_POP(C)

if test "x$X11_A_OK" = xno && test "x$X11_NOT_NEEDED" != xyes; then 
    help_on_build_dependency x11
    AC_ERROR([Could not find all X11 headers (shape.h Xrender.h XTest.h). $HELP_MSG])
fi

###############################################################################
#
# The common unix printing system cups is used to print from java.
#
AC_ARG_WITH(cups, [AS_HELP_STRING([--with-cups],
    [specify prefix directory for the cups package
	 (expecting the libraries under PATH/lib and the headers under PATH/include)])])
AC_ARG_WITH(cups-include, [AS_HELP_STRING([--with-cups-include],
	[specify directory for the cups include files])])
AC_ARG_WITH(cups-lib, [AS_HELP_STRING([--with-cups-lib],
	[specify directory for the cups library])])

if test "x$CUPS_NOT_NEEDED" = xyes; then
	if test "x$with_cups" != x || test "x$with_cups-include" != x || test "x$with_cups-lib" != x; then
		AC_MSG_WARN([cups not used, so --with-cups is ignored])
	fi
	CUPS_CFLAGS=
	CUPS_LIBS=
else
	CUPS_FOUND=no

	if test "x$with_cups" = xno || test "x$with_cups-include" = xno || test "x$with_cups-lib" = xno; then
	    AC_ERROR([It is not possible to disable the use of cups. Remove the --without-cups option.])
	fi

	if test "x$with_cups" != x; then
	    CUPS_LIBS="-L$with_cups/lib -lcups"
	    CUPS_CFLAGS="-I$with_cups/include"
	    CUPS_FOUND=yes
	fi
	if test "x$with_cups-include" != x; then
	    CUPS_CFLAGS="-I$with_cups-include"
	    CUPS_FOUND=yes
	fi
	if test "x$with_cups-lib" != x; then
	    CUPS_LIBS="-L$with_cups-lib -lcups"
	    CUPS_FOUND=yes
	fi
	if test "x$CUPS_FOUND" = xno; then
	    BDEPS_CHECK_MODULE(CUPS, cups, xxx, [CUPS_FOUND=yes])
	fi
	if test "x$CUPS_FOUND" = xno; then
	    # Are the cups headers installed in the default /usr/include location?
	    AC_CHECK_HEADERS([cups/cups.h cups/ppd.h],
	                     [CUPS_FOUND=yes
	                      CUPS_CFLAGS=
	                      CUPS_LIBS="-lcups"
	                      DEFAULT_CUPS=yes])
	fi
	if test "x$CUPS_FOUND" = xno; then
	    # Getting nervous now? Lets poke around for standard Solaris third-party
	    # package installation locations.
	    AC_MSG_CHECKING([for cups headers and libs])
	    if test -s /opt/sfw/cups/include/cups/cups.h; then
	       # An SFW package seems to be installed!
	       CUPS_FOUND=yes
	       CUPS_CFLAGS="-I/opt/sfw/cups/include"
	       CUPS_LIBS="-L/opt/sfw/cups/lib -lcups"
	    elif test -s /opt/csw/include/cups/cups.h; then
	       # A CSW package seems to be installed!
	       CUPS_FOUND=yes
	       CUPS_CFLAGS="-I/opt/csw/include"
	       CUPS_LIBS="-L/opt/csw/lib -lcups"
	    fi
	    AC_MSG_RESULT([$CUPS_FOUND])
	fi
	if test "x$CUPS_FOUND" = xno; then 
	    help_on_build_dependency cups
	    AC_ERROR([Could not find cups! $HELP_MSG ])
	fi
fi

AC_SUBST(CUPS_CFLAGS)
AC_SUBST(CUPS_LIBS)

###############################################################################
#
# The ubiquitous freetype2 library is used to render fonts.
#
AC_ARG_WITH(freetype, [AS_HELP_STRING([--with-freetype],
	[specify prefix directory for the freetype2 package
     (expecting the libraries under PATH/lib and the headers under PATH/include)])])

# If we are using the OS installed system lib for freetype, then we do not need to copy it to the build tree
USING_SYSTEM_FT_LIB=false

if test "x$FREETYPE2_NOT_NEEDED" = xyes; then
	if test "x$with_freetype" != x || test "x$with_freetype_include" != x || test "x$with_freetype_lib" != x; then
		AC_MSG_WARN([freetype not used, so --with-freetype is ignored])
	fi
	FREETYPE2_CFLAGS=
	FREETYPE2_LIBS=
        FREETYPE2_LIB_PATH=
else
	FREETYPE2_FOUND=no

	if test "x$with_freetype" != x; then
            SPACESAFE(with_freetype,[the path to freetype])
	    FREETYPE2_LIBS="-L$with_freetype/lib -lfreetype"
            if test "x$HOST_OS" = xwindows; then
                FREETYPE2_LIBS="$with_freetype/lib/freetype.lib"
            fi
            FREETYPE2_LIB_PATH="$with_freetype/lib"
	    FREETYPE2_CFLAGS="-I$with_freetype/include"
            if test -s $with_freetype/include/ft2build.h && test -d $with_freetype/include/freetype2/freetype; then
                FREETYPE2_CFLAGS="-I$with_freetype/include/freetype2 -I$with_freetype/include"
            fi
	    FREETYPE2_FOUND=yes
   	    if test "x$FREETYPE2_FOUND" = xyes; then
	        # Verify that the directories exist 
                if ! test -d "$with_freetype/lib" || ! test -d "$with_freetype/include"; then
		   AC_ERROR([Could not find the expected directories $with_freetype/lib and $with_freetype/include])
		fi
	        # List the contents of the lib.
		FREETYPELIB=`ls $with_freetype/lib/libfreetype.so $with_freetype/lib/freetype.dll 2> /dev/null`
                if test "x$FREETYPELIB" = x; then
		   AC_ERROR([Could not find libfreetype.se nor freetype.dll in $with_freetype/lib])
		fi
	        # Check one h-file
                if ! test -s "$with_freetype/include/ft2build.h"; then
		   AC_ERROR([Could not find $with_freetype/include/ft2build.h])
		fi
            fi
        fi
	if test "x$FREETYPE2_FOUND" = xno; then
	    BDEPS_CHECK_MODULE(FREETYPE2, freetype2, xxx, [FREETYPE2_FOUND=yes], [FREETYPE2_FOUND=no])
            USING_SYSTEM_FT_LIB=true
	fi
	if test "x$FREETYPE2_FOUND" = xno; then
	    PKG_CHECK_MODULES(FREETYPE2, freetype2, [FREETYPE2_FOUND=yes], [FREETYPE2_FOUND=no])
            USING_SYSTEM_FT_LIB=true
	fi
	if test "x$FREETYPE2_FOUND" = xno; then
	    AC_MSG_CHECKING([for freetype in some standard locations])
	
	    if test -s /usr/X11/include/ft2build.h && test -d /usr/X11/include/freetype2/freetype; then
	        DEFAULT_FREETYPE_CFLAGS="-I/usr/X11/include/freetype2 -I/usr/X11/include"
	        DEFAULT_FREETYPE_LIBS="-L/usr/X11/lib -lfreetype"
	    fi
	    if test -s /usr/include/ft2build.h && test -d /usr/include/freetype2/freetype; then
	        DEFAULT_FREETYPE_CFLAGS="-I/usr/include/freetype2"
	        DEFAULT_FREETYPE_LIBS="-lfreetype"
	    fi
	
	    PREV_CXXCFLAGS="$CXXFLAGS"
	    PREV_LDFLAGS="$LDFLAGS"
	    CXXFLAGS="$CXXFLAGS $DEFAULT_FREETYPE_CFLAGS"
	    LDFLAGS="$LDFLAGS $DEFAULT_FREETYPE_LIBS"
	    AC_LINK_IFELSE([AC_LANG_SOURCE([[#include<ft2build.h>
	                    #include FT_FREETYPE_H 
	                   int main() { return 0; }
	                  ]])],
	                  [
	                      # Yes, the default cflags and libs did the trick.
	                      FREETYPE2_FOUND=yes
	                      FREETYPE2_CFLAGS="$DEFAULT_FREETYPE_CFLAGS"
	                      FREETYPE2_LIBS="$DEFAULT_FREETYPE_LIBS"
	                  ],
	                  [
	                      FREETYPE2_FOUND=no
	                  ])
            CXXCFLAGS="$PREV_CXXFLAGS"
	    LDFLAGS="$PREV_LDFLAGS"
	    AC_MSG_RESULT([$FREETYPE2_FOUND])
            USING_SYSTEM_FT_LIB=true
	fi
	if test "x$FREETYPE2_FOUND" = xno; then
		help_on_build_dependency freetype2
		AC_ERROR([Could not find freetype2! $HELP_MSG ])
	fi    
fi

AC_SUBST(USING_SYSTEM_FT_LIB)
AC_SUBST(FREETYPE2_LIB_PATH)
AC_SUBST(FREETYPE2_CFLAGS)
AC_SUBST(FREETYPE2_LIBS)

###############################################################################
#
# Check for alsa headers and libraries. Used on Linux/GNU systems.
#
AC_ARG_WITH(alsa, [AS_HELP_STRING([--with-alsa],
	[specify prefix directory for the alsa package
	 (expecting the libraries under PATH/lib and the headers under PATH/include)])])
AC_ARG_WITH(alsa-include, [AS_HELP_STRING([--with-alsa-include],
	[specify directory for the alsa include files])])
AC_ARG_WITH(alsa-lib, [AS_HELP_STRING([--with-alsa-lib],
	[specify directory for the alsa library])])

if test "x$ALSA_NOT_NEEDED" = xyes; then
	if test "x$with_alsa" != x || test "x$with_alsa-include" != x || test "x$with_alsa-lib" != x; then
		AC_MSG_WARN([alsa not used, so --with-alsa is ignored])
	fi
	ALSA_CFLAGS=
	ALSA_LIBS=
else
	ALSA_FOUND=no

	if test "x$with_alsa" = xno || test "x$with_alsa-include" = xno || test "x$with_alsa-lib" = xno; then
	    AC_ERROR([It is not possible to disable the use of alsa. Remove the --without-alsa option.])
	fi

	if test "x$with_alsa" != x; then
	    ALSA_LIBS="-L$with_alsa/lib -lalsa"
	    ALSA_CFLAGS="-I$with_alsa/include"
	    ALSA_FOUND=yes
	fi
	if test "x$with_alsa-include" != x; then
	    ALSA_CFLAGS="-I$with_alsa/include"
	    ALSA_FOUND=yes
	fi
	if test "x$with_alsa-lib" != x; then
	    ALSA_LIBS="-L$with_alsa/lib -lalsa"
	    ALSA_FOUND=yes
	fi
	if test "x$ALSA_FOUND" = xno; then
	    BDEPS_CHECK_MODULE(ALSA, alsa, xxx, [ALSA_FOUND=yes], [ALSA_FOUND=no])
	fi
	if test "x$ALSA_FOUND" = xno; then
	    PKG_CHECK_MODULES(ALSA, alsa, [ALSA_FOUND=yes], [ALSA_FOUND=no])
	fi
	if test "x$ALSA_FOUND" = xno; then
	    AC_CHECK_HEADERS([alsa/asoundlib.h],
	                     [ALSA_FOUND=yes
	                      ALSA_CFLAGS=-Iignoreme
	                      ALSA_LIBS=-lasound
	                      DEFAULT_ALSA=yes],
	                     [ALSA_FOUND=no])
	fi
	if test "x$ALSA_FOUND" = xno; then 
	    help_on_build_dependency alsa
	    AC_ERROR([Could not find alsa! $HELP_MSG ])
	fi    
fi

AC_SUBST(ALSA_CFLAGS)
AC_SUBST(ALSA_LIBS)

###############################################################################
#
# Check for pulse audio headers and libraries.
#
PULSE_FOUND=no
AC_ARG_WITH(pulse, [AS_HELP_STRING([--with-pulse],
	[specify prefix directory for the pulseaudio package
	 (expecting the libraries under PATH/lib and the headers under PATH/include)])])
AC_ARG_WITH(pulse-include, [AS_HELP_STRING([--with-pulse-include],
	[specify directory for the pulseaudio include files])])
AC_ARG_WITH(pulse-lib, [AS_HELP_STRING([--with-pulse-lib],
	[specify directory for the pulseaudio library])])

if test "x$with_pulse" != x; then
    PULSE_LIBS="-L$with_pulse/lib -lfreetype"
    PULSE_CFLAGS="-I$with_pulse/include"
    PULSE_FOUND=yes
fi
if test "x$with_pulse-include" != x; then
    PULSE_CFLAGS="-I$with_pulse/include"
    PULSE_FOUND=yes
fi
if test "x$with_pulse-lib" != x; then
    PULSE_LIBS="-L$with_pulse/lib -lfreetype"
    PULSE_FOUND=yes
fi
if test "x$PULSE_FOUND" = xno; then
    BDEPS_CHECK_MODULE(PULSE, pulse, xxx, [PULSE_FOUND=yes], [PULSE_FOUND=no])
fi
if test "x$PULSE_FOUND" = xno; then
    PKG_CHECK_MODULES(LIBPULSE,[libpulse >= 0.9.11],[PULSE_FOUND=yes],[PULSE_FOUND=no])
fi
if test "x$PULSE_FOUND" = xno; then
    AC_CHECK_HEADERS([pulse/pulseaudio.h],
                     [PULSE_FOUND=yes
                      PULSE_CFLAGS=-Iignoreme
                      PULSE_LIBS=
                      DEFAULT_PULSE=yes],
                     [PULSE_FOUND=no])
fi

if test "x$PULSE_FOUND" = xno && test "x$PULSE_NOT_NEEDED" != xyes; then 
    help_on_build_dependency pulse
    AC_ERROR([Could not find pulse audio libraries. $HELP_MSG ])
fi    

AC_SUBST(PULSE_CFLAGS)
AC_SUBST(PULSE_LIBS)

###############################################################################
#
# Check for the jpeg library
#

USE_EXTERNAL_LIBJPEG=true
AC_CHECK_LIB(jpeg, main, [],
             [ USE_EXTERNAL_LIBJPEG=false
               AC_MSG_NOTICE([Will use jpeg decoder bundled with the OpenJDK source])
             ])
AC_SUBST(USE_EXTERNAL_LIBJPEG)
        
###############################################################################
#
# Check for the gif library
#

USE_EXTERNAL_LIBJPEG=true
AC_CHECK_LIB(gif, main, [],
             [ USE_EXTERNAL_LIBGIF=false
               AC_MSG_NOTICE([Will use gif decoder bundled with the OpenJDK source])
             ])
AC_SUBST(USE_EXTERNAL_LIBGIF)

###############################################################################
#
# Check for the zlib library
#

USE_EXTERNAL_LIBZ=true
AC_CHECK_LIB(z, main, [],
             [ USE_EXTERNAL_LIBZ=false
               AC_MSG_NOTICE([Will use zlib bundled with the OpenJDK source])
             ])
AC_SUBST(USE_EXTERNAL_LIBZ)

###############################################################################
#
# Check if altzone exists in time.h
#

AC_TRY_LINK([#include <time.h>], [return (int)altzone;],
            has_altzone=yes,
            has_altzone=no)
if test "x$has_altzone" = xyes; then
    AC_DEFINE([HAVE_ALTZONE], 1, [Define if you have the external 'altzone' variable in time.h])
fi

###############################################################################
#
# Check the maths library
#

AC_CHECK_LIB(m, main, [],
             [ 
                  AC_MSG_NOTICE([Maths library was not found])
             ])
AC_SUBST(LIBM)

###############################################################################
#
# Should we run the painfully slow javadoc tool?
#
AC_MSG_CHECKING([whether to build documentation])
AC_ARG_ENABLE([docs], [AS_HELP_STRING([--enable-docs],
	[enable generation of Javadoc documentation @<:@disabled@:>@])],
	[ENABLE_DOCS="${enableval}"], [ENABLE_DOCS='no'])
AC_MSG_RESULT([$ENABLE_DOCS])
AC_SUBST(ENABLE_DOCS)
GENERATE_DOCS=false
if test "x$ENABLE_DOCS" = xyes; then
    GENERATE_DOCS=true
fi
AC_SUBST(GENERATE_DOCS)

###############################################################################
#
# Should we compile nimbus swing L&F? We can probably remove this option
# since nimbus is officially part of javax now.
#
AC_MSG_CHECKING([whether to build nimbus L&F])
AC_ARG_ENABLE([nimbus], [AS_HELP_STRING([--disable-nimbus],
	[disable Nimbus L&F @<:@enabled@:>@])],
	[ENABLE_NIMBUS="${enableval}"], [ENABLE_NIMBUS='yes'])
AC_MSG_RESULT([$ENABLE_NIMBUS])
DISABLE_NIMBUS=
if test "x$ENABLE_NIMBUS" = xno; then
    DISABLE_NIMBUS=true
fi
AC_SUBST(DISABLE_NIMBUS)

###############################################################################
#
# Setup the opt flags for different compilers
# and different operating systems.
#
case $COMPILER_TYPE in
  CC )
    D_FLAG="-g"
    case $COMPILER_NAME in
      gcc )
      	case $PLATFORM in
	  macosx )
	    # On MacOSX we optimize for size, something
	    # we should do for all platforms?
	    C_O_FLAG_HI="-O3"
	    C_O_FLAG_NORM="-Os"
	    C_O_FLAG_NONE="-O0"
	    ;;
	  *)
	    C_O_FLAG_HI="-O3"
	    C_O_FLAG_NORM="-O2"
	    C_O_FLAG_NONE="-O0"
	    ;;
	esac
        CXX_O_FLAG_HI="$C_O_FLAG_HI"
        CXX_O_FLAG_NORM="$C_O_FLAG_NORM"
        CXX_O_FLAG_NONE="$C_O_FLAG_NONE"
        ;;
      ossc )
        case $LEGACY_HOST_CPU1 in
          i586)
            C_O_FLAG_HI="-xO4 -Wu,-O4~yz"
            C_O_FLAG_NORM="-xO2 -Wu,-O2~yz"
            C_O_FLAG_NONE=""
            CXX_O_FLAG_HI="-xO4 -Qoption ube -O4~yz"
            CXX_O_FLAG_NORM="-xO2 -Qoption ube -O2~yz"
            CXX_O_FLAG_NONE=""
            ;;
          sparc)
            C_O_FLAG_HI="-xO4 -Wc,-Qrm-s -Wc,-Qiselect-T0"
            C_O_FLAG_NORM="-xO2 -Wc,-Qrm-s -Wc,-Qiselect-T0"
            C_O_FLAG_NONE=""
            CXX_O_FLAG_HI="-xO4 -Qoption cg -Qrm-s -Qoption cg -Qiselect-T0"
            CXX_O_FLAG_NORM="-xO2 -Qoption cg -Qrm-s -Qoption cg -Qiselect-T0"
            CXX_O_FLAG_NONE=""
            ;;
        esac
    esac
    ;;
  CL )
    D_FLAG=
    C_O_FLAG_HI="-O2"
    C_O_FLAG_NORM="-O1"
    C_O_FLAG_NONE="-Od"
    CXX_O_FLAG_HI="$C_O_FLAG_HI"
    CXX_O_FLAG_NORM="$C_O_FLAG_NORM"
    CXX_O_FLAG_NONE="$C_O_FLAG_NONE"
    ;;
esac

AC_SUBST(C_O_FLAG_HI)
AC_SUBST(C_O_FLAG_NORM)
AC_SUBST(C_O_FLAG_NONE)
AC_SUBST(CXX_O_FLAG_HI)
AC_SUBST(CXX_O_FLAG_NORM)
AC_SUBST(CXX_O_FLAG_NONE)

###############################################################################
#
# Setup legacy vars/targets and new vars to deal with different debug levels.
#
case $DEBUG_LEVEL in
      release )
              HOTSPOT_DEBUG_LEVEL="product"
              HOTSPOT_EXPORT="product"
              ;;
      fastdebug ) 
              HOTSPOT_DEBUG_LEVEL="fastdebug"   
              HOTSPOT_EXPORT="fastdebug"
              CFLAGS="$CFLAGS $D_FLAG"
              JAVAC_FLAGS="$JAVAC_FLAGS -g"
              ;;
      slowdebug )
              HOTSPOT_DEBUG_LEVEL="jvmg"
              HOTSPOT_EXPORT="debug"
              CFLAGS="$CFLAGS $D_FLAG"
	      C_O_FLAG_HI="$C_O_FLAG_NONE"
	      C_O_FLAG_NORM="$C_O_FLAG_NONE"
	      CXX_O_FLAG_HI="$CXX_O_FLAG_NONE"
	      CXX_O_FLAG_NORM="$CXX_O_FLAG_NONE"
              JAVAC_FLAGS="$JAVAC_FLAGS -g"
              ;;
esac              

###############################################################################
#
# Generate the legacy makefile targets for hotspot.
# The hotspot api for selecting the build artifacts, really, needs to be improved.
#
HOTSPOT_TARGET=""

if test "x$JVM_VARIANT_SERVER" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL} "
fi

if test "x$JVM_VARIANT_CLIENT" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}1 "
fi

if test "x$JVM_VARIANT_KERNEL" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}kernel "
fi

if test "x$JVM_VARIANT_ZERO" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}zero "
fi

if test "x$JVM_VARIANT_ZEROSHARK" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}shark "
fi

HOTSPOT_TARGET="$HOTSPOT_TARGET docs export_$HOTSPOT_EXPORT"

###############################################################################
#
# Setup all directories for the subrepoes and the arguments to the sub makes.
#
LANGTOOLS_OUTPUTDIR="$OUTPUT_ROOT/langtools"
LANGTOOLS_DIST="$OUTPUT_ROOT/langtools/dist"
LANGTOOLS_MAKE_ARGS=""
AC_SUBST(LANGTOOLS_OUTPUTDIR)
AC_SUBST(LANGTOOLS_DIST)
AC_SUBST(LANGTOOLS_MAKE_ARGS)

CORBA_OUTPUTDIR="$OUTPUT_ROOT/corba"
CORBA_DIST="$OUTPUT_ROOT/corba/dist"
CORBA_MAKE_ARGS=""
AC_SUBST(CORBA_OUTPUTDIR)
AC_SUBST(CORBA_DIST)
AC_SUBST(CORBA_MAKE_ARGS)

JAXP_OUTPUTDIR="$OUTPUT_ROOT/jaxp"
JAXP_DIST="$OUTPUT_ROOT/jaxp/dist"
JAXP_MAKE_ARGS=""
AC_SUBST(JAXP_OUTPUTDIR)
AC_SUBST(JAXP_DIST)
AC_SUBST(JAXP_MAKE_ARGS)

JAXWS_OUTPUTDIR="$OUTPUT_ROOT/jaxws"
JAXWS_DIST="$OUTPUT_ROOT/jaxws/dist"
JAXWS_MAKE_ARGS=""
AC_SUBST(JAXWS_OUTPUTDIR)
AC_SUBST(JAXWS_DIST)
AC_SUBST(JAXWS_MAKE_ARGS)

HOTSPOT_OUTPUTDIR="$OUTPUT_ROOT/hotspot"
HOTSPOT_DIST="$OUTPUT_ROOT/hotspot/dist"
HOTSPOT_MAKE_ARGS="ALT_OUTPUTDIR=$HOTSPOT_OUTPUTDIR ALT_EXPORT_PATH=$HOTSPOT_DIST $HOTSPOT_TARGET"
AC_SUBST(HOTSPOT_OUTPUTDIR)
AC_SUBST(HOTSPOT_DIST)
AC_SUBST(HOTSPOT_MAKE_ARGS)

JDK_OUTPUTDIR="$OUTPUT_ROOT/jdk"
JDK_MAKE_ARGS="ALT_OUTPUTDIR=\"$OUTPUT_ROOT/jdk\""
AC_SUBST(JDK_OUTPUTDIR)
AC_SUBST(JDK_MAKE_ARGS)

IMAGES_OUTPUTDIR=$OUTPUT_ROOT/images
IMAGES_MAKE_ARGS="ALT_OUTPUTDIR=$OUTPUT_ROOT/jdk \
                  SHARE_SRC=$JDK_TOPDIR/src/share \
                  PLATFORM_SRC=$JDK_TOPDIR/src/$LEGACY_HOST_OS_API \
                  TEMPDIR=$IMAGES_OUTPUTDIR/tmp \
                  ABS_TEMPDIR=$IMAGES_OUTPUTDIR/tmp "
AC_SUBST(IMAGES_OUTPUTDIR)
AC_SUBST(IMAGES_MAKE_ARGS)

###############################################################################
#
# Now setup the CFLAGS and LDFLAGS for the JDK build.
# Later we will also have CFLAGS and LDFLAGS for the hotspot subrepo build.
#
case $COMPILER_NAME in
      gcc )
      	  CCXXFLAGS_JDK="$CCXXFLAGS_JDK -W -Wall -Wno-unused -Wno-parentheses -pipe -fno-omit-frame-pointer \
                          -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE"
          CFLAGS_JDK="$CFLAGS_JDK -fno-strict-aliasing"
          ;;
      ossc )
      	  CFLAGS_JDK="$CFLAGS_JDK -xc99=%none -xCC -errshort=tags -Xa REQUIRED -v -mt -norunpath -xnolib"
      	  CXXFLAGS_JDK="$CXXFLAGS_JDK -errtags=yes +w -mt REQUIRED -features=no%except -DCC_NOEX"
          ;;
      cl )
          CCXXFLAGS_JDK="$CCXXFLAGS_JDK -Zi -MD -Zc:wchar_t- -W3 -wd4800 \
               -D_STATIC_CPPLIB -D_DISABLE_DEPRECATE_STATIC_CPPLIB -DWIN32_LEAN_AND_MEAN \
	       -D_CRT_SECURE_NO_DEPRECATE -D_CRT_NONSTDC_NO_DEPRECATE \
	       -DWIN32 -DIAL"
          case $LEGACY_HOST_CPU1 in
              i?86 )
                  CCXXFLAGS_JDK="$CCXXFLAGS_JDK -D_X86_ -Dx86"
                  ;;
              amd64 )
                  CCXXFLAGS_JDK="$CCXXFLAGS_JDK -D_AMD64_ -Damd64"
                  ;;
          esac
          ;;
esac

CCXXFLAGS_JDK="$CCXXFLAGS_JDK $ADD_LP64"

# The package path is used only on macosx?
PACKAGE_PATH=/opt/local
AC_SUBST(PACKAGE_PATH)

# Sometimes we use a cpu dir (.../lib/amd64/server) 
# Sometimes not (.../lib/server) 
LIBARCHDIR="$LEGACY_HOST_CPU2/"
if test "x$ENDIAN" = xlittle; then
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -D_LITTLE_ENDIAN"
else
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -D_BIG_ENDIAN"
fi
if test "x$HOST_OS" = xlinux; then
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DLINUX"
fi
if test "x$HOST_OS" = xwindows; then
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DWINDOWS"
fi
if test "x$HOST_OS" = xsolaris; then
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DSOLARIS"
fi
if test "x$HOST_OS" = xmacosx; then
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DMACOSX -D_ALLBSD_SOURCE"
    LIBARCHDIR=""
fi
if test "x$HOST_OS" = xbsd; then
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DBSD -D_ALLBSD_SOURCE"
fi
if test "x$DEBUG_LEVEL" = xrelease; then
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DNDEBUG"
else
    CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DDEBUG"
fi

CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DARCH='\"$LEGACY_HOST_CPU1\"' -D$LEGACY_HOST_CPU1"
CCXXFLAGS_JDK="$CCXXFLAGS_JDK -DRELEASE='\"$RELEASE\"'"

CCXXFLAGS_JDK="$CCXXFLAGS_JDK \
        -I${JDK_OUTPUTDIR}/include \
        -I${JDK_OUTPUTDIR}/include/$PLATFORM \
        -I${JDK_TOPDIR}/src/share/javavm/export \
        -I${JDK_TOPDIR}/src/$LEGACY_HOST_OS_API/javavm/export \
        -I${JDK_TOPDIR}/src/share/native/common \
        -I${JDK_TOPDIR}/src/$LEGACY_HOST_OS_API/native/common"

# The shared libraries are compiled using the picflag.
CFLAGS_JDKLIB="$CCXXFLAGS_JDK $CFLAGS_JDK $PICFLAG $CFLAGS_JDKLIB_EXTRA"
CXXFLAGS_JDKLIB="$CCXXFLAGS_JDK $CXXFLAGS_JDK $PICFLAG"

# Executable flags
CFLAGS_JDKEXE="$CCXXFLAGS_JDK $CFLAGS_JDK"
CXXFLAGS_JDKEXE="$CCXXFLAGS_JDK $CXXFLAGS_JDK"

# Now this is odd. The JDK native libraries have to link against libjvm.so
# On 32-bit machines there is normally two distinct libjvm.so:s, client and server.
# Which should we link to? Are we lucky enough that the binary api to the libjvm.so library
# is identical for client and server? Yes. Which is picked at runtime (client or server)?
# Neither, since the chosen libjvm.so has already been loaded by the launcher, all the following
# libraries will link to whatever is in memory. Yuck. 
#
# Thus we offer the compiler to find libjvm.so first in server then in client. It works. Ugh.
if test "x$COMPILER_TYPE" = xCL; then
    LDFLAGS_JDKLIB="$LDFLAGS -libpath:${JDK_OUTPUTDIR}/lib jvm.lib java.lib"
    LDFLAGS_JDKLIB_SUFFIX=""
    if test "x$HOST_CPU_BITS" = "x64"; then
        LDFLAGS_JDKEXE="$CFLAGS ${JDK_OUTPUTDIR}/tmp/java/jli/obj64/jli.lib"
    else
        LDFLAGS_JDKEXE="$CFLAGS ${JDK_OUTPUTDIR}/tmp/java/jli/obj/jli.lib"
    fi
    LDFLAGS_JDKEXE_SUFFIX=""
else
    # If this is a --hash-style=gnu system, use --hash-style=both, why?
    HAS_GNU_HASH=`$CC -dumpspecs 2>/dev/null | $GREP 'hash-style=gnu'`
    if test -n "$HAS_GNU_HASH"; then
        # And since we now know that the linker is gnu, then add -z defs, to forbid
        # undefined symbols in object files.
        LDFLAGS="$LDFLAGS -Xlinker --hash-style=both -Xlinker -z -Xlinker defs"
        if test "x$DEBUG_LEVEL" == "xrelease"; then
            # When building release libraries, tell the linker optimize them.
            # Should this be supplied to the OSS linker as well?
            LDFLAGS="$LDFLAGS -Xlinker -O1"
        fi
    fi

    LDFLAGS_JDKLIB="$LDFLAGS $SHARED_LIBRARY_FLAGS -L${JDK_OUTPUTDIR}/lib/${LIBARCHDIR}server \
                    -L${JDK_OUTPUTDIR}/lib/${LIBARCHDIR}client \
  	            -L${JDK_OUTPUTDIR}/lib/${LIBARCHDIR}" 
    LDFLAGS_JDKLIB_SUFFIX="-ljvm -ljava"
    if test "x$COMPILER_NAME" = xossc; then
        LDFLAGS_JDKLIB_SUFFIX="$LDFLAGS_JDKLIB_SUFFIX -lc"
    fi

    # Only the jli library is explicitly linked when the launchers are built.
    # The libjvm is then dynamically loaded/linked by the launcher.
    LDFLAGS_JDKEXE="$LDFLAGS -L${JDK_OUTPUTDIR}/lib/${LIBARCHDIR}jli"
    LDFLAGS_JDKEXE_SUFFIX="-ljli"
fi

                
AC_SUBST(CFLAGS_JDKLIB)
AC_SUBST(CFLAGS_JDKEXE)

AC_SUBST(CXXFLAGS_JDKLIB)
AC_SUBST(CXXFLAGS_JDKEXE)

AC_SUBST(LDFLAGS_JDKLIB)
AC_SUBST(LDFLAGS_JDKEXE)
AC_SUBST(LDFLAGS_JDKLIB_SUFFIX)
AC_SUBST(LDFLAGS_JDKEXE_SUFFIX)


###############################################################################
#
# statically link libstdc++ before C++ ABI is stablized on Linux unless 
# dynamic build is configured on command line.
#
AC_ARG_ENABLE([static-link-stdc++], [AS_HELP_STRING([--disable-static-link-stdc++],
	[disable static linking of the C++ runtime on Linux @<:@enabled@:>@])],,
	[
		enable_static_link_stdc__=yes
    ])

if test "x$HOST_OS" = xlinux; then
    # Test if -lstdc++ works.
    AC_MSG_CHECKING([if dynamic link of stdc++ is possible])
    AC_LANG_PUSH(C++)
    OLD_CXXFLAGS="$CXXFLAGS"
    CXXFLAGS="$CXXFLAGS -lstdc++"
    AC_TRY_LINK([], [return 0;],
            has_dynamic_libstdcxx=yes,
            has_dynamic_libstdcxx=no)
    CXXFLAGS="$OLD_CXXFLAGS"
    AC_LANG_POP(C++)
    AC_MSG_RESULT([$has_dynamic_libstdcxx])

    # Test if stdc++ can be linked statically.
    AC_MSG_CHECKING([if static link of stdc++ is possible])
    STATIC_STDCXX_FLAGS="-Wl,-Bstatic -lstdc++ -lgcc -Wl,-Bdynamic"
    AC_LANG_PUSH(C++)
    OLD_LIBS="$LIBS"
    OLD_CXX="$CXX"
    LIBS="$STATIC_STDCXX_FLAGS"
    CXX="$CC"                       
    AC_TRY_LINK([], [return 0;],
            has_static_libstdcxx=yes,
            has_static_libstdcxx=no)
    LIBS="$OLD_LIBS"
    CXX="$OLD_CXX"
    AC_LANG_POP(C++)
    AC_MSG_RESULT([$has_static_libstdcxx])

    if test "x$has_static_libcxx" = xno && test "x$has_dynamic_libcxx" = xno; then
        AC_ERROR([I cannot link to stdc++! Neither dynamically nor statically.])
    fi

    if test "x$enable_static_link_stdc__" = xyes && test "x$has_static_libstdcxx" = xno; then
        AC_MSG_NOTICE([Static linking of libstdc++ was not possible reverting to dynamic linking.])
        enable_static_link_stdc__=no
    fi

    if test "x$enable_static_link_stdc__" = xno && test "x$has_dynamic_libstdcxx" = xno; then
        AC_MSG_NOTICE([Dynamic linking of libstdc++ was not possible reverting to static linking.])
        enable_static_link_stdc__=yes
    fi

    AC_MSG_CHECKING([how to link with libstdc++])
    if test "x$enable_static_link_stdc__" = xyes; then
        LIBCXX="$LIBCXX $STATIC_STDCXX_FLAGS"
        LDCXX="$CC"
        AC_MSG_RESULT([static])
    else
        LIBCXX="$LIBCXX -lstdc++"
        LDCXX="$CXX"
        AC_MSG_RESULT([dynamic])
    fi
fi

###############################################################################
#
# Could someone enlighten this configure script with a comment about libCrun?
# The LEGACY_HOST_CPU3 is the setting for ISA_DIR.
#
if test "x$HOST_OS" = xsolaris; then
    LIBCXX="$LIBCXX /usr/lib${LEGACY_HOST_CPU3}/libCrun.so.1"
fi

AC_SUBST(LIBCXX)

###############################################################################
#
# Misc
#

# Control wether Hotspot runs Queens test after build.
AC_ARG_ENABLE([hotspot-test-in-build], [AS_HELP_STRING([--enable-hotspot-test-in-build],
	[enable running of Queens test after Hotspot build (not yet available) @<:@disabled@:>@])],,
    [enable_hotspot_test_in_build=no])
if test "x$enable_hotspot_test_in_build" = "xyes"; then
    TEST_IN_BUILD=true
else
    TEST_IN_BUILD=false
fi
AC_SUBST(TEST_IN_BUILD)

###############################################################################
#
# A helpful message at the end of the configure run.
#
if test "x$CCACHE_FOUND" = x && test "x$GCC" = xyes; then
    help_on_build_dependency ccache
    
    printf "\nTip of the day:\nYou should really install ccache version 3.1.4 or newer.\n"
    printf "It gives a tremendous speedup for C++ recompilations with precompiled headers!\n"
fi    

if test "x$CCACHE_FOUND" != x && test "x$HAS_GOOD_CCACHE" = x; then
    printf "You have a ccache installed, but it is a version prior to 3.1.4. Try upgrading.\n"
fi

AC_OUTPUT
