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

AC_DEFUN([PLATFORM_EXTRACT_TARGET_AND_BUILD_AND_LEGACY_VARS],
[
    # Expects $host_os $host_cpu $build_os and $build_cpu
    # and $with_target_bits to have been setup!
    #
    # Translate the standard triplet(quadruplet) definition
    # of the target/build system into
    # OPENJDK_TARGET_OS=aix,bsd,hpux,linux,macosx,solaris,windows
    # OPENJDK_TARGET_OS_FAMILY=bsd,gnu,sysv,win32,wince
    # OPENJDK_TARGET_OS_API=posix,winapi
    # 
    # OPENJDK_TARGET_CPU=ia32,x64,sparc,sparcv9,arm,arm64,ppc,ppc64
    # OPENJDK_TARGET_CPU_ARCH=x86,sparc,pcc,arm
    # OPENJDK_TARGET_CPU_BITS=32,64
    # OPENJDK_TARGET_CPU_ENDIAN=big,little
    #
    # The same values are setup for BUILD_...
    # 
    # And the legacy variables, for controlling the old makefiles.
    # LEGACY_OPENJDK_TARGET_CPU1=i586,amd64/x86_64,sparc,sparcv9,arm,arm64...
    # LEGACY_OPENJDK_TARGET_CPU2=i386,amd64,sparc,sparcv9,arm,arm64...
    # LEGACY_OPENJDK_TARGET_CPU3=sparcv9,amd64 (but only on solaris)
    # LEGACY_OPENJDK_TARGET_OS_API=solaris,windows
    #
    # We also copy the autoconf trip/quadruplet
    # verbatim to OPENJDK_TARGET_SYSTEM (from the autoconf "host") and OPENJDK_BUILD_SYSTEM
    OPENJDK_TARGET_SYSTEM="$host"
    OPENJDK_BUILD_SYSTEM="$build"
    AC_SUBST(OPENJDK_TARGET_SYSTEM)
    AC_SUBST(OPENJDK_BUILD_SYSTEM)
    
    PLATFORM_EXTRACT_VARS_FROM_OS_TO(OPENJDK_TARGET,$host_os)
    PLATFORM_EXTRACT_VARS_FROM_CPU_TO(OPENJDK_TARGET,$host_cpu)

    PLATFORM_EXTRACT_VARS_FROM_OS_TO(OPENJDK_BUILD,$build_os)
    PLATFORM_EXTRACT_VARS_FROM_CPU_TO(OPENJDK_BUILD,$build_cpu)

    if test "x$OPENJDK_TARGET_OS" != xsolaris; then
        LEGACY_OPENJDK_TARGET_CPU3=""
        LEGACY_OPENJDK_BUILD_CPU3=""
    fi

    # On MacOSX and MacOSX only, we have a different name for the x64 CPU in ARCH (LEGACY_OPENJDK_TARGET_CPU1) ...
    if test "x$OPENJDK_TARGET_OS" = xmacosx && test "x$OPENJDK_TARGET_CPU" = xx64; then
        LEGACY_OPENJDK_TARGET_CPU1="x86_64"
    fi

    PLATFORM_SET_RELEASE_FILE_OS_VALUES
])

AC_DEFUN([PLATFORM_EXTRACT_VARS_FROM_OS_TO],
[
    PLATFORM_EXTRACT_VARS_FROM_OS($2)
    $1_OS="$VAR_OS"
    $1_OS_FAMILY="$VAR_OS_FAMILY"
    $1_OS_API="$VAR_OS_API"

    AC_SUBST($1_OS)
    AC_SUBST($1_OS_FAMILY)
    AC_SUBST($1_OS_API)

    if test "x$$1_OS_API" = xposix; then
        LEGACY_$1_OS_API="solaris"
    fi
    if test "x$$1_OS_API" = xwinapi; then
        LEGACY_$1_OS_API="windows"
    fi
    AC_SUBST(LEGACY_$1_OS_API)    
])

