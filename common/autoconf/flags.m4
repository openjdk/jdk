#
# Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

AC_DEFUN_ONCE([FLAGS_SETUP_INIT_FLAGS],
[
  # Option used to tell the compiler whether to create 32- or 64-bit executables
  if test "x$TOOLCHAIN_TYPE" = xxlc; then
    COMPILER_TARGET_BITS_FLAG="-q"
  else
    COMPILER_TARGET_BITS_FLAG="-m"
  fi
  AC_SUBST(COMPILER_TARGET_BITS_FLAG)

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

  if test "x$OPENJDK_TARGET_OS" != xwindows; then
    POST_STRIP_CMD="$STRIP $STRIPFLAGS"
  fi
  AC_SUBST(POST_STRIP_CMD)

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
    if test "x$VARIANT" = xOPT; then
      RC_FLAGS="$RC_FLAGS -DNDEBUG"
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
        -D\"JDK_VER=\$(VERSION_NUMBER_FOUR_POSITIONS)\" \
        -D\"JDK_COPYRIGHT=Copyright \xA9 $COPYRIGHT_YEAR\" \
        -D\"JDK_NAME=\$(PRODUCT_NAME) \$(JDK_RC_PLATFORM_NAME) \$(VERSION_MAJOR)\" \
        -D\"JDK_FVER=\$(subst .,\$(COMMA),\$(VERSION_NUMBER_FOUR_POSITIONS))\""
  fi
  AC_SUBST(RC_FLAGS)

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    # silence copyright notice and other headers.
    COMMON_CCXXFLAGS="$COMMON_CCXXFLAGS -nologo"
  fi

  if test "x$SYSROOT" != "x"; then
    if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
      if test "x$OPENJDK_TARGET_OS" = xsolaris; then
        # Solaris Studio does not have a concept of sysroot. Instead we must
        # make sure the default include and lib dirs are appended to each
        # compile and link command line.
        SYSROOT_CFLAGS="-I$SYSROOT/usr/include"
        SYSROOT_LDFLAGS="-L$SYSROOT/usr/lib$OPENJDK_TARGET_CPU_ISADIR \
            -L$SYSROOT/lib$OPENJDK_TARGET_CPU_ISADIR \
            -L$SYSROOT/usr/ccs/lib$OPENJDK_TARGET_CPU_ISADIR"
      fi
    elif test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Apple only wants -isysroot <path>, but we also need -iframework<path>/System/Library/Frameworks
      SYSROOT_CFLAGS="-isysroot \"$SYSROOT\" -iframework\"$SYSROOT/System/Library/Frameworks\""
      SYSROOT_LDFLAGS=$SYSROOT_CFLAGS
    elif test "x$TOOLCHAIN_TYPE" = xgcc; then
      SYSROOT_CFLAGS="--sysroot=$SYSROOT"
      SYSROOT_LDFLAGS="--sysroot=$SYSROOT"
    elif test "x$TOOLCHAIN_TYPE" = xclang; then
      SYSROOT_CFLAGS="-isysroot \"$SYSROOT\""
      SYSROOT_LDFLAGS="-isysroot \"$SYSROOT\""
    fi
    # Propagate the sysroot args to hotspot
    LEGACY_EXTRA_CFLAGS="$LEGACY_EXTRA_CFLAGS $SYSROOT_CFLAGS"
    LEGACY_EXTRA_CXXFLAGS="$LEGACY_EXTRA_CXXFLAGS $SYSROOT_CFLAGS"
    LEGACY_EXTRA_LDFLAGS="$LEGACY_EXTRA_LDFLAGS $SYSROOT_LDFLAGS"
  fi

  # These always need to be set, or we can't find the frameworks embedded in JavaVM.framework
  # set this here so it doesn't have to be peppered throughout the forest
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    SYSROOT_CFLAGS="$SYSROOT_CFLAGS -F\"$SYSROOT/System/Library/Frameworks/JavaVM.framework/Frameworks\""
    SYSROOT_LDFLAGS="$SYSROOT_LDFLAGS -F\"$SYSROOT/System/Library/Frameworks/JavaVM.framework/Frameworks\""
  fi

  AC_SUBST(SYSROOT_CFLAGS)
  AC_SUBST(SYSROOT_LDFLAGS)
])

