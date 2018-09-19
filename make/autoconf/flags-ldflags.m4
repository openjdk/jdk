#
# Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

AC_DEFUN([FLAGS_SETUP_LDFLAGS],
[
  FLAGS_SETUP_LDFLAGS_HELPER

  # Setup the target toolchain

  # On some platforms (mac) the linker warns about non existing -L dirs.
  # For any of the variants server, client or minimal, the dir matches the
  # variant name. The "main" variant should be used for linking. For the
  # rest, the dir is just server.
  if HOTSPOT_CHECK_JVM_VARIANT(server) || HOTSPOT_CHECK_JVM_VARIANT(client) \
      || HOTSPOT_CHECK_JVM_VARIANT(minimal); then
    TARGET_JVM_VARIANT_PATH=$JVM_VARIANT_MAIN
  else
    TARGET_JVM_VARIANT_PATH=server
  fi
  FLAGS_SETUP_LDFLAGS_CPU_DEP([TARGET])

  # Setup the build toolchain

  # When building a buildjdk, it's always only the server variant
  BUILD_JVM_VARIANT_PATH=server

  FLAGS_SETUP_LDFLAGS_CPU_DEP([BUILD], [OPENJDK_BUILD_])

  LDFLAGS_TESTLIB="$LDFLAGS_JDKLIB"
  LDFLAGS_TESTEXE="$LDFLAGS_JDKEXE ${TARGET_LDFLAGS_JDK_LIBPATH}"
  AC_SUBST(LDFLAGS_TESTLIB)
  AC_SUBST(LDFLAGS_TESTEXE)
])

################################################################################

# CPU independent LDFLAGS setup, used for both target and build toolchain.
AC_DEFUN([FLAGS_SETUP_LDFLAGS_HELPER],
[
  # Setup basic LDFLAGS
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    # If this is a --hash-style=gnu system, use --hash-style=both, why?
    # We have previously set HAS_GNU_HASH if this is the case
    if test -n "$HAS_GNU_HASH"; then
      BASIC_LDFLAGS="-Wl,--hash-style=both"
      LIBJSIG_HASHSTYLE_LDFLAGS="-Wl,--hash-style=both"
    fi

    # Add -z defs, to forbid undefined symbols in object files.
    BASIC_LDFLAGS="$BASIC_LDFLAGS -Wl,-z,defs"

    BASIC_LDFLAGS_JVM_ONLY="-Wl,-O1 -Wl,-z,relro"


  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    BASIC_LDFLAGS_JVM_ONLY="-mno-omit-leaf-frame-pointer -mstack-alignment=16 \
        -fPIC"

  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    BASIC_LDFLAGS="-Wl,-z,defs"
    BASIC_LDFLAGS_ONLYCXX="-norunpath"
    BASIC_LDFLAGS_ONLYCXX_JDK_ONLY="-xnolib"

    BASIC_LDFLAGS_JDK_ONLY="-ztext"
    BASIC_LDFLAGS_JVM_ONLY="-library=%none -mt -z noversion"

  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    BASIC_LDFLAGS="-b64 -brtl -bnolibpath -bexpall -bernotok -btextpsize:64K \
        -bdatapsize:64K -bstackpsize:64K"
    # libjvm.so has gotten too large for normal TOC size; compile with qpic=large and link with bigtoc
    BASIC_LDFLAGS_JVM_ONLY="-Wl,-lC_r -bbigtoc"

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    BASIC_LDFLAGS="-nologo -opt:ref"
    BASIC_LDFLAGS_JDK_ONLY="-incremental:no"
    BASIC_LDFLAGS_JVM_ONLY="-opt:icf,8 -subsystem:windows"
  fi

  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    if test -n "$HAS_NOEXECSTACK"; then
      BASIC_LDFLAGS="$BASIC_LDFLAGS -Wl,-z,noexecstack"
    fi
  fi

  # Setup OS-dependent LDFLAGS
  if test "x$TOOLCHAIN_TYPE" = xclang || test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Assume clang or gcc.
      # FIXME: We should really generalize SET_SHARED_LIBRARY_ORIGIN instead.
      OS_LDFLAGS_JVM_ONLY="-Wl,-rpath,@loader_path/. -Wl,-rpath,@loader_path/.."
      OS_LDFLAGS_JDK_ONLY="-mmacosx-version-min=$MACOSX_VERSION_MIN"
    fi
  fi

  # Setup debug level-dependent LDFLAGS
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      if test x$DEBUG_LEVEL = xrelease; then
        DEBUGLEVEL_LDFLAGS_JDK_ONLY="$DEBUGLEVEL_LDFLAGS_JDK_ONLY -Wl,-O1"
      else
        # mark relocations read only on (fast/slow) debug builds
        DEBUGLEVEL_LDFLAGS_JDK_ONLY="-Wl,-z,relro"
      fi
      if test x$DEBUG_LEVEL = xslowdebug; then
        # do relocations at load
        DEBUGLEVEL_LDFLAGS="-Wl,-z,now"
      fi
    fi

  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    # We need '-qminimaltoc' or '-qpic=large -bbigtoc' if the TOC overflows.
    # Hotspot now overflows its 64K TOC (currently only for debug),
    # so we build with '-qpic=large -bbigtoc'.
    if test "x$DEBUG_LEVEL" != xrelease; then
      DEBUGLEVEL_LDFLAGS_JVM_ONLY="$DEBUGLEVEL_LDFLAGS_JVM_ONLY -bbigtoc"
    fi
  fi

  # Setup LDFLAGS for linking executables
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    EXECUTABLE_LDFLAGS="$EXECUTABLE_LDFLAGS -Wl,--allow-shlib-undefined"
  fi

  # Export some intermediate variables for compatibility
  LDFLAGS_CXX_JDK="$BASIC_LDFLAGS_ONLYCXX $BASIC_LDFLAGS_ONLYCXX_JDK_ONLY $DEBUGLEVEL_LDFLAGS_JDK_ONLY"
  AC_SUBST(LDFLAGS_CXX_JDK)
  AC_SUBST(LIBJSIG_HASHSTYLE_LDFLAGS)
  AC_SUBST(LIBJSIG_NOEXECSTACK_LDFLAGS)
])

