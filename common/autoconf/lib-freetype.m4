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
# Build the freetype lib from source
################################################################################
AC_DEFUN([LIB_BUILD_FREETYPE],
[
  FREETYPE_SRC_PATH="$1"
  BUILD_FREETYPE=yes

  # Check if the freetype sources are acessible..
  if ! test -d $FREETYPE_SRC_PATH; then
    AC_MSG_WARN([--with-freetype-src specified, but can not find path "$FREETYPE_SRC_PATH" - ignoring --with-freetype-src])
    BUILD_FREETYPE=no
  fi
  # ..and contain a vc2010 project file
  vcxproj_path="$FREETYPE_SRC_PATH/builds/windows/vc2010/freetype.vcxproj"
  if test "x$BUILD_FREETYPE" = xyes && ! test -s $vcxproj_path; then
    AC_MSG_WARN([Can not find project file $vcxproj_path (you may try a newer freetype version) - ignoring --with-freetype-src])
    BUILD_FREETYPE=no
  fi
  # Now check if configure found a version of 'msbuild.exe'
  if test "x$BUILD_FREETYPE" = xyes && test "x$MSBUILD" == x ; then
    AC_MSG_WARN([Can not find an msbuild.exe executable (you may try to install .NET 4.0) - ignoring --with-freetype-src])
    BUILD_FREETYPE=no
  fi

  # Ready to go..
  if test "x$BUILD_FREETYPE" = xyes; then
    # msbuild requires trailing slashes for output directories
    freetype_lib_path="$FREETYPE_SRC_PATH/lib$OPENJDK_TARGET_CPU_BITS/"
    freetype_lib_path_unix="$freetype_lib_path"
    freetype_obj_path="$FREETYPE_SRC_PATH/obj$OPENJDK_TARGET_CPU_BITS/"
    BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH(vcxproj_path)
    BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH(freetype_lib_path)
    BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH(freetype_obj_path)
    if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
      freetype_platform=x64
    else
      freetype_platform=win32
    fi

    # The original freetype project file is for VS 2010 (i.e. 'v100'),
    # so we have to adapt the toolset if building with any other toolsed (i.e. SDK).
    # Currently 'PLATFORM_TOOLSET' is set in 'TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT'/
    # 'TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT' in toolchain_windows.m4
    AC_MSG_NOTICE([Trying to compile freetype sources with PlatformToolset=$PLATFORM_TOOLSET to $freetype_lib_path_unix ...])

    # First we try to build the freetype.dll
    $ECHO -e "@echo off\n"\
        "$MSBUILD $vcxproj_path "\
        "/p:PlatformToolset=$PLATFORM_TOOLSET "\
        "/p:Configuration=\"Release Multithreaded\" "\
        "/p:Platform=$freetype_platform "\
        "/p:ConfigurationType=DynamicLibrary "\
        "/p:TargetName=freetype "\
        "/p:OutDir=\"$freetype_lib_path\" "\
        "/p:IntDir=\"$freetype_obj_path\" > freetype.log" > freetype.bat
    cmd /c freetype.bat

    if test -s "$freetype_lib_path_unix/freetype.dll"; then
      # If that succeeds we also build freetype.lib
      $ECHO -e "@echo off\n"\
          "$MSBUILD $vcxproj_path "\
          "/p:PlatformToolset=$PLATFORM_TOOLSET "\
          "/p:Configuration=\"Release Multithreaded\" "\
          "/p:Platform=$freetype_platform "\
          "/p:ConfigurationType=StaticLibrary "\
          "/p:TargetName=freetype "\
          "/p:OutDir=\"$freetype_lib_path\" "\
          "/p:IntDir=\"$freetype_obj_path\" >> freetype.log" > freetype.bat
      cmd /c freetype.bat

      if test -s "$freetype_lib_path_unix/freetype.lib"; then
        # Once we build both, lib and dll, set freetype lib and include path appropriately
        POTENTIAL_FREETYPE_INCLUDE_PATH="$FREETYPE_SRC_PATH/include"
        POTENTIAL_FREETYPE_LIB_PATH="$freetype_lib_path_unix"
        AC_MSG_NOTICE([Compiling freetype sources succeeded! (see freetype.log for build results)])
      else
        BUILD_FREETYPE=no
      fi
    else
      BUILD_FREETYPE=no
    fi
  fi
])

