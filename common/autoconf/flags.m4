#
# Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

################################################################################
#
# Setup ABI profile (for arm)
#
AC_DEFUN([FLAGS_SETUP_ABI_PROFILE],
[
  AC_ARG_WITH(abi-profile, [AS_HELP_STRING([--with-abi-profile],
      [specify ABI profile for ARM builds (arm-vfp-sflt,arm-vfp-hflt,arm-sflt, armv5-vfp-sflt,armv6-vfp-hflt,arm64,aarch64) @<:@toolchain dependent@:>@ ])])

  if test "x$with_abi_profile" != x; then
    if test "x$OPENJDK_TARGET_CPU" != xarm && \
        test "x$OPENJDK_TARGET_CPU" != xaarch64; then
      AC_MSG_ERROR([--with-abi-profile only available on arm/aarch64])
    fi

    OPENJDK_TARGET_ABI_PROFILE=$with_abi_profile
    AC_MSG_CHECKING([for ABI profle])
    AC_MSG_RESULT([$OPENJDK_TARGET_ABI_PROFILE])

    if test "x$OPENJDK_TARGET_ABI_PROFILE" = xarm-vfp-sflt; then
      ARM_FLOAT_TYPE=vfp-sflt
      ARM_ARCH_TYPE_FLAGS='-march=armv7-a -mthumb'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarm-vfp-hflt; then
      ARM_FLOAT_TYPE=vfp-hflt
      ARM_ARCH_TYPE_FLAGS='-march=armv7-a -mthumb'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarm-sflt; then
      ARM_FLOAT_TYPE=sflt
      ARM_ARCH_TYPE_FLAGS='-march=armv5t -marm'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarmv5-vfp-sflt; then
      ARM_FLOAT_TYPE=vfp-sflt
      ARM_ARCH_TYPE_FLAGS='-march=armv5t -marm'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarmv6-vfp-hflt; then
      ARM_FLOAT_TYPE=vfp-hflt
      ARM_ARCH_TYPE_FLAGS='-march=armv6 -marm'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarm64; then
      # No special flags, just need to trigger setting JDK_ARCH_ABI_PROP_NAME
      ARM_FLOAT_TYPE=
      ARM_ARCH_TYPE_FLAGS=
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xaarch64; then
      # No special flags, just need to trigger setting JDK_ARCH_ABI_PROP_NAME
      ARM_FLOAT_TYPE=
      ARM_ARCH_TYPE_FLAGS=
    else
      AC_MSG_ERROR([Invalid ABI profile: "$OPENJDK_TARGET_ABI_PROFILE"])
    fi

    if test "x$ARM_FLOAT_TYPE" = xvfp-sflt; then
      ARM_FLOAT_TYPE_FLAGS='-mfloat-abi=softfp -mfpu=vfp -DFLOAT_ARCH=-vfp-sflt'
    elif test "x$ARM_FLOAT_TYPE" = xvfp-hflt; then
      ARM_FLOAT_TYPE_FLAGS='-mfloat-abi=hard -mfpu=vfp -DFLOAT_ARCH=-vfp-hflt'
    elif test "x$ARM_FLOAT_TYPE" = xsflt; then
      ARM_FLOAT_TYPE_FLAGS='-msoft-float -mfpu=vfp'
    fi
    AC_MSG_CHECKING([for $ARM_FLOAT_TYPE floating point flags])
    AC_MSG_RESULT([$ARM_FLOAT_TYPE_FLAGS])

    AC_MSG_CHECKING([for arch type flags])
    AC_MSG_RESULT([$ARM_ARCH_TYPE_FLAGS])

    # Now set JDK_ARCH_ABI_PROP_NAME. This is equivalent to the last part of the
    # autoconf target triplet.
    [ JDK_ARCH_ABI_PROP_NAME=`$ECHO $OPENJDK_TARGET_AUTOCONF_NAME | $SED -e 's/.*-\([^-]*\)$/\1/'` ]
    # Sanity check that it is a known ABI.
    if test "x$JDK_ARCH_ABI_PROP_NAME" != xgnu && \
        test "x$JDK_ARCH_ABI_PROP_NAME" != xgnueabi  && \
        test "x$JDK_ARCH_ABI_PROP_NAME" != xgnueabihf; then
          AC_MSG_WARN([Unknown autoconf target triplet ABI: "$JDK_ARCH_ABI_PROP_NAME"])
    fi
    AC_MSG_CHECKING([for ABI property name])
    AC_MSG_RESULT([$JDK_ARCH_ABI_PROP_NAME])
    AC_SUBST(JDK_ARCH_ABI_PROP_NAME)

    # Pass these on to the open part of configure as if they were set using
    # --with-extra-c[xx]flags.
    EXTRA_CFLAGS="$EXTRA_CFLAGS $ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS"
    EXTRA_CXXFLAGS="$EXTRA_CXXFLAGS $ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS"
    # Get rid of annoying "note: the mangling of 'va_list' has changed in GCC 4.4"
    # FIXME: This should not really be set using extra_cflags.
    if test "x$OPENJDK_TARGET_CPU" = xarm; then
        EXTRA_CFLAGS="$EXTRA_CFLAGS -Wno-psabi"
        EXTRA_CXXFLAGS="$EXTRA_CXXFLAGS -Wno-psabi"
    fi
    # Also add JDK_ARCH_ABI_PROP_NAME define, but only to CFLAGS.
    EXTRA_CFLAGS="$EXTRA_CFLAGS -DJDK_ARCH_ABI_PROP_NAME='\"\$(JDK_ARCH_ABI_PROP_NAME)\"'"
    # And pass the architecture flags to the linker as well
    EXTRA_LDFLAGS="$EXTRA_LDFLAGS $ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS"
  fi

  # When building with an abi profile, the name of that profile is appended on the
  # bundle platform, which is used in bundle names.
  if test "x$OPENJDK_TARGET_ABI_PROFILE" != x; then
    OPENJDK_TARGET_BUNDLE_PLATFORM="$OPENJDK_TARGET_OS_BUNDLE-$OPENJDK_TARGET_ABI_PROFILE"
  fi
])

# Reset the global CFLAGS/LDFLAGS variables and initialize them with the
# corresponding configure arguments instead
AC_DEFUN_ONCE([FLAGS_SETUP_USER_SUPPLIED_FLAGS],
[
  if test "x$CFLAGS" != "x${ADDED_CFLAGS}"; then
    AC_MSG_WARN([Ignoring CFLAGS($CFLAGS) found in environment. Use --with-extra-cflags])
  fi

  if test "x$CXXFLAGS" != "x${ADDED_CXXFLAGS}"; then
    AC_MSG_WARN([Ignoring CXXFLAGS($CXXFLAGS) found in environment. Use --with-extra-cxxflags])
  fi

  if test "x$LDFLAGS" != "x${ADDED_LDFLAGS}"; then
    AC_MSG_WARN([Ignoring LDFLAGS($LDFLAGS) found in environment. Use --with-extra-ldflags])
  fi

  AC_ARG_WITH(extra-cflags, [AS_HELP_STRING([--with-extra-cflags],
      [extra flags to be used when compiling jdk c-files])])

  AC_ARG_WITH(extra-cxxflags, [AS_HELP_STRING([--with-extra-cxxflags],
      [extra flags to be used when compiling jdk c++-files])])

  AC_ARG_WITH(extra-ldflags, [AS_HELP_STRING([--with-extra-ldflags],
      [extra flags to be used when linking jdk])])

  EXTRA_CFLAGS="$with_extra_cflags"
  EXTRA_CXXFLAGS="$with_extra_cxxflags"
  EXTRA_LDFLAGS="$with_extra_ldflags"

  AC_SUBST(EXTRA_CFLAGS)
  AC_SUBST(EXTRA_CXXFLAGS)
  AC_SUBST(EXTRA_LDFLAGS)

  # The global CFLAGS and LDLAGS variables are used by configure tests and
  # should include the extra parameters
  CFLAGS="$EXTRA_CFLAGS"
  CXXFLAGS="$EXTRA_CXXFLAGS"
  LDFLAGS="$EXTRA_LDFLAGS"
  CPPFLAGS=""
])

# Setup the sysroot flags and add them to global CFLAGS and LDFLAGS so
# that configure can use them while detecting compilers.
# TOOLCHAIN_TYPE is available here.
# Param 1 - Optional prefix to all variables. (e.g BUILD_)
AC_DEFUN([FLAGS_SETUP_SYSROOT_FLAGS],
[
  if test "x[$]$1SYSROOT" != "x"; then
    if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
      if test "x$OPENJDK_TARGET_OS" = xsolaris; then
        # Solaris Studio does not have a concept of sysroot. Instead we must
        # make sure the default include and lib dirs are appended to each
        # compile and link command line. Must also add -I-xbuiltin to enable
        # inlining of system functions and intrinsics.
        $1SYSROOT_CFLAGS="-I-xbuiltin -I[$]$1SYSROOT/usr/include"
        $1SYSROOT_LDFLAGS="-L[$]$1SYSROOT/usr/lib$OPENJDK_TARGET_CPU_ISADIR \
            -L[$]$1SYSROOT/lib$OPENJDK_TARGET_CPU_ISADIR"
      fi
    elif test "x$TOOLCHAIN_TYPE" = xgcc; then
      $1SYSROOT_CFLAGS="--sysroot=[$]$1SYSROOT"
      $1SYSROOT_LDFLAGS="--sysroot=[$]$1SYSROOT"
    elif test "x$TOOLCHAIN_TYPE" = xclang; then
      $1SYSROOT_CFLAGS="-isysroot [$]$1SYSROOT"
      $1SYSROOT_LDFLAGS="-isysroot [$]$1SYSROOT"
    fi
    # The global CFLAGS and LDFLAGS variables need these for configure to function
    $1CFLAGS="[$]$1CFLAGS [$]$1SYSROOT_CFLAGS"
    $1CPPFLAGS="[$]$1CPPFLAGS [$]$1SYSROOT_CFLAGS"
    $1CXXFLAGS="[$]$1CXXFLAGS [$]$1SYSROOT_CFLAGS"
    $1LDFLAGS="[$]$1LDFLAGS [$]$1SYSROOT_LDFLAGS"
  fi

  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # We also need -iframework<path>/System/Library/Frameworks
    $1SYSROOT_CFLAGS="[$]$1SYSROOT_CFLAGS -iframework [$]$1SYSROOT/System/Library/Frameworks"
    $1SYSROOT_LDFLAGS="[$]$1SYSROOT_LDFLAGS -iframework [$]$1SYSROOT/System/Library/Frameworks"
    # These always need to be set, or we can't find the frameworks embedded in JavaVM.framework
    # set this here so it doesn't have to be peppered throughout the forest
    $1SYSROOT_CFLAGS="[$]$1SYSROOT_CFLAGS -F [$]$1SYSROOT/System/Library/Frameworks/JavaVM.framework/Frameworks"
    $1SYSROOT_LDFLAGS="[$]$1SYSROOT_LDFLAGS -F [$]$1SYSROOT/System/Library/Frameworks/JavaVM.framework/Frameworks"
  fi

  AC_SUBST($1SYSROOT_CFLAGS)
  AC_SUBST($1SYSROOT_LDFLAGS)
])