AC_DEFUN([PLATFORM_EXTRACT_VARS_FROM_CPU_TO],
[
    PLATFORM_EXTRACT_VARS_FROM_CPU($2)
    $1_CPU="$VAR_CPU"
    $1_CPU_ARCH="$VAR_CPU_ARCH"
    $1_CPU_BITS="$VAR_CPU_BITS"
    $1_CPU_ENDIAN="$VAR_CPU_ENDIAN"

    AC_SUBST($1_CPU)
    AC_SUBST($1_CPU_ARCH)
    AC_SUBST($1_CPU_BITS)
    AC_SUBST($1_CPU_ENDIAN)
    
    # Also store the legacy naming of the cpu.
    # Ie i586 and amd64 instead of ia32 and x64
    LEGACY_$1_CPU1="$VAR_LEGACY_CPU"
    AC_SUBST(LEGACY_$1_CPU1)

    # And the second legacy naming of the cpu.
    # Ie i386 and amd64 instead of ia32 and x64.
    LEGACY_$1_CPU2="$LEGACY_$1_CPU1"
    if test "x$LEGACY_$1_CPU1" = xi586; then 
        LEGACY_$1_CPU2=i386
    fi
    AC_SUBST(LEGACY_$1_CPU2)

    # And the third legacy naming of the cpu.
    # Ie only amd64 or sparcv9, used for the ISA_DIR on Solaris.
    LEGACY_$1_CPU3=""
    if test "x$$1_CPU" = xx64; then 
        LEGACY_$1_CPU3=amd64
    fi
    if test "x$$1_CPU" = xsparcv9; then 
        LEGACY_$1_CPU3=sparcv9
    fi
    AC_SUBST(LEGACY_$1_CPU3)
])

AC_DEFUN([PLATFORM_EXTRACT_VARS_FROM_CPU],
[
  # First argument is the cpu name from the trip/quad
  case "$1" in
    x86_64)
      VAR_CPU=x64
      VAR_CPU_ARCH=x86
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=little
      VAR_LEGACY_CPU=amd64
      ;;
    i?86)
      VAR_CPU=ia32
      VAR_CPU_ARCH=x86
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=little
      VAR_LEGACY_CPU=i586
      ;;
    alpha*)
      VAR_CPU=alpha
      VAR_CPU_ARCH=alpha
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=alpha
      ;;
    arm*)
      VAR_CPU=arm
      VAR_CPU_ARCH=arm
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=little
      VAR_LEGACY_CPU=arm
      ;;
    mips)
      VAR_CPU=mips
      VAR_CPU_ARCH=mips
      VAR_CPU_BITS=woot
      VAR_CPU_ENDIAN=woot
      VAR_LEGACY_CPU=mips
       ;;
    mipsel)
      VAR_CPU=mipsel
      VAR_CPU_ARCH=mips
      VAR_CPU_BITS=woot
      VAR_CPU_ENDIAN=woot
      VAR_LEGACY_CPU=mipsel
       ;;
    powerpc)
      VAR_CPU=ppc
      VAR_CPU_ARCH=ppc
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=ppc
       ;;
    powerpc64)
      VAR_CPU=ppc64
      VAR_CPU_ARCH=ppc
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=ppc64
       ;;
    sparc)
      VAR_CPU=sparc
      VAR_CPU_ARCH=sparc
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=sparc
       ;;
    sparc64)
      VAR_CPU=sparcv9
      VAR_CPU_ARCH=sparc
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=sparcv9
       ;;
    s390)
      VAR_CPU=s390
      VAR_CPU_ARCH=s390
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=woot
      VAR_LEGACY_CPU=s390
      VAR_LEGACY_CPU=s390
       ;;
    s390x)
      VAR_CPU=s390x
      VAR_CPU_ARCH=s390
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=woot
      VAR_LEGACY_CPU=s390x
       ;;
    *)
      AC_MSG_ERROR([unsupported cpu $1])
      ;;
  esac

  # Workaround cygwin not knowing about 64 bit.
  if test "x$VAR_OS" = "xwindows"; then
      if test "x$PROCESSOR_IDENTIFIER" != "x"; then
          PROC_ARCH=`echo $PROCESSOR_IDENTIFIER | $CUT -f1 -d' '`
          case "$PROC_ARCH" in
            intel64|Intel64|INTEL64|em64t|EM64T|amd64|AMD64|8664|x86_64)
              VAR_CPU=x64
              VAR_CPU_BITS=64
              VAR_LEGACY_CPU=amd64
              ;;
          esac
      fi
  fi

  # on solaris x86...default seems to be 32-bit
  if test "x$VAR_OS" = "xsolaris" && \
     test "x$with_target_bits" = "x" && \
     test "x$VAR_CPU_ARCH" = "xx86"
  then
      with_target_bits=32
  fi

  if test "x$VAR_CPU_ARCH" = "xx86"; then
      if test "x$with_target_bits" = "x64"; then
          VAR_CPU=x64
          VAR_CPU_BITS=64
          VAR_LEGACY_CPU=amd64
      fi
      if test "x$with_target_bits" = "x32"; then
          VAR_CPU=ia32
          VAR_CPU_BITS=32
          VAR_LEGACY_CPU=i586
      fi
  fi 

  if test "x$VAR_CPU_ARCH" = "xsparc"; then
      if test "x$with_target_bits" = "x64"; then
          VAR_CPU=sparcv9
          VAR_CPU_BITS=64
          VAR_LEGACY_CPU=sparcv9
      fi
  fi 
])

