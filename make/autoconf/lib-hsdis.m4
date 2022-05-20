#
# Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
# Helper function to setup hsdis using Capstone
#
AC_DEFUN([LIB_SETUP_HSDIS_CAPSTONE],
[
  AC_ARG_WITH(capstone, [AS_HELP_STRING([--with-capstone],
      [where to find the Capstone files needed for hsdis/capstone])])

  if test "x$with_capstone" != x; then
    AC_MSG_CHECKING([for capstone])
    CAPSTONE="$with_capstone"
    AC_MSG_RESULT([$CAPSTONE])

    HSDIS_CFLAGS="-I${CAPSTONE}/include/capstone"
    if test "x$OPENJDK_TARGET_OS" != xwindows; then
      HSDIS_LDFLAGS="-L${CAPSTONE}/lib"
      HSDIS_LIBS="-lcapstone"
    else
      HSDIS_LDFLAGS="-nodefaultlib:libcmt.lib"
      HSDIS_LIBS="${CAPSTONE}/capstone.lib"
    fi
  else
    if test "x$OPENJDK_TARGET_OS" = xwindows; then
      # There is no way to auto-detect capstone on Windowos
      AC_MSG_NOTICE([You must specify capstone location using --with-capstone=<path>])
      AC_MSG_ERROR([Cannot continue])
    fi

    PKG_CHECK_MODULES(CAPSTONE, capstone, [CAPSTONE_FOUND=yes], [CAPSTONE_FOUND=no])
    if test "x$CAPSTONE_FOUND" = xyes; then
      HSDIS_CFLAGS="$CAPSTONE_CFLAGS"
      HSDIS_LDFLAGS="$CAPSTONE_LDFLAGS"
      HSDIS_LIBS="$CAPSTONE_LIBS"
    else
      HELP_MSG_MISSING_DEPENDENCY([capstone])
      AC_MSG_NOTICE([Cannot locate capstone which is needed for hsdis/capstone. Try using --with-capstone=<path>. $HELP_MSG])
      AC_MSG_ERROR([Cannot continue])
    fi
  fi
])

################################################################################
#
# Helper function to setup hsdis using LLVM
#
AC_DEFUN([LIB_SETUP_HSDIS_LLVM],
[
  AC_ARG_WITH([llvm], [AS_HELP_STRING([--with-llvm],
      [where to find the LLVM files needed for hsdis/llvm])])

  if test "x$with_llvm" != x; then
    LLVM_DIR="$with_llvm"
  fi

  if test "x$OPENJDK_TARGET_OS" != xwindows; then
    if test "x$LLVM_DIR" = x; then
      # Macs with homebrew can have llvm in different places
      UTIL_LOOKUP_PROGS(LLVM_CONFIG, llvm-config, [$PATH:/usr/local/opt/llvm/bin:/opt/homebrew/opt/llvm/bin])
      if test "x$LLVM_CONFIG" = x; then
        AC_MSG_NOTICE([Cannot locate llvm-config which is needed for hsdis/llvm. Try using --with-llvm=<LLVM home>.])
        AC_MSG_ERROR([Cannot continue])
      fi
    else
      UTIL_LOOKUP_PROGS(LLVM_CONFIG, llvm-config, [$LLVM_DIR/bin])
      if test "x$LLVM_CONFIG" = x; then
        AC_MSG_NOTICE([Cannot locate llvm-config in $LLVM_DIR. Check your --with-llvm argument.])
        AC_MSG_ERROR([Cannot continue])
      fi
    fi

    # We need the LLVM flags and libs, and llvm-config provides them for us.
    HSDIS_CFLAGS=`$LLVM_CONFIG --cflags`
    HSDIS_LDFLAGS=`$LLVM_CONFIG --ldflags`
    HSDIS_LIBS=`$LLVM_CONFIG --libs $OPENJDK_TARGET_CPU_ARCH ${OPENJDK_TARGET_CPU_ARCH}disassembler`
  else
    if test "x$LLVM_DIR" = x; then
      AC_MSG_NOTICE([--with-llvm is needed on Windows to point out the LLVM home])
      AC_MSG_ERROR([Cannot continue])
    fi

    # Official Windows installation of LLVM do not ship llvm-config, and self-built llvm-config
    # produced unusable output, so just ignore it on Windows.
    if ! test -e $LLVM_DIR/include/llvm-c/lto.h; then
      AC_MSG_NOTICE([$LLVM_DIR does not seem like a valid LLVM home; include dir is missing])
      AC_MSG_ERROR([Cannot continue])
    fi
    if ! test -e $LLVM_DIR/include/llvm-c/Disassembler.h; then
      AC_MSG_NOTICE([$LLVM_DIR does not point to a complete LLVM installation. ])
      AC_MSG_NOTICE([The official LLVM distribution is missing crucical files; you need to build LLVM yourself or get all include files elsewhere])
      AC_MSG_ERROR([Cannot continue])
    fi
    if ! test -e $LLVM_DIR/lib/llvm-c.lib; then
      AC_MSG_NOTICE([$LLVM_DIR does not seem like a valid LLVM home; lib dir is missing])
      AC_MSG_ERROR([Cannot continue])
    fi
    HSDIS_CFLAGS="-I$LLVM_DIR/include"
    HSDIS_LDFLAGS="-libpath:$LLVM_DIR/lib"
    HSDIS_LIBS="llvm-c.lib"
  fi
])