AC_DEFUN_ONCE([FLAGS_SETUP_INIT_FLAGS],
[
  # COMPILER_TARGET_BITS_FLAG  : option for selecting 32- or 64-bit output
  # COMPILER_COMMAND_FILE_FLAG : option for passing a command file to the compiler
  # COMPILER_BINDCMD_FILE_FLAG : option for specifying a file which saves the binder
  #                              commands produced by the link step (currently AIX only)
  if test "x$TOOLCHAIN_TYPE" = xxlc; then
    COMPILER_TARGET_BITS_FLAG="-q"
    COMPILER_COMMAND_FILE_FLAG="-f"
    COMPILER_BINDCMD_FILE_FLAG="-bloadmap:"
  else
    COMPILER_TARGET_BITS_FLAG="-m"
    COMPILER_COMMAND_FILE_FLAG="@"
    COMPILER_BINDCMD_FILE_FLAG=""

    # The solstudio linker does not support @-files.
    if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
      COMPILER_COMMAND_FILE_FLAG=
    fi

    # Check if @file is supported by gcc
    if test "x$TOOLCHAIN_TYPE" = xgcc; then
      AC_MSG_CHECKING([if @file is supported by gcc])
      # Extra emtpy "" to prevent ECHO from interpreting '--version' as argument
      $ECHO "" "--version" > command.file
      if $CXX @command.file 2>&AS_MESSAGE_LOG_FD >&AS_MESSAGE_LOG_FD; then
        AC_MSG_RESULT(yes)
        COMPILER_COMMAND_FILE_FLAG="@"
      else
        AC_MSG_RESULT(no)
        COMPILER_COMMAND_FILE_FLAG=
      fi
      $RM command.file
    fi
  fi
  AC_SUBST(COMPILER_TARGET_BITS_FLAG)
  AC_SUBST(COMPILER_COMMAND_FILE_FLAG)
  AC_SUBST(COMPILER_BINDCMD_FILE_FLAG)

  # FIXME: figure out if we should select AR flags depending on OS or toolchain.
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    ARFLAGS="-r"
  elif test "x$OPENJDK_TARGET_OS" = xaix; then
    ARFLAGS="-X64"
  elif test "x$OPENJDK_TARGET_OS" = xwindows; then
    # lib.exe is used as AR to create static libraries.
    ARFLAGS="-nologo -NODEFAULTLIB:MSVCRT"
  else
    ARFLAGS=""
  fi
  AC_SUBST(ARFLAGS)

  ## Setup strip.
  # FIXME: should this really be per platform, or should it be per toolchain type?
  # strip is not provided by clang or solstudio; so guessing platform makes most sense.
  # FIXME: we should really only export STRIPFLAGS from here, not POST_STRIP_CMD.
  if test "x$OPENJDK_TARGET_OS" = xlinux; then
    STRIPFLAGS="-g"
  elif test "x$OPENJDK_TARGET_OS" = xsolaris; then
    STRIPFLAGS="-x"
  elif test "x$OPENJDK_TARGET_OS" = xmacosx; then
    STRIPFLAGS="-S"
  elif test "x$OPENJDK_TARGET_OS" = xaix; then
    STRIPFLAGS="-X32_64"
  fi

  AC_SUBST(STRIPFLAGS)

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    CC_OUT_OPTION=-Fo
    EXE_OUT_OPTION=-out:
    LD_OUT_OPTION=-out:
    AR_OUT_OPTION=-out:
  else
    # The option used to specify the target .o,.a or .so file.
    # When compiling, how to specify the to be created object file.
    CC_OUT_OPTION='-o$(SPACE)'
    # When linking, how to specify the to be created executable.
    EXE_OUT_OPTION='-o$(SPACE)'
    # When linking, how to specify the to be created dynamically linkable library.
    LD_OUT_OPTION='-o$(SPACE)'
    # When archiving, how to specify the to be create static archive for object files.
    AR_OUT_OPTION='rcs$(SPACE)'
  fi
  AC_SUBST(CC_OUT_OPTION)
  AC_SUBST(EXE_OUT_OPTION)
  AC_SUBST(LD_OUT_OPTION)
  AC_SUBST(AR_OUT_OPTION)

  # On Windows, we need to set RC flags.
  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    RC_FLAGS="-nologo -l0x409"
    JVM_RCFLAGS="-nologo"
    if test "x$DEBUG_LEVEL" = xrelease; then
      RC_FLAGS="$RC_FLAGS -DNDEBUG"
      JVM_RCFLAGS="$JVM_RCFLAGS -DNDEBUG"
    fi

    # The version variables used to create RC_FLAGS may be overridden
    # in a custom configure script, or possibly the command line.
    # Let those variables be expanded at make time in spec.gmk.
    # The \$ are escaped to the shell, and the $(...) variables
    # are evaluated by make.
    RC_FLAGS="$RC_FLAGS \
        -D\"JDK_VERSION_STRING=\$(VERSION_STRING)\" \
        -D\"JDK_COMPANY=\$(COMPANY_NAME)\" \
        -D\"JDK_COMPONENT=\$(PRODUCT_NAME) \$(JDK_RC_PLATFORM_NAME) binary\" \
        -D\"JDK_VER=\$(VERSION_NUMBER)\" \
        -D\"JDK_COPYRIGHT=Copyright \xA9 $COPYRIGHT_YEAR\" \
        -D\"JDK_NAME=\$(PRODUCT_NAME) \$(JDK_RC_PLATFORM_NAME) \$(VERSION_MAJOR)\" \
        -D\"JDK_FVER=\$(subst .,\$(COMMA),\$(VERSION_NUMBER_FOUR_POSITIONS))\""

    JVM_RCFLAGS="$JVM_RCFLAGS \
        -D\"HS_BUILD_ID=\$(VERSION_STRING)\" \
        -D\"HS_COMPANY=\$(COMPANY_NAME)\" \
        -D\"JDK_DOTVER=\$(VERSION_NUMBER_FOUR_POSITIONS)\" \
        -D\"HS_COPYRIGHT=Copyright $COPYRIGHT_YEAR\" \
        -D\"HS_NAME=\$(PRODUCT_NAME) \$(VERSION_SHORT)\" \
        -D\"JDK_VER=\$(subst .,\$(COMMA),\$(VERSION_NUMBER_FOUR_POSITIONS))\" \
        -D\"HS_FNAME=jvm.dll\" \
        -D\"HS_INTERNAL_NAME=jvm\""
  fi
  AC_SUBST(RC_FLAGS)
  AC_SUBST(JVM_RCFLAGS)

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    # silence copyright notice and other headers.
    COMMON_CCXXFLAGS="$COMMON_CCXXFLAGS -nologo"
  fi
])

AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_LIBS],
[
  ###############################################################################
  #
  # How to compile shared libraries.
  #

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    PICFLAG="-fPIC"
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''

    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Linking is different on MacOSX
      if test "x$STATIC_BUILD" = xtrue; then
        SHARED_LIBRARY_FLAGS ='-undefined dynamic_lookup'
      else
        SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0 $PICFLAG"
        JVM_CFLAGS="$JVM_CFLAGS $PICFLAG"
      fi
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,@loader_path/.'
      SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Wl,-install_name,@rpath/[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-exported_symbols_list,[$]1'
    else
      # Default works for linux, might work on other platforms as well.
      SHARED_LIBRARY_FLAGS='-shared'
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,\$$ORIGIN[$]1'
      SET_SHARED_LIBRARY_ORIGIN="-Wl,-z,origin $SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Wl,-soname=[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-version-script=[$]1'
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''

    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Linking is different on MacOSX
      PICFLAG=''
      SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0 $PICFLAG"
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,@loader_path/.'
      SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Wl,-install_name,@rpath/[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-exported_symbols_list,[$]1'

      if test "x$STATIC_BUILD" = xfalse; then
        JVM_CFLAGS="$JVM_CFLAGS -fPIC"
      fi
    else
      # Default works for linux, might work on other platforms as well.
      PICFLAG='-fPIC'
      SHARED_LIBRARY_FLAGS='-shared'
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,\$$ORIGIN[$]1'
      SET_SHARED_LIBRARY_NAME='-Wl,-soname=[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-version-script=[$]1'

      # arm specific settings
      if test "x$OPENJDK_TARGET_CPU" = "xarm"; then
        # '-Wl,-z,origin' isn't used on arm.
        SET_SHARED_LIBRARY_ORIGIN='-Wl,-rpath,\$$$$ORIGIN[$]1'
      else
        SET_SHARED_LIBRARY_ORIGIN="-Wl,-z,origin $SET_EXECUTABLE_ORIGIN"
      fi

    fi
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    if test "x$OPENJDK_TARGET_CPU" = xsparcv9; then
      PICFLAG="-xcode=pic32"
    else
      PICFLAG="-KPIC"
    fi
    C_FLAG_REORDER='-xF'
    CXX_FLAG_REORDER='-xF'
    SHARED_LIBRARY_FLAGS="-G"
    SET_EXECUTABLE_ORIGIN='-R\$$ORIGIN[$]1'
    SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
    SET_SHARED_LIBRARY_NAME='-h [$]1'
    SET_SHARED_LIBRARY_MAPFILE='-M[$]1'
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    # '-qpic' defaults to 'qpic=small'. This means that the compiler generates only
    # one instruction for accessing the TOC. If the TOC grows larger than 64K, the linker
    # will have to patch this single instruction with a call to some out-of-order code which
    # does the load from the TOC. This is of course slow. But in that case we also would have
    # to use '-bbigtoc' for linking anyway so we could also change the PICFLAG to 'qpic=large'.
    # With 'qpic=large' the compiler will by default generate a two-instruction sequence which
    # can be patched directly by the linker and does not require a jump to out-of-order code.
    # Another alternative instead of using 'qpic=large -bbigtoc' may be to use '-qminimaltoc'
    # instead. This creates a distinct TOC for every compilation unit (and thus requires two
    # loads for accessing a global variable). But there are rumors that this may be seen as a
    # 'performance feature' because of improved code locality of the symbols used in a
    # compilation unit.
    PICFLAG="-qpic"
    JVM_CFLAGS="$JVM_CFLAGS $PICFLAG"
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''
    SHARED_LIBRARY_FLAGS="-qmkshrobj -bM:SRE -bnoentry"
    SET_EXECUTABLE_ORIGIN=""
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE=''
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    PICFLAG=""
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''
    SHARED_LIBRARY_FLAGS="-dll"
    SET_EXECUTABLE_ORIGIN=''
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE='-def:[$]1'
  fi

  AC_SUBST(C_FLAG_REORDER)
  AC_SUBST(CXX_FLAG_REORDER)
  AC_SUBST(SET_EXECUTABLE_ORIGIN)
  AC_SUBST(SET_SHARED_LIBRARY_ORIGIN)
  AC_SUBST(SET_SHARED_LIBRARY_NAME)
  AC_SUBST(SET_SHARED_LIBRARY_MAPFILE)
  AC_SUBST(SHARED_LIBRARY_FLAGS)

  # The (cross) compiler is now configured, we can now test capabilities
  # of the target platform.
])

# Documentation on common flags used for solstudio in HIGHEST.
#
# WARNING: Use of OPTIMIZATION_LEVEL=HIGHEST in your Makefile needs to be
#          done with care, there are some assumptions below that need to
#          be understood about the use of pointers, and IEEE behavior.
#
# -fns: Use non-standard floating point mode (not IEEE 754)
# -fsimple: Do some simplification of floating point arithmetic (not IEEE 754)
# -fsingle: Use single precision floating point with 'float'
# -xalias_level=basic: Assume memory references via basic pointer types do not alias
#   (Source with excessing pointer casting and data access with mixed
#    pointer types are not recommended)
# -xbuiltin=%all: Use intrinsic or inline versions for math/std functions
#   (If you expect perfect errno behavior, do not use this)
# -xdepend: Loop data dependency optimizations (need -xO3 or higher)
# -xrestrict: Pointer parameters to functions do not overlap
#   (Similar to -xalias_level=basic usage, but less obvious sometimes.
#    If you pass in multiple pointers to the same data, do not use this)
# -xlibmil: Inline some library routines
#   (If you expect perfect errno behavior, do not use this)
# -xlibmopt: Use optimized math routines (CURRENTLY DISABLED)
#   (If you expect perfect errno behavior, do not use this)
#  Can cause undefined external on Solaris 8 X86 on __sincos, removing for now

    # FIXME: this will never happen since sparc != sparcv9, ie 32 bit, which we don't build anymore.
    # Bug?
    #if test "x$OPENJDK_TARGET_CPU" = xsparc; then
    #  CFLAGS_JDK="${CFLAGS_JDK} -xmemalign=4s"
    #  CXXFLAGS_JDK="${CXXFLAGS_JDK} -xmemalign=4s"
    #fi

AC_DEFUN_ONCE([FLAGS_SETUP_COMPILER_FLAGS_FOR_OPTIMIZATION],
[

  ###############################################################################
  #
  # Setup the opt flags for different compilers
  # and different operating systems.
  #

  # FIXME: this was indirectly the old default, but just inherited.
  # if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
  #   C_FLAG_DEPS="-MMD -MF"
  # fi

  # Generate make dependency files
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    C_FLAG_DEPS="-MMD -MF"
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    C_FLAG_DEPS="-MMD -MF"
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    C_FLAG_DEPS="-xMMD -xMF"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    C_FLAG_DEPS="-qmakedep=gcc -MF"
  fi
  CXX_FLAG_DEPS="$C_FLAG_DEPS"
  AC_SUBST(C_FLAG_DEPS)
  AC_SUBST(CXX_FLAG_DEPS)

  # Debug symbols
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$OPENJDK_TARGET_CPU_BITS" = "x64" && test "x$DEBUG_LEVEL" = "xfastdebug"; then
      # reduce from default "-g2" option to save space
      CFLAGS_DEBUG_SYMBOLS="-g1"
      CXXFLAGS_DEBUG_SYMBOLS="-g1"
    else
      CFLAGS_DEBUG_SYMBOLS="-g"
      CXXFLAGS_DEBUG_SYMBOLS="-g"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    CFLAGS_DEBUG_SYMBOLS="-g"
    CXXFLAGS_DEBUG_SYMBOLS="-g"
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    CFLAGS_DEBUG_SYMBOLS="-g -xs"
    # -g0 enables debug symbols without disabling inlining.
    CXXFLAGS_DEBUG_SYMBOLS="-g0 -xs"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    CFLAGS_DEBUG_SYMBOLS="-g"
    CXXFLAGS_DEBUG_SYMBOLS="-g"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    CFLAGS_DEBUG_SYMBOLS="-Zi"
    CXXFLAGS_DEBUG_SYMBOLS="-Zi"
  fi
  AC_SUBST(CFLAGS_DEBUG_SYMBOLS)
  AC_SUBST(CXXFLAGS_DEBUG_SYMBOLS)

  # Debug symbols for JVM_CFLAGS
  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -xs"
    if test "x$DEBUG_LEVEL" = xslowdebug; then
      JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -g"
    else
      # -g0 does not disable inlining, which -g does.
      JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -g0"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -Z7 -d2Zi+"
  else
    JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -g"
  fi
  AC_SUBST(JVM_CFLAGS_SYMBOLS)

  # bounds, memory and behavior checking options
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    case $DEBUG_LEVEL in
    release )
      # no adjustment
      ;;
    fastdebug )
      # no adjustment
      ;;
    slowdebug )
      # FIXME: By adding this to C(XX)FLAGS_DEBUG_OPTIONS/JVM_CFLAGS_SYMBOLS it
      # get's added conditionally on whether we produce debug symbols or not.
      # This is most likely not really correct.

      # Add runtime stack smashing and undefined behavior checks.
      # Not all versions of gcc support -fstack-protector
      STACK_PROTECTOR_CFLAG="-fstack-protector-all"
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$STACK_PROTECTOR_CFLAG -Werror], IF_FALSE: [STACK_PROTECTOR_CFLAG=""])

      CFLAGS_DEBUG_OPTIONS="$STACK_PROTECTOR_CFLAG --param ssp-buffer-size=1"
      CXXFLAGS_DEBUG_OPTIONS="$STACK_PROTECTOR_CFLAG --param ssp-buffer-size=1"
      if test "x$STACK_PROTECTOR_CFLAG" != x; then
        JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS $STACK_PROTECTOR_CFLAG --param ssp-buffer-size=1"
      fi
      ;;
    esac
  fi

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$DEBUG_LEVEL" != xrelease; then
      if test "x$OPENJDK_TARGET_CPU" = xx86_64; then
        JVM_CFLAGS="$JVM_CFLAGS -homeparams"
      fi
    fi
  fi

  # Optimization levels
  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    CC_HIGHEST="$CC_HIGHEST -fns -fsimple -fsingle -xbuiltin=%all -xdepend -xrestrict -xlibmil"

    if test "x$OPENJDK_TARGET_CPU_ARCH" = "xx86"; then
      # FIXME: seems we always set -xregs=no%frameptr; put it elsewhere more global?
      C_O_FLAG_HIGHEST_JVM="-xO4"
      C_O_FLAG_HIGHEST="-xO4 -Wu,-O4~yz $CC_HIGHEST -xalias_level=basic -xregs=no%frameptr"
      C_O_FLAG_HI="-xO4 -Wu,-O4~yz -xregs=no%frameptr"
      C_O_FLAG_NORM="-xO2 -Wu,-O2~yz -xregs=no%frameptr"
      C_O_FLAG_DEBUG="-xregs=no%frameptr"
      C_O_FLAG_DEBUG_JVM=""
      C_O_FLAG_NONE="-xregs=no%frameptr"
      CXX_O_FLAG_HIGHEST_JVM="-xO4"
      CXX_O_FLAG_HIGHEST="-xO4 -Qoption ube -O4~yz $CC_HIGHEST -xregs=no%frameptr"
      CXX_O_FLAG_HI="-xO4 -Qoption ube -O4~yz -xregs=no%frameptr"
      CXX_O_FLAG_NORM="-xO2 -Qoption ube -O2~yz -xregs=no%frameptr"
      CXX_O_FLAG_DEBUG="-xregs=no%frameptr"
      CXX_O_FLAG_DEBUG_JVM=""
      CXX_O_FLAG_NONE="-xregs=no%frameptr"
      if test "x$OPENJDK_TARGET_CPU_BITS" = "x32"; then
        C_O_FLAG_HIGHEST="$C_O_FLAG_HIGHEST -xchip=pentium"
        CXX_O_FLAG_HIGHEST="$CXX_O_FLAG_HIGHEST -xchip=pentium"
      fi
    elif test "x$OPENJDK_TARGET_CPU_ARCH" = "xsparc"; then
      C_O_FLAG_HIGHEST_JVM="-xO4"
      C_O_FLAG_HIGHEST="-xO4 -Wc,-Qrm-s -Wc,-Qiselect-T0 $CC_HIGHEST -xalias_level=basic -xprefetch=auto,explicit -xchip=ultra"
      C_O_FLAG_HI="-xO4 -Wc,-Qrm-s -Wc,-Qiselect-T0"
      C_O_FLAG_NORM="-xO2 -Wc,-Qrm-s -Wc,-Qiselect-T0"
      C_O_FLAG_DEBUG=""
      C_O_FLAG_DEBUG_JVM=""
      C_O_FLAG_NONE=""
      CXX_O_FLAG_HIGHEST_JVM="-xO4"
      CXX_O_FLAG_HIGHEST="-xO4 -Qoption cg -Qrm-s -Qoption cg -Qiselect-T0 $CC_HIGHEST -xprefetch=auto,explicit -xchip=ultra"
      CXX_O_FLAG_HI="-xO4 -Qoption cg -Qrm-s -Qoption cg -Qiselect-T0"
      CXX_O_FLAG_NORM="-xO2 -Qoption cg -Qrm-s -Qoption cg -Qiselect-T0"
      CXX_O_FLAG_DEBUG=""
      CXX_O_FLAG_DEBUG_JVM=""
      CXX_O_FLAG_NONE=""
    fi
  else
    # The remaining toolchains share opt flags between CC and CXX;
    # setup for C and duplicate afterwards.
    if test "x$TOOLCHAIN_TYPE" = xgcc; then
      if test "x$OPENJDK_TARGET_OS" = xmacosx; then
        # On MacOSX we optimize for size, something
        # we should do for all platforms?
        C_O_FLAG_HIGHEST_JVM="-Os"
        C_O_FLAG_HIGHEST="-Os"
        C_O_FLAG_HI="-Os"
        C_O_FLAG_NORM="-Os"
        C_O_FLAG_SIZE="-Os"
      else
        C_O_FLAG_HIGHEST_JVM="-O3"
        C_O_FLAG_HIGHEST="-O3"
        C_O_FLAG_HI="-O3"
        C_O_FLAG_NORM="-O2"
        C_O_FLAG_SIZE="-Os"
      fi
      C_O_FLAG_DEBUG="-O0"
      if test "x$OPENJDK_TARGET_OS" = xmacosx; then
        C_O_FLAG_DEBUG_JVM=""
      elif test "x$OPENJDK_TARGET_OS" = xlinux; then
        C_O_FLAG_DEBUG_JVM="-O0"
      fi
      C_O_FLAG_NONE="-O0"
    elif test "x$TOOLCHAIN_TYPE" = xclang; then
      if test "x$OPENJDK_TARGET_OS" = xmacosx; then
        # On MacOSX we optimize for size, something
        # we should do for all platforms?
        C_O_FLAG_HIGHEST_JVM="-Os"
        C_O_FLAG_HIGHEST="-Os"
        C_O_FLAG_HI="-Os"
        C_O_FLAG_NORM="-Os"
        C_O_FLAG_SIZE="-Os"
      else
        C_O_FLAG_HIGHEST_JVM="-O3"
        C_O_FLAG_HIGHEST="-O3"
        C_O_FLAG_HI="-O3"
        C_O_FLAG_NORM="-O2"
        C_O_FLAG_SIZE="-Os"
      fi
      C_O_FLAG_DEBUG="-O0"
      if test "x$OPENJDK_TARGET_OS" = xmacosx; then
        C_O_FLAG_DEBUG_JVM=""
      elif test "x$OPENJDK_TARGET_OS" = xlinux; then
        C_O_FLAG_DEBUG_JVM="-O0"
      fi
      C_O_FLAG_NONE="-O0"
    elif test "x$TOOLCHAIN_TYPE" = xxlc; then
      C_O_FLAG_HIGHEST_JVM="-O3 -qhot=level=1 -qinline -qinlglue"
      C_O_FLAG_HIGHEST="-O3 -qhot=level=1 -qinline -qinlglue"
      C_O_FLAG_HI="-O3 -qinline -qinlglue"
      C_O_FLAG_NORM="-O2"
      C_O_FLAG_DEBUG="-qnoopt"
      # FIXME: Value below not verified.
      C_O_FLAG_DEBUG_JVM=""
      C_O_FLAG_NONE="-qnoopt"
    elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
      C_O_FLAG_HIGHEST_JVM="-O2 -Oy-"
      C_O_FLAG_HIGHEST="-O2"
      C_O_FLAG_HI="-O1"
      C_O_FLAG_NORM="-O1"
      C_O_FLAG_DEBUG="-Od"
      C_O_FLAG_DEBUG_JVM=""
      C_O_FLAG_NONE="-Od"
      C_O_FLAG_SIZE="-Os"
    fi
    CXX_O_FLAG_HIGHEST_JVM="$C_O_FLAG_HIGHEST_JVM"
    CXX_O_FLAG_HIGHEST="$C_O_FLAG_HIGHEST"
    CXX_O_FLAG_HI="$C_O_FLAG_HI"
    CXX_O_FLAG_NORM="$C_O_FLAG_NORM"
    CXX_O_FLAG_DEBUG="$C_O_FLAG_DEBUG"
    CXX_O_FLAG_DEBUG_JVM="$C_O_FLAG_DEBUG_JVM"
    CXX_O_FLAG_NONE="$C_O_FLAG_NONE"
    CXX_O_FLAG_SIZE="$C_O_FLAG_SIZE"
  fi

  # Adjust optimization flags according to debug level.
  case $DEBUG_LEVEL in
    release )
      # no adjustment
      ;;
    fastdebug )
      # Not quite so much optimization
      C_O_FLAG_HI="$C_O_FLAG_NORM"
      CXX_O_FLAG_HI="$CXX_O_FLAG_NORM"
      ;;
    slowdebug )
      # Disable optimization
      C_O_FLAG_HIGHEST_JVM="$C_O_FLAG_DEBUG_JVM"
      C_O_FLAG_HIGHEST="$C_O_FLAG_DEBUG"
      C_O_FLAG_HI="$C_O_FLAG_DEBUG"
      C_O_FLAG_NORM="$C_O_FLAG_DEBUG"
      C_O_FLAG_SIZE="$C_O_FLAG_DEBUG"
      CXX_O_FLAG_HIGHEST_JVM="$CXX_O_FLAG_DEBUG_JVM"
      CXX_O_FLAG_HIGHEST="$CXX_O_FLAG_DEBUG"
      CXX_O_FLAG_HI="$CXX_O_FLAG_DEBUG"
      CXX_O_FLAG_NORM="$CXX_O_FLAG_DEBUG"
      CXX_O_FLAG_SIZE="$CXX_O_FLAG_DEBUG"
      ;;
  esac

  AC_SUBST(C_O_FLAG_HIGHEST_JVM)
  AC_SUBST(C_O_FLAG_HIGHEST)
  AC_SUBST(C_O_FLAG_HI)
  AC_SUBST(C_O_FLAG_NORM)
  AC_SUBST(C_O_FLAG_DEBUG)
  AC_SUBST(C_O_FLAG_NONE)
  AC_SUBST(C_O_FLAG_SIZE)
  AC_SUBST(CXX_O_FLAG_HIGHEST_JVM)
  AC_SUBST(CXX_O_FLAG_HIGHEST)
  AC_SUBST(CXX_O_FLAG_HI)
  AC_SUBST(CXX_O_FLAG_NORM)
  AC_SUBST(CXX_O_FLAG_DEBUG)
  AC_SUBST(CXX_O_FLAG_NONE)
  AC_SUBST(CXX_O_FLAG_SIZE)
])


AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK],
[

  FLAGS_SETUP_ABI_PROFILE
  FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_HELPER([TARGET])
  FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_HELPER([BUILD], [OPENJDK_BUILD_])

  # Tests are only ever compiled for TARGET
  # Flags for compiling test libraries
  CFLAGS_TESTLIB="$COMMON_CCXXFLAGS_JDK $CFLAGS_JDK $PICFLAG $CFLAGS_JDKLIB_EXTRA"
  CXXFLAGS_TESTLIB="$COMMON_CCXXFLAGS_JDK $CXXFLAGS_JDK $PICFLAG $CXXFLAGS_JDKLIB_EXTRA"

  # Flags for compiling test executables
  CFLAGS_TESTEXE="$COMMON_CCXXFLAGS_JDK $CFLAGS_JDK"
  CXXFLAGS_TESTEXE="$COMMON_CCXXFLAGS_JDK $CXXFLAGS_JDK"

  AC_SUBST(CFLAGS_TESTLIB)
  AC_SUBST(CFLAGS_TESTEXE)
  AC_SUBST(CXXFLAGS_TESTLIB)
  AC_SUBST(CXXFLAGS_TESTEXE)

  LDFLAGS_TESTLIB="$LDFLAGS_JDKLIB"
  LDFLAGS_TESTEXE="$LDFLAGS_JDKEXE $JAVA_BASE_LDFLAGS"

  AC_SUBST(LDFLAGS_TESTLIB)
  AC_SUBST(LDFLAGS_TESTEXE)

])