AC_DEFUN([PLATFORM_EXTRACT_VARS_FROM_OS],
[
  case "$1" in
    *linux*)
      VAR_OS=linux
      VAR_OS_API=posix
      VAR_OS_FAMILY=gnu
      ;;
    *solaris*)
      VAR_OS=solaris
      VAR_OS_API=posix
      VAR_OS_FAMILY=sysv
      ;;
    *darwin*)
      VAR_OS=macosx
      VAR_OS_API=posix
      VAR_OS_FAMILY=bsd
      ;;
    *bsd*)
      VAR_OS=bsd
      VAR_OS_API=posix
      VAR_OS_FAMILY=bsd
      ;;
    *cygwin*|*windows*)
      VAR_OS=windows
      VAR_OS_API=winapi
      VAR_OS_FAMILY=windows
      ;;
    *)
      AC_MSG_ERROR([unsupported operating system $1])
      ;;
  esac
])

AC_DEFUN([PLATFORM_SET_RELEASE_FILE_OS_VALUES],
[
    if test "x$OPENJDK_TARGET_OS" = "xsolaris"; then
       REQUIRED_OS_NAME=SunOS
       REQUIRED_OS_VERSION=5.10
    fi
    if test "x$OPENJDK_TARGET_OS" = "xlinux"; then
       REQUIRED_OS_NAME=Linux
       REQUIRED_OS_VERSION=2.6
    fi
    if test "x$OPENJDK_TARGET_OS" = "xwindows"; then
        REQUIRED_OS_NAME=Windows
        REQUIRED_OS_VERSION=5.1
    fi
    if test "x$OPENJDK_TARGET_OS" = "xmacosx"; then
        REQUIRED_OS_NAME=Darwin
        REQUIRED_OS_VERSION=11.2
    fi

    AC_SUBST(REQUIRED_OS_NAME)
    AC_SUBST(REQUIRED_OS_VERSION)
])