AC_DEFUN_ONCE([FLAGS_SETUP_COMPILER_FLAGS_FOR_LIBS],
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
      SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0 $PICFLAG"
      SET_EXECUTABLE_ORIGIN='-Xlinker -rpath -Xlinker @loader_path/.'
      SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Xlinker -install_name -Xlinker @rpath/[$]1'
      SET_SHARED_LIBRARY_MAPFILE=''
    else
      # Default works for linux, might work on other platforms as well.
      SHARED_LIBRARY_FLAGS='-shared'
      SET_EXECUTABLE_ORIGIN='-Xlinker -rpath -Xlinker \$$$$ORIGIN[$]1'
      SET_SHARED_LIBRARY_ORIGIN="-Xlinker -z -Xlinker origin $SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Xlinker -soname=[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Xlinker -version-script=[$]1'
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    PICFLAG=''
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''

    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Linking is different on MacOSX
      SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0 $PICFLAG"
      SET_EXECUTABLE_ORIGIN='-Xlinker -rpath -Xlinker @loader_path/.'
      SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Xlinker -install_name -Xlinker @rpath/[$]1'
      SET_SHARED_LIBRARY_MAPFILE=''
    else
      # Default works for linux, might work on other platforms as well.
      SHARED_LIBRARY_FLAGS='-shared'
      SET_EXECUTABLE_ORIGIN='-Xlinker -rpath -Xlinker \$$$$ORIGIN[$]1'
      SET_SHARED_LIBRARY_ORIGIN="-Xlinker -z -Xlinker origin $SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Xlinker -soname=[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Xlinker -version-script=[$]1'
    fi
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    PICFLAG="-KPIC"
    C_FLAG_REORDER='-xF'
    CXX_FLAG_REORDER='-xF'
    SHARED_LIBRARY_FLAGS="-G"
    SET_EXECUTABLE_ORIGIN='-R\$$$$ORIGIN[$]1'
    SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE='-M[$]1'
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    PICFLAG="-qpic=large"
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''
    SHARED_LIBRARY_FLAGS="-qmkshrobj"
    SET_EXECUTABLE_ORIGIN=""
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE=''
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    PICFLAG=""
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''
    SHARED_LIBRARY_FLAGS="-LD"
    SET_EXECUTABLE_ORIGIN=''
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE=''
  fi

  AC_SUBST(C_FLAG_REORDER)
  AC_SUBST(CXX_FLAG_REORDER)
  AC_SUBST(SET_EXECUTABLE_ORIGIN)
  AC_SUBST(SET_SHARED_LIBRARY_ORIGIN)
  AC_SUBST(SET_SHARED_LIBRARY_NAME)
  AC_SUBST(SET_SHARED_LIBRARY_MAPFILE)

  if test "x$OPENJDK_TARGET_OS" = xsolaris; then
    CFLAGS_JDK="${CFLAGS_JDK} -D__solaris__"
    CXXFLAGS_JDK="${CXXFLAGS_JDK} -D__solaris__"
    CFLAGS_JDKLIB_EXTRA='-xstrconst'
  fi
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
    # FIXME: likely a bug, this disables debug symbols rather than enables them
    CXXFLAGS_DEBUG_SYMBOLS="-g0 -xs"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    CFLAGS_DEBUG_SYMBOLS="-g"
    CXXFLAGS_DEBUG_SYMBOLS="-g"
  fi
  AC_SUBST(CFLAGS_DEBUG_SYMBOLS)
  AC_SUBST(CXXFLAGS_DEBUG_SYMBOLS)

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
      # Add runtime stack smashing and undefined behavior checks.
      # Not all versions of gcc support -fstack-protector
      STACK_PROTECTOR_CFLAG="-fstack-protector-all"
      FLAGS_COMPILER_CHECK_ARGUMENTS([$STACK_PROTECTOR_CFLAG], [], [STACK_PROTECTOR_CFLAG=""])

      CFLAGS_DEBUG_OPTIONS="$STACK_PROTECTOR_CFLAG --param ssp-buffer-size=1"
      CXXFLAGS_DEBUG_OPTIONS="$STACK_PROTECTOR_CFLAG --param ssp-buffer-size=1"
      ;;
    esac
  fi

  # Optimization levels
  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    CC_HIGHEST="$CC_HIGHEST -fns -fsimple -fsingle -xbuiltin=%all -xdepend -xrestrict -xlibmil"

    if test "x$OPENJDK_TARGET_CPU_ARCH" = "xx86"; then
      # FIXME: seems we always set -xregs=no%frameptr; put it elsewhere more global?
      C_O_FLAG_HIGHEST="-xO4 -Wu,-O4~yz $CC_HIGHEST -xalias_level=basic -xregs=no%frameptr"
      C_O_FLAG_HI="-xO4 -Wu,-O4~yz -xregs=no%frameptr"
      C_O_FLAG_NORM="-xO2 -Wu,-O2~yz -xregs=no%frameptr"
      C_O_FLAG_DEBUG="-xregs=no%frameptr"
      C_O_FLAG_NONE="-xregs=no%frameptr"
      CXX_O_FLAG_HIGHEST="-xO4 -Qoption ube -O4~yz $CC_HIGHEST -xregs=no%frameptr"
      CXX_O_FLAG_HI="-xO4 -Qoption ube -O4~yz -xregs=no%frameptr"
      CXX_O_FLAG_NORM="-xO2 -Qoption ube -O2~yz -xregs=no%frameptr"
      CXX_O_FLAG_DEBUG="-xregs=no%frameptr"
      CXX_O_FLAG_NONE="-xregs=no%frameptr"
      if test "x$OPENJDK_TARGET_CPU_BITS" = "x32"; then
        C_O_FLAG_HIGHEST="$C_O_FLAG_HIGHEST -xchip=pentium"
        CXX_O_FLAG_HIGHEST="$CXX_O_FLAG_HIGHEST -xchip=pentium"
      fi
    elif test "x$OPENJDK_TARGET_CPU_ARCH" = "xsparc"; then
      C_O_FLAG_HIGHEST="-xO4 -Wc,-Qrm-s -Wc,-Qiselect-T0 $CC_HIGHEST -xalias_level=basic -xprefetch=auto,explicit -xchip=ultra"
      C_O_FLAG_HI="-xO4 -Wc,-Qrm-s -Wc,-Qiselect-T0"
      C_O_FLAG_NORM="-xO2 -Wc,-Qrm-s -Wc,-Qiselect-T0"
      C_O_FLAG_DEBUG=""
      C_O_FLAG_NONE=""
      CXX_O_FLAG_HIGHEST="-xO4 -Qoption cg -Qrm-s -Qoption cg -Qiselect-T0 $CC_HIGHEST -xprefetch=auto,explicit -xchip=ultra"
      CXX_O_FLAG_HI="-xO4 -Qoption cg -Qrm-s -Qoption cg -Qiselect-T0"
      CXX_O_FLAG_NORM="-xO2 -Qoption cg -Qrm-s -Qoption cg -Qiselect-T0"
      C_O_FLAG_DEBUG=""
      CXX_O_FLAG_NONE=""
    fi
  else
    # The remaining toolchains share opt flags between CC and CXX;
    # setup for C and duplicate afterwards.
    if test "x$TOOLCHAIN_TYPE" = xgcc; then
      if test "x$OPENJDK_TARGET_OS" = xmacosx; then
        # On MacOSX we optimize for size, something
        # we should do for all platforms?
        C_O_FLAG_HIGHEST="-Os"
        C_O_FLAG_HI="-Os"
        C_O_FLAG_NORM="-Os"
      else
        C_O_FLAG_HIGHEST="-O3"
        C_O_FLAG_HI="-O3"
        C_O_FLAG_NORM="-O2"
      fi
      C_O_FLAG_DEBUG="-O0"
      C_O_FLAG_NONE="-O0"
    elif test "x$TOOLCHAIN_TYPE" = xclang; then
      if test "x$OPENJDK_TARGET_OS" = xmacosx; then
        # On MacOSX we optimize for size, something
        # we should do for all platforms?
        C_O_FLAG_HIGHEST="-Os"
        C_O_FLAG_HI="-Os"
        C_O_FLAG_NORM="-Os"
      else
        C_O_FLAG_HIGHEST="-O3"
        C_O_FLAG_HI="-O3"
        C_O_FLAG_NORM="-O2"
      fi
      C_O_FLAG_DEBUG="-O0"
      C_O_FLAG_NONE="-O0"
    elif test "x$TOOLCHAIN_TYPE" = xxlc; then
      C_O_FLAG_HIGHEST="-O3"
      C_O_FLAG_HI="-O3 -qstrict"
      C_O_FLAG_NORM="-O2"
      C_O_FLAG_DEBUG="-qnoopt"
      C_O_FLAG_NONE="-qnoop"
    elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
      C_O_FLAG_HIGHEST="-O2"
      C_O_FLAG_HI="-O1"
      C_O_FLAG_NORM="-O1"
      C_O_FLAG_DEBUG="-Od"
      C_O_FLAG_NONE="-Od"
    fi
    CXX_O_FLAG_HIGHEST="$C_O_FLAG_HIGHEST"
    CXX_O_FLAG_HI="$C_O_FLAG_HI"
    CXX_O_FLAG_NORM="$C_O_FLAG_NORM"
    CXX_O_FLAG_DEBUG="$C_O_FLAG_DEBUG"
    CXX_O_FLAG_NONE="$C_O_FLAG_NONE"
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
      C_O_FLAG_HIGHEST="$C_O_FLAG_DEBUG"
      C_O_FLAG_HI="$C_O_FLAG_DEBUG"
      C_O_FLAG_NORM="$C_O_FLAG_DEBUG"
      CXX_O_FLAG_HIGHEST="$CXX_O_FLAG_DEBUG"
      CXX_O_FLAG_HI="$CXX_O_FLAG_DEBUG"
      CXX_O_FLAG_NORM="$CXX_O_FLAG_DEBUG"
      ;;
  esac

  AC_SUBST(C_O_FLAG_HIGHEST)
  AC_SUBST(C_O_FLAG_HI)
  AC_SUBST(C_O_FLAG_NORM)
  AC_SUBST(C_O_FLAG_DEBUG)
  AC_SUBST(C_O_FLAG_NONE)
  AC_SUBST(CXX_O_FLAG_HIGHEST)
  AC_SUBST(CXX_O_FLAG_HI)
  AC_SUBST(CXX_O_FLAG_NORM)
  AC_SUBST(CXX_O_FLAG_DEBUG)
  AC_SUBST(CXX_O_FLAG_NONE)
])