################################################################################
#
# Helper function to build binutils from source.
#
AC_DEFUN([LIB_BUILD_BINUTILS],
[
  BINUTILS_SRC="$with_binutils_src"
  UTIL_FIXUP_PATH(BINUTILS_SRC)

  if ! test -d $BINUTILS_SRC; then
    AC_MSG_ERROR([--with-binutils-src is not pointing to a directory])
  fi
  if ! test -x $BINUTILS_SRC/configure; then
    AC_MSG_ERROR([--with-binutils-src does not look like a binutils source directory])
  fi

  if test -e $BINUTILS_SRC/bfd/libbfd.a && \
      test -e $BINUTILS_SRC/opcodes/libopcodes.a && \
      test -e $BINUTILS_SRC/libiberty/libiberty.a && \
      test -e $BINUTILS_SRC/zlib/libz.a; then
    AC_MSG_NOTICE([Found binutils binaries in binutils source directory -- not building])
  else
    # On Windows, we cannot build with the normal Microsoft CL, but must instead use
    # a separate mingw toolchain.
    if test "x$OPENJDK_BUILD_OS" = xwindows; then
      if test "x$OPENJDK_TARGET_CPU" = "xx86"; then
        target_base="i686-w64-mingw32"
      else
        target_base="$OPENJDK_TARGET_CPU-w64-mingw32"
      fi
      binutils_cc="$target_base-gcc"
      binutils_target="--host=$target_base --target=$target_base"
      # Somehow the uint typedef is not included when building with mingw
      binutils_cflags="-Duint=unsigned"
      compiler_version=`$binutils_cc --version 2>&1`
      if ! [ [[ "$compiler_version" =~ GCC ]] ]; then
        AC_MSG_NOTICE([Could not find correct mingw compiler $binutils_cc.])
        HELP_MSG_MISSING_DEPENDENCY([$binutils_cc])
        AC_MSG_ERROR([Cannot continue. $HELP_MSG])
      else
        AC_MSG_NOTICE([Using compiler $binutils_cc with version $compiler_version])
      fi
    elif test "x$OPENJDK_BUILD_OS" = xmacosx; then
      if test "x$OPENJDK_TARGET_CPU" = "xaarch64"; then
        binutils_target="--enable-targets=aarch64-darwin"
      else
        binutils_target=""
      fi
    else
      binutils_cc="$CC $SYSROOT_CFLAGS"
      binutils_target=""
    fi
    binutils_cflags="$binutils_cflags $MACHINE_FLAG $JVM_PICFLAG $C_O_FLAG_NORM"

    AC_MSG_NOTICE([Running binutils configure])
    AC_MSG_NOTICE([configure command line: ./configure --disable-nls CFLAGS="$binutils_cflags" CC="$binutils_cc" $binutils_target])
    saved_dir=`pwd`
    cd "$BINUTILS_SRC"
    ./configure --disable-nls CFLAGS="$binutils_cflags" CC="$binutils_cc" $binutils_target
    if test $? -ne 0 || ! test -e $BINUTILS_SRC/Makefile; then
      AC_MSG_NOTICE([Automatic building of binutils failed on configure. Try building it manually])
      AC_MSG_ERROR([Cannot continue])
    fi
    AC_MSG_NOTICE([Running binutils make])
    $MAKE all-opcodes
    if test $? -ne 0; then
      AC_MSG_NOTICE([Automatic building of binutils failed on make. Try building it manually])
      AC_MSG_ERROR([Cannot continue])
    fi
    cd $saved_dir
    AC_MSG_NOTICE([Building of binutils done])
  fi

  BINUTILS_DIR="$BINUTILS_SRC"
])

