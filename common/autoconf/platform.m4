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

# Support macro for PLATFORM_EXTRACT_TARGET_AND_BUILD.
# Converts autoconf style CPU name to OpenJDK style, into
# VAR_CPU, VAR_CPU_ARCH, VAR_CPU_BITS and VAR_CPU_ENDIAN.
AC_DEFUN([PLATFORM_EXTRACT_VARS_FROM_CPU],
[
  # First argument is the cpu name from the trip/quad
  case "$1" in
    x86_64)
      VAR_CPU=x86_64
      VAR_CPU_ARCH=x86
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=little
      ;;
    i?86)
      VAR_CPU=x86
      VAR_CPU_ARCH=x86
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=little
      ;;
    arm*)
      VAR_CPU=arm
      VAR_CPU_ARCH=arm
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=little
      ;;
    aarch64)
      VAR_CPU=aarch64
      VAR_CPU_ARCH=aarch64
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=little
      ;;
    powerpc)
      VAR_CPU=ppc
      VAR_CPU_ARCH=ppc
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=big
      ;;
    powerpc64)
      VAR_CPU=ppc64
      VAR_CPU_ARCH=ppc
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=big
      ;;
    powerpc64le)
      VAR_CPU=ppc64le
      VAR_CPU_ARCH=ppc
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=little
      ;;
    s390)
      VAR_CPU=s390
      VAR_CPU_ARCH=s390
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=big
      ;;
    s390x)
      VAR_CPU=s390x
      VAR_CPU_ARCH=s390
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=big
      ;;
    sparc)
      VAR_CPU=sparc
      VAR_CPU_ARCH=sparc
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=big
      ;;
    sparcv9|sparc64)
      VAR_CPU=sparcv9
      VAR_CPU_ARCH=sparc
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=big
      ;;
    *)
      AC_MSG_ERROR([unsupported cpu $1])
      ;;
  esac
])

# Support macro for PLATFORM_EXTRACT_TARGET_AND_BUILD.
# Converts autoconf style OS name to OpenJDK style, into
# VAR_OS, VAR_OS_TYPE and VAR_OS_ENV.
AC_DEFUN([PLATFORM_EXTRACT_VARS_FROM_OS],
[
  case "$1" in
    *linux*)
      VAR_OS=linux
      VAR_OS_TYPE=unix
      ;;
    *solaris*)
      VAR_OS=solaris
      VAR_OS_TYPE=unix
      ;;
    *darwin*)
      VAR_OS=macosx
      VAR_OS_TYPE=unix
      ;;
    *bsd*)
      VAR_OS=bsd
      VAR_OS_TYPE=unix
      ;;
    *cygwin*)
      VAR_OS=windows
      VAR_OS_ENV=windows.cygwin
      ;;
    *mingw*)
      VAR_OS=windows
      VAR_OS_ENV=windows.msys
      ;;
    *aix*)
      VAR_OS=aix
      VAR_OS_TYPE=unix
      ;;
    *)
      AC_MSG_ERROR([unsupported operating system $1])
      ;;
  esac
])