#%%% Build and target systems %%%
AC_DEFUN_ONCE([PLATFORM_SETUP_OPENJDK_BUILD_AND_TARGET],
[
# Figure out the build and target systems. # Note that in autoconf terminology, "build" is obvious, but "target"
# is confusing; it assumes you are cross-compiling a cross-compiler (!)  and "target" is thus the target of the
# product you're building. The target of this build is called "host". Since this is confusing to most people, we
# have not adopted that system, but use "target" as the platform we are building for. In some places though we need
# to use the configure naming style.
AC_CANONICAL_BUILD
AC_CANONICAL_HOST
AC_CANONICAL_TARGET

AC_ARG_WITH(target-bits, [AS_HELP_STRING([--with-target-bits],
   [build 32-bit or 64-bit binaries (for platforms that support it), e.g. --with-target-bits=32 @<:@guessed@:>@])])

if test "x$with_target_bits" != x && \
   test "x$with_target_bits" != x32 && \
   test "x$with_target_bits" != x64 ; then
    AC_MSG_ERROR([--with-target-bits can only be 32 or 64, you specified $with_target_bits!])
fi
# Translate the standard cpu-vendor-kernel-os quadruplets into
# the new TARGET_.... and BUILD_... and the legacy names used by
# the openjdk build.
# It uses $host_os $host_cpu $build_os $build_cpu and $with_target_bits
PLATFORM_EXTRACT_TARGET_AND_BUILD_AND_LEGACY_VARS

# The LEGACY_OPENJDK_TARGET_CPU3 is the setting for ISA_DIR.
if test "x$LEGACY_OPENJDK_TARGET_CPU3" != x; then
   LEGACY_OPENJDK_TARGET_CPU3="/${LEGACY_OPENJDK_TARGET_CPU3}"
fi

# Now the following vars are defined.
# OPENJDK_TARGET_OS=aix,bsd,hpux,linux,macosx,solaris,windows
# OPENJDK_TARGET_OS_FAMILY=bsd,gnu,sysv,win32,wince
# OPENJDK_TARGET_OS_API=posix,winapi
#
# OPENJDK_TARGET_CPU=ia32,x64,sparc,sparcv9,arm,arm64,ppc,ppc64
# OPENJDK_TARGET_CPU_ARCH=x86,sparc,pcc,arm
# OPENJDK_TARGET_CPU_BITS=32,64
# OPENJDK_TARGET_CPU_ENDIAN=big,little
#
# There is also a:
# LEGACY_OPENJDK_TARGET_CPU1=i586,amd64,....  # used to set the old var ARCH
# LEGACY_OPENJDK_TARGET_CPU2=i386,amd64,.... # used to set the old var LIBARCH
# LEGACY_OPENJDK_TARGET_CPU3=only sparcv9,amd64 # used to set the ISA_DIR on Solaris
# There was also a BUILDARCH that had i486,amd64,... but we do not use that
# in the new build.
# LEGACY_OPENJDK_TARGET_OS_API=solaris,windows # used to select source roots
])

AC_DEFUN_ONCE([PLATFORM_SETUP_OPENJDK_BUILD_OS_VERSION],
[
###############################################################################

# Note that this is the build platform OS version!

OS_VERSION="`uname -r | ${SED} 's!\.! !g' | ${SED} 's!-! !g'`"
OS_VERSION_MAJOR="`${ECHO} ${OS_VERSION} | ${CUT} -f 1 -d ' '`"
OS_VERSION_MINOR="`${ECHO} ${OS_VERSION} | ${CUT} -f 2 -d ' '`"
OS_VERSION_MICRO="`${ECHO} ${OS_VERSION} | ${CUT} -f 3 -d ' '`"
AC_SUBST(OS_VERSION_MAJOR)
AC_SUBST(OS_VERSION_MINOR)
AC_SUBST(OS_VERSION_MICRO)
])

