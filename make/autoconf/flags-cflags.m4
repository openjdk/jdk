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
# Setup flags for C/C++ compiler
#

################################################################################
#
# How to compile shared libraries.
#
AC_DEFUN([FLAGS_SETUP_SHARED_LIBS],
[
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    # Default works for linux, might work on other platforms as well.
    SHARED_LIBRARY_FLAGS='-shared'
    # --disable-new-dtags forces use of RPATH instead of RUNPATH for rpaths.
    # This protects internal library dependencies within the JDK from being
    # overridden using LD_LIBRARY_PATH. See JDK-8326891 for more information.
    SET_EXECUTABLE_ORIGIN='-Wl,-rpath,\$$ORIGIN[$]1 -Wl,--disable-new-dtags'
    SET_SHARED_LIBRARY_ORIGIN="-Wl,-z,origin $SET_EXECUTABLE_ORIGIN"
    SET_SHARED_LIBRARY_NAME='-Wl,-soname=[$]1'

  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Linking is different on MacOSX
      SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0"
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,@loader_path$(or [$]1,/.)'
      SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Wl,-install_name,@rpath/[$]1'

    elif test "x$OPENJDK_TARGET_OS" = xaix; then
      # Linking is different on aix
      SHARED_LIBRARY_FLAGS="-shared -Wl,-bM:SRE -Wl,-bnoentry"
      SET_EXECUTABLE_ORIGIN=""
      SET_SHARED_LIBRARY_ORIGIN=''
      SET_SHARED_LIBRARY_NAME=''

    else
      # Default works for linux, might work on other platforms as well.
      SHARED_LIBRARY_FLAGS='-shared'
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,\$$ORIGIN[$]1'
      if test "x$OPENJDK_TARGET_OS" = xlinux; then
        SET_EXECUTABLE_ORIGIN="$SET_EXECUTABLE_ORIGIN -Wl,--disable-new-dtags"
      fi
      SET_SHARED_LIBRARY_NAME='-Wl,-soname=[$]1'

      # arm specific settings
      if test "x$OPENJDK_TARGET_CPU" = "xarm"; then
        # '-Wl,-z,origin' isn't used on arm.
        SET_SHARED_LIBRARY_ORIGIN='-Wl,-rpath,\$$$$ORIGIN[$]1'
      else
        SET_SHARED_LIBRARY_ORIGIN="-Wl,-z,origin $SET_EXECUTABLE_ORIGIN"
      fi
    fi

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    SHARED_LIBRARY_FLAGS="-dll"
    SET_EXECUTABLE_ORIGIN=''
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
  fi

  AC_SUBST(SET_EXECUTABLE_ORIGIN)
  AC_SUBST(SET_SHARED_LIBRARY_ORIGIN)
  AC_SUBST(SET_SHARED_LIBRARY_NAME)
  AC_SUBST(SHARED_LIBRARY_FLAGS)
])

AC_DEFUN([FLAGS_SETUP_DEBUG_SYMBOLS],
[
  # By default don't set any specific assembler debug
  # info flags for toolchains unless we know they work.
  # See JDK-8207057.
  ASFLAGS_DEBUG_SYMBOLS=""

  # Debug prefix mapping if supported by compiler
  DEBUG_PREFIX_CFLAGS=

  # Debug symbols
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$ALLOW_ABSOLUTE_PATHS_IN_OUTPUT" = "xfalse"; then
      # Check if compiler supports -fdebug-prefix-map. If so, use that to make
      # the debug symbol paths resolve to paths relative to the workspace root.
      workspace_root_trailing_slash="${WORKSPACE_ROOT%/}/"
      DEBUG_PREFIX_CFLAGS="-fdebug-prefix-map=${workspace_root_trailing_slash}="
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${DEBUG_PREFIX_CFLAGS}],
        IF_FALSE: [
            DEBUG_PREFIX_CFLAGS=
        ],
        IF_TRUE: [
            # Add debug prefix map gcc system include paths, as they cause
            # non-deterministic debug paths depending on gcc path location.
            DEBUG_PREFIX_MAP_GCC_INCLUDE_PATHS

            # Add debug prefix map for OUTPUTDIR to handle the scenario when
            # it is not located within WORKSPACE_ROOT
            outputdir_slash="${OUTPUTDIR%/}/"
            DEBUG_PREFIX_CFLAGS="$DEBUG_PREFIX_CFLAGS -fdebug-prefix-map=${outputdir_slash}="
        ]
      )
    fi

    CFLAGS_DEBUG_SYMBOLS="-g -gdwarf-4"
    ASFLAGS_DEBUG_SYMBOLS="-g"
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    if test "x$ALLOW_ABSOLUTE_PATHS_IN_OUTPUT" = "xfalse"; then
      # Check if compiler supports -fdebug-prefix-map. If so, use that to make
      # the debug symbol paths resolve to paths relative to the workspace root.
      workspace_root_trailing_slash="${WORKSPACE_ROOT%/}/"
      DEBUG_PREFIX_CFLAGS="-fdebug-prefix-map=${workspace_root_trailing_slash}="
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${DEBUG_PREFIX_CFLAGS}],
        IF_FALSE: [
            DEBUG_PREFIX_CFLAGS=
        ]
      )
    fi

    # -gdwarf-4 and -gdwarf-aranges were introduced in clang 5.0
    GDWARF_FLAGS="-gdwarf-4 -gdwarf-aranges"
    FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${GDWARF_FLAGS}],
        IF_FALSE: [GDWARF_FLAGS=""])

    CFLAGS_DEBUG_SYMBOLS="-g ${GDWARF_FLAGS}"
    ASFLAGS_DEBUG_SYMBOLS="-g"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    CFLAGS_DEBUG_SYMBOLS="-Z7"
  fi

  if test "x$DEBUG_PREFIX_CFLAGS" != x; then
    CFLAGS_DEBUG_SYMBOLS="$CFLAGS_DEBUG_SYMBOLS $DEBUG_PREFIX_CFLAGS"
    ASFLAGS_DEBUG_SYMBOLS="$ASFLAGS_DEBUG_SYMBOLS $DEBUG_PREFIX_CFLAGS"
  fi

  AC_SUBST(CFLAGS_DEBUG_SYMBOLS)
  AC_SUBST(ASFLAGS_DEBUG_SYMBOLS)
])

