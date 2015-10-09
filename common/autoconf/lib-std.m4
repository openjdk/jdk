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

################################################################################
# Setup the standard C/C++ runtime libraries.
#
# Most importantly, determine if stdc++ should be linked statically or
# dynamically.
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_STD_LIBS],
[
  # statically link libstdc++ before C++ ABI is stablized on Linux unless
  # dynamic build is configured on command line.
  AC_ARG_WITH([stdc++lib], [AS_HELP_STRING([--with-stdc++lib=<static>,<dynamic>,<default>],
      [force linking of the C++ runtime on Linux to either static or dynamic, default is static with dynamic as fallback])],
      [
        if test "x$with_stdc__lib" != xdynamic && test "x$with_stdc__lib" != xstatic \
                && test "x$with_stdc__lib" != xdefault; then
          AC_MSG_ERROR([Bad parameter value --with-stdc++lib=$with_stdc__lib!])
        fi
      ],
      [with_stdc__lib=default]
  )

  if test "x$OPENJDK_TARGET_OS" = xlinux; then
    # Test if -lstdc++ works.
    AC_MSG_CHECKING([if dynamic link of stdc++ is possible])
    AC_LANG_PUSH(C++)
    OLD_CXXFLAGS="$CXXFLAGS"
    CXXFLAGS="$CXXFLAGS -lstdc++"
    AC_LINK_IFELSE([AC_LANG_PROGRAM([], [return 0;])],
        [has_dynamic_libstdcxx=yes],
        [has_dynamic_libstdcxx=no])
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
    AC_LINK_IFELSE([AC_LANG_PROGRAM([], [return 0;])],
        [has_static_libstdcxx=yes],
        [has_static_libstdcxx=no])
    LIBS="$OLD_LIBS"
    CXX="$OLD_CXX"
    AC_LANG_POP(C++)
    AC_MSG_RESULT([$has_static_libstdcxx])

    if test "x$has_static_libstdcxx" = xno && test "x$has_dynamic_libstdcxx" = xno; then
      AC_MSG_ERROR([Cannot link to stdc++, neither dynamically nor statically!])
    fi

    if test "x$with_stdc__lib" = xstatic && test "x$has_static_libstdcxx" = xno; then
      AC_MSG_ERROR([Static linking of libstdc++ was not possible!])
    fi

    if test "x$with_stdc__lib" = xdynamic && test "x$has_dynamic_libstdcxx" = xno; then
      AC_MSG_ERROR([Dynamic linking of libstdc++ was not possible!])
    fi

    # If dynamic was requested, it's available since it would fail above otherwise.
    # If dynamic wasn't requested, go with static unless it isn't available.
    AC_MSG_CHECKING([how to link with libstdc++])
    if test "x$with_stdc__lib" = xdynamic || test "x$has_static_libstdcxx" = xno || test "x$JVM_VARIANT_ZEROSHARK" = xtrue; then
      LIBCXX="$LIBCXX -lstdc++"
      LDCXX="$CXX"
      STATIC_CXX_SETTING="STATIC_CXX=false"
      AC_MSG_RESULT([dynamic])
    else
      LIBCXX="$LIBCXX $STATIC_STDCXX_FLAGS"
      LDCXX="$CC"
      STATIC_CXX_SETTING="STATIC_CXX=true"
      AC_MSG_RESULT([static])
    fi
  fi
  AC_SUBST(STATIC_CXX_SETTING)

  # libCrun is the c++ runtime-library with SunStudio (roughly the equivalent of gcc's libstdc++.so)
  if test "x$TOOLCHAIN_TYPE" = xsolstudio && test "x$LIBCXX" = x; then
    LIBCXX="${SYSROOT}/usr/lib${OPENJDK_TARGET_CPU_ISADIR}/libCrun.so.1"
  fi

  # TODO better (platform agnostic) test
  if test "x$OPENJDK_TARGET_OS" = xmacosx && test "x$LIBCXX" = x && test "x$TOOLCHAIN_TYPE" = xgcc; then
    LIBCXX="-lstdc++"
  fi
  AC_SUBST(LIBCXX)

  # Setup Windows runtime dlls
  if test "x$OPENJDK_TARGET_OS" = "xwindows"; then
    TOOLCHAIN_SETUP_VS_RUNTIME_DLLS
  fi
])