# Expects $host_os $host_cpu $build_os and $build_cpu
# and $with_target_bits to have been setup!
#
# Translate the standard triplet(quadruplet) definition
# of the target/build system into OPENJDK_TARGET_OS, OPENJDK_TARGET_CPU,
# OPENJDK_BUILD_OS, etc.
AC_DEFUN([PLATFORM_EXTRACT_TARGET_AND_BUILD],
[
  # Copy the autoconf trip/quadruplet verbatim to OPENJDK_TARGET_AUTOCONF_NAME
  # (from the autoconf "host") and OPENJDK_BUILD_AUTOCONF_NAME
  # Note that we might later on rewrite e.g. OPENJDK_TARGET_CPU due to reduced build,
  # but this will not change the value of OPENJDK_TARGET_AUTOCONF_NAME.
  OPENJDK_TARGET_AUTOCONF_NAME="$host"
  OPENJDK_BUILD_AUTOCONF_NAME="$build"
  AC_SUBST(OPENJDK_TARGET_AUTOCONF_NAME)
  AC_SUBST(OPENJDK_BUILD_AUTOCONF_NAME)

  # Convert the autoconf OS/CPU value to our own data, into the VAR_OS/CPU variables.
  PLATFORM_EXTRACT_VARS_FROM_OS($build_os)
  PLATFORM_EXTRACT_VARS_FROM_CPU($build_cpu)
  # ..and setup our own variables. (Do this explicitely to facilitate searching)
  OPENJDK_BUILD_OS="$VAR_OS"
  if test "x$VAR_OS_TYPE" != x; then
    OPENJDK_BUILD_OS_TYPE="$VAR_OS_TYPE"
  else
    OPENJDK_BUILD_OS_TYPE="$VAR_OS"
  fi
  if test "x$VAR_OS_ENV" != x; then
    OPENJDK_BUILD_OS_ENV="$VAR_OS_ENV"
  else
    OPENJDK_BUILD_OS_ENV="$VAR_OS"
  fi
  OPENJDK_BUILD_CPU="$VAR_CPU"
  OPENJDK_BUILD_CPU_ARCH="$VAR_CPU_ARCH"
  OPENJDK_BUILD_CPU_BITS="$VAR_CPU_BITS"
  OPENJDK_BUILD_CPU_ENDIAN="$VAR_CPU_ENDIAN"
  AC_SUBST(OPENJDK_BUILD_OS)
  AC_SUBST(OPENJDK_BUILD_OS_TYPE)
  AC_SUBST(OPENJDK_BUILD_OS_ENV)
  AC_SUBST(OPENJDK_BUILD_CPU)
  AC_SUBST(OPENJDK_BUILD_CPU_ARCH)
  AC_SUBST(OPENJDK_BUILD_CPU_BITS)
  AC_SUBST(OPENJDK_BUILD_CPU_ENDIAN)

  AC_MSG_CHECKING([openjdk-build os-cpu])
  AC_MSG_RESULT([$OPENJDK_BUILD_OS-$OPENJDK_BUILD_CPU])

  # Convert the autoconf OS/CPU value to our own data, into the VAR_OS/CPU variables.
  PLATFORM_EXTRACT_VARS_FROM_OS($host_os)
  PLATFORM_EXTRACT_VARS_FROM_CPU($host_cpu)
  # ... and setup our own variables. (Do this explicitely to facilitate searching)
  OPENJDK_TARGET_OS="$VAR_OS"
  if test "x$VAR_OS_TYPE" != x; then
    OPENJDK_TARGET_OS_TYPE="$VAR_OS_TYPE"
  else
    OPENJDK_TARGET_OS_TYPE="$VAR_OS"
  fi
  if test "x$VAR_OS_ENV" != x; then
    OPENJDK_TARGET_OS_ENV="$VAR_OS_ENV"
  else
    OPENJDK_TARGET_OS_ENV="$VAR_OS"
  fi
  OPENJDK_TARGET_CPU="$VAR_CPU"
  OPENJDK_TARGET_CPU_ARCH="$VAR_CPU_ARCH"
  OPENJDK_TARGET_CPU_BITS="$VAR_CPU_BITS"
  OPENJDK_TARGET_CPU_ENDIAN="$VAR_CPU_ENDIAN"
  AC_SUBST(OPENJDK_TARGET_OS)
  AC_SUBST(OPENJDK_TARGET_OS_TYPE)
  AC_SUBST(OPENJDK_TARGET_OS_ENV)
  AC_SUBST(OPENJDK_TARGET_CPU)
  AC_SUBST(OPENJDK_TARGET_CPU_ARCH)
  AC_SUBST(OPENJDK_TARGET_CPU_BITS)
  AC_SUBST(OPENJDK_TARGET_CPU_ENDIAN)

  AC_MSG_CHECKING([openjdk-target os-cpu])
  AC_MSG_RESULT([$OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU])
])