# gcc will embed the full system include paths in the debug info
# resulting in non-deterministic debug symbol files and thus
# non-reproducible native libraries if gcc includes are located
# in different paths.
# Add -fdebug-prefix-map'ings for root and gcc include paths,
# pointing to a common set of folders so that the binaries are deterministic:
#  root include : /usr/include
#  gcc include  : /usr/local/gcc_include
#  g++ include  : /usr/local/gxx_include
AC_DEFUN([DEBUG_PREFIX_MAP_GCC_INCLUDE_PATHS],
[
    # Determine gcc system include paths.
    # Assume default roots to start with:
    GCC_ROOT_INCLUDE="/usr/include"

    # Determine is sysroot or devkit specified?
    if test "x$SYSROOT" != "x"; then
      GCC_ROOT_INCLUDE="${SYSROOT%/}/usr/include"
    fi

    # Add root include mapping => /usr/include
    GCC_INCLUDE_DEBUG_MAP_FLAGS="-fdebug-prefix-map=${GCC_ROOT_INCLUDE}/=/usr/include/"

    # Add gcc system include mapping => /usr/local/gcc_include
    #   Find location of stddef.h using build C compiler
    GCC_SYSTEM_INCLUDE=`$ECHO "#include <stddef.h>" | \
                        $CC $CFLAGS -v -E - 2>&1 | \
                        $GREP stddef | $TAIL -1 | $TR -s " " | $CUT -d'"' -f2`
    if test "x$GCC_SYSTEM_INCLUDE" != "x"; then
      GCC_SYSTEM_INCLUDE=`$DIRNAME $GCC_SYSTEM_INCLUDE`
      GCC_INCLUDE_DEBUG_MAP_FLAGS="$GCC_INCLUDE_DEBUG_MAP_FLAGS \
          -fdebug-prefix-map=${GCC_SYSTEM_INCLUDE}/=/usr/local/gcc_include/"
    fi

    # Add g++ system include mapping => /usr/local/gxx_include
    #   Find location of cstddef using build C++ compiler
    GXX_SYSTEM_INCLUDE=`$ECHO "#include <cstddef>" | \
                        $CXX $CXXFLAGS -v -E -x c++ - 2>&1 | \
                        $GREP cstddef | $TAIL -1 | $TR -s " " | $CUT -d'"' -f2`
    if test "x$GXX_SYSTEM_INCLUDE" != "x"; then
      GXX_SYSTEM_INCLUDE=`$DIRNAME $GXX_SYSTEM_INCLUDE`
      GCC_INCLUDE_DEBUG_MAP_FLAGS="$GCC_INCLUDE_DEBUG_MAP_FLAGS \
          -fdebug-prefix-map=${GXX_SYSTEM_INCLUDE}/=/usr/local/gxx_include/"
    fi

    # Add to debug prefix cflags
    DEBUG_PREFIX_CFLAGS="$DEBUG_PREFIX_CFLAGS $GCC_INCLUDE_DEBUG_MAP_FLAGS"
])

AC_DEFUN([FLAGS_SETUP_WARNINGS],
[
  # Set default value.
  WARNINGS_AS_ERRORS_DEFAULT=true

  UTIL_ARG_ENABLE(NAME: warnings-as-errors, DEFAULT: $WARNINGS_AS_ERRORS_DEFAULT,
      RESULT: WARNINGS_AS_ERRORS,
      DEFAULT_DESC: [auto],
      DESC: [consider native warnings to be an error])
  AC_SUBST(WARNINGS_AS_ERRORS)

  case "${TOOLCHAIN_TYPE}" in
    microsoft)
      DISABLE_WARNING_PREFIX="-wd"
      BUILD_CC_DISABLE_WARNING_PREFIX="-wd"
      CFLAGS_WARNINGS_ARE_ERRORS="-WX"

      WARNINGS_ENABLE_ALL="-W3"
      DISABLED_WARNINGS="4800 5105"
      ;;

    gcc)
      DISABLE_WARNING_PREFIX="-Wno-"
      BUILD_CC_DISABLE_WARNING_PREFIX="-Wno-"
      CFLAGS_WARNINGS_ARE_ERRORS="-Werror"

      # Additional warnings that are not activated by -Wall and -Wextra
      WARNINGS_ENABLE_ADDITIONAL="-Wpointer-arith -Wsign-compare \
          -Wunused-function -Wundef -Wunused-value -Wreturn-type \
          -Wtrampolines"
      WARNINGS_ENABLE_ADDITIONAL_CXX="-Woverloaded-virtual -Wreorder"
      WARNINGS_ENABLE_ALL_CFLAGS="-Wall -Wextra -Wformat=2 $WARNINGS_ENABLE_ADDITIONAL"
      WARNINGS_ENABLE_ALL_CXXFLAGS="$WARNINGS_ENABLE_ALL_CFLAGS $WARNINGS_ENABLE_ADDITIONAL_CXX"

      DISABLED_WARNINGS="unused-parameter unused"
      # gcc10/11 on ppc generate lots of abi warnings about layout of aggregates containing vectors
      if test "x$OPENJDK_TARGET_CPU_ARCH" = "xppc"; then
        DISABLED_WARNINGS="$DISABLED_WARNINGS psabi"
      fi
      ;;

    clang)
      DISABLE_WARNING_PREFIX="-Wno-"
      BUILD_CC_DISABLE_WARNING_PREFIX="-Wno-"
      CFLAGS_WARNINGS_ARE_ERRORS="-Werror"

      # Additional warnings that are not activated by -Wall and -Wextra
      WARNINGS_ENABLE_ADDITIONAL="-Wpointer-arith -Wsign-compare -Wreorder \
          -Wunused-function -Wundef -Wunused-value -Woverloaded-virtual"
      WARNINGS_ENABLE_ALL="-Wall -Wextra -Wformat=2 $WARNINGS_ENABLE_ADDITIONAL"

      DISABLED_WARNINGS="unknown-warning-option unused-parameter unused"
      ;;
  esac
  AC_SUBST(DISABLE_WARNING_PREFIX)
  AC_SUBST(BUILD_CC_DISABLE_WARNING_PREFIX)
  AC_SUBST(CFLAGS_WARNINGS_ARE_ERRORS)
  AC_SUBST(DISABLED_WARNINGS)
  AC_SUBST(DISABLED_WARNINGS_C)
  AC_SUBST(DISABLED_WARNINGS_CXX)
])

