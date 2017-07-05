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

AC_DEFUN_ONCE([SRCDIRS_SETUP_TOPDIRS],
[
  # Where are the sources. Any of these can be overridden
  # using --with-override-corba and the likes.
  LANGTOOLS_TOPDIR="$SRC_ROOT/langtools"
  CORBA_TOPDIR="$SRC_ROOT/corba"
  JAXP_TOPDIR="$SRC_ROOT/jaxp"
  JAXWS_TOPDIR="$SRC_ROOT/jaxws"
  HOTSPOT_TOPDIR="$SRC_ROOT/hotspot"
  NASHORN_TOPDIR="$SRC_ROOT/nashorn"
  JDK_TOPDIR="$SRC_ROOT/jdk"
  AC_SUBST(LANGTOOLS_TOPDIR)
  AC_SUBST(CORBA_TOPDIR)
  AC_SUBST(JAXP_TOPDIR)
  AC_SUBST(JAXWS_TOPDIR)
  AC_SUBST(HOTSPOT_TOPDIR)
  AC_SUBST(NASHORN_TOPDIR)
  AC_SUBST(JDK_TOPDIR)
])


AC_DEFUN_ONCE([SRCDIRS_SETUP_ALTERNATIVE_TOPDIRS],
[

  ###############################################################################
  #
  # Pickup additional source for a component from outside of the source root
  # or override source for a component.
  #
  AC_ARG_WITH(add-source-root, [AS_HELP_STRING([--with-add-source-root],
      [for each and every source directory, look in this additional source root for
      the same directory; if it exists and have files in it, include it in the build])])

  AC_ARG_WITH(override-source-root, [AS_HELP_STRING([--with-override-source-root],
      [for each and every source directory, look in this override source root for
      the same directory; if it exists, use that directory instead and
      ignore the directory in the original source root])])

  AC_ARG_WITH(adds-and-overrides, [AS_HELP_STRING([--with-adds-and-overrides],
      [use the subdirs 'adds' and 'overrides' in the specified directory as
      add-source-root and override-source-root])])

  if test "x$with_adds_and_overrides" != x; then
    with_add_source_root="$with_adds_and_overrides/adds"
    with_override_source_root="$with_adds_and_overrides/overrides"
  fi

  if test "x$with_add_source_root" != x; then
    if ! test -d $with_add_source_root; then
      AC_MSG_ERROR([Trying to use a non-existant add-source-root $with_add_source_root])
    fi
    CURDIR="$PWD"
    cd "$with_add_source_root"
    ADD_SRC_ROOT="`pwd`"
    cd "$CURDIR"
    # Verify that the addon source root does not have any root makefiles.
    # If it does, then it is usually an error, prevent this.
    if test -f $with_add_source_root/langtools/makefiles/Makefile || \
      test -f $with_add_source_root/langtools/make/Makefile; then
      AC_MSG_ERROR([Your add source root seems to contain a full langtools repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/corba/makefiles/Makefile || \
      test -f $with_add_source_root/corba/make/Makefile; then
      AC_MSG_ERROR([Your add source root seems to contain a full corba repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/jaxp/makefiles/Makefile || \
      test -f $with_add_source_root/jaxp/make/Makefile; then
      AC_MSG_ERROR([Your add source root seems to contain a full jaxp repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/jaxws/makefiles/Makefile || \
      test -f $with_add_source_root/jaxws/make/Makefile; then
      AC_MSG_ERROR([Your add source root seems to contain a full jaxws repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/hotspot/makefiles/Makefile || \
      test -f $with_add_source_root/hotspot/make/Makefile; then
      AC_MSG_ERROR([Your add source root seems to contain a full hotspot repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/nashorn/makefiles/Makefile || \
      test -f $with_add_source_root/nashorn/make/Makefile; then
      AC_MSG_ERROR([Your add source root seems to contain a full nashorn repo! An add source root should only contain additional sources.])
    fi
    if test -f $with_add_source_root/jdk/makefiles/Makefile || \
      test -f $with_add_source_root/jdk/make/Makefile; then
      AC_MSG_ERROR([Your add source root seems to contain a full JDK repo! An add source root should only contain additional sources.])
    fi
  fi
  AC_SUBST(ADD_SRC_ROOT)

  if test "x$with_override_source_root" != x; then
    if ! test -d $with_override_source_root; then
      AC_MSG_ERROR([Trying to use a non-existant override-source-root $with_override_source_root])
    fi
    CURDIR="$PWD"
    cd "$with_override_source_root"
    OVERRIDE_SRC_ROOT="`pwd`"
    cd "$CURDIR"
    if test -f $with_override_source_root/langtools/makefiles/Makefile || \
      test -f $with_override_source_root/langtools/make/Makefile; then
      AC_MSG_ERROR([Your override source root seems to contain a full langtools repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/corba/makefiles/Makefile || \
      test -f $with_override_source_root/corba/make/Makefile; then
      AC_MSG_ERROR([Your override source root seems to contain a full corba repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/jaxp/makefiles/Makefile || \
      test -f $with_override_source_root/jaxp/make/Makefile; then
      AC_MSG_ERROR([Your override source root seems to contain a full jaxp repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/jaxws/makefiles/Makefile || \
      test -f $with_override_source_root/jaxws/make/Makefile; then
      AC_MSG_ERROR([Your override source root seems to contain a full jaxws repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/hotspot/makefiles/Makefile || \
      test -f $with_override_source_root/hotspot/make/Makefile; then
      AC_MSG_ERROR([Your override source root seems to contain a full hotspot repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/nashorn/makefiles/Makefile || \
      test -f $with_override_source_root/nashorn/make/Makefile; then
      AC_MSG_ERROR([Your override source root seems to contain a full nashorn repo! An override source root should only contain sources that override.])
    fi
    if test -f $with_override_source_root/jdk/makefiles/Makefile || \
      test -f $with_override_source_root/jdk/make/Makefile; then
      AC_MSG_ERROR([Your override source root seems to contain a full JDK repo! An override source root should only contain sources that override.])
    fi
  fi
  AC_SUBST(OVERRIDE_SRC_ROOT)

  ###############################################################################
  #
  # Override a repo completely, this is used for example when you have 3 small
  # development sandboxes of the langtools sources and want to avoid having 3 full
  # OpenJDK sources checked out on disk.
  #
  # Assuming that the 3 langtools sandboxes are located here:
  # /home/fredrik/sandbox1/langtools
  # /home/fredrik/sandbox2/langtools
  # /home/fredrik/sandbox3/langtools
  #
  # From the source root you create build subdirs manually:
  #     mkdir -p build1 build2 build3
  # in each build directory run:
  #     (cd build1 && ../configure --with-override-langtools=/home/fredrik/sandbox1 && make)
  #     (cd build2 && ../configure --with-override-langtools=/home/fredrik/sandbox2 && make)
  #     (cd build3 && ../configure --with-override-langtools=/home/fredrik/sandbox3 && make)
  #

  AC_ARG_WITH(override-langtools, [AS_HELP_STRING([--with-override-langtools],
      [use this langtools dir for the build])])

  AC_ARG_WITH(override-corba, [AS_HELP_STRING([--with-override-corba],
      [use this corba dir for the build])])

  AC_ARG_WITH(override-jaxp, [AS_HELP_STRING([--with-override-jaxp],
      [use this jaxp dir for the build])])

  AC_ARG_WITH(override-jaxws, [AS_HELP_STRING([--with-override-jaxws],
      [use this jaxws dir for the build])])

  AC_ARG_WITH(override-hotspot, [AS_HELP_STRING([--with-override-hotspot],
      [use this hotspot dir for the build])])

  AC_ARG_WITH(override-nashorn, [AS_HELP_STRING([--with-override-nashorn],
      [use this nashorn dir for the build])])

  AC_ARG_WITH(override-jdk, [AS_HELP_STRING([--with-override-jdk],
      [use this jdk dir for the build])])

  if test "x$with_override_langtools" != x; then
    CURDIR="$PWD"
    cd "$with_override_langtools"
    LANGTOOLS_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $LANGTOOLS_TOPDIR/makefiles/Makefile; then
      AC_MSG_ERROR([You have to override langtools with a full langtools repo!])
    fi
    AC_MSG_CHECKING([if langtools should be overridden])
    AC_MSG_RESULT([yes with $LANGTOOLS_TOPDIR])
  fi
  if test "x$with_override_corba" != x; then
    CURDIR="$PWD"
    cd "$with_override_corba"
    CORBA_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $CORBA_TOPDIR/makefiles/Makefile; then
      AC_MSG_ERROR([You have to override corba with a full corba repo!])
    fi
    AC_MSG_CHECKING([if corba should be overridden])
    AC_MSG_RESULT([yes with $CORBA_TOPDIR])
  fi
  if test "x$with_override_jaxp" != x; then
    CURDIR="$PWD"
    cd "$with_override_jaxp"
    JAXP_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $JAXP_TOPDIR/makefiles/Makefile; then
      AC_MSG_ERROR([You have to override jaxp with a full jaxp repo!])
    fi
    AC_MSG_CHECKING([if jaxp should be overridden])
    AC_MSG_RESULT([yes with $JAXP_TOPDIR])
  fi
  if test "x$with_override_jaxws" != x; then
    CURDIR="$PWD"
    cd "$with_override_jaxws"
    JAXWS_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $JAXWS_TOPDIR/makefiles/Makefile; then
      AC_MSG_ERROR([You have to override jaxws with a full jaxws repo!])
    fi
    AC_MSG_CHECKING([if jaxws should be overridden])
    AC_MSG_RESULT([yes with $JAXWS_TOPDIR])
  fi
  if test "x$with_override_hotspot" != x; then
    CURDIR="$PWD"
    cd "$with_override_hotspot"
    HOTSPOT_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $HOTSPOT_TOPDIR/make/Makefile && \
        ! test -f $HOTSPOT_TOPDIR/makefiles/Makefile; then
      AC_MSG_ERROR([You have to override hotspot with a full hotspot repo!])
    fi
    AC_MSG_CHECKING([if hotspot should be overridden])
    AC_MSG_RESULT([yes with $HOTSPOT_TOPDIR])
  fi
  if test "x$with_override_nashorn" != x; then
    CURDIR="$PWD"
    cd "$with_override_nashorn"
    NASHORN_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $NASHORN_TOPDIR/makefiles/Makefile; then
      AC_MSG_ERROR([You have to override nashorn with a full nashorn repo!])
    fi
    AC_MSG_CHECKING([if nashorn should be overridden])
    AC_MSG_RESULT([yes with $NASHORN_TOPDIR])
  fi
  if test "x$with_override_jdk" != x; then
    CURDIR="$PWD"
    cd "$with_override_jdk"
    JDK_TOPDIR="`pwd`"
    cd "$CURDIR"
    if ! test -f $JDK_TOPDIR/makefiles/Makefile; then
      AC_MSG_ERROR([You have to override JDK with a full JDK repo!])
    fi
    AC_MSG_CHECKING([if JDK should be overridden])
    AC_MSG_RESULT([yes with $JDK_TOPDIR])
  fi
])

AC_DEFUN_ONCE([SRCDIRS_SETUP_OUTPUT_DIRS],
[
  BUILD_OUTPUT="$OUTPUT_ROOT"
  AC_SUBST(BUILD_OUTPUT)

  HOTSPOT_DIST="$OUTPUT_ROOT/hotspot/dist"
  BUILD_HOTSPOT=true
  AC_SUBST(HOTSPOT_DIST)
  AC_SUBST(BUILD_HOTSPOT)
  AC_ARG_WITH(import-hotspot, [AS_HELP_STRING([--with-import-hotspot],
  [import hotspot binaries from this jdk image or hotspot build dist dir instead of building from source])])
  if test "x$with_import_hotspot" != x; then
    CURDIR="$PWD"
    cd "$with_import_hotspot"
    HOTSPOT_DIST="`pwd`"
    cd "$CURDIR"
    if ! (test -d $HOTSPOT_DIST/lib && test -d $HOTSPOT_DIST/jre/lib); then
      AC_MSG_ERROR([You have to import hotspot from a full jdk image or hotspot build dist dir!])
    fi
    AC_MSG_CHECKING([if hotspot should be imported])
    AC_MSG_RESULT([yes from $HOTSPOT_DIST])
    BUILD_HOTSPOT=false
  fi

  JDK_OUTPUTDIR="$OUTPUT_ROOT/jdk"
])