# Check if a reduced build (32-bit on 64-bit platforms) is requested, and modify behaviour
# accordingly. Must be done after setting up build and target system, but before
# doing anything else with these values.
AC_DEFUN([PLATFORM_SETUP_TARGET_CPU_BITS],
[
  AC_ARG_WITH(target-bits, [AS_HELP_STRING([--with-target-bits],
       [build 32-bit or 64-bit binaries (for platforms that support it), e.g. --with-target-bits=32 @<:@guessed@:>@])])

  # We have three types of compiles:
  # native  == normal compilation, target system == build system
  # cross   == traditional cross compilation, target system != build system; special toolchain needed
  # reduced == using native compilers, but with special flags (e.g. -m32) to produce 32-bit builds on 64-bit machines
  #
  if test "x$OPENJDK_BUILD_AUTOCONF_NAME" != "x$OPENJDK_TARGET_AUTOCONF_NAME"; then
    # We're doing a proper cross-compilation
    COMPILE_TYPE="cross"
  else
    COMPILE_TYPE="native"
  fi

  if test "x$with_target_bits" != x; then
    if test "x$COMPILE_TYPE" = "xcross"; then
      AC_MSG_ERROR([It is not possible to combine --with-target-bits=X and proper cross-compilation. Choose either.])
    fi

    if test "x$with_target_bits" = x32 && test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
      # A reduced build is requested
      COMPILE_TYPE="reduced"
      OPENJDK_TARGET_CPU_BITS=32
      if test "x$OPENJDK_TARGET_CPU_ARCH" = "xx86"; then
        OPENJDK_TARGET_CPU=x86
      elif test "x$OPENJDK_TARGET_CPU_ARCH" = "xsparc"; then
        OPENJDK_TARGET_CPU=sparc
      else
        AC_MSG_ERROR([Reduced build (--with-target-bits=32) is only supported on x86_64 and sparcv9])
      fi
    elif test "x$with_target_bits" = x64 && test "x$OPENJDK_TARGET_CPU_BITS" = x32; then
      AC_MSG_ERROR([It is not possible to use --with-target-bits=64 on a 32 bit system. Use proper cross-compilation instead.])
    elif test "x$with_target_bits" = "x$OPENJDK_TARGET_CPU_BITS"; then
      AC_MSG_NOTICE([--with-target-bits are set to build platform address size; argument has no meaning])
    else
      AC_MSG_ERROR([--with-target-bits can only be 32 or 64, you specified $with_target_bits!])
    fi
  fi
  AC_SUBST(COMPILE_TYPE)

  AC_MSG_CHECKING([compilation type])
  AC_MSG_RESULT([$COMPILE_TYPE])
])