AC_DEFUN([FLAGS_SETUP_QUALITY_CHECKS],
[
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
      # gets added conditionally on whether we produce debug symbols or not.
      # This is most likely not really correct.

      # Add runtime stack smashing and undefined behavior checks.
      CFLAGS_DEBUG_OPTIONS="-fstack-protector-all --param ssp-buffer-size=1"
      CXXFLAGS_DEBUG_OPTIONS="-fstack-protector-all --param ssp-buffer-size=1"

      JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -fstack-protector-all --param ssp-buffer-size=1"
      ;;
    esac
  fi
])

AC_DEFUN([FLAGS_SETUP_OPTIMIZATION],
[
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    C_O_FLAG_HIGHEST_JVM="-O3"
    C_O_FLAG_HIGHEST="-O3"
    C_O_FLAG_HI="-O3"
    C_O_FLAG_NORM="-O2"
    C_O_FLAG_SIZE="-Os"
    C_O_FLAG_DEBUG="-O0"
    C_O_FLAG_DEBUG_JVM="-O0"
    C_O_FLAG_NONE="-O0"
    # -D_FORTIFY_SOURCE=2 hardening option needs optimization (at least -O1) enabled
    # set for lower O-levels -U_FORTIFY_SOURCE to overwrite previous settings
    if test "x$OPENJDK_TARGET_OS" = xlinux -a "x$DEBUG_LEVEL" = "xfastdebug"; then
      DISABLE_FORTIFY_CFLAGS="-U_FORTIFY_SOURCE"
      # ASan doesn't work well with _FORTIFY_SOURCE
      # See https://github.com/google/sanitizers/wiki/AddressSanitizer#faq
      if test "x$ASAN_ENABLED" = xyes; then
        ENABLE_FORTIFY_CFLAGS="${DISABLE_FORTIFY_CFLAGS}"
      else
        ENABLE_FORTIFY_CFLAGS="-D_FORTIFY_SOURCE=2"
      fi
      C_O_FLAG_HIGHEST_JVM="${C_O_FLAG_HIGHEST_JVM} ${ENABLE_FORTIFY_CFLAGS}"
      C_O_FLAG_HIGHEST="${C_O_FLAG_HIGHEST} ${ENABLE_FORTIFY_CFLAGS}"
      C_O_FLAG_HI="${C_O_FLAG_HI} ${ENABLE_FORTIFY_CFLAGS}"
      C_O_FLAG_NORM="${C_O_FLAG_NORM} ${ENABLE_FORTIFY_CFLAGS}"
      C_O_FLAG_SIZE="${C_O_FLAG_SIZE} ${DISABLE_FORTIFY_CFLAGS}"
      C_O_FLAG_DEBUG="${C_O_FLAG_DEBUG} ${DISABLE_FORTIFY_CFLAGS}"
      C_O_FLAG_DEBUG_JVM="${C_O_FLAG_DEBUG_JVM} ${DISABLE_FORTIFY_CFLAGS}"
      C_O_FLAG_NONE="${C_O_FLAG_NONE} ${DISABLE_FORTIFY_CFLAGS}"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    if test "x$OPENJDK_TARGET_OS" = xaix; then
      C_O_FLAG_HIGHEST_JVM="-O3 -finline-functions"
      C_O_FLAG_HIGHEST="-O3 -finline-functions"
      C_O_FLAG_HI="-O3 -finline-functions"
    else
      C_O_FLAG_HIGHEST_JVM="-O3"
      C_O_FLAG_HIGHEST="-O3"
      C_O_FLAG_HI="-O3"
    fi
    C_O_FLAG_NORM="-O2"
    C_O_FLAG_DEBUG_JVM="-O0"
    C_O_FLAG_SIZE="-Os"
    C_O_FLAG_DEBUG="-O0"
    C_O_FLAG_NONE="-O0"
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

  # Now copy to C++ flags
  CXX_O_FLAG_HIGHEST_JVM="$C_O_FLAG_HIGHEST_JVM"
  CXX_O_FLAG_HIGHEST="$C_O_FLAG_HIGHEST"
  CXX_O_FLAG_HI="$C_O_FLAG_HI"
  CXX_O_FLAG_NORM="$C_O_FLAG_NORM"
  CXX_O_FLAG_DEBUG="$C_O_FLAG_DEBUG"
  CXX_O_FLAG_DEBUG_JVM="$C_O_FLAG_DEBUG_JVM"
  CXX_O_FLAG_NONE="$C_O_FLAG_NONE"
  CXX_O_FLAG_SIZE="$C_O_FLAG_SIZE"

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
  AC_SUBST(C_O_FLAG_NONE)
  AC_SUBST(C_O_FLAG_SIZE)
  AC_SUBST(CXX_O_FLAG_HIGHEST_JVM)
  AC_SUBST(CXX_O_FLAG_HIGHEST)
  AC_SUBST(CXX_O_FLAG_HI)
  AC_SUBST(CXX_O_FLAG_NORM)
  AC_SUBST(CXX_O_FLAG_NONE)
  AC_SUBST(CXX_O_FLAG_SIZE)
])