AC_DEFUN_ONCE([FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK],
[
  # Special extras...
  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    if test "x$OPENJDK_TARGET_CPU_ARCH" = "xsparc"; then
      CFLAGS_JDKLIB_EXTRA="${CFLAGS_JDKLIB_EXTRA} -xregs=no%appl"
      CXXFLAGS_JDKLIB_EXTRA="${CXXFLAGS_JDKLIB_EXTRA} -xregs=no%appl"
    fi
    CFLAGS_JDKLIB_EXTRA="${CFLAGS_JDKLIB_EXTRA} -errtags=yes -errfmt"
    CXXFLAGS_JDKLIB_EXTRA="${CXXFLAGS_JDKLIB_EXTRA} -errtags=yes -errfmt"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    CFLAGS_JDK="${CFLAGS_JDK} -qchars=signed -qfullpath -qsaveopt"
    CXXFLAGS_JDK="${CXXFLAGS_JDK} -qchars=signed -qfullpath -qsaveopt"
  fi

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

  CFLAGS_JDK="${CFLAGS_JDK} $with_extra_cflags"
  CXXFLAGS_JDK="${CXXFLAGS_JDK} $with_extra_cxxflags"
  LDFLAGS_JDK="${LDFLAGS_JDK} $with_extra_ldflags"

  # Hotspot needs these set in their legacy form
  LEGACY_EXTRA_CFLAGS="$LEGACY_EXTRA_CFLAGS $with_extra_cflags"
  LEGACY_EXTRA_CXXFLAGS="$LEGACY_EXTRA_CXXFLAGS $with_extra_cxxflags"
  LEGACY_EXTRA_LDFLAGS="$LEGACY_EXTRA_LDFLAGS $with_extra_ldflags"

  AC_SUBST(LEGACY_EXTRA_CFLAGS)
  AC_SUBST(LEGACY_EXTRA_CXXFLAGS)
  AC_SUBST(LEGACY_EXTRA_LDFLAGS)

  ###############################################################################
  #
  # Now setup the CFLAGS and LDFLAGS for the JDK build.
  # Later we will also have CFLAGS and LDFLAGS for the hotspot subrepo build.
  #

  # Setup compiler/platform specific flags into
  #    CFLAGS_JDK    - C Compiler flags
  #    CXXFLAGS_JDK  - C++ Compiler flags
  #    COMMON_CCXXFLAGS_JDK - common to C and C++
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$OPENJDK_TARGET_CPU" = xx86; then
      # Force compatibility with i586 on 32 bit intel platforms.
      COMMON_CCXXFLAGS="${COMMON_CCXXFLAGS} -march=i586"
    fi
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS $COMMON_CCXXFLAGS_JDK -Wall -Wextra -Wno-unused -Wno-unused-parameter -Wformat=2 \
        -pipe -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE"
    case $OPENJDK_TARGET_CPU_ARCH in
      arm )
        # on arm we don't prevent gcc to omit frame pointer but do prevent strict aliasing
        CFLAGS_JDK="${CFLAGS_JDK} -fno-strict-aliasing"
        ;;
      ppc )
        # on ppc we don't prevent gcc to omit frame pointer but do prevent strict aliasing
        CFLAGS_JDK="${CFLAGS_JDK} -fno-strict-aliasing"
        ;;
      * )
        COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -fno-omit-frame-pointer"
        CFLAGS_JDK="${CFLAGS_JDK} -fno-strict-aliasing"
        ;;
    esac
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS $COMMON_CCXXFLAGS_JDK -DTRACING -DMACRO_MEMSYS_OPS -DBREAKPTS"
    if test "x$OPENJDK_TARGET_CPU_ARCH" = xx86; then
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -DcpuIntel -Di586 -D$OPENJDK_TARGET_CPU_LEGACY_LIB"
    fi

    CFLAGS_JDK="$CFLAGS_JDK -xc99=%none -xCC -errshort=tags -Xa -v -mt -W0,-noglobal"
    CXXFLAGS_JDK="$CXXFLAGS_JDK -errtags=yes +w -mt -features=no%except -DCC_NOEX -norunpath -xnolib"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    CFLAGS_JDK="$CFLAGS_JDK -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE -DSTDC"
    CXXFLAGS_JDK="$CXXFLAGS_JDK -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE -DSTDC"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS $COMMON_CCXXFLAGS_JDK \
        -Zi -MD -Zc:wchar_t- -W3 -wd4800 \
        -DWIN32_LEAN_AND_MEAN \
        -D_CRT_SECURE_NO_DEPRECATE -D_CRT_NONSTDC_NO_DEPRECATE \
        -D_WINSOCK_DEPRECATED_NO_WARNINGS \
        -DWIN32 -DIAL"
    if test "x$OPENJDK_TARGET_CPU" = xx86_64; then
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D_AMD64_ -Damd64"
    else
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D_X86_ -Dx86"
    fi
    # If building with Visual Studio 2010, we can still use _STATIC_CPPLIB to
    # avoid bundling msvcpNNN.dll. Doesn't work with newer versions of visual
    # studio.
    if test "x$TOOLCHAIN_VERSION" = "x2010"; then
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK \
          -D_STATIC_CPPLIB -D_DISABLE_DEPRECATE_STATIC_CPPLIB"
    fi
  fi

  ###############################################################################

  # Adjust flags according to debug level.
  case $DEBUG_LEVEL in
    fastdebug | slowdebug )
      CFLAGS_JDK="$CFLAGS_JDK $CFLAGS_DEBUG_SYMBOLS $CFLAGS_DEBUG_OPTIONS"
      CXXFLAGS_JDK="$CXXFLAGS_JDK $CXXFLAGS_DEBUG_SYMBOLS $CXXFLAGS_DEBUG_OPTIONS"
      JAVAC_FLAGS="$JAVAC_FLAGS -g"
      ;;
    release )
      ;;
    * )
      AC_MSG_ERROR([Unrecognized \$DEBUG_LEVEL: $DEBUG_LEVEL])
      ;;
  esac

  # Setup LP64
  COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK $ADD_LP64"

  # Set some common defines. These works for all compilers, but assume
  # -D is universally accepted.

  # Setup endianness
  if test "x$OPENJDK_TARGET_CPU_ENDIAN" = xlittle; then
    # The macro _LITTLE_ENDIAN needs to be defined the same to avoid the
    #   Sun C compiler warning message: warning: macro redefined: _LITTLE_ENDIAN
    #   (The Solaris X86 system defines this in file /usr/include/sys/isa_defs.h).
    #   Note: -Dmacro         is the same as    #define macro 1
    #         -Dmacro=        is the same as    #define macro
    if test "x$OPENJDK_TARGET_OS" = xsolaris; then
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D_LITTLE_ENDIAN="
    else
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D_LITTLE_ENDIAN"
    fi
  else
    # Same goes for _BIG_ENDIAN. Do we really need to set *ENDIAN on Solaris if they
    # are defined in the system?
    if test "x$OPENJDK_TARGET_OS" = xsolaris; then
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D_BIG_ENDIAN="
    else
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D_BIG_ENDIAN"
    fi
  fi

  # Setup target OS define. Use OS target name but in upper case.
  OPENJDK_TARGET_OS_UPPERCASE=`$ECHO $OPENJDK_TARGET_OS | $TR 'abcdefghijklmnopqrstuvwxyz' 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'`
  COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D$OPENJDK_TARGET_OS_UPPERCASE"

  # Setup target CPU
  COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -DARCH='\"$OPENJDK_TARGET_CPU_LEGACY\"' -D$OPENJDK_TARGET_CPU_LEGACY"

  # Setup debug/release defines
  if test "x$DEBUG_LEVEL" = xrelease; then
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -DNDEBUG"
    if test "x$OPENJDK_TARGET_OS" = xsolaris; then
      COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -DTRIMMED"
    fi
  else
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -DDEBUG"
  fi

  # Set some additional per-OS defines.
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D_ALLBSD_SOURCE -D_DARWIN_UNLIMITED_SELECT"
  elif test "x$OPENJDK_TARGET_OS" = xaix; then
    # FIXME: PPC64 should not be here.
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -DPPC64"
  elif test "x$OPENJDK_TARGET_OS" = xbsd; then
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -D_ALLBSD_SOURCE"
  fi

  # Additional macosx handling
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # Setting these parameters makes it an error to link to macosx APIs that are
    # newer than the given OS version and makes the linked binaries compatible
    # even if built on a newer version of the OS.
    # The expected format is X.Y.Z
    MACOSX_VERSION_MIN=10.7.0
    AC_SUBST(MACOSX_VERSION_MIN)

    # The macro takes the version with no dots, ex: 1070
    # Let the flags variables get resolved in make for easier override on make
    # command line.
    COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK -DMAC_OS_X_VERSION_MAX_ALLOWED=\$(subst .,,\$(MACOSX_VERSION_MIN)) -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"
    LDFLAGS_JDK="$LDFLAGS_JDK -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"
  fi

  # Setup some hard coded includes
  COMMON_CCXXFLAGS_JDK="$COMMON_CCXXFLAGS_JDK \
      -I${JDK_TOPDIR}/src/java.base/share/native/include \
      -I${JDK_TOPDIR}/src/java.base/$OPENJDK_TARGET_OS/native/include \
      -I${JDK_TOPDIR}/src/java.base/$OPENJDK_TARGET_OS_TYPE/native/include"

  # The shared libraries are compiled using the picflag.
  CFLAGS_JDKLIB="$COMMON_CCXXFLAGS_JDK $CFLAGS_JDK $PICFLAG $CFLAGS_JDKLIB_EXTRA"
  CXXFLAGS_JDKLIB="$COMMON_CCXXFLAGS_JDK $CXXFLAGS_JDK $PICFLAG $CXXFLAGS_JDKLIB_EXTRA"

  # Executable flags
  CFLAGS_JDKEXE="$COMMON_CCXXFLAGS_JDK $CFLAGS_JDK"
  CXXFLAGS_JDKEXE="$COMMON_CCXXFLAGS_JDK $CXXFLAGS_JDK"

  AC_SUBST(CFLAGS_JDKLIB)
  AC_SUBST(CFLAGS_JDKEXE)
  AC_SUBST(CXXFLAGS_JDKLIB)
  AC_SUBST(CXXFLAGS_JDKEXE)

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

  # Setup LDFLAGS et al.
  #

  # Now this is odd. The JDK native libraries have to link against libjvm.so
  # On 32-bit machines there is normally two distinct libjvm.so:s, client and server.
  # Which should we link to? Are we lucky enough that the binary api to the libjvm.so library
  # is identical for client and server? Yes. Which is picked at runtime (client or server)?
  # Neither, since the chosen libjvm.so has already been loaded by the launcher, all the following
  # libraries will link to whatever is in memory. Yuck.
  #
  # Thus we offer the compiler to find libjvm.so first in server then in client. It works. Ugh.
  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    LDFLAGS_JDK="$LDFLAGS_JDK -nologo -opt:ref -incremental:no"
    if test "x$OPENJDK_TARGET_CPU_BITS" = "x32"; then
      LDFLAGS_JDK="$LDFLAGS_JDK -safeseh"
    fi
    # TODO: make -debug optional "--disable-full-debug-symbols"
    LDFLAGS_JDK="$LDFLAGS_JDK -debug"
  elif test "x$TOOLCHAIN_TYPE" = xgcc; then
    # If this is a --hash-style=gnu system, use --hash-style=both, why?
    # We have previously set HAS_GNU_HASH if this is the case
    if test -n "$HAS_GNU_HASH"; then
      LDFLAGS_JDK="${LDFLAGS_JDK} -Xlinker --hash-style=both"
    fi
    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      # And since we now know that the linker is gnu, then add -z defs, to forbid
      # undefined symbols in object files.
      LDFLAGS_JDK="${LDFLAGS_JDK} -Xlinker -z -Xlinker defs"
      case $DEBUG_LEVEL in
        release )
          # tell linker to optimize libraries.
          # Should this be supplied to the OSS linker as well?
          LDFLAGS_JDK="${LDFLAGS_JDK} -Xlinker -O1"
          ;;
        slowdebug )
          if test "x$HAS_LINKER_NOW" = "xtrue"; then
            # do relocations at load
            LDFLAGS_JDK="$LDFLAGS_JDK $LINKER_NOW_FLAG"
            LDFLAGS_CXX_JDK="$LDFLAGS_CXX_JDK $LINKER_NOW_FLAG"
          fi
          if test "x$HAS_LINKER_RELRO" = "xtrue"; then
            # mark relocations read only
            LDFLAGS_JDK="$LDFLAGS_JDK $LINKER_RELRO_FLAG"
            LDFLAGS_CXX_JDK="$LDFLAGS_CXX_JDK $LINKER_RELRO_FLAG"
          fi
          ;;
        fastdebug )
          if test "x$HAS_LINKER_RELRO" = "xtrue"; then
            # mark relocations read only
            LDFLAGS_JDK="$LDFLAGS_JDK $LINKER_RELRO_FLAG"
            LDFLAGS_CXX_JDK="$LDFLAGS_CXX_JDK $LINKER_RELRO_FLAG"
          fi
          ;;
        * )
          AC_MSG_ERROR([Unrecognized \$DEBUG_LEVEL: $DEBUG_LEVEL])
          ;;
        esac
    fi
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    LDFLAGS_JDK="$LDFLAGS_JDK -z defs -xildoff -ztext"
    LDFLAGS_CXX_JDK="$LDFLAGS_CXX_JDK -norunpath -xnolib"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    LDFLAGS_JDK="${LDFLAGS_JDK} -brtl -bnolibpath -liconv -bexpall -bernotok"
  fi

  # Customize LDFLAGS for executables

  LDFLAGS_JDKEXE="${LDFLAGS_JDK}"

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$OPENJDK_TARGET_CPU_BITS" = "x64"; then
      LDFLAGS_STACK_SIZE=1048576
    else
      LDFLAGS_STACK_SIZE=327680
    fi
    LDFLAGS_JDKEXE="${LDFLAGS_JDKEXE} /STACK:$LDFLAGS_STACK_SIZE"
  elif test "x$OPENJDK_TARGET_OS" = xlinux; then
    LDFLAGS_JDKEXE="$LDFLAGS_JDKEXE -Xlinker --allow-shlib-undefined"
  fi

  # Customize LDFLAGS for libs
  LDFLAGS_JDKLIB="${LDFLAGS_JDK}"

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    LDFLAGS_JDKLIB="${LDFLAGS_JDKLIB} -dll -libpath:${OUTPUT_ROOT}/support/modules_libs/java.base"
    LDFLAGS_JDKLIB_SUFFIX=""
  else
    LDFLAGS_JDKLIB="${LDFLAGS_JDKLIB}  ${SHARED_LIBRARY_FLAGS} \
        -L${OUTPUT_ROOT}/support/modules_libs/java.base${OPENJDK_TARGET_CPU_LIBDIR}"

    # On some platforms (mac) the linker warns about non existing -L dirs.
    # Add server first if available. Linking aginst client does not always produce the same results.
    # Only add client dir if client is being built. Add minimal (note not minimal1) if only building minimal1.
    # Default to server for other variants.
    if test "x$JVM_VARIANT_SERVER" = xtrue; then
      LDFLAGS_JDKLIB="${LDFLAGS_JDKLIB} -L${OUTPUT_ROOT}/support/modules_libs/java.base${OPENJDK_TARGET_CPU_LIBDIR}/server"
    elif test "x$JVM_VARIANT_CLIENT" = xtrue; then
      LDFLAGS_JDKLIB="${LDFLAGS_JDKLIB} -L${OUTPUT_ROOT}/support/modules_libs/java.base${OPENJDK_TARGET_CPU_LIBDIR}/client"
    elif test "x$JVM_VARIANT_MINIMAL1" = xtrue; then
      LDFLAGS_JDKLIB="${LDFLAGS_JDKLIB} -L${OUTPUT_ROOT}/support/modules_libs/java.base${OPENJDK_TARGET_CPU_LIBDIR}/minimal"
    else
      LDFLAGS_JDKLIB="${LDFLAGS_JDKLIB} -L${OUTPUT_ROOT}/support/modules_libs/java.base${OPENJDK_TARGET_CPU_LIBDIR}/server"
    fi

    LDFLAGS_JDKLIB_SUFFIX="-ljava -ljvm"
    if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
      LDFLAGS_JDKLIB_SUFFIX="$LDFLAGS_JDKLIB_SUFFIX -lc"
    fi
  fi

  AC_SUBST(LDFLAGS_JDKLIB)
  AC_SUBST(LDFLAGS_JDKEXE)
  AC_SUBST(LDFLAGS_JDKLIB_SUFFIX)
  AC_SUBST(LDFLAGS_JDKEXE_SUFFIX)
  AC_SUBST(LDFLAGS_CXX_JDK)

  LDFLAGS_TESTLIB="$LDFLAGS_JDKLIB"
  LDFLAGS_TESTEXE="$LDFLAGS_JDKEXE"
  LDFLAGS_TESTLIB_SUFFIX="$LDFLAGS_JDKLIB_SUFFIX"
  LDFLAGS_TESTEXE_SUFFIX="$LDFLAGS_JDKEXE_SUFFIX"

  AC_SUBST(LDFLAGS_TESTLIB)
  AC_SUBST(LDFLAGS_TESTEXE)
  AC_SUBST(LDFLAGS_TESTLIB_SUFFIX)
  AC_SUBST(LDFLAGS_TESTEXE_SUFFIX)
])