################################################################################
# $1 - Either BUILD or TARGET to pick the correct OS/CPU variables to check
#      conditionals against.
# $2 - Optional prefix for each variable defined.
AC_DEFUN([FLAGS_SETUP_LDFLAGS_CPU_DEP],
[
  # Setup CPU-dependent basic LDFLAGS. These can differ between the target and
  # build toolchain.
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x${OPENJDK_$1_CPU}" = xx86; then
      $1_CPU_LDFLAGS_JVM_ONLY="-march=i586"
    elif test "x$OPENJDK_$1_CPU" = xarm; then
      $1_CPU_LDFLAGS_JVM_ONLY="${$1_CPU_LDFLAGS_JVM_ONLY} -fsigned-char"
      $1_CPU_LDFLAGS="$ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS"
    elif test "x$FLAGS_CPU" = xaarch64; then
      if test "x$HOTSPOT_TARGET_CPU_PORT" = xarm64; then
        $1_CPU_LDFLAGS_JVM_ONLY="${$1_CPU_LDFLAGS_JVM_ONLY} -fsigned-char"
      fi
    fi

  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    if test "x${OPENJDK_$1_CPU}" = "xsparcv9"; then
      $1_CPU_LDFLAGS_JVM_ONLY="-xarch=sparc"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x${OPENJDK_$1_CPU}" = "xx86"; then
      $1_CPU_LDFLAGS="-safeseh"
      # NOTE: Old build added -machine. Probably not needed.
      $1_CPU_LDFLAGS_JVM_ONLY="-machine:I386"
      $1_CPU_EXECUTABLE_LDFLAGS="-stack:327680"
    else
      $1_CPU_LDFLAGS_JVM_ONLY="-machine:AMD64"
      $1_CPU_EXECUTABLE_LDFLAGS="-stack:1048576"
    fi
  fi

  # JVM_VARIANT_PATH depends on if this is build or target...
  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    $1_LDFLAGS_JDK_LIBPATH="-libpath:${OUTPUTDIR}/support/modules_libs/java.base"
  else
    $1_LDFLAGS_JDK_LIBPATH="-L\$(SUPPORT_OUTPUTDIR)/modules_libs/java.base \
        -L\$(SUPPORT_OUTPUTDIR)/modules_libs/java.base/${$1_JVM_VARIANT_PATH}"
  fi

  # Export variables according to old definitions, prefix with $2 if present.
  LDFLAGS_JDK_COMMON="$BASIC_LDFLAGS $BASIC_LDFLAGS_JDK_ONLY \
      $OS_LDFLAGS_JDK_ONLY $DEBUGLEVEL_LDFLAGS_JDK_ONLY ${$2EXTRA_LDFLAGS}"
  $2LDFLAGS_JDKLIB="$LDFLAGS_JDK_COMMON $BASIC_LDFLAGS_JDK_LIB_ONLY \
      ${$1_LDFLAGS_JDK_LIBPATH} $SHARED_LIBRARY_FLAGS"
  $2LDFLAGS_JDKEXE="$LDFLAGS_JDK_COMMON $EXECUTABLE_LDFLAGS \
      ${$1_CPU_EXECUTABLE_LDFLAGS}"

  $2JVM_LDFLAGS="$BASIC_LDFLAGS $BASIC_LDFLAGS_JVM_ONLY $OS_LDFLAGS_JVM_ONLY \
      $DEBUGLEVEL_LDFLAGS $DEBUGLEVEL_LDFLAGS_JVM_ONLY $BASIC_LDFLAGS_ONLYCXX \
      ${$1_CPU_LDFLAGS} ${$1_CPU_LDFLAGS_JVM_ONLY} ${$2EXTRA_LDFLAGS}"

  AC_SUBST($2LDFLAGS_JDKLIB)
  AC_SUBST($2LDFLAGS_JDKEXE)

  AC_SUBST($2JVM_LDFLAGS)
])