AC_DEFUN([FLAGS_SETUP_CFLAGS],
[
  ### CFLAGS

  FLAGS_SETUP_CFLAGS_HELPER

  FLAGS_OS=$OPENJDK_TARGET_OS
  FLAGS_OS_TYPE=$OPENJDK_TARGET_OS_TYPE
  FLAGS_CPU=$OPENJDK_TARGET_CPU
  FLAGS_CPU_ARCH=$OPENJDK_TARGET_CPU_ARCH
  FLAGS_CPU_BITS=$OPENJDK_TARGET_CPU_BITS
  FLAGS_CPU_ENDIAN=$OPENJDK_TARGET_CPU_ENDIAN
  FLAGS_CPU_LEGACY=$OPENJDK_TARGET_CPU_LEGACY
  FLAGS_CPU_LEGACY_LIB=$OPENJDK_TARGET_CPU_LEGACY_LIB

  FLAGS_SETUP_CFLAGS_CPU_DEP([TARGET])

  # Repeat the check for the BUILD_CC and BUILD_CXX. Need to also reset CFLAGS
  # since any target specific flags will likely not work with the build compiler.
  CC_OLD="$CC"
  CXX_OLD="$CXX"
  CFLAGS_OLD="$CFLAGS"
  CXXFLAGS_OLD="$CXXFLAGS"
  CC="$BUILD_CC"
  CXX="$BUILD_CXX"
  CFLAGS=""
  CXXFLAGS=""

  FLAGS_OS=$OPENJDK_BUILD_OS
  FLAGS_OS_TYPE=$OPENJDK_BUILD_OS_TYPE
  FLAGS_CPU=$OPENJDK_BUILD_CPU
  FLAGS_CPU_ARCH=$OPENJDK_BUILD_CPU_ARCH
  FLAGS_CPU_BITS=$OPENJDK_BUILD_CPU_BITS
  FLAGS_CPU_ENDIAN=$OPENJDK_BUILD_CPU_ENDIAN
  FLAGS_CPU_LEGACY=$OPENJDK_BUILD_CPU_LEGACY
  FLAGS_CPU_LEGACY_LIB=$OPENJDK_BUILD_CPU_LEGACY_LIB

  FLAGS_SETUP_CFLAGS_CPU_DEP([BUILD], [OPENJDK_BUILD_], [BUILD_])

  CC="$CC_OLD"
  CXX="$CXX_OLD"
  CFLAGS="$CFLAGS_OLD"
  CXXFLAGS="$CXXFLAGS_OLD"
])