################################################################################
# $1 - Either BUILD or TARGET to pick the correct OS/CPU variables to check
#      conditionals against.
# $2 - Optional prefix for each variable defined.
AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_HELPER],
[
  # Special extras...
  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    if test "x$OPENJDK_$1_CPU_ARCH" = "xsparc"; then
      $2CFLAGS_JDKLIB_EXTRA="${$2CFLAGS_JDKLIB_EXTRA} -xregs=no%appl"
      $2CXXFLAGS_JDKLIB_EXTRA="${$2CXXFLAGS_JDKLIB_EXTRA} -xregs=no%appl"
    fi
    $2CFLAGS_JDKLIB_EXTRA="${$2CFLAGS_JDKLIB_EXTRA} -errtags=yes -errfmt"
    $2CXXFLAGS_JDKLIB_EXTRA="${$2CXXFLAGS_JDKLIB_EXTRA} -errtags=yes -errfmt"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    $2CFLAGS_JDK="${$2CFLAGS_JDK} -qchars=signed -qfullpath -qsaveopt"
    $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} -qchars=signed -qfullpath -qsaveopt"
  elif test "x$TOOLCHAIN_TYPE" = xgcc; then
    $2CXXSTD_CXXFLAG="-std=gnu++98"
    FLAGS_CXX_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [[$]$2CXXSTD_CXXFLAG -Werror],
    						 IF_FALSE: [$2CXXSTD_CXXFLAG=""])
    $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} ${$2CXXSTD_CXXFLAG}"
    $2JVM_CFLAGS="${$2JVM_CFLAGS} ${$2CXXSTD_CXXFLAG}"
    AC_SUBST([$2CXXSTD_CXXFLAG])
  fi
  if test "x$OPENJDK_TARGET_OS" = xsolaris; then
    $2CFLAGS_JDK="${$2CFLAGS_JDK} -D__solaris__"
    $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} -D__solaris__"
  fi

  if test "x$OPENJDK_TARGET_OS" = xsolaris; then
    $2CFLAGS_JDK="${$2CFLAGS_JDK} -D__solaris__"
    $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} -D__solaris__"
  fi

  $2CFLAGS_JDK="${$2CFLAGS_JDK} ${$2EXTRA_CFLAGS}"
  $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} ${$2EXTRA_CXXFLAGS}"
  $2LDFLAGS_JDK="${$2LDFLAGS_JDK} ${$2EXTRA_LDFLAGS}"

  ###############################################################################
  #
  # Now setup the CFLAGS and LDFLAGS for the JDK build.
  # Later we will also have CFLAGS and LDFLAGS for the hotspot subrepo build.
  #

  # Setup compiler/platform specific flags into
  #    $2CFLAGS_JDK    - C Compiler flags
  #    $2CXXFLAGS_JDK  - C++ Compiler flags
  #    $2COMMON_CCXXFLAGS_JDK - common to C and C++
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_GNU_SOURCE"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_REENTRANT"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -fcheck-new"
    if test "x$OPENJDK_$1_CPU" = xx86; then
      # Force compatibility with i586 on 32 bit intel platforms.
      $2COMMON_CCXXFLAGS="${$2COMMON_CCXXFLAGS} -march=i586"
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -march=i586"
    fi
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS [$]$2COMMON_CCXXFLAGS_JDK -Wall -Wextra -Wno-unused -Wno-unused-parameter -Wformat=2 \
        -pipe -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE"
    case $OPENJDK_$1_CPU_ARCH in
      arm )
        # on arm we don't prevent gcc to omit frame pointer but do prevent strict aliasing
        $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
        $2COMMON_CCXXFLAGS_JDK="${$2COMMON_CCXXFLAGS_JDK} -fsigned-char"
        ;;
      ppc )
        # on ppc we don't prevent gcc to omit frame pointer but do prevent strict aliasing
        $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
        ;;
      s390 )
        $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -fno-omit-frame-pointer -mbackchain -march=z10"
        $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
        ;;
      * )
        $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -fno-omit-frame-pointer"
        $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
        ;;
    esac
    TOOLCHAIN_CHECK_COMPILER_VERSION(VERSION: 6, PREFIX: $2, IF_AT_LEAST: FLAGS_SETUP_GCC6_COMPILER_FLAGS($2))
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_GNU_SOURCE"

    # Restrict the debug information created by Clang to avoid
    # too big object files and speed the build up a little bit
    # (see http://llvm.org/bugs/show_bug.cgi?id=7554)
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -flimit-debug-info"
    if test "x$OPENJDK_$1_OS" = xlinux; then
      if test "x$OPENJDK_$1_CPU" = xx86; then
        # Force compatibility with i586 on 32 bit intel platforms.
        $2COMMON_CCXXFLAGS="${$2COMMON_CCXXFLAGS} -march=i586"
        $2JVM_CFLAGS="[$]$2JVM_CFLAGS -march=i586"
      fi
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wno-sometimes-uninitialized"
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS [$]$2COMMON_CCXXFLAGS_JDK -Wall -Wextra -Wno-unused -Wno-unused-parameter -Wformat=2 \
          -pipe -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE"
      case $OPENJDK_$1_CPU_ARCH in
        ppc )
          # on ppc we don't prevent gcc to omit frame pointer but do prevent strict aliasing
          $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
          ;;
        * )
          $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -fno-omit-frame-pointer"
          $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
          ;;
      esac
    fi
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DSPARC_WORKS"
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS [$]$2COMMON_CCXXFLAGS_JDK -DTRACING -DMACRO_MEMSYS_OPS -DBREAKPTS"
    if test "x$OPENJDK_$1_CPU_ARCH" = xx86; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DcpuIntel -Di586 -D$OPENJDK_$1_CPU_LEGACY_LIB"
    fi

    $2CFLAGS_JDK="[$]$2CFLAGS_JDK -xc99=%none -xCC -errshort=tags -Xa -v -mt -W0,-noglobal"
    $2CXXFLAGS_JDK="[$]$2CXXFLAGS_JDK -errtags=yes +w -mt -features=no%except -DCC_NOEX -norunpath -xnolib"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_REENTRANT -D__STDC_FORMAT_MACROS"
    $2CFLAGS_JDK="[$]$2CFLAGS_JDK -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE -DSTDC"
    $2CXXFLAGS_JDK="[$]$2CXXFLAGS_JDK -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE -DSTDC"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS [$]$2COMMON_CCXXFLAGS_JDK \
        -MD -Zc:wchar_t- -W3 -wd4800 \
        -DWIN32_LEAN_AND_MEAN \
        -D_CRT_SECURE_NO_DEPRECATE -D_CRT_NONSTDC_NO_DEPRECATE \
        -D_WINSOCK_DEPRECATED_NO_WARNINGS \
        -DWIN32 -DIAL"
    if test "x$OPENJDK_$1_CPU" = xx86_64; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_AMD64_ -Damd64"
    else
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_X86_ -Dx86"
    fi
    # If building with Visual Studio 2010, we can still use _STATIC_CPPLIB to
    # avoid bundling msvcpNNN.dll. Doesn't work with newer versions of visual
    # studio.
    if test "x$TOOLCHAIN_VERSION" = "x2010"; then
      STATIC_CPPLIB_FLAGS="-D_STATIC_CPPLIB -D_DISABLE_DEPRECATE_STATIC_CPPLIB"
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK $STATIC_CPPLIB_FLAGS"
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS $STATIC_CPPLIB_FLAGS"
    fi
  fi

  ###############################################################################

  # Adjust flags according to debug level.
  case $DEBUG_LEVEL in
    fastdebug | slowdebug )
      $2CFLAGS_JDK="[$]$2CFLAGS_JDK $CFLAGS_DEBUG_SYMBOLS $CFLAGS_DEBUG_OPTIONS"
      $2CXXFLAGS_JDK="[$]$2CXXFLAGS_JDK $CXXFLAGS_DEBUG_SYMBOLS $CXXFLAGS_DEBUG_OPTIONS"
      ;;
    release )
      ;;
    * )
      AC_MSG_ERROR([Unrecognized \$DEBUG_LEVEL: $DEBUG_LEVEL])
      ;;
  esac

  # Set some common defines. These works for all compilers, but assume
  # -D is universally accepted.

  # Setup endianness
  if test "x$OPENJDK_$1_CPU_ENDIAN" = xlittle; then
    # The macro _LITTLE_ENDIAN needs to be defined the same to avoid the
    #   Sun C compiler warning message: warning: macro redefined: _LITTLE_ENDIAN
    #   (The Solaris X86 system defines this in file /usr/include/sys/isa_defs.h).
    #   Note: -Dmacro         is the same as    #define macro 1
    #         -Dmacro=        is the same as    #define macro
    if test "x$OPENJDK_$1_OS" = xsolaris; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_LITTLE_ENDIAN="
    else
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_LITTLE_ENDIAN"
    fi
  else
    # Same goes for _BIG_ENDIAN. Do we really need to set *ENDIAN on Solaris if they
    # are defined in the system?
    if test "x$OPENJDK_$1_OS" = xsolaris; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_BIG_ENDIAN="
    else
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_BIG_ENDIAN"
    fi
  fi

  # Setup target OS define. Use OS target name but in upper case.
  OPENJDK_$1_OS_UPPERCASE=`$ECHO $OPENJDK_$1_OS | $TR 'abcdefghijklmnopqrstuvwxyz' 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'`
  $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D$OPENJDK_$1_OS_UPPERCASE"

  # Setup target CPU
  $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK \
      $OPENJDK_$1_ADD_LP64 \
      -DARCH='\"$OPENJDK_$1_CPU_LEGACY\"' -D$OPENJDK_$1_CPU_LEGACY"

  # Setup debug/release defines
  if test "x$DEBUG_LEVEL" = xrelease; then
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DNDEBUG"
    if test "x$OPENJDK_$1_OS" = xsolaris; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DTRIMMED"
    fi
  else
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DDEBUG"
  fi

  # Set some additional per-OS defines.
  if test "x$OPENJDK_$1_OS" = xlinux; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DLINUX"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -pipe $PICFLAG -fno-rtti -fno-exceptions \
        -fvisibility=hidden -fno-strict-aliasing -fno-omit-frame-pointer"
  elif test "x$OPENJDK_$1_OS" = xsolaris; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DSOLARIS"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -template=no%extdef -features=no%split_init \
        -D_Crun_inline_placement -library=%none $PICFLAG -mt -features=no%except"
  elif test "x$OPENJDK_$1_OS" = xmacosx; then
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_ALLBSD_SOURCE -D_DARWIN_UNLIMITED_SELECT"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_ALLBSD_SOURCE"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_DARWIN_C_SOURCE -D_XOPEN_SOURCE"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -fno-rtti -fno-exceptions -fvisibility=hidden \
        -mno-omit-leaf-frame-pointer -mstack-alignment=16 -pipe -fno-strict-aliasing \
        -DMAC_OS_X_VERSION_MAX_ALLOWED=1070 -mmacosx-version-min=10.7.0 \
        -fno-omit-frame-pointer"
  elif test "x$OPENJDK_$1_OS" = xaix; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DAIX"
    # We may need '-qminimaltoc' or '-qpic=large -bbigtoc' if the TOC overflows.
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -qtune=balanced \
        -qalias=noansi -qstrict -qtls=default -qlanglvl=c99vla \
        -qlanglvl=noredefmac -qnortti -qnoeh -qignerrno"
  elif test "x$OPENJDK_$1_OS" = xbsd; then
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_ALLBSD_SOURCE"
  elif test "x$OPENJDK_$1_OS" = xwindows; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_WINDOWS -DWIN32 -D_JNI_IMPLEMENTATION_"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -nologo -W3 -MD -MP"
  fi

  # Set some additional per-CPU defines.
  if test "x$OPENJDK_$1_OS-$OPENJDK_$1_CPU" = xwindows-x86; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -arch:IA32"
  elif test "x$OPENJDK_$1_CPU" = xsparcv9; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -xarch=sparc"
  elif test "x$OPENJDK_$1_CPU" = xppc64; then
    if test "x$OPENJDK_$1_OS" = xlinux; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -minsert-sched-nops=regroup_exact -mno-multiple -mno-string"
      # fixes `relocation truncated to fit' error for gcc 4.1.
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -mminimal-toc"
      # Use ppc64 instructions, but schedule for power5
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -mcpu=powerpc64 -mtune=power5"
    elif test "x$OPENJDK_$1_OS" = xaix; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -qarch=ppc64"
    fi
  elif test "x$OPENJDK_$1_CPU" = xppc64le; then
    if test "x$OPENJDK_$1_OS" = xlinux; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -minsert-sched-nops=regroup_exact -mno-multiple -mno-string"
      # Little endian machine uses ELFv2 ABI.
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DABI_ELFv2"
      # Use Power8, this is the first CPU to support PPC64 LE with ELFv2 ABI.
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -mcpu=power8 -mtune=power8"
    fi
  elif test "x$OPENJDK_$1_CPU" = xs390x; then
    if test "x$OPENJDK_$1_OS" = xlinux; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -mbackchain -march=z10"
    fi
  fi

  if test "x$OPENJDK_$1_CPU_ENDIAN" = xlittle; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DVM_LITTLE_ENDIAN"
  fi

  if test "x$OPENJDK_$1_CPU_BITS" = x64; then
    if test "x$OPENJDK_$1_OS" != xsolaris && test "x$OPENJDK_$1_OS" != xaix; then
      # Solaris does not have _LP64=1 in the old build.
      # xlc on AIX defines _LP64=1 by default and issues a warning if we redefine it.
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_LP64=1"
    fi
  fi

  # Set $2JVM_CFLAGS warning handling
  if test "x$OPENJDK_$1_OS" = xlinux; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wpointer-arith -Wsign-compare -Wunused-function \
        -Wunused-value -Woverloaded-virtual"

    if test "x$TOOLCHAIN_TYPE" = xgcc; then
      TOOLCHAIN_CHECK_COMPILER_VERSION(VERSION: [4.8], PREFIX: $2,
          IF_AT_LEAST: [
            # These flags either do not work or give spurious warnings prior to gcc 4.8.
            $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wno-format-zero-length -Wtype-limits -Wuninitialized"
          ]
      )
    fi
    if ! HOTSPOT_CHECK_JVM_VARIANT(zero) && ! HOTSPOT_CHECK_JVM_VARIANT(zeroshark); then
      # Non-zero builds have stricter warnings
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wreturn-type -Wundef -Wformat=2"
    else
      if test "x$TOOLCHAIN_TYPE" = xclang; then
        # Some versions of llvm do not like -Wundef
        $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wno-undef"
      fi
    fi
  elif test "x$OPENJDK_$1_OS" = xmacosx; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wno-deprecated -Wpointer-arith \
        -Wsign-compare -Wundef -Wunused-function -Wformat=2"
  fi

  # Additional macosx handling
  if test "x$OPENJDK_$1_OS" = xmacosx; then
    # Setting these parameters makes it an error to link to macosx APIs that are
    # newer than the given OS version and makes the linked binaries compatible
    # even if built on a newer version of the OS.
    # The expected format is X.Y.Z
    MACOSX_VERSION_MIN=10.7.0
    AC_SUBST(MACOSX_VERSION_MIN)

    # The macro takes the version with no dots, ex: 1070
    # Let the flags variables get resolved in make for easier override on make
    # command line.
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DMAC_OS_X_VERSION_MAX_ALLOWED=\$(subst .,,\$(MACOSX_VERSION_MIN)) -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"
    $2LDFLAGS_JDK="[$]$2LDFLAGS_JDK -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"
  fi

  # Setup some hard coded includes
  $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK \
      -I\$(SUPPORT_OUTPUTDIR)/modules_include/java.base \
      -I${JDK_TOPDIR}/src/java.base/share/native/include \
      -I${JDK_TOPDIR}/src/java.base/$OPENJDK_$1_OS/native/include \
      -I${JDK_TOPDIR}/src/java.base/$OPENJDK_$1_OS_TYPE/native/include \
      -I${JDK_TOPDIR}/src/java.base/share/native/libjava \
      -I${JDK_TOPDIR}/src/java.base/$OPENJDK_$1_OS_TYPE/native/libjava"

  # The shared libraries are compiled using the picflag.
  $2CFLAGS_JDKLIB="[$]$2COMMON_CCXXFLAGS_JDK \
      [$]$2CFLAGS_JDK [$]$2EXTRA_CFLAGS_JDK $PICFLAG [$]$2CFLAGS_JDKLIB_EXTRA"
  $2CXXFLAGS_JDKLIB="[$]$2COMMON_CCXXFLAGS_JDK \
      [$]$2CXXFLAGS_JDK [$]$2EXTRA_CXXFLAGS_JDK $PICFLAG [$]$2CXXFLAGS_JDKLIB_EXTRA"

  # Executable flags
  $2CFLAGS_JDKEXE="[$]$2COMMON_CCXXFLAGS_JDK [$]$2CFLAGS_JDK [$]$2EXTRA_CFLAGS_JDK"
  $2CXXFLAGS_JDKEXE="[$]$2COMMON_CCXXFLAGS_JDK [$]$2CXXFLAGS_JDK [$]$2EXTRA_CXXFLAGS_JDK"

  AC_SUBST($2CFLAGS_JDKLIB)
  AC_SUBST($2CFLAGS_JDKEXE)
  AC_SUBST($2CXXFLAGS_JDKLIB)
  AC_SUBST($2CXXFLAGS_JDKEXE)

  # Setup LDFLAGS et al.
  #

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    LDFLAGS_MICROSOFT="-nologo -opt:ref"
    $2LDFLAGS_JDK="[$]$2LDFLAGS_JDK $LDFLAGS_MICROSOFT -incremental:no"
    $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LDFLAGS_MICROSOFT -opt:icf,8 -subsystem:windows -base:0x8000000"
    if test "x$OPENJDK_$1_CPU_BITS" = "x32"; then
      LDFLAGS_SAFESH="-safeseh"
      $2LDFLAGS_JDK="[$]$2LDFLAGS_JDK $LDFLAGS_SAFESH"
      $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LDFLAGS_SAFESH"
      # NOTE: Old build added -machine. Probably not needed.
      $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -machine:I386"
    else
      $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -machine:AMD64"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
      $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -mno-omit-leaf-frame-pointer -mstack-alignment=16 -stdlib=libstdc++ -fPIC"
      if test "x$OPENJDK_$1_OS" = xmacosx; then
        # FIXME: We should really generalize SET_SHARED_LIBRARY_ORIGIN instead.
        $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -Wl,-rpath,@loader_path/. -Wl,-rpath,@loader_path/.."
    fi
  elif test "x$TOOLCHAIN_TYPE" = xgcc; then
    # If this is a --hash-style=gnu system, use --hash-style=both, why?
    # We have previously set HAS_GNU_HASH if this is the case
    if test -n "$HAS_GNU_HASH"; then
      $2LDFLAGS_HASH_STYLE="-Wl,--hash-style=both"
      $2LDFLAGS_JDK="${$2LDFLAGS_JDK} [$]$2LDFLAGS_HASH_STYLE"
      $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS [$]$2LDFLAGS_HASH_STYLE"
    fi
      if test "x$OPENJDK_$1_OS" = xmacosx; then
        $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -Wl,-rpath,@loader_path/. -Wl,-rpath,@loader_path/.."
    fi
    if test "x$OPENJDK_$1_OS" = xlinux; then
      # And since we now know that the linker is gnu, then add -z defs, to forbid
      # undefined symbols in object files.
      LDFLAGS_NO_UNDEF_SYM="-Wl,-z,defs"
      $2LDFLAGS_JDK="${$2LDFLAGS_JDK} $LDFLAGS_NO_UNDEF_SYM"
      $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS  $LDFLAGS_NO_UNDEF_SYM"
      LDFLAGS_NO_EXEC_STACK="-Wl,-z,noexecstack"
      $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LDFLAGS_NO_EXEC_STACK"
      if test "x$OPENJDK_$1_CPU" = xx86; then
        $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -march=i586"
      fi
      case $DEBUG_LEVEL in
        release )
          # tell linker to optimize libraries.
          # Should this be supplied to the OSS linker as well?
          LDFLAGS_DEBUGLEVEL_release="-Wl,-O1"
          $2LDFLAGS_JDK="${$2LDFLAGS_JDK} $LDFLAGS_DEBUGLEVEL_release"
          $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LDFLAGS_DEBUGLEVEL_release"
          if test "x$HAS_LINKER_RELRO" = "xtrue"; then
            $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LINKER_RELRO_FLAG"
          fi
          ;;
        slowdebug )
          # Hotspot always let the linker optimize
          $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -Wl,-O1"
          if test "x$HAS_LINKER_NOW" = "xtrue"; then
            # do relocations at load
            $2LDFLAGS_JDK="[$]$2LDFLAGS_JDK $LINKER_NOW_FLAG"
            $2LDFLAGS_CXX_JDK="[$]$2LDFLAGS_CXX_JDK $LINKER_NOW_FLAG"
            $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LINKER_NOW_FLAG"
          fi
          if test "x$HAS_LINKER_RELRO" = "xtrue"; then
            # mark relocations read only
            $2LDFLAGS_JDK="[$]$2LDFLAGS_JDK $LINKER_RELRO_FLAG"
            $2LDFLAGS_CXX_JDK="[$]$2LDFLAGS_CXX_JDK $LINKER_RELRO_FLAG"
            $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LINKER_RELRO_FLAG"
          fi
          ;;
        fastdebug )
          # Hotspot always let the linker optimize
          $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -Wl,-O1"
          if test "x$HAS_LINKER_RELRO" = "xtrue"; then
            # mark relocations read only
            $2LDFLAGS_JDK="[$]$2LDFLAGS_JDK $LINKER_RELRO_FLAG"
            $2LDFLAGS_CXX_JDK="[$]$2LDFLAGS_CXX_JDK $LINKER_RELRO_FLAG"
            $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LINKER_RELRO_FLAG"
          fi
          ;;
        * )
          AC_MSG_ERROR([Unrecognized \$DEBUG_LEVEL: $DEBUG_LEVEL])
          ;;
        esac
    fi
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    LDFLAGS_SOLSTUDIO="-Wl,-z,defs"
    $2LDFLAGS_JDK="[$]$2LDFLAGS_JDK $LDFLAGS_SOLSTUDIO -ztext"
    LDFLAGS_CXX_SOLSTUDIO="-norunpath"
    $2LDFLAGS_CXX_JDK="[$]$2LDFLAGS_CXX_JDK $LDFLAGS_CXX_SOLSTUDIO -xnolib"
    $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LDFLAGS_SOLSTUDIO -library=%none -mt $LDFLAGS_CXX_SOLSTUDIO -z noversion"
    if test "x$OPENJDK_$1_CPU_ARCH" = "xsparc"; then
      $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS -xarch=sparc"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    LDFLAGS_XLC="-b64 -brtl -bnolibpath -bexpall -bernotok"
    $2LDFLAGS_JDK="${$2LDFLAGS_JDK} $LDFLAGS_XLC"
    $2JVM_LDFLAGS="[$]$2JVM_LDFLAGS $LDFLAGS_XLC"
  fi

  # Customize LDFLAGS for executables

  $2LDFLAGS_JDKEXE="${$2LDFLAGS_JDK}"

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$OPENJDK_$1_CPU_BITS" = "x64"; then
      LDFLAGS_STACK_SIZE=1048576
    else
      LDFLAGS_STACK_SIZE=327680
    fi
    $2LDFLAGS_JDKEXE="${$2LDFLAGS_JDKEXE} /STACK:$LDFLAGS_STACK_SIZE"
  elif test "x$OPENJDK_$1_OS" = xlinux; then
    $2LDFLAGS_JDKEXE="[$]$2LDFLAGS_JDKEXE -Wl,--allow-shlib-undefined"
  fi

  $2LDFLAGS_JDKEXE="${$2LDFLAGS_JDKEXE} ${$2EXTRA_LDFLAGS_JDK}"

  # Customize LDFLAGS for libs
  $2LDFLAGS_JDKLIB="${$2LDFLAGS_JDK}"

  $2LDFLAGS_JDKLIB="${$2LDFLAGS_JDKLIB} ${SHARED_LIBRARY_FLAGS}"
  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    $2JAVA_BASE_LDFLAGS="${$2JAVA_BASE_LDFLAGS} \
        -libpath:${OUTPUT_ROOT}/support/modules_libs/java.base"
    $2JDKLIB_LIBS=""
  else
    $2JAVA_BASE_LDFLAGS="${$2JAVA_BASE_LDFLAGS} \
        -L\$(SUPPORT_OUTPUTDIR)/modules_libs/java.base"

    if test "x$1" = "xTARGET"; then
      # On some platforms (mac) the linker warns about non existing -L dirs.
      # For any of the variants server, client or minimal, the dir matches the
      # variant name. The "main" variant should be used for linking. For the
      # rest, the dir is just server.
      if HOTSPOT_CHECK_JVM_VARIANT(server) || HOTSPOT_CHECK_JVM_VARIANT(client) \
          || HOTSPOT_CHECK_JVM_VARIANT(minimal); then
        $2JAVA_BASE_LDFLAGS="${$2JAVA_BASE_LDFLAGS} \
            -L\$(SUPPORT_OUTPUTDIR)/modules_libs/java.base/$JVM_VARIANT_MAIN"
      else
        $2JAVA_BASE_LDFLAGS="${$2JAVA_BASE_LDFLAGS} \
            -L\$(SUPPORT_OUTPUTDIR)/modules_libs/java.base/server"
      fi
    elif test "x$1" = "xBUILD"; then
      # When building a buildjdk, it's always only the server variant
      $2JAVA_BASE_LDFLAGS="${$2JAVA_BASE_LDFLAGS} \
          -L\$(SUPPORT_OUTPUTDIR)/modules_libs/java.base/server"
    fi

    $2JDKLIB_LIBS="-ljava -ljvm"
    if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
      $2JDKLIB_LIBS="[$]$2JDKLIB_LIBS -lc"
    fi

  fi