# Setup the legacy variables, for controlling the old makefiles.
#
AC_DEFUN([PLATFORM_SETUP_LEGACY_VARS],
[
  # Also store the legacy naming of the cpu.
  # Ie i586 and amd64 instead of x86 and x86_64
  OPENJDK_TARGET_CPU_LEGACY="$OPENJDK_TARGET_CPU"
  if test "x$OPENJDK_TARGET_CPU" = xx86; then
    OPENJDK_TARGET_CPU_LEGACY="i586"
  elif test "x$OPENJDK_TARGET_OS" != xmacosx && test "x$OPENJDK_TARGET_CPU" = xx86_64; then
    # On all platforms except MacOSX replace x86_64 with amd64.
    OPENJDK_TARGET_CPU_LEGACY="amd64"
  fi
  AC_SUBST(OPENJDK_TARGET_CPU_LEGACY)

  # And the second legacy naming of the cpu.
  # Ie i386 and amd64 instead of x86 and x86_64.
  OPENJDK_TARGET_CPU_LEGACY_LIB="$OPENJDK_TARGET_CPU"
  if test "x$OPENJDK_TARGET_CPU" = xx86; then
    OPENJDK_TARGET_CPU_LEGACY_LIB="i386"
  elif test "x$OPENJDK_TARGET_CPU" = xx86_64; then
    OPENJDK_TARGET_CPU_LEGACY_LIB="amd64"
  fi
  AC_SUBST(OPENJDK_TARGET_CPU_LEGACY_LIB)

  # This is the name of the cpu (but using i386 and amd64 instead of
  # x86 and x86_64, respectively), preceeded by a /, to be used when
  # locating libraries. On macosx, it's empty, though.
  OPENJDK_TARGET_CPU_LIBDIR="/$OPENJDK_TARGET_CPU_LEGACY_LIB"
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    OPENJDK_TARGET_CPU_LIBDIR=""
  fi
  AC_SUBST(OPENJDK_TARGET_CPU_LIBDIR)

  # OPENJDK_TARGET_CPU_ISADIR is normally empty. On 64-bit Solaris systems, it is set to
  # /amd64 or /sparcv9. This string is appended to some library paths, like this:
  # /usr/lib${OPENJDK_TARGET_CPU_ISADIR}/libexample.so
  OPENJDK_TARGET_CPU_ISADIR=""
  if test "x$OPENJDK_TARGET_OS" = xsolaris; then
    if test "x$OPENJDK_TARGET_CPU" = xx86_64; then
      OPENJDK_TARGET_CPU_ISADIR="/amd64"
    elif test "x$OPENJDK_TARGET_CPU" = xsparcv9; then
      OPENJDK_TARGET_CPU_ISADIR="/sparcv9"
    fi
  fi
  AC_SUBST(OPENJDK_TARGET_CPU_ISADIR)

  # Setup OPENJDK_TARGET_CPU_OSARCH, which is used to set the os.arch Java system property
  OPENJDK_TARGET_CPU_OSARCH="$OPENJDK_TARGET_CPU"
  if test "x$OPENJDK_TARGET_OS" = xlinux && test "x$OPENJDK_TARGET_CPU" = xx86; then
    # On linux only, we replace x86 with i386.
    OPENJDK_TARGET_CPU_OSARCH="i386"
  elif test "x$OPENJDK_TARGET_OS" != xmacosx && test "x$OPENJDK_TARGET_CPU" = xx86_64; then
    # On all platforms except macosx, we replace x86_64 with amd64.
    OPENJDK_TARGET_CPU_OSARCH="amd64"
  fi
  AC_SUBST(OPENJDK_TARGET_CPU_OSARCH)

  OPENJDK_TARGET_CPU_JLI="$OPENJDK_TARGET_CPU"
  if test "x$OPENJDK_TARGET_CPU" = xx86; then
    OPENJDK_TARGET_CPU_JLI="i386"
  elif test "x$OPENJDK_TARGET_OS" != xmacosx && test "x$OPENJDK_TARGET_CPU" = xx86_64; then
    # On all platforms except macosx, we replace x86_64 with amd64.
    OPENJDK_TARGET_CPU_JLI="amd64"
  fi
  # Now setup the -D flags for building libjli.
  OPENJDK_TARGET_CPU_JLI_CFLAGS="-DLIBARCHNAME='\"$OPENJDK_TARGET_CPU_JLI\"'"
  if test "x$OPENJDK_TARGET_OS" = xsolaris; then
    if test "x$OPENJDK_TARGET_CPU_ARCH" = xsparc; then
      OPENJDK_TARGET_CPU_JLI_CFLAGS="$OPENJDK_TARGET_CPU_JLI_CFLAGS -DLIBARCH32NAME='\"sparc\"' -DLIBARCH64NAME='\"sparcv9\"'"
    elif test "x$OPENJDK_TARGET_CPU_ARCH" = xx86; then
      OPENJDK_TARGET_CPU_JLI_CFLAGS="$OPENJDK_TARGET_CPU_JLI_CFLAGS -DLIBARCH32NAME='\"i386\"' -DLIBARCH64NAME='\"amd64\"'"
    fi
  fi
  AC_SUBST(OPENJDK_TARGET_CPU_JLI_CFLAGS)

  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      OPENJDK_TARGET_OS_EXPORT_DIR=macosx
  else
      OPENJDK_TARGET_OS_EXPORT_DIR=${OPENJDK_TARGET_OS_TYPE}
  fi
  AC_SUBST(OPENJDK_TARGET_OS_EXPORT_DIR)

  if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
    A_LP64="LP64:="
    # -D_LP64=1 is only set on linux and mac. Setting on windows causes diff in
    # unpack200.exe
    if test "x$OPENJDK_TARGET_OS" = xlinux || test "x$OPENJDK_TARGET_OS" = xmacosx; then
      ADD_LP64="-D_LP64=1"
    fi
  fi
  AC_SUBST(LP64,$A_LP64)

  if test "x$COMPILE_TYPE" = "xcross"; then
    # FIXME: ... or should this include reduced builds..?
    DEFINE_CROSS_COMPILE_ARCH="CROSS_COMPILE_ARCH:=$OPENJDK_TARGET_CPU_LEGACY"
  else
    DEFINE_CROSS_COMPILE_ARCH=""
  fi
  AC_SUBST(DEFINE_CROSS_COMPILE_ARCH)

  # ZERO_ARCHDEF is used to enable architecture-specific code
  case "${OPENJDK_TARGET_CPU}" in
    ppc)     ZERO_ARCHDEF=PPC32 ;;
    ppc64)   ZERO_ARCHDEF=PPC64 ;;
    s390*)   ZERO_ARCHDEF=S390  ;;
    sparc*)  ZERO_ARCHDEF=SPARC ;;
    x86_64*) ZERO_ARCHDEF=AMD64 ;;
    x86)     ZERO_ARCHDEF=IA32  ;;
    *)      ZERO_ARCHDEF=$(echo "${OPENJDK_TARGET_CPU_LEGACY_LIB}" | tr a-z A-Z)
  esac
  AC_SUBST(ZERO_ARCHDEF)
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
    if test "x$OPENJDK_TARGET_CPU_BITS" = "x64"; then
      REQUIRED_OS_VERSION=5.2
    else
      REQUIRED_OS_VERSION=5.1
    fi
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

  PLATFORM_EXTRACT_TARGET_AND_BUILD
  PLATFORM_SETUP_TARGET_CPU_BITS
  PLATFORM_SET_RELEASE_FILE_OS_VALUES
  PLATFORM_SETUP_LEGACY_VARS
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