################################################################################
# platform independent
AC_DEFUN([FLAGS_SETUP_CFLAGS_HELPER],
[
  #### OS DEFINES, these should be independent on toolchain
  if test "x$OPENJDK_TARGET_OS" = xlinux; then
    CFLAGS_OS_DEF_JVM="-DLINUX -D_FILE_OFFSET_BITS=64"
    CFLAGS_OS_DEF_JDK="-D_GNU_SOURCE -D_REENTRANT -D_FILE_OFFSET_BITS=64"
  elif test "x$OPENJDK_TARGET_OS" = xmacosx; then
    CFLAGS_OS_DEF_JVM="-D_ALLBSD_SOURCE -D_DARWIN_C_SOURCE -D_XOPEN_SOURCE"
    CFLAGS_OS_DEF_JDK="-D_ALLBSD_SOURCE -D_DARWIN_UNLIMITED_SELECT"
  elif test "x$OPENJDK_TARGET_OS" = xaix; then
    CFLAGS_OS_DEF_JVM="-DAIX -Dalloca'(size)'=__builtin_alloca'(size)' -D_LARGE_FILES"
    CFLAGS_OS_DEF_JDK="-D_LARGE_FILES"
  elif test "x$OPENJDK_TARGET_OS" = xbsd; then
    CFLAGS_OS_DEF_JDK="-D_ALLBSD_SOURCE"
  elif test "x$OPENJDK_TARGET_OS" = xwindows; then
    CFLAGS_OS_DEF_JVM="-D_WINDOWS -DWIN32 -D_JNI_IMPLEMENTATION_"
  fi

  CFLAGS_OS_DEF_JDK="$CFLAGS_OS_DEF_JDK -D$OPENJDK_TARGET_OS_UPPERCASE"

  #### GLOBAL DEFINES
  # Set some common defines. These works for all compilers, but assume
  # -D is universally accepted.

  # Always enable optional macros for VM.
  ALWAYS_CFLAGS_JVM="-D__STDC_FORMAT_MACROS -D__STDC_LIMIT_MACROS -D__STDC_CONSTANT_MACROS"

  ###############################################################################

  # Adjust flags according to debug level.
  # Setup debug/release defines
  if test "x$DEBUG_LEVEL" = xrelease; then
    DEBUG_CFLAGS_JDK="-DNDEBUG"
  else
    DEBUG_CFLAGS_JDK="-DDEBUG"

    if test "x$TOOLCHAIN_TYPE" = xclang && test "x$OPENJDK_TARGET_OS" = xaix; then
      DEBUG_CFLAGS_JVM="-fpic -mcmodel=large"
    fi
  fi

  if test "x$DEBUG_LEVEL" != xrelease; then
    DEBUG_OPTIONS_FLAGS_JDK="$CFLAGS_DEBUG_OPTIONS"
    DEBUG_SYMBOLS_CFLAGS_JDK="$CFLAGS_DEBUG_SYMBOLS"
  fi

  #### TOOLCHAIN DEFINES

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    ALWAYS_DEFINES_JVM="-D_GNU_SOURCE -D_REENTRANT"
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    ALWAYS_DEFINES_JVM="-D_GNU_SOURCE"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    # Access APIs for Windows 8 and above
    # see https://docs.microsoft.com/en-us/cpp/porting/modifying-winver-and-win32-winnt?view=msvc-170
    ALWAYS_DEFINES_JDK="-DWIN32_LEAN_AND_MEAN -D_WIN32_WINNT=0x0602 \
        -D_CRT_SECURE_NO_WARNINGS -D_CRT_NONSTDC_NO_DEPRECATE -DWIN32 -DIAL"
    ALWAYS_DEFINES_JVM="-DNOMINMAX -DWIN32_LEAN_AND_MEAN -D_WIN32_WINNT=0x0602 \
        -D_CRT_SECURE_NO_WARNINGS -D_CRT_NONSTDC_NO_DEPRECATE"
  fi

  ###############################################################################
  #
  #
  # CFLAGS BASIC
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    # COMMON to gcc and clang
    TOOLCHAIN_CFLAGS_JVM="-pipe -fno-rtti -fno-exceptions \
        -fvisibility=hidden -fno-strict-aliasing -fno-omit-frame-pointer"
  fi

  if test "x$TOOLCHAIN_TYPE" = xclang && test "x$OPENJDK_TARGET_OS" = xaix; then
    # clang compiler on aix needs -ffunction-sections
    TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM -ffunction-sections -ftls-model -fno-math-errno -fstack-protector"
    TOOLCHAIN_CFLAGS_JDK="-ffunction-sections -fsigned-char -fstack-protector"
  fi

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM -fstack-protector"
    TOOLCHAIN_CFLAGS_JDK="-fvisibility=hidden -pipe -fstack-protector"
    # reduce lib size on linux in link step, this needs also special compile flags
    # do this on s390x also for libjvm (where serviceability agent is not supported)
    if test "x$ENABLE_LINKTIME_GC" = xtrue; then
      TOOLCHAIN_CFLAGS_JDK="$TOOLCHAIN_CFLAGS_JDK -ffunction-sections -fdata-sections"
      if test "x$OPENJDK_TARGET_CPU" = xs390x && test "x$DEBUG_LEVEL" == xrelease; then
        TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM -ffunction-sections -fdata-sections"
      fi
    fi
    # technically NOT for CXX (but since this gives *worse* performance, use
    # no-strict-aliasing everywhere!)
    TOOLCHAIN_CFLAGS_JDK_CONLY="-fno-strict-aliasing"

  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    # Restrict the debug information created by Clang to avoid
    # too big object files and speed the build up a little bit
    # (see http://llvm.org/bugs/show_bug.cgi?id=7554)
    TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM -flimit-debug-info"

    # In principle the stack alignment below is cpu- and ABI-dependent and
    # should agree with values of StackAlignmentInBytes in various
    # src/hotspot/cpu/*/globalDefinitions_*.hpp files, but this value currently
    # works for all platforms.
    TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM -mno-omit-leaf-frame-pointer -mstack-alignment=16"

    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      if test "x$DEBUG_LEVEL" = xrelease; then
        # Clang does not inline as much as GCC does for functions with "inline" keyword by default.
        # This causes noticeable slowdown in pause time for G1, and possibly in other areas.
        # Increasing the inline hint threshold avoids the slowdown for Clang-built JVM.
        TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM -mllvm -inlinehint-threshold=100000"
      fi
      TOOLCHAIN_CFLAGS_JDK="-pipe"
      TOOLCHAIN_CFLAGS_JDK_CONLY="-fno-strict-aliasing" # technically NOT for CXX
    fi
    TOOLCHAIN_CFLAGS_JDK="$TOOLCHAIN_CFLAGS_JDK -fvisibility=hidden"

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    # The -utf-8 option sets source and execution character sets to UTF-8 to enable correct
    # compilation of all source files regardless of the active code page on Windows.
    TOOLCHAIN_CFLAGS_JVM="-nologo -MD -Zc:preprocessor -Zc:inline -permissive- -utf-8 -MP"
    TOOLCHAIN_CFLAGS_JDK="-nologo -MD -Zc:preprocessor -Zc:inline -permissive- -utf-8 -Zc:wchar_t-"
  fi

  # CFLAGS C language level for JDK sources (hotspot only uses C++)
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    LANGSTD_CFLAGS="-std=c11"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    LANGSTD_CFLAGS="-std:c11"
  fi
  TOOLCHAIN_CFLAGS_JDK_CONLY="$LANGSTD_CFLAGS $TOOLCHAIN_CFLAGS_JDK_CONLY"

  # CXXFLAGS C++ language level for all of JDK, including Hotspot.
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    LANGSTD_CXXFLAGS="-std=c++14"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    LANGSTD_CXXFLAGS="-std:c++14"
  else
    AC_MSG_ERROR([Cannot enable C++14 for this toolchain])
  fi
  TOOLCHAIN_CFLAGS_JDK_CXXONLY="$TOOLCHAIN_CFLAGS_JDK_CXXONLY $LANGSTD_CXXFLAGS"
  TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM $LANGSTD_CXXFLAGS"
  ADLC_LANGSTD_CXXFLAGS="$LANGSTD_CXXFLAGS"

  # CFLAGS WARNINGS STUFF
  # Set JVM_CFLAGS warning handling
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    WARNING_CFLAGS_JDK_CONLY="$WARNINGS_ENABLE_ALL_CFLAGS"
    WARNING_CFLAGS_JDK_CXXONLY="$WARNINGS_ENABLE_ALL_CXXFLAGS"
    WARNING_CFLAGS_JVM="$WARNINGS_ENABLE_ALL_CXXFLAGS"

  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    WARNING_CFLAGS="$WARNINGS_ENABLE_ALL"

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    WARNING_CFLAGS="$WARNINGS_ENABLE_ALL"

  fi

  # Set some additional per-OS defines.

  # Additional macosx handling
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    OS_CFLAGS="-DMAC_OS_X_VERSION_MIN_REQUIRED=$MACOSX_VERSION_MIN_NODOTS \
        -mmacosx-version-min=$MACOSX_VERSION_MIN"

    if test -n "$MACOSX_VERSION_MAX"; then
        OS_CFLAGS="$OS_CFLAGS \
            -DMAC_OS_X_VERSION_MAX_ALLOWED=$MACOSX_VERSION_MAX_NODOTS"
    fi
  fi

  OS_CFLAGS="$OS_CFLAGS -DLIBC=$OPENJDK_TARGET_LIBC"
  if test "x$OPENJDK_TARGET_LIBC" = xmusl; then
    OS_CFLAGS="$OS_CFLAGS -DMUSL_LIBC"
  fi

  # Where does this really belong??
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    PICFLAG="-fPIC"
    PIEFLAG="-fPIE"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    PICFLAG=""
  fi

  if test "x$TOOLCHAIN_TYPE" = xclang && test "x$OPENJDK_TARGET_OS" = xaix; then
    JVM_PICFLAG="-fpic -mcmodel=large"
  else
    JVM_PICFLAG="$PICFLAG"
  fi
  JDK_PICFLAG="$PICFLAG"

  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # Linking is different on MacOSX
    JDK_PICFLAG=''
    if test "x$STATIC_BUILD" = xtrue; then
      JVM_PICFLAG=""
    fi
  fi

  # Extra flags needed when building optional static versions of certain
  # JDK libraries.
  STATIC_LIBS_CFLAGS="-DSTATIC_BUILD=1"
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    STATIC_LIBS_CFLAGS="$STATIC_LIBS_CFLAGS -ffunction-sections -fdata-sections \
      -DJNIEXPORT='__attribute__((visibility(\"default\")))'"
  else
    STATIC_LIBS_CFLAGS="$STATIC_LIBS_CFLAGS -DJNIEXPORT="
  fi
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    # Disable relax-relocation to enable compatibility with older linkers
    RELAX_RELOCATIONS_FLAG="-Xassembler -mrelax-relocations=no"
    FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${RELAX_RELOCATIONS_FLAG}],
        IF_TRUE: [STATIC_LIBS_CFLAGS="$STATIC_LIBS_CFLAGS ${RELAX_RELOCATIONS_FLAG}"])
  fi
  AC_SUBST(STATIC_LIBS_CFLAGS)
])

