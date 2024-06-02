#
# Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
  FLAGS_SETUP_LDFLAGS_CPU_DEP([TARGET])

  # Setup the build toolchain
  FLAGS_SETUP_LDFLAGS_CPU_DEP([BUILD], [OPENJDK_BUILD_])

  AC_SUBST(ADLC_LDFLAGS)
])

################################################################################

# CPU independent LDFLAGS setup, used for both target and build toolchain.
AC_DEFUN([FLAGS_SETUP_LDFLAGS_HELPER],
[
  # Setup basic LDFLAGS
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    # Add -z,defs, to forbid undefined symbols in object files.
    # add -z,relro (mark relocations read only) for all libs
    # add -z,now ("full relro" - more of the Global Offset Table GOT is marked read only)
    # add --no-as-needed to disable default --as-needed link flag on some GCC toolchains
    BASIC_LDFLAGS="-Wl,-z,defs -Wl,-z,relro -Wl,-z,now -Wl,--no-as-needed -Wl,--exclude-libs,ALL"
    # Linux : remove unused code+data in link step
    if test "x$ENABLE_LINKTIME_GC" = xtrue; then
      if test "x$OPENJDK_TARGET_CPU" = xs390x; then
        BASIC_LDFLAGS="$BASIC_LDFLAGS -Wl,--gc-sections"
      else
        BASIC_LDFLAGS_JDK_ONLY="$BASIC_LDFLAGS_JDK_ONLY -Wl,--gc-sections"
      fi
    fi

    BASIC_LDFLAGS_JVM_ONLY=""

    LDFLAGS_CXX_PARTIAL_LINKING="$MACHINE_FLAG -r"

  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    BASIC_LDFLAGS_JVM_ONLY="-mno-omit-leaf-frame-pointer -mstack-alignment=16 \
        -fPIC"

    LDFLAGS_CXX_PARTIAL_LINKING="$MACHINE_FLAG -r"

    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      BASIC_LDFLAGS="-fuse-ld=lld -Wl,--exclude-libs,ALL"
    fi
    if test "x$OPENJDK_TARGET_OS" = xaix; then
      BASIC_LDFLAGS="-Wl,-b64 -Wl,-brtl -Wl,-bnorwexec -Wl,-bnolibpath -Wl,-bnoexpall \
        -Wl,-bernotok -Wl,-bdatapsize:64k -Wl,-btextpsize:64k -Wl,-bstackpsize:64k"
      BASIC_LDFLAGS_JVM_ONLY="$BASIC_LDFLAGS_JVM_ONLY -Wl,-lC_r -Wl,-bbigtoc"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    BASIC_LDFLAGS="-opt:ref"
    BASIC_LDFLAGS_JDK_ONLY="-incremental:no"
    BASIC_LDFLAGS_JVM_ONLY="-opt:icf,8 -subsystem:windows"
  fi

  if (test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang) \
      && test "x$OPENJDK_TARGET_OS" != xaix; then
    if test -n "$HAS_NOEXECSTACK"; then
      BASIC_LDFLAGS="$BASIC_LDFLAGS -Wl,-z,noexecstack"
    fi
  fi

  # Setup OS-dependent LDFLAGS
  if test "x$OPENJDK_TARGET_OS" = xmacosx && test "x$TOOLCHAIN_TYPE" = xclang; then
    # FIXME: We should really generalize SET_SHARED_LIBRARY_ORIGIN instead.
    OS_LDFLAGS_JVM_ONLY="-Wl,-rpath,@loader_path/. -Wl,-rpath,@loader_path/.."
    OS_LDFLAGS="-mmacosx-version-min=$MACOSX_VERSION_MIN"
  fi

  # Setup debug level-dependent LDFLAGS
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      if test x$DEBUG_LEVEL = xrelease; then
        DEBUGLEVEL_LDFLAGS_JDK_ONLY="$DEBUGLEVEL_LDFLAGS_JDK_ONLY -Wl,-O1"
      fi
    fi

  elif test "x$TOOLCHAIN_TYPE" = xclang && test "x$OPENJDK_TARGET_OS" = xaix; then
    # We need '-fpic' or '-fpic -mcmodel=large -Wl,-bbigtoc' if the TOC overflows.
    # Hotspot now overflows its 64K TOC (currently only for debug),
    # so we build with '-fpic -mcmodel=large -Wl,-bbigtoc'.
    if test "x$DEBUG_LEVEL" != xrelease; then
      DEBUGLEVEL_LDFLAGS_JVM_ONLY="$DEBUGLEVEL_LDFLAGS_JVM_ONLY -Wl,-bbigtoc"
    fi
  fi

  # Setup LDFLAGS for linking executables
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    # Enabling pie on 32 bit builds prevents the JVM from allocating a continuous
    # java heap.
    if test "x$OPENJDK_TARGET_CPU_BITS" != "x32"; then
      EXECUTABLE_LDFLAGS="$EXECUTABLE_LDFLAGS -pie"
    fi
  fi

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    REPRODUCIBLE_LDFLAGS="-experimental:deterministic"
    FLAGS_LINKER_CHECK_ARGUMENTS(ARGUMENT: [$REPRODUCIBLE_LDFLAGS],
        IF_FALSE: [
            REPRODUCIBLE_LDFLAGS=
        ]
    )
  fi

  if test "x$ALLOW_ABSOLUTE_PATHS_IN_OUTPUT" = "xfalse"; then
    if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
      BASIC_LDFLAGS="$BASIC_LDFLAGS -pdbaltpath:%_PDB%"
      # PATHMAP_FLAGS is setup in flags-cflags.m4.
      FILE_MACRO_LDFLAGS="${PATHMAP_FLAGS}"
    fi
  fi

  # Export some intermediate variables for compatibility
  LDFLAGS_CXX_JDK="$DEBUGLEVEL_LDFLAGS_JDK_ONLY"
  AC_SUBST(LDFLAGS_CXX_JDK)
  AC_SUBST(LDFLAGS_CXX_PARTIAL_LINKING)
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
    fi

    # MIPS ABI does not support GNU hash style
    if test "x${OPENJDK_$1_CPU}" = xmips ||
       test "x${OPENJDK_$1_CPU}" = xmipsel ||
       test "x${OPENJDK_$1_CPU}" = xmips64 ||
       test "x${OPENJDK_$1_CPU}" = xmips64el; then
      $1_CPU_LDFLAGS="${$1_CPU_LDFLAGS} -Wl,--hash-style=sysv"
    else
      $1_CPU_LDFLAGS="${$1_CPU_LDFLAGS} -Wl,--hash-style=gnu"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x${OPENJDK_$1_CPU_BITS}" = "x32"; then
      $1_CPU_EXECUTABLE_LDFLAGS="-stack:327680"
    elif test "x${OPENJDK_$1_CPU_BITS}" = "x64"; then
      $1_CPU_EXECUTABLE_LDFLAGS="-stack:1048576"
    fi
    if test "x${OPENJDK_$1_CPU}" = "xx86"; then
      $1_CPU_LDFLAGS="-safeseh"
    fi
  fi

  # Export variables according to old definitions, prefix with $2 if present.
  LDFLAGS_JDK_COMMON="$BASIC_LDFLAGS $BASIC_LDFLAGS_JDK_ONLY \
      $OS_LDFLAGS $DEBUGLEVEL_LDFLAGS_JDK_ONLY ${$2EXTRA_LDFLAGS}"
  $2LDFLAGS_JDKLIB="$LDFLAGS_JDK_COMMON $BASIC_LDFLAGS_JDK_LIB_ONLY \
      $SHARED_LIBRARY_FLAGS $REPRODUCIBLE_LDFLAGS $FILE_MACRO_LDFLAGS"
  $2LDFLAGS_JDKEXE="$LDFLAGS_JDK_COMMON $EXECUTABLE_LDFLAGS \
      ${$1_CPU_EXECUTABLE_LDFLAGS} $REPRODUCIBLE_LDFLAGS $FILE_MACRO_LDFLAGS"

  $2JVM_LDFLAGS="$BASIC_LDFLAGS $BASIC_LDFLAGS_JVM_ONLY $OS_LDFLAGS $OS_LDFLAGS_JVM_ONLY \
      $DEBUGLEVEL_LDFLAGS $DEBUGLEVEL_LDFLAGS_JVM_ONLY $BASIC_LDFLAGS_ONLYCXX \
      ${$1_CPU_LDFLAGS} ${$1_CPU_LDFLAGS_JVM_ONLY} ${$2EXTRA_LDFLAGS} \
      $REPRODUCIBLE_LDFLAGS $FILE_MACRO_LDFLAGS"

  AC_SUBST($2LDFLAGS_JDKLIB)
  AC_SUBST($2LDFLAGS_JDKEXE)

  AC_SUBST($2JVM_LDFLAGS)
])