$2LDFLAGS_JDKLIB="${$2LDFLAGS_JDKLIB} ${$2JAVA_BASE_LDFLAGS}"

  # Set $2JVM_LIBS (per os)
  if test "x$OPENJDK_$1_OS" = xlinux; then
    $2JVM_LIBS="[$]$2JVM_LIBS -lm -ldl -lpthread"
  elif test "x$OPENJDK_$1_OS" = xsolaris; then
    # FIXME: This hard-coded path is not really proper.
    if test "x$OPENJDK_$1_CPU" = xx86_64; then
      $2SOLARIS_LIBM_LIBS="/usr/lib/amd64/libm.so.1"
    elif test "x$OPENJDK_$1_CPU" = xsparcv9; then
      $2SOLARIS_LIBM_LIBS="/usr/lib/sparcv9/libm.so.1"
    fi
    $2JVM_LIBS="[$]$2JVM_LIBS -lsocket -lsched -ldl $SOLARIS_LIBM_LIBS -lCrun \
        -lthread -ldoor -lc -ldemangle -lnsl -lkstat -lrt"
  elif test "x$OPENJDK_$1_OS" = xmacosx; then
    $2JVM_LIBS="[$]$2JVM_LIBS -lm"
  elif test "x$OPENJDK_$1_OS" = xaix; then
    $2JVM_LIBS="[$]$2JVM_LIBS -Wl,-lC_r -lm -ldl -lpthread"
  elif test "x$OPENJDK_$1_OS" = xbsd; then
    $2JVM_LIBS="[$]$2JVM_LIBS -lm"
  elif test "x$OPENJDK_$1_OS" = xwindows; then
    $2JVM_LIBS="[$]$2JVM_LIBS kernel32.lib user32.lib gdi32.lib winspool.lib \
        comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib \
        wsock32.lib winmm.lib version.lib psapi.lib"
    fi

  # Set $2JVM_ASFLAGS
  if test "x$OPENJDK_$1_OS" = xlinux; then
    if test "x$OPENJDK_$1_CPU" = xx86; then
      $2JVM_ASFLAGS="[$]$2JVM_ASFLAGS -march=i586"
    fi
  elif test "x$OPENJDK_$1_OS" = xmacosx; then
    $2JVM_ASFLAGS="[$]$2JVM_ASFLAGS -x assembler-with-cpp -mno-omit-leaf-frame-pointer -mstack-alignment=16"
  fi

  $2LDFLAGS_JDKLIB="${$2LDFLAGS_JDKLIB} ${$2EXTRA_LDFLAGS_JDK}"

  AC_SUBST($2LDFLAGS_JDKLIB)
  AC_SUBST($2LDFLAGS_JDKEXE)
  AC_SUBST($2JDKLIB_LIBS)
  AC_SUBST($2JDKEXE_LIBS)
  AC_SUBST($2LDFLAGS_CXX_JDK)
  AC_SUBST($2LDFLAGS_HASH_STYLE)

  AC_SUBST($2JVM_CFLAGS)
  AC_SUBST($2JVM_LDFLAGS)
  AC_SUBST($2JVM_ASFLAGS)
  AC_SUBST($2JVM_LIBS)

])