################################################################################
# $1 - Either BUILD or TARGET to pick the correct OS/CPU variables to check
#      conditionals against.
# $2 - Optional prefix for each variable defined.
# $3 - Optional prefix for compiler variables (either BUILD_ or nothing).
AC_DEFUN([FLAGS_SETUP_CFLAGS_CPU_DEP],
[
  #### CPU DEFINES, these should (in theory) be independent on toolchain

  # Setup target CPU
  # Setup endianness
  if test "x$FLAGS_CPU_ENDIAN" = xlittle; then
    $1_DEFINES_CPU_JVM="-DVM_LITTLE_ENDIAN"
    $1_DEFINES_CPU_JDK="-D_LITTLE_ENDIAN"
  else
    $1_DEFINES_CPU_JDK="-D_BIG_ENDIAN"
  fi

  # setup CPU bit size
  $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -DARCH='\"$FLAGS_CPU_LEGACY\"' \
      -D$FLAGS_CPU_LEGACY"

  if test "x$FLAGS_CPU_BITS" = x64; then
    $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -D_LP64=1"
    $1_DEFINES_CPU_JVM="${$1_DEFINES_CPU_JVM} -D_LP64=1"
  fi

  # toolchain dependent, per-cpu
  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$FLAGS_CPU" = xaarch64; then
      $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -D_ARM64_ -Darm64"
    elif test "x$FLAGS_CPU" = xx86_64; then
      $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -D_AMD64_ -Damd64"
    else
      $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -D_X86_ -Dx86"
    fi
  fi

  # CFLAGS PER CPU
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    # COMMON to gcc and clang
    AC_MSG_CHECKING([if $1 is x86])
    if test "x$FLAGS_CPU" = xx86; then
      AC_MSG_RESULT([yes])
      AC_MSG_CHECKING([if control flow protection is enabled by additional compiler flags])
      if echo "${EXTRA_CFLAGS}${EXTRA_CXXFLAGS}${EXTRA_ASFLAGS}" | ${GREP} -q 'fcf-protection' ; then
        # cf-protection requires CMOV and thus i686
        $1_CFLAGS_CPU="-march=i686"
        AC_MSG_RESULT([yes, forcing ${$1_CFLAGS_CPU}])
      else
        # Force compatibility with i586 on 32 bit intel platforms.
        $1_CFLAGS_CPU="-march=i586"
        AC_MSG_RESULT([no, forcing ${$1_CFLAGS_CPU}])
      fi
    else
      AC_MSG_RESULT([no])
    fi
  fi

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$FLAGS_CPU" = xaarch64; then
      # -Wno-psabi to get rid of annoying "note: parameter passing for argument of type '<type> changed in GCC 9.1"
      $1_CFLAGS_CPU="-Wno-psabi"
    elif test "x$FLAGS_CPU" = xarm; then
      # -Wno-psabi to get rid of annoying "note: the mangling of 'va_list' has changed in GCC 4.4"
      $1_CFLAGS_CPU="-fsigned-char -Wno-psabi $ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS -DJDK_ARCH_ABI_PROP_NAME='\"\$(JDK_ARCH_ABI_PROP_NAME)\"'"
      $1_CFLAGS_CPU_JVM="-DARM"
    elif test "x$FLAGS_CPU_ARCH" = xppc; then
      $1_CFLAGS_CPU_JVM="-minsert-sched-nops=regroup_exact -mno-multiple -mno-string"
      if test "x$FLAGS_CPU" = xppc64; then
        # -mminimal-toc fixes `relocation truncated to fit' error for gcc 4.1.
        # Use ppc64 instructions, but schedule for power5
        $1_CFLAGS_CPU_JVM="${$1_CFLAGS_CPU_JVM} -mminimal-toc -mcpu=powerpc64 -mtune=power5"
      elif test "x$FLAGS_CPU" = xppc64le; then
        # Little endian machine uses ELFv2 ABI.
        # Use Power8, this is the first CPU to support PPC64 LE with ELFv2 ABI.
        $1_CFLAGS_CPU_JVM="${$1_CFLAGS_CPU_JVM} -DABI_ELFv2 -mcpu=power8 -mtune=power8"
      fi
    elif test "x$FLAGS_CPU" = xs390x; then
      $1_CFLAGS_CPU="-mbackchain -march=z10"
    fi

    if test "x$FLAGS_CPU_ARCH" != xarm &&  test "x$FLAGS_CPU_ARCH" != xppc; then
      # for all archs except arm and ppc, prevent gcc to omit frame pointer
      $1_CFLAGS_CPU_JDK="${$1_CFLAGS_CPU_JDK} -fno-omit-frame-pointer"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    if test "x$FLAGS_OS" = xlinux; then
      # ppc test not really needed for clang
      if test "x$FLAGS_CPU_ARCH" != xarm &&  test "x$FLAGS_CPU_ARCH" != xppc; then
        # for all archs except arm and ppc, prevent gcc to omit frame pointer
        $1_CFLAGS_CPU_JDK="${$1_CFLAGS_CPU_JDK} -fno-omit-frame-pointer"
      fi
    fi
    if test "x$OPENJDK_TARGET_OS" = xaix; then
      $1_CFLAGS_CPU="-mcpu=pwr8"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$FLAGS_CPU" = xx86; then
      $1_CFLAGS_CPU_JVM="-arch:IA32"
    elif test "x$OPENJDK_TARGET_CPU" = xx86_64; then
      if test "x$DEBUG_LEVEL" != xrelease; then
        # NOTE: This is probably redundant; -homeparams is default on
        # non-release builds.
        $1_CFLAGS_CPU_JVM="-homeparams"
      fi
    fi
  fi

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    FLAGS_SETUP_GCC6_COMPILER_FLAGS($1, $3)
    $1_TOOLCHAIN_CFLAGS="${$1_GCC6_CFLAGS}"

    $1_WARNING_CFLAGS_JVM="-Wno-format-zero-length -Wtype-limits -Wuninitialized"
  fi

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    REPRODUCIBLE_CFLAGS="-experimental:deterministic"
    FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${REPRODUCIBLE_CFLAGS}],
        PREFIX: $3,
        IF_FALSE: [
            REPRODUCIBLE_CFLAGS=
        ]
    )
    AC_SUBST(REPRODUCIBLE_CFLAGS)
  fi

  # Prevent the __FILE__ macro from generating absolute paths into the built
  # binaries. Depending on toolchain, different mitigations are possible.
  # * GCC and Clang of new enough versions have -fmacro-prefix-map.
  # * For most other toolchains, supplying all source files and -I flags as
  #   relative paths fixes the issue.
  FILE_MACRO_CFLAGS=
  if test "x$ALLOW_ABSOLUTE_PATHS_IN_OUTPUT" = "xfalse"; then
    if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
      # Check if compiler supports -fmacro-prefix-map. If so, use that to make
      # the __FILE__ macro resolve to paths relative to the workspace root.
      workspace_root_trailing_slash="${WORKSPACE_ROOT%/}/"
      FILE_MACRO_CFLAGS="-fmacro-prefix-map=${workspace_root_trailing_slash}="
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${FILE_MACRO_CFLAGS}],
          PREFIX: $3,
          IF_FALSE: [
              FILE_MACRO_CFLAGS=
          ]
      )
    elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
      # There is a known issue with the pathmap if the mapping is made to the
      # empty string. Add a minimal string "s" as prefix to work around this.
      # PATHMAP_FLAGS is also added to LDFLAGS in flags-ldflags.m4.
      PATHMAP_FLAGS="-pathmap:${WORKSPACE_ROOT}=s"
      FILE_MACRO_CFLAGS="$PATHMAP_FLAGS"
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${FILE_MACRO_CFLAGS}],
          PREFIX: $3,
          IF_FALSE: [
              PATHMAP_FLAGS=
              FILE_MACRO_CFLAGS=
          ]
      )
      if test "x$FILE_MACRO_CFLAGS" != x; then
        # Add -pathmap for all VS system include paths using Windows
        # full Long path name that is generated by the compiler
        # Not enabled under WSL as there is no easy way to obtain the
        # Windows full long paths, thus reproducible WSL builds will
        # depend on building with the same VS toolchain install location.
        if test "x$OPENJDK_BUILD_OS_ENV" != "xwindows.wsl1" && test "x$OPENJDK_BUILD_OS_ENV" != "xwindows.wsl2"; then
          for ipath in ${$3SYSROOT_CFLAGS}; do
              if test "x${ipath:0:2}" == "x-I"; then
                  ipath_path=${ipath#"-I"}
                  UTIL_FIXUP_WIN_LONG_PATH(ipath_path)
                  FILE_MACRO_CFLAGS="$FILE_MACRO_CFLAGS -pathmap:\"$ipath_path\"=vsi"
              fi
          done
        fi
      fi
    fi

    AC_MSG_CHECKING([how to prevent absolute paths in output])
    if test "x$FILE_MACRO_CFLAGS" != x; then
      AC_MSG_RESULT([using compiler options])
    else
      AC_MSG_RESULT([using relative paths])
    fi
  fi
  AC_SUBST(FILE_MACRO_CFLAGS)

  FLAGS_SETUP_BRANCH_PROTECTION

  # EXPORT to API
  CFLAGS_JVM_COMMON="$ALWAYS_CFLAGS_JVM $ALWAYS_DEFINES_JVM \
      $TOOLCHAIN_CFLAGS_JVM ${$1_TOOLCHAIN_CFLAGS_JVM} \
      $OS_CFLAGS $OS_CFLAGS_JVM $CFLAGS_OS_DEF_JVM $DEBUG_CFLAGS_JVM \
      $WARNING_CFLAGS $WARNING_CFLAGS_JVM $JVM_PICFLAG $FILE_MACRO_CFLAGS \
      $REPRODUCIBLE_CFLAGS $BRANCH_PROTECTION_CFLAGS"

  CFLAGS_JDK_COMMON="$ALWAYS_DEFINES_JDK $TOOLCHAIN_CFLAGS_JDK \
      $OS_CFLAGS $CFLAGS_OS_DEF_JDK $DEBUG_CFLAGS_JDK $DEBUG_OPTIONS_FLAGS_JDK \
      $WARNING_CFLAGS $WARNING_CFLAGS_JDK $DEBUG_SYMBOLS_CFLAGS_JDK \
      $FILE_MACRO_CFLAGS $REPRODUCIBLE_CFLAGS $BRANCH_PROTECTION_CFLAGS"

  # Use ${$2EXTRA_CFLAGS} to block EXTRA_CFLAGS to be added to build flags.
  # (Currently we don't have any OPENJDK_BUILD_EXTRA_CFLAGS, but that might
  # change in the future.)

  CFLAGS_JDK_COMMON_CONLY="$TOOLCHAIN_CFLAGS_JDK_CONLY  \
      $WARNING_CFLAGS_JDK_CONLY ${$2EXTRA_CFLAGS}"
  CFLAGS_JDK_COMMON_CXXONLY="$ALWAYS_DEFINES_JDK_CXXONLY \
      $TOOLCHAIN_CFLAGS_JDK_CXXONLY \
      ${$1_TOOLCHAIN_CFLAGS_JDK_CXXONLY} \
      $WARNING_CFLAGS_JDK_CXXONLY ${$2EXTRA_CXXFLAGS}"

  $1_CFLAGS_JVM="${$1_DEFINES_CPU_JVM} ${$1_CFLAGS_CPU} ${$1_CFLAGS_CPU_JVM} ${$1_TOOLCHAIN_CFLAGS} ${$1_WARNING_CFLAGS_JVM}"
  $1_CFLAGS_JDK="${$1_DEFINES_CPU_JDK} ${$1_CFLAGS_CPU} ${$1_CFLAGS_CPU_JDK} ${$1_TOOLCHAIN_CFLAGS}"

  $2JVM_CFLAGS="$CFLAGS_JVM_COMMON ${$1_CFLAGS_JVM} ${$2EXTRA_CXXFLAGS}"

  $2CFLAGS_JDKEXE="$CFLAGS_JDK_COMMON $CFLAGS_JDK_COMMON_CONLY ${$1_CFLAGS_JDK} $PIEFLAG"
  $2CXXFLAGS_JDKEXE="$CFLAGS_JDK_COMMON $CFLAGS_JDK_COMMON_CXXONLY ${$1_CFLAGS_JDK} $PIEFLAG"
  $2CFLAGS_JDKLIB="$CFLAGS_JDK_COMMON $CFLAGS_JDK_COMMON_CONLY ${$1_CFLAGS_JDK} \
      $JDK_PICFLAG ${$1_CFLAGS_CPU_JDK_LIBONLY}"
  $2CXXFLAGS_JDKLIB="$CFLAGS_JDK_COMMON $CFLAGS_JDK_COMMON_CXXONLY ${$1_CFLAGS_JDK} \
      $JDK_PICFLAG ${$1_CFLAGS_CPU_JDK_LIBONLY}"

  AC_SUBST($2JVM_CFLAGS)
  AC_SUBST($2CFLAGS_JDKLIB)
  AC_SUBST($2CFLAGS_JDKEXE)
  AC_SUBST($2CXXFLAGS_JDKLIB)
  AC_SUBST($2CXXFLAGS_JDKEXE)
  AC_SUBST($2ADLC_LANGSTD_CXXFLAGS)

  COMPILER_FP_CONTRACT_OFF_FLAG="-ffp-contract=off"
  # Check that the compiler supports -ffp-contract=off flag
  # Set FDLIBM_CFLAGS to -ffp-contract=off if it does. Empty
  # otherwise.
  # These flags are required for GCC-based builds of
  # fdlibm with optimization without losing precision.
  # Notably, -ffp-contract=off needs to be added for GCC >= 4.6.
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${COMPILER_FP_CONTRACT_OFF_FLAG}],
        PREFIX: $3,
        IF_TRUE: [$2FDLIBM_CFLAGS=${COMPILER_FP_CONTRACT_OFF_FLAG}],
        IF_FALSE: [$2FDLIBM_CFLAGS=""])
  fi
  AC_SUBST($2FDLIBM_CFLAGS)
])