# Support macro for PLATFORM_SETUP_OPENJDK_TARGET_BITS.
# Add -mX to various FLAGS variables.
AC_DEFUN([PLATFORM_SET_COMPILER_TARGET_BITS_FLAGS],
[
  # When we add flags to the "official" CFLAGS etc, we need to
  # keep track of these additions in ADDED_CFLAGS etc. These
  # will later be checked to make sure only controlled additions
  # have been made to CFLAGS etc.
  ADDED_CFLAGS=" ${COMPILER_TARGET_BITS_FLAG}${OPENJDK_TARGET_CPU_BITS}"
  ADDED_CXXFLAGS=" ${COMPILER_TARGET_BITS_FLAG}${OPENJDK_TARGET_CPU_BITS}"
  ADDED_LDFLAGS=" ${COMPILER_TARGET_BITS_FLAG}${OPENJDK_TARGET_CPU_BITS}"

  CFLAGS="${CFLAGS}${ADDED_CFLAGS}"
  CXXFLAGS="${CXXFLAGS}${ADDED_CXXFLAGS}"
  LDFLAGS="${LDFLAGS}${ADDED_LDFLAGS}"

  CFLAGS_JDK="${CFLAGS_JDK}${ADDED_CFLAGS}"
  CXXFLAGS_JDK="${CXXFLAGS_JDK}${ADDED_CXXFLAGS}"
  LDFLAGS_JDK="${LDFLAGS_JDK}${ADDED_LDFLAGS}"
])