# FLAGS_C_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARGUMENT], IF_TRUE: [RUN-IF-TRUE],
#                                  IF_FALSE: [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the C compiler supports an argument
BASIC_DEFUN_NAMED([FLAGS_C_COMPILER_CHECK_ARGUMENTS],
    [*ARGUMENT IF_TRUE IF_FALSE], [$@],
[
  AC_MSG_CHECKING([if the C compiler supports "ARG_ARGUMENT"])
  supports=yes

  saved_cflags="$CFLAGS"
  CFLAGS="$CFLAGS ARG_ARGUMENT"
  AC_LANG_PUSH([C])
  AC_COMPILE_IFELSE([AC_LANG_SOURCE([[int i;]])], [],
      [supports=no])
  AC_LANG_POP([C])
  CFLAGS="$saved_cflags"

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    :
    ARG_IF_TRUE
  else
    :
    ARG_IF_FALSE
  fi
])

# FLAGS_CXX_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARGUMENT], IF_TRUE: [RUN-IF-TRUE],
#                                    IF_FALSE: [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the C++ compiler supports an argument
BASIC_DEFUN_NAMED([FLAGS_CXX_COMPILER_CHECK_ARGUMENTS],
    [*ARGUMENT IF_TRUE IF_FALSE], [$@],
[
  AC_MSG_CHECKING([if the C++ compiler supports "ARG_ARGUMENT"])
  supports=yes

  saved_cxxflags="$CXXFLAGS"
  CXXFLAGS="$CXXFLAG ARG_ARGUMENT"
  AC_LANG_PUSH([C++])
  AC_COMPILE_IFELSE([AC_LANG_SOURCE([[int i;]])], [],
      [supports=no])
  AC_LANG_POP([C++])
  CXXFLAGS="$saved_cxxflags"

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    :
    ARG_IF_TRUE
  else
    :
    ARG_IF_FALSE
  fi
])

# FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARGUMENT], IF_TRUE: [RUN-IF-TRUE],
#                                IF_FALSE: [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the C and C++ compilers support an argument
BASIC_DEFUN_NAMED([FLAGS_COMPILER_CHECK_ARGUMENTS],
    [*ARGUMENT IF_TRUE IF_FALSE], [$@],
[
  FLAGS_C_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARG_ARGUMENT],
  					     IF_TRUE: [C_COMP_SUPPORTS="yes"],
					     IF_FALSE: [C_COMP_SUPPORTS="no"])
  FLAGS_CXX_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARG_ARGUMENT],
  					       IF_TRUE: [CXX_COMP_SUPPORTS="yes"],
					       IF_FALSE: [CXX_COMP_SUPPORTS="no"])

  AC_MSG_CHECKING([if both compilers support "ARG_ARGUMENT"])
  supports=no
  if test "x$C_COMP_SUPPORTS" = "xyes" -a "x$CXX_COMP_SUPPORTS" = "xyes"; then supports=yes; fi

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    :
    ARG_IF_TRUE
  else
    :
    ARG_IF_FALSE
  fi
])

# FLAGS_LINKER_CHECK_ARGUMENTS(ARGUMENT: [ARGUMENT], IF_TRUE: [RUN-IF-TRUE],
#                                   IF_FALSE: [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the linker support an argument
BASIC_DEFUN_NAMED([FLAGS_LINKER_CHECK_ARGUMENTS],
    [*ARGUMENT IF_TRUE IF_FALSE], [$@],
[
  AC_MSG_CHECKING([if linker supports "ARG_ARGUMENT"])
  supports=yes

  saved_ldflags="$LDFLAGS"
  LDFLAGS="$LDFLAGS ARG_ARGUMENT"
  AC_LANG_PUSH([C])
  AC_LINK_IFELSE([AC_LANG_PROGRAM([[]],[[]])],
      [], [supports=no])
  AC_LANG_POP([C])
  LDFLAGS="$saved_ldflags"

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    :
    ARG_IF_TRUE
  else
    :
    ARG_IF_FALSE
  fi
])

AC_DEFUN_ONCE([FLAGS_SETUP_COMPILER_FLAGS_MISC],
[
  # Some Zero and Shark settings.
  # ZERO_ARCHFLAG tells the compiler which mode to build for
  case "${OPENJDK_TARGET_CPU}" in
    s390)
      ZERO_ARCHFLAG="${COMPILER_TARGET_BITS_FLAG}31"
      ;;
    *)
      ZERO_ARCHFLAG="${COMPILER_TARGET_BITS_FLAG}${OPENJDK_TARGET_CPU_BITS}"
  esac
  FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$ZERO_ARCHFLAG], IF_FALSE: [ZERO_ARCHFLAG=""])
  AC_SUBST(ZERO_ARCHFLAG)

  # Check that the compiler supports -mX (or -qX on AIX) flags
  # Set COMPILER_SUPPORTS_TARGET_BITS_FLAG to 'true' if it does
  FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${COMPILER_TARGET_BITS_FLAG}${OPENJDK_TARGET_CPU_BITS}],
      IF_TRUE: [COMPILER_SUPPORTS_TARGET_BITS_FLAG=true],
      IF_FALSE: [COMPILER_SUPPORTS_TARGET_BITS_FLAG=false])
  AC_SUBST(COMPILER_SUPPORTS_TARGET_BITS_FLAG)

  AC_ARG_ENABLE([warnings-as-errors], [AS_HELP_STRING([--disable-warnings-as-errors],
      [do not consider native warnings to be an error @<:@enabled@:>@])])

  AC_MSG_CHECKING([if native warnings are errors])
  if test "x$enable_warnings_as_errors" = "xyes"; then
    AC_MSG_RESULT([yes (explicitly set)])
    WARNINGS_AS_ERRORS=true
  elif test "x$enable_warnings_as_errors" = "xno"; then
    AC_MSG_RESULT([no])
    WARNINGS_AS_ERRORS=false
  elif test "x$enable_warnings_as_errors" = "x"; then
    AC_MSG_RESULT([yes (default)])
    WARNINGS_AS_ERRORS=true
  else
    AC_MSG_ERROR([--enable-warnings-as-errors accepts no argument])
  fi

  if test "x$WARNINGS_AS_ERRORS" = "xfalse"; then
    # Set legacy hotspot variable
    HOTSPOT_SET_WARNINGS_AS_ERRORS="WARNINGS_ARE_ERRORS="
  else
    HOTSPOT_SET_WARNINGS_AS_ERRORS=""
  fi

  AC_SUBST(WARNINGS_AS_ERRORS)
  AC_SUBST(HOTSPOT_SET_WARNINGS_AS_ERRORS)

  case "${TOOLCHAIN_TYPE}" in
    microsoft)
      DISABLE_WARNING_PREFIX="-wd"
      CFLAGS_WARNINGS_ARE_ERRORS="-WX"
      ;;
    solstudio)
      DISABLE_WARNING_PREFIX="-erroff="
      CFLAGS_WARNINGS_ARE_ERRORS="-errtags -errwarn=%all"
      ;;
    gcc)
      # Prior to gcc 4.4, a -Wno-X where X is unknown for that version of gcc will cause an error
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [-Wno-this-is-a-warning-that-do-not-exist],
          IF_TRUE: [GCC_CAN_DISABLE_WARNINGS=true],
          IF_FALSE: [GCC_CAN_DISABLE_WARNINGS=false]
      )
      if test "x$GCC_CAN_DISABLE_WARNINGS" = "xtrue"; then
        DISABLE_WARNING_PREFIX="-Wno-"
      else
        DISABLE_WARNING_PREFIX=
      fi
      CFLAGS_WARNINGS_ARE_ERRORS="-Werror"
      # Repeate the check for the BUILD_CC and BUILD_CXX. Need to also reset
      # CFLAGS since any target specific flags will likely not work with the
      # build compiler
      CC_OLD="$CC"
      CXX_OLD="$CXX"
      CC="$BUILD_CC"
      CXX="$BUILD_CXX"
      CFLAGS_OLD="$CFLAGS"
      CFLAGS=""
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [-Wno-this-is-a-warning-that-do-not-exist],
          IF_TRUE: [BUILD_CC_CAN_DISABLE_WARNINGS=true],
          IF_FALSE: [BUILD_CC_CAN_DISABLE_WARNINGS=false]
      )
      if test "x$BUILD_CC_CAN_DISABLE_WARNINGS" = "xtrue"; then
        BUILD_CC_DISABLE_WARNING_PREFIX="-Wno-"
      else
        BUILD_CC_DISABLE_WARNING_PREFIX=
      fi
      CC="$CC_OLD"
      CXX="$CXX_OLD"
      CFLAGS="$CFLAGS_OLD"
      ;;
    clang)
      DISABLE_WARNING_PREFIX="-Wno-"
      CFLAGS_WARNINGS_ARE_ERRORS="-Werror"
      ;;
    xlc)
      DISABLE_WARNING_PREFIX="-qsuppress="
      CFLAGS_WARNINGS_ARE_ERRORS="-qhalt=w"
      ;;
  esac
  AC_SUBST(DISABLE_WARNING_PREFIX)
  AC_SUBST(BUILD_CC_DISABLE_WARNING_PREFIX)
  AC_SUBST(CFLAGS_WARNINGS_ARE_ERRORS)
])

# FLAGS_SETUP_GCC6_COMPILER_FLAGS([PREFIX])
# Arguments:
# $1 - Optional prefix for each variable defined.
AC_DEFUN([FLAGS_SETUP_GCC6_COMPILER_FLAGS],
[
  # These flags are required for GCC 6 builds as undefined behaviour in OpenJDK code
  # runs afoul of the more aggressive versions of these optimisations.
  # Notably, value range propagation now assumes that the this pointer of C++
  # member functions is non-null.
  NO_DELETE_NULL_POINTER_CHECKS_CFLAG="-fno-delete-null-pointer-checks"
  dnl Argument check is disabled until FLAGS_COMPILER_CHECK_ARGUMENTS handles cross-compilation
  dnl FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$NO_DELETE_NULL_POINTER_CHECKS_CFLAG -Werror],
  dnl					     IF_FALSE: [NO_DELETE_NULL_POINTER_CHECKS_CFLAG=""])
  NO_LIFETIME_DSE_CFLAG="-fno-lifetime-dse"
  dnl Argument check is disabled until FLAGS_COMPILER_CHECK_ARGUMENTS handles cross-compilation
  dnl FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$NO_LIFETIME_DSE_CFLAG -Werror],
  dnl					     IF_FALSE: [NO_LIFETIME_DSE_CFLAG=""])
  AC_MSG_NOTICE([GCC >= 6 detected; adding ${NO_DELETE_NULL_POINTER_CHECKS_CFLAG} and ${NO_LIFETIME_DSE_CFLAG}])
  $1CFLAGS_JDK="[$]$1CFLAGS_JDK ${NO_DELETE_NULL_POINTER_CHECKS_CFLAG} ${NO_LIFETIME_DSE_CFLAG}"
  $1JVM_CFLAGS="[$]$1JVM_CFLAGS ${NO_DELETE_NULL_POINTER_CHECKS_CFLAG} ${NO_LIFETIME_DSE_CFLAG}"
])