# FLAGS_COMPILER_CHECK_ARGUMENTS([ARGUMENT], [RUN-IF-TRUE],
#                                   [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the c and c++ compilers support an argument
AC_DEFUN([FLAGS_COMPILER_CHECK_ARGUMENTS],
[
  AC_MSG_CHECKING([if compiler supports "$1"])
  supports=yes

  saved_cflags="$CFLAGS"
  CFLAGS="$CFLAGS $1"
  AC_LANG_PUSH([C])
  AC_COMPILE_IFELSE([AC_LANG_SOURCE([[int i;]])], [],
      [supports=no])
  AC_LANG_POP([C])
  CFLAGS="$saved_cflags"

  saved_cxxflags="$CXXFLAGS"
  CXXFLAGS="$CXXFLAG $1"
  AC_LANG_PUSH([C++])
  AC_COMPILE_IFELSE([AC_LANG_SOURCE([[int i;]])], [],
      [supports=no])
  AC_LANG_POP([C++])
  CXXFLAGS="$saved_cxxflags"

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    m4_ifval([$2], [$2], [:])
  else
    m4_ifval([$3], [$3], [:])
  fi
])

# FLAGS_LINKER_CHECK_ARGUMENTS([ARGUMENT], [RUN-IF-TRUE],
#                                    [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the linker support an argument
AC_DEFUN([FLAGS_LINKER_CHECK_ARGUMENTS],
[
  AC_MSG_CHECKING([if linker supports "$1"])
  supports=yes

  saved_ldflags="$LDFLAGS"
  LDFLAGS="$LDFLAGS $1"
  AC_LANG_PUSH([C])
  AC_LINK_IFELSE([AC_LANG_PROGRAM([[]],[[]])],
      [], [supports=no])
  AC_LANG_POP([C])
  LDFLAGS="$saved_ldflags"

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    m4_ifval([$2], [$2], [:])
  else
    m4_ifval([$3], [$3], [:])
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
  FLAGS_COMPILER_CHECK_ARGUMENTS([$ZERO_ARCHFLAG], [], [ZERO_ARCHFLAG=""])
  AC_SUBST(ZERO_ARCHFLAG)

  # Check that the compiler supports -mX (or -qX on AIX) flags
  # Set COMPILER_SUPPORTS_TARGET_BITS_FLAG to 'true' if it does
  FLAGS_COMPILER_CHECK_ARGUMENTS([${COMPILER_TARGET_BITS_FLAG}${OPENJDK_TARGET_CPU_BITS}],
      [COMPILER_SUPPORTS_TARGET_BITS_FLAG=true],
      [COMPILER_SUPPORTS_TARGET_BITS_FLAG=false])
  AC_SUBST(COMPILER_SUPPORTS_TARGET_BITS_FLAG)

  AC_ARG_ENABLE([warnings-as-errors], [AS_HELP_STRING([--disable-warnings-as-errors],
      [do not consider native warnings to be an error @<:@enabled@:>@])])

  AC_MSG_CHECKING([if native warnings are errors])
  if test "x$enable_warnings_as_errors" = "xyes"; then
    AC_MSG_RESULT([yes (explicitely set)])
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
  AC_SUBST(WARNINGS_AS_ERRORS)

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
      FLAGS_COMPILER_CHECK_ARGUMENTS([-Wno-this-is-a-warning-that-do-not-exist],
          [GCC_CAN_DISABLE_WARNINGS=true],
          [GCC_CAN_DISABLE_WARNINGS=false]
      )
      if test "x$GCC_CAN_DISABLE_WARNINGS" = "xtrue"; then
        DISABLE_WARNING_PREFIX="-Wno-"
      else
        DISABLE_WARNING_PREFIX=
      fi
      CFLAGS_WARNINGS_ARE_ERRORS="-Werror"
      ;;
    clang)
      DISABLE_WARNING_PREFIX="-Wno-"
      CFLAGS_WARNINGS_ARE_ERRORS="-Werror"
      ;;
  esac
  AC_SUBST(DISABLE_WARNING_PREFIX)
  AC_SUBST(CFLAGS_WARNINGS_ARE_ERRORS)
])