################################################################################
# Check if a potential freeype library match is correct and usable
################################################################################
AC_DEFUN([LIB_CHECK_POTENTIAL_FREETYPE],
[
  POTENTIAL_FREETYPE_INCLUDE_PATH="$1"
  POTENTIAL_FREETYPE_LIB_PATH="$2"
  METHOD="$3"

  # Let's start with an optimistic view of the world :-)
  FOUND_FREETYPE=yes

  # First look for the canonical freetype main include file ft2build.h.
  if ! test -s "$POTENTIAL_FREETYPE_INCLUDE_PATH/ft2build.h"; then
    # Oh no! Let's try in the freetype2 directory. This is needed at least at Mac OS X Yosemite.
    POTENTIAL_FREETYPE_INCLUDE_PATH="$POTENTIAL_FREETYPE_INCLUDE_PATH/freetype2"
    if ! test -s "$POTENTIAL_FREETYPE_INCLUDE_PATH/ft2build.h"; then
      # Fail.
      FOUND_FREETYPE=no
    fi
  fi

  if test "x$FOUND_FREETYPE" = xyes; then
    # Include file found, let's continue the sanity check.
    AC_MSG_NOTICE([Found freetype include files at $POTENTIAL_FREETYPE_INCLUDE_PATH using $METHOD])

    # Reset to default value
    FREETYPE_BASE_NAME=freetype
    FREETYPE_LIB_NAME="${LIBRARY_PREFIX}${FREETYPE_BASE_NAME}${SHARED_LIBRARY_SUFFIX}"
    if ! test -s "$POTENTIAL_FREETYPE_LIB_PATH/$FREETYPE_LIB_NAME"; then
      if test "x$OPENJDK_TARGET_OS" = xmacosx \
          && test -s "$POTENTIAL_FREETYPE_LIB_PATH/${LIBRARY_PREFIX}freetype.6${SHARED_LIBRARY_SUFFIX}"; then
        # On Mac OS X Yosemite, the symlink from libfreetype.dylib to libfreetype.6.dylib disappeared. Check
        # for the .6 version explicitly.
        FREETYPE_BASE_NAME=freetype.6
        FREETYPE_LIB_NAME="${LIBRARY_PREFIX}${FREETYPE_BASE_NAME}${SHARED_LIBRARY_SUFFIX}"
        AC_MSG_NOTICE([Compensating for missing symlink by using version 6 explicitly])
      else
        AC_MSG_NOTICE([Could not find $POTENTIAL_FREETYPE_LIB_PATH/$FREETYPE_LIB_NAME. Ignoring location.])
        FOUND_FREETYPE=no
      fi
    else
      if test "x$OPENJDK_TARGET_OS" = xwindows; then
        # On Windows, we will need both .lib and .dll file.
        if ! test -s "$POTENTIAL_FREETYPE_LIB_PATH/${FREETYPE_BASE_NAME}.lib"; then
          AC_MSG_NOTICE([Could not find $POTENTIAL_FREETYPE_LIB_PATH/${FREETYPE_BASE_NAME}.lib. Ignoring location.])
          FOUND_FREETYPE=no
        fi
      elif test "x$OPENJDK_TARGET_OS" = xsolaris \
          && test -s "$POTENTIAL_FREETYPE_LIB_PATH$OPENJDK_TARGET_CPU_ISADIR/$FREETYPE_LIB_NAME"; then
        # Found lib in isa dir, use that instead.
        POTENTIAL_FREETYPE_LIB_PATH="$POTENTIAL_FREETYPE_LIB_PATH$OPENJDK_TARGET_CPU_ISADIR"
        AC_MSG_NOTICE([Rewriting to use $POTENTIAL_FREETYPE_LIB_PATH instead])
      fi
    fi
  fi

  if test "x$FOUND_FREETYPE" = xyes; then
    BASIC_FIXUP_PATH(POTENTIAL_FREETYPE_INCLUDE_PATH)
    BASIC_FIXUP_PATH(POTENTIAL_FREETYPE_LIB_PATH)

    FREETYPE_INCLUDE_PATH="$POTENTIAL_FREETYPE_INCLUDE_PATH"
    AC_MSG_CHECKING([for freetype includes])
    AC_MSG_RESULT([$FREETYPE_INCLUDE_PATH])
    FREETYPE_LIB_PATH="$POTENTIAL_FREETYPE_LIB_PATH"
    AC_MSG_CHECKING([for freetype libraries])
    AC_MSG_RESULT([$FREETYPE_LIB_PATH])
  fi
])