################################################################################
#
# Helper function to setup hsdis using binutils
#
AC_DEFUN([LIB_SETUP_HSDIS_BINUTILS],
[
  AC_ARG_WITH([binutils], [AS_HELP_STRING([--with-binutils],
      [where to find the binutils files needed for hsdis/binutils])])

  AC_ARG_WITH([binutils-src], [AS_HELP_STRING([--with-binutils-src],
      [where to find the binutils source for building])])

  # We need the binutils static libs and includes.
  if test "x$with_binutils_src" != x; then
    # Try building the source first. If it succeeds, it sets $BINUTILS_DIR.
    LIB_BUILD_BINUTILS
  fi

  if test "x$with_binutils" != x; then
    BINUTILS_DIR="$with_binutils"
  fi

  binutils_system_error=""
  HSDIS_LIBS=""
  if test "x$BINUTILS_DIR" = xsystem; then
    AC_CHECK_LIB(bfd, bfd_openr, [ HSDIS_LIBS="-lbfd" ], [ binutils_system_error="libbfd not found" ])
    AC_CHECK_LIB(opcodes, disassembler, [ HSDIS_LIBS="$HSDIS_LIBS -lopcodes" ], [ binutils_system_error="libopcodes not found" ])
    AC_CHECK_LIB(iberty, xmalloc, [ HSDIS_LIBS="$HSDIS_LIBS -liberty" ], [ binutils_system_error="libiberty not found" ])
    AC_CHECK_LIB(z, deflate, [ HSDIS_LIBS="$HSDIS_LIBS -lz" ], [ binutils_system_error="libz not found" ])
    HSDIS_CFLAGS="-DLIBARCH_$OPENJDK_TARGET_CPU_LEGACY_LIB"
  elif test "x$BINUTILS_DIR" != x; then
    if test -e $BINUTILS_DIR/bfd/libbfd.a && \
        test -e $BINUTILS_DIR/opcodes/libopcodes.a && \
        test -e $BINUTILS_DIR/libiberty/libiberty.a; then
      HSDIS_CFLAGS="-I$BINUTILS_DIR/include -I$BINUTILS_DIR/bfd -DLIBARCH_$OPENJDK_TARGET_CPU_LEGACY_LIB"
      HSDIS_LDFLAGS=""
      HSDIS_LIBS="$BINUTILS_DIR/bfd/libbfd.a $BINUTILS_DIR/opcodes/libopcodes.a $BINUTILS_DIR/libiberty/libiberty.a $BINUTILS_DIR/zlib/libz.a"
    fi
  fi

  AC_MSG_CHECKING([for binutils to use with hsdis])
  case "x$BINUTILS_DIR" in
    xsystem)
      if test "x$OPENJDK_TARGET_OS" != xlinux; then
        AC_MSG_RESULT([invalid])
        AC_MSG_ERROR([binutils on system is supported for Linux only])
      elif test "x$binutils_system_error" = x; then
        AC_MSG_RESULT([system])
        HSDIS_CFLAGS="$HSDIS_CFLAGS -DSYSTEM_BINUTILS"
      else
        AC_MSG_RESULT([invalid])
        AC_MSG_ERROR([$binutils_system_error])
      fi
      ;;
    x)
      AC_MSG_RESULT([missing])
      AC_MSG_NOTICE([--with-hsdis=binutils requires specifying a binutils installation.])
      AC_MSG_NOTICE([Download binutils from https://www.gnu.org/software/binutils and unpack it,])
      AC_MSG_NOTICE([and point --with-binutils-src to the resulting directory, or use])
      AC_MSG_NOTICE([--with-binutils to point to a pre-built binutils installation.])
      AC_MSG_ERROR([Cannot continue])
      ;;
    *)
      if test "x$HSDIS_LIBS" != x; then
        AC_MSG_RESULT([$BINUTILS_DIR])
      else
        AC_MSG_RESULT([invalid])
        AC_MSG_ERROR([$BINUTILS_DIR does not contain a proper binutils installation])
      fi
      ;;
  esac
])