AC_DEFUN_ONCE([PLATFORM_SETUP_OPENJDK_TARGET_BITS],
[
  ###############################################################################
  #
  # Now we check if libjvm.so will use 32 or 64 bit pointers for the C/C++ code.
  # (The JVM can use 32 or 64 bit Java pointers but that decision
  # is made at runtime.)
  #

  if test "x$OPENJDK_TARGET_OS" = xsolaris || test "x$OPENJDK_TARGET_OS" = xaix; then
    # Always specify -m flag on Solaris
    # And -q on AIX because otherwise the compiler produces 32-bit objects by default
    PLATFORM_SET_COMPILER_TARGET_BITS_FLAGS
  elif test "x$COMPILE_TYPE" = xreduced; then
    if test "x$OPENJDK_TARGET_OS_TYPE" = xunix; then
      # Specify -m if running reduced on unix platforms
      PLATFORM_SET_COMPILER_TARGET_BITS_FLAGS
    fi
  fi

  # Make compilation sanity check
  AC_CHECK_HEADERS([stdio.h], , [
    AC_MSG_NOTICE([Failed to compile stdio.h. This likely implies missing compile dependencies.])
    if test "x$COMPILE_TYPE" = xreduced; then
      HELP_MSG_MISSING_DEPENDENCY([reduced])
      AC_MSG_NOTICE([You are doing a reduced build. Check that you have 32-bit libraries installed. $HELP_MSG])
    elif test "x$COMPILE_TYPE" = xcross; then
      AC_MSG_NOTICE([You are doing a cross-compilation. Check that you have all target platform libraries installed.])
    fi
    AC_MSG_ERROR([Cannot continue.])
  ])

  AC_CHECK_SIZEOF([int *], [1111])

  # AC_CHECK_SIZEOF defines 'ac_cv_sizeof_int_p' to hold the number of bytes used by an 'int*'
  if test "x$ac_cv_sizeof_int_p" = x; then
    # The test failed, lets stick to the assumed value.
    AC_MSG_WARN([The number of bits in the target could not be determined, using $OPENJDK_TARGET_CPU_BITS.])
  else
    TESTED_TARGET_CPU_BITS=`expr 8 \* $ac_cv_sizeof_int_p`

    if test "x$TESTED_TARGET_CPU_BITS" != "x$OPENJDK_TARGET_CPU_BITS"; then
      # This situation may happen on 64-bit platforms where the compiler by default only generates 32-bit objects
      # Let's try to implicitely set the compilers target architecture and retry the test
      AC_MSG_NOTICE([The tested number of bits in the target ($TESTED_TARGET_CPU_BITS) differs from the number of bits expected to be found in the target ($OPENJDK_TARGET_CPU_BITS).])
      AC_MSG_NOTICE([Retrying with platforms compiler target bits flag to ${COMPILER_TARGET_BITS_FLAG}${OPENJDK_TARGET_CPU_BITS}])
      PLATFORM_SET_COMPILER_TARGET_BITS_FLAGS

      # We have to unset 'ac_cv_sizeof_int_p' first, otherwise AC_CHECK_SIZEOF will use the previously cached value!
      unset ac_cv_sizeof_int_p
      # And we have to undef the definition of SIZEOF_INT_P in confdefs.h by the previous invocation of AC_CHECK_SIZEOF
      cat >>confdefs.h <<_ACEOF
#undef SIZEOF_INT_P
_ACEOF

      AC_CHECK_SIZEOF([int *], [1111])

      TESTED_TARGET_CPU_BITS=`expr 8 \* $ac_cv_sizeof_int_p`

      if test "x$TESTED_TARGET_CPU_BITS" != "x$OPENJDK_TARGET_CPU_BITS"; then
        AC_MSG_NOTICE([The tested number of bits in the target ($TESTED_TARGET_CPU_BITS) differs from the number of bits expected to be found in the target ($OPENJDK_TARGET_CPU_BITS)])
        if test "x$COMPILE_TYPE" = xreduced; then
          HELP_MSG_MISSING_DEPENDENCY([reduced])
          AC_MSG_NOTICE([You are doing a reduced build. Check that you have 32-bit libraries installed. $HELP_MSG])
        elif test "x$COMPILE_TYPE" = xcross; then
          AC_MSG_NOTICE([You are doing a cross-compilation. Check that you have all target platform libraries installed.])
        fi
        AC_MSG_ERROR([Cannot continue.])
      fi
    fi
  fi

  AC_MSG_CHECKING([for target address size])
  AC_MSG_RESULT([$OPENJDK_TARGET_CPU_BITS bits])
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
  if test "x$ENDIAN" != "x$OPENJDK_TARGET_CPU_ENDIAN"; then
    AC_MSG_ERROR([The tested endian in the target ($ENDIAN) differs from the endian expected to be found in the target ($OPENJDK_TARGET_CPU_ENDIAN)])
  fi
])