################################################################################
# Setup freetype (The FreeType2 font rendering library)
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_FREETYPE],
[
  AC_ARG_WITH(freetype, [AS_HELP_STRING([--with-freetype],
      [specify prefix directory for the freetype package
      (expecting the libraries under PATH/lib and the headers under PATH/include)])])
  AC_ARG_WITH(freetype-include, [AS_HELP_STRING([--with-freetype-include],
      [specify directory for the freetype include files])])
  AC_ARG_WITH(freetype-lib, [AS_HELP_STRING([--with-freetype-lib],
      [specify directory for the freetype library])])
  AC_ARG_WITH(freetype-src, [AS_HELP_STRING([--with-freetype-src],
      [specify directory with freetype sources to automatically build the library (experimental, Windows-only)])])
  AC_ARG_ENABLE(freetype-bundling, [AS_HELP_STRING([--disable-freetype-bundling],
      [disable bundling of the freetype library with the build result @<:@enabled on Windows or when using --with-freetype, disabled otherwise@:>@])])

  # Need to specify explicitly since it needs to be overridden on some versions of macosx
  FREETYPE_BASE_NAME=freetype
  FREETYPE_CFLAGS=
  FREETYPE_LIBS=
  FREETYPE_BUNDLE_LIB_PATH=

  if test "x$NEEDS_LIB_FREETYPE" = xfalse; then
    if (test "x$with_freetype" != x  && test "x$with_freetype" != xno) || \
        (test "x$with_freetype_include" != x && test "x$with_freetype_include" != xno) || \
        (test "x$with_freetype_lib" != x && test "x$with_freetype_lib" != xno) || \
        (test "x$with_freetype_src" != x && test "x$with_freetype_src" != xno); then
      AC_MSG_WARN([[freetype not used, so --with-freetype[-*] is ignored]])
    fi
    if (test "x$enable_freetype_bundling" != x && test "x$enable_freetype_bundling" != xno); then
      AC_MSG_WARN([freetype not used, so --enable-freetype-bundling is ignored])
    fi
  else
    # freetype is needed to build; go get it!

    BUNDLE_FREETYPE="$enable_freetype_bundling"

    if  test "x$with_freetype_src" != x; then
      if test "x$OPENJDK_TARGET_OS" = xwindows; then
        # Try to build freetype if --with-freetype-src was given on Windows
        LIB_BUILD_FREETYPE([$with_freetype_src])
        if test "x$BUILD_FREETYPE" = xyes; then
          # Okay, we built it. Check that it works.
          LIB_CHECK_POTENTIAL_FREETYPE($POTENTIAL_FREETYPE_INCLUDE_PATH, $POTENTIAL_FREETYPE_LIB_PATH, [--with-freetype-src])
          if test "x$FOUND_FREETYPE" != xyes; then
            AC_MSG_ERROR([Can not use the built freetype at location given by --with-freetype-src])
          fi
        else
          AC_MSG_NOTICE([User specified --with-freetype-src but building freetype failed. (see freetype.log for build results)])
          AC_MSG_ERROR([Consider building freetype manually and using --with-freetype instead.])
        fi
      else
        AC_MSG_WARN([--with-freetype-src is currently only supported on Windows - ignoring])
      fi
    fi

    if test "x$with_freetype" != x || test "x$with_freetype_include" != x || test "x$with_freetype_lib" != x; then
      # User has specified settings

      if test "x$BUNDLE_FREETYPE" = x; then
        # If not specified, default is to bundle freetype
        BUNDLE_FREETYPE=yes
      fi

      if test "x$with_freetype" != x; then
        POTENTIAL_FREETYPE_INCLUDE_PATH="$with_freetype/include"
        POTENTIAL_FREETYPE_LIB_PATH="$with_freetype/lib"
      fi

      # Allow --with-freetype-lib and --with-freetype-include to override
      if test "x$with_freetype_include" != x; then
        POTENTIAL_FREETYPE_INCLUDE_PATH="$with_freetype_include"
      fi
      if test "x$with_freetype_lib" != x; then
        POTENTIAL_FREETYPE_LIB_PATH="$with_freetype_lib"
      fi

      if test "x$POTENTIAL_FREETYPE_INCLUDE_PATH" != x && test "x$POTENTIAL_FREETYPE_LIB_PATH" != x; then
        # Okay, we got it. Check that it works.
        LIB_CHECK_POTENTIAL_FREETYPE($POTENTIAL_FREETYPE_INCLUDE_PATH, $POTENTIAL_FREETYPE_LIB_PATH, [--with-freetype])
        if test "x$FOUND_FREETYPE" != xyes; then
          AC_MSG_ERROR([Can not find or use freetype at location given by --with-freetype])
        fi
      else
        # User specified only one of lib or include. This is an error.
        if test "x$POTENTIAL_FREETYPE_INCLUDE_PATH" = x ; then
          AC_MSG_NOTICE([User specified --with-freetype-lib but not --with-freetype-include])
          AC_MSG_ERROR([Need both freetype lib and include paths. Consider using --with-freetype instead.])
        else
          AC_MSG_NOTICE([User specified --with-freetype-include but not --with-freetype-lib])
          AC_MSG_ERROR([Need both freetype lib and include paths. Consider using --with-freetype instead.])
        fi
      fi
    else
      # User did not specify settings, but we need freetype. Try to locate it.

      if test "x$BUNDLE_FREETYPE" = x; then
        # If not specified, default is to bundle freetype only on windows
        if test "x$OPENJDK_TARGET_OS" = xwindows; then
          BUNDLE_FREETYPE=yes
        else
          BUNDLE_FREETYPE=no
        fi
      fi

      # If we have a sysroot, assume that's where we are supposed to look and skip pkg-config.
      if test "x$SYSROOT" = x; then
        if test "x$FOUND_FREETYPE" != xyes; then
          # Check modules using pkg-config, but only if we have it (ugly output results otherwise)
          if test "x$PKG_CONFIG" != x; then
            PKG_CHECK_MODULES(FREETYPE, freetype2, [FOUND_FREETYPE=yes], [FOUND_FREETYPE=no])
            if test "x$FOUND_FREETYPE" = xyes; then
              # On solaris, pkg_check adds -lz to freetype libs, which isn't necessary for us.
              FREETYPE_LIBS=`$ECHO $FREETYPE_LIBS | $SED 's/-lz//g'`
              # 64-bit libs for Solaris x86 are installed in the amd64 subdirectory, change lib to lib/amd64
              if test "x$OPENJDK_TARGET_OS" = xsolaris && test "x$OPENJDK_TARGET_CPU" = xx86_64; then
                FREETYPE_LIBS=`$ECHO $FREETYPE_LIBS | $SED 's?/lib?/lib/amd64?g'`
              fi
              # PKG_CHECK_MODULES will set FREETYPE_CFLAGS and _LIBS, but we don't get a lib path for bundling.
              if test "x$BUNDLE_FREETYPE" = xyes; then
                AC_MSG_NOTICE([Found freetype using pkg-config, but ignoring since we can not bundle that])
                FOUND_FREETYPE=no
              else
                AC_MSG_CHECKING([for freetype])
                AC_MSG_RESULT([yes (using pkg-config)])
              fi
            fi
          fi
        fi
      fi

      if test "x$FOUND_FREETYPE" != xyes; then
        # Check in well-known locations
        if test "x$OPENJDK_TARGET_OS" = xwindows; then
          FREETYPE_BASE_DIR="$PROGRAMFILES/GnuWin32"
          BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(FREETYPE_BASE_DIR)
          LIB_CHECK_POTENTIAL_FREETYPE([$FREETYPE_BASE_DIR/include], [$FREETYPE_BASE_DIR/lib], [well-known location])
          if test "x$FOUND_FREETYPE" != xyes; then
            FREETYPE_BASE_DIR="$ProgramW6432/GnuWin32"
            BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(FREETYPE_BASE_DIR)
            LIB_CHECK_POTENTIAL_FREETYPE([$FREETYPE_BASE_DIR/include], [$FREETYPE_BASE_DIR/lib], [well-known location])
          fi
        else
          FREETYPE_BASE_DIR="$SYSROOT/usr"
          LIB_CHECK_POTENTIAL_FREETYPE([$FREETYPE_BASE_DIR/include], [$FREETYPE_BASE_DIR/lib], [well-known location])

          if test "x$FOUND_FREETYPE" != xyes; then
            FREETYPE_BASE_DIR="$SYSROOT/usr/X11"
            LIB_CHECK_POTENTIAL_FREETYPE([$FREETYPE_BASE_DIR/include], [$FREETYPE_BASE_DIR/lib], [well-known location])
          fi

          if test "x$FOUND_FREETYPE" != xyes; then
            FREETYPE_BASE_DIR="$SYSROOT/usr/sfw"
            LIB_CHECK_POTENTIAL_FREETYPE([$FREETYPE_BASE_DIR/include], [$FREETYPE_BASE_DIR/lib], [well-known location])
          fi

          if test "x$FOUND_FREETYPE" != xyes; then
            FREETYPE_BASE_DIR="$SYSROOT/usr"
            if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
              LIB_CHECK_POTENTIAL_FREETYPE([$FREETYPE_BASE_DIR/include], [$FREETYPE_BASE_DIR/lib/x86_64-linux-gnu], [well-known location])
            else
              LIB_CHECK_POTENTIAL_FREETYPE([$FREETYPE_BASE_DIR/include], [$FREETYPE_BASE_DIR/lib/i386-linux-gnu], [well-known location])
              if test "x$FOUND_FREETYPE" != xyes; then
                LIB_CHECK_POTENTIAL_FREETYPE([$FREETYPE_BASE_DIR/include], [$FREETYPE_BASE_DIR/lib32], [well-known location])
              fi
            fi
          fi
        fi
      fi # end check in well-known locations

      if test "x$FOUND_FREETYPE" != xyes; then
        HELP_MSG_MISSING_DEPENDENCY([freetype])
        AC_MSG_ERROR([Could not find freetype! $HELP_MSG ])
      fi
    fi # end user specified settings

    # Set FREETYPE_CFLAGS, _LIBS and _LIB_PATH from include and lib dir.
    if test "x$FREETYPE_CFLAGS" = x; then
      BASIC_FIXUP_PATH(FREETYPE_INCLUDE_PATH)
      if test -d $FREETYPE_INCLUDE_PATH/freetype2/freetype; then
        FREETYPE_CFLAGS="-I$FREETYPE_INCLUDE_PATH/freetype2 -I$FREETYPE_INCLUDE_PATH"
      else
        FREETYPE_CFLAGS="-I$FREETYPE_INCLUDE_PATH"
      fi
    fi

    if test "x$FREETYPE_LIBS" = x; then
      BASIC_FIXUP_PATH(FREETYPE_LIB_PATH)
      if test "x$OPENJDK_TARGET_OS" = xwindows; then
        FREETYPE_LIBS="$FREETYPE_LIB_PATH/$FREETYPE_BASE_NAME.lib"
      else
        FREETYPE_LIBS="-L$FREETYPE_LIB_PATH -l$FREETYPE_BASE_NAME"
      fi
    fi

    # Try to compile it
    AC_MSG_CHECKING([if we can compile and link with freetype])
    AC_LANG_PUSH(C++)
    PREV_CXXCFLAGS="$CXXFLAGS"
    PREV_LIBS="$LIBS"
    PREV_CXX="$CXX"
    CXXFLAGS="$CXXFLAGS $FREETYPE_CFLAGS"
    LIBS="$LIBS $FREETYPE_LIBS"
    CXX="$FIXPATH $CXX"
    AC_LINK_IFELSE([AC_LANG_SOURCE([[
          #include<ft2build.h>
          #include FT_FREETYPE_H
          int main () {
            FT_Init_FreeType(NULL);
            return 0;
          }
        ]])],
        [
          AC_MSG_RESULT([yes])
        ],
        [
          AC_MSG_RESULT([no])
          AC_MSG_NOTICE([Could not compile and link with freetype. This might be a 32/64-bit mismatch.])
          AC_MSG_NOTICE([Using FREETYPE_CFLAGS=$FREETYPE_CFLAGS and FREETYPE_LIBS=$FREETYPE_LIBS])

          HELP_MSG_MISSING_DEPENDENCY([freetype])

          AC_MSG_ERROR([Can not continue without freetype. $HELP_MSG])
        ]
    )
    CXXCFLAGS="$PREV_CXXFLAGS"
    LIBS="$PREV_LIBS"
    CXX="$PREV_CXX"
    AC_LANG_POP(C++)

    AC_MSG_CHECKING([if we should bundle freetype])
    if test "x$BUNDLE_FREETYPE" = xyes; then
      FREETYPE_BUNDLE_LIB_PATH="$FREETYPE_LIB_PATH"
    fi
    AC_MSG_RESULT([$BUNDLE_FREETYPE])

  fi # end freetype needed

  AC_SUBST(FREETYPE_BUNDLE_LIB_PATH)
  AC_SUBST(FREETYPE_CFLAGS)
  AC_SUBST(FREETYPE_LIBS)
])