AC_DEFUN_ONCE([PLATFORM_TEST_OPENJDK_TARGET_BITS],
[
###############################################################################
#
# Now we check if libjvm.so will use 32 or 64 bit pointers for the C/C++ code.
# (The JVM can use 32 or 64 bit Java pointers but that decision
# is made at runtime.)
#
AC_LANG_PUSH(C++)
OLD_CXXFLAGS="$CXXFLAGS"
if test "x$OPENJDK_TARGET_OS" != xwindows && test "x$with_target_bits" != x; then
	CXXFLAGS="-m${with_target_bits} $CXXFLAGS"
fi
AC_CHECK_SIZEOF([int *], [1111])
CXXFLAGS="$OLD_CXXFLAGS"
AC_LANG_POP(C++)

# keep track of c/cxx flags that we added outselves...
#   to prevent emitting warning...
ADDED_CFLAGS=
ADDED_CXXFLAGS=
ADDED_LDFLAGS=

if test "x$ac_cv_sizeof_int_p" = x0; then 
    # The test failed, lets pick the assumed value.
    ARCH_DATA_MODEL=$OPENJDK_TARGET_CPU_BITS
else
    ARCH_DATA_MODEL=`expr 8 \* $ac_cv_sizeof_int_p`

    if test "x$OPENJDK_TARGET_OS" != xwindows && test "x$with_target_bits" != x; then
       ADDED_CFLAGS=" -m${with_target_bits}"
       ADDED_CXXFLAGS=" -m${with_target_bits}"
       ADDED_LDFLAGS=" -m${with_target_bits}"

       CFLAGS="${CFLAGS}${ADDED_CFLAGS}"
       CXXFLAGS="${CXXFLAGS}${ADDED_CXXFLAGS}"
       LDFLAGS="${LDFLAGS}${ADDED_LDFLAGS}"

       CFLAGS_JDK="${CFLAGS_JDK}${ADDED_CFLAGS}"
       CXXFLAGS_JDK="${CXXFLAGS_JDK}${ADDED_CXXFLAGS}"
       LDFLAGS_JDK="${LDFLAGS_JDK}${ADDED_LDFLAGS}"
    fi
fi

if test "x$ARCH_DATA_MODEL" = x64; then
    A_LP64="LP64:="
    ADD_LP64="-D_LP64=1"
fi
AC_MSG_CHECKING([for target address size])
AC_MSG_RESULT([$ARCH_DATA_MODEL bits])
AC_SUBST(LP64,$A_LP64)
AC_SUBST(ARCH_DATA_MODEL)

if test "x$ARCH_DATA_MODEL" != "x$OPENJDK_TARGET_CPU_BITS"; then
    AC_MSG_ERROR([The tested number of bits in the target ($ARCH_DATA_MODEL) differs from the number of bits expected to be found in the target ($OPENJDK_TARGET_CPU_BITS)])
fi

#
# NOTE: check for -mstackrealign needs to be below potential addition of -m32
#
if test "x$OPENJDK_TARGET_CPU_BITS" = x32 && test "x$OPENJDK_TARGET_OS" = xmacosx; then
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
	                AC_MSG_ERROR([The selected compiler $CXX does not support -mstackrealign! Try to put another compiler in the path.])
	           ])
fi
])

AC_DEFUN_ONCE([PLATFORM_SETUP_OPENJDK_TARGET_ENDIANNESS],
[
###############################################################################
#
# Is the target little of big endian?
#
AC_C_BIGENDIAN([ENDIAN="big"],[ENDIAN="little"],[ENDIAN="unknown"],[ENDIAN="universal_endianness"])

if test "x$ENDIAN" = xuniversal_endianness; then
    AC_MSG_ERROR([Building with both big and little endianness is not supported])
fi
if test "x$ENDIAN" = xunknown; then
    ENDIAN="$OPENJDK_TARGET_CPU_ENDIAN"
fi
if test "x$ENDIAN" != "x$OPENJDK_TARGET_CPU_ENDIAN"; then
    AC_MSG_WARN([The tested endian in the target ($ENDIAN) differs from the endian expected to be found in the target ($OPENJDK_TARGET_CPU_ENDIAN)])
    ENDIAN="$OPENJDK_TARGET_CPU_ENDIAN"
fi
AC_SUBST(ENDIAN)
])

AC_DEFUN_ONCE([PLATFORM_SETUP_OPENJDK_TARGET_ISADIR],
[
###############################################################################
#
# Could someone enlighten this configure script with a comment about libCrun?
#
#
])