################################################################################
#
# Determine if hsdis should be built, and if so, with which backend.
#
AC_DEFUN_ONCE([LIB_SETUP_HSDIS],
[
  AC_ARG_WITH([hsdis], [AS_HELP_STRING([--with-hsdis],
      [what hsdis backend to use ('none', 'capstone', 'llvm', 'binutils') @<:@none@:>@])])

  UTIL_ARG_ENABLE(NAME: hsdis-bundling, DEFAULT: false,
    RESULT: ENABLE_HSDIS_BUNDLING,
    DESC: [enable bundling of hsdis to allow HotSpot disassembly out-of-the-box])

  AC_MSG_CHECKING([what hsdis backend to use])

  if test "x$with_hsdis" = xyes; then
    AC_MSG_ERROR([--with-hsdis must have a value])
  elif test "x$with_hsdis" = xnone || test "x$with_hsdis" = xno || test "x$with_hsdis" = x; then
    HSDIS_BACKEND=none
    AC_MSG_RESULT(['none', hsdis will not be built])
  elif test "x$with_hsdis" = xcapstone; then
    HSDIS_BACKEND=capstone
    AC_MSG_RESULT(['capstone'])

    LIB_SETUP_HSDIS_CAPSTONE
  elif test "x$with_hsdis" = xllvm; then
    HSDIS_BACKEND=llvm
    AC_MSG_RESULT(['llvm'])

    LIB_SETUP_HSDIS_LLVM
  elif test "x$with_hsdis" = xbinutils; then
    HSDIS_BACKEND=binutils
    AC_MSG_RESULT(['binutils'])

    LIB_SETUP_HSDIS_BINUTILS
  else
    AC_MSG_RESULT([invalid])
    AC_MSG_ERROR([Incorrect hsdis backend "$with_hsdis"])
  fi

  AC_SUBST(HSDIS_BACKEND)
  AC_SUBST(HSDIS_CFLAGS)
  AC_SUBST(HSDIS_LDFLAGS)
  AC_SUBST(HSDIS_LIBS)

  AC_MSG_CHECKING([if hsdis should be bundled])
  if test "x$ENABLE_HSDIS_BUNDLING" = "xtrue"; then
    if test "x$HSDIS_BACKEND" = xnone; then
      AC_MSG_RESULT([no, backend missing])
      AC_MSG_ERROR([hsdis-bundling requires a hsdis backend. Please set --with-hsdis=<backend>]);
    fi
    AC_MSG_RESULT([yes])
    if test "x$HSDIS_BACKEND" = xbinutils; then
      AC_MSG_WARN([The resulting build might not be redistributable. Seek legal advice before distributing.])
    fi
  else
    AC_MSG_RESULT([no])
  fi
  AC_SUBST(ENABLE_HSDIS_BUNDLING)
])