# FLAGS_SETUP_GCC6_COMPILER_FLAGS([PREFIX])
# Arguments:
# $1 - Prefix for each variable defined.
# $2 - Prefix for compiler variables (either BUILD_ or nothing).
AC_DEFUN([FLAGS_SETUP_GCC6_COMPILER_FLAGS],
[
  # This flag is required for GCC 6 builds as undefined behavior in OpenJDK code
  # runs afoul of the more aggressive versions of this optimization.
  NO_LIFETIME_DSE_CFLAG="-fno-lifetime-dse"
  FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$NO_LIFETIME_DSE_CFLAG],
      PREFIX: $2, IF_FALSE: [NO_LIFETIME_DSE_CFLAG=""])
  $1_GCC6_CFLAGS="${NO_LIFETIME_DSE_CFLAG}"
])

AC_DEFUN_ONCE([FLAGS_SETUP_BRANCH_PROTECTION],
[
  # Is branch protection available?
  BRANCH_PROTECTION_AVAILABLE=false
  BRANCH_PROTECTION_FLAG="-mbranch-protection=standard"

  if test "x$OPENJDK_TARGET_CPU" = xaarch64; then
    if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${BRANCH_PROTECTION_FLAG}],
          IF_TRUE: [BRANCH_PROTECTION_AVAILABLE=true])
    fi
  fi

  BRANCH_PROTECTION_CFLAGS=""
  UTIL_ARG_ENABLE(NAME: branch-protection, DEFAULT: false,
      RESULT: USE_BRANCH_PROTECTION, AVAILABLE: $BRANCH_PROTECTION_AVAILABLE,
      DESC: [enable branch protection when compiling C/C++],
      IF_ENABLED: [ BRANCH_PROTECTION_CFLAGS=${BRANCH_PROTECTION_FLAG}])
  AC_SUBST(BRANCH_PROTECTION_CFLAGS)
])
