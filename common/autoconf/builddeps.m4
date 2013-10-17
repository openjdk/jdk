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

AC_DEFUN_ONCE([BDEPS_SCAN_FOR_BUILDDEPS],
[
  define(LIST_OF_BUILD_DEPENDENCIES,)
  if test "x$with_builddeps_server" != x || test "x$with_builddeps_conf" != x; then
    if test "x$with_builddeps_conf" != x; then
      AC_MSG_CHECKING([for supplied builddeps configuration file])
      builddepsfile=$with_builddeps_conf
      if test -s $builddepsfile; then
        . $builddepsfile
        AC_MSG_RESULT([loaded!])
      else
        AC_MSG_ERROR([The given builddeps conf file $with_builddeps_conf could not be loaded!])
      fi
    else
      AC_MSG_CHECKING([for builddeps.conf files in sources...])
      builddepsfile=`mktemp`
      touch $builddepsfile
      # Put all found confs into a single file.
      find ${SRC_ROOT} -name builddeps.conf -exec cat \{\} \; >> $builddepsfile
      # Source the file to acquire the variables
      if test -s $builddepsfile; then
        . $builddepsfile
        AC_MSG_RESULT([found at least one!])
      else
        AC_MSG_ERROR([Could not find any builddeps.conf at all!])
      fi
    fi
    # Create build and target names that use _ instead of "-" and ".".
    # This is necessary to use them in variable names.
    build_var=`echo ${OPENJDK_BUILD_AUTOCONF_NAME} | tr '-' '_' | tr '.' '_'`
    target_var=`echo ${OPENJDK_TARGET_AUTOCONF_NAME} | tr '-' '_' | tr '.' '_'`
    # Extract rewrite information for build and target
    eval rewritten_build=\${REWRITE_${build_var}}
    if test "x$rewritten_build" = x; then
      rewritten_build=${OPENJDK_BUILD_AUTOCONF_NAME}
      echo Build stays the same $rewritten_build
    else
      echo Rewriting build for builddeps into $rewritten_build
    fi
    eval rewritten_target=\${REWRITE_${target_var}}
    if test "x$rewritten_target" = x; then
      rewritten_target=${OPENJDK_TARGET_AUTOCONF_NAME}
      echo Target stays the same $rewritten_target
    else
      echo Rewriting target for builddeps into $rewritten_target
    fi
    rewritten_build_var=`echo ${rewritten_build} | tr '-' '_' | tr '.' '_'`
    rewritten_target_var=`echo ${rewritten_target} | tr '-' '_' | tr '.' '_'`
  fi
  AC_CHECK_PROGS(BDEPS_UNZIP, [7z unzip])
  if test "x$BDEPS_UNZIP" = x7z; then
    BDEPS_UNZIP="7z x"
  fi

  AC_CHECK_PROGS(BDEPS_FTP, [wget lftp ftp])
])

AC_DEFUN([BDEPS_FTPGET],
[
  # $1 is the ftp://abuilddeps.server.com/libs/cups.zip
  # $2 is the local file name for the downloaded file.
  VALID_TOOL=no
  if test "x$BDEPS_FTP" = xwget; then
    VALID_TOOL=yes
    wget -O $2 $1
  fi
  if test "x$BDEPS_FTP" = xlftp; then
    VALID_TOOL=yes
    lftp -c "get $1 -o $2"
  fi
  if test "x$BDEPS_FTP" = xftp; then
    VALID_TOOL=yes
    FTPSERVER=`echo $1 | cut -f 3 -d '/'`
    FTPPATH=`echo $1 | cut -f 4- -d '/'`
    FTPUSERPWD=${FTPSERVER%%@*}
    if test "x$FTPSERVER" != "x$FTPUSERPWD"; then
      FTPUSER=${userpwd%%:*}
      FTPPWD=${userpwd#*@}
      FTPSERVER=${FTPSERVER#*@}
    else
      FTPUSER=ftp
      FTPPWD=ftp
    fi
    # the "pass" command does not work on some
    # ftp clients (read ftp.exe) but if it works,
    # passive mode is better!
    ( \
        echo "user $FTPUSER $FTPPWD"        ; \
        echo "pass"                         ; \
        echo "bin"                          ; \
        echo "get $FTPPATH $2"              ; \
    ) | ftp -in $FTPSERVER
  fi
  if test "x$VALID_TOOL" != xyes; then
    AC_MSG_ERROR([I do not know how to use the tool: $BDEPS_FTP])
  fi
])

AC_DEFUN([BDEPS_CHECK_MODULE],
[
  define([LIST_OF_BUILD_DEPENDENCIES],LIST_OF_BUILD_DEPENDENCIES[$2=$3'\n'])
  if test "x$with_builddeps_server" != x || test "x$with_builddeps_conf" != x; then
    # Source the builddeps file again, to make sure it uses the latest variables!
    . $builddepsfile
    # Look for a target and build machine specific resource!
    eval resource=\${builddep_$2_BUILD_${rewritten_build_var}_TARGET_${rewritten_target_var}}
    if test "x$resource" = x; then
      # Ok, lets instead look for a target specific resource
      eval resource=\${builddep_$2_TARGET_${rewritten_target_var}}
    fi
    if test "x$resource" = x; then
      # Ok, lets instead look for a build specific resource
      eval resource=\${builddep_$2_BUILD_${rewritten_build_var}}
    fi
    if test "x$resource" = x; then
      # Ok, lets instead look for a generic resource
      # (The $2 comes from M4 and not the shell, thus no need for eval here.)
      resource=${builddep_$2}
    fi
    if test "x$resource" != x; then
      AC_MSG_NOTICE([Using builddeps $resource for $2])
      # If the resource in the builddeps.conf file is an existing directory,
      # for example /java/linux/cups
      if test -d ${resource}; then
        depdir=${resource}
      else
        BDEPS_FETCH($2, $resource, $with_builddeps_server, $with_builddeps_dir, depdir)
      fi
      # Source the builddeps file again, because in the previous command, the depdir
      # was updated to point at the current build dependency install directory.
      . $builddepsfile
      # Now extract variables from the builddeps.conf files.
      theroot=${builddep_$2_ROOT}
      thecflags=${builddep_$2_CFLAGS}
      thelibs=${builddep_$2_LIBS}
      if test "x$depdir" = x; then
        AC_MSG_ERROR([Could not download build dependency $2])
      fi
      $1=$depdir
      if test "x$theroot" != x; then
        $1="$theroot"
      fi
      if test "x$thecflags" != x; then
        $1_CFLAGS="$thecflags"
      fi
      if test "x$thelibs" != x; then
        $1_LIBS="$thelibs"
      fi
      m4_default([$4], [:])
      m4_ifvaln([$5], [else $5])
    fi
    m4_ifvaln([$5], [else $5])
  fi
])

AC_DEFUN([BDEPS_FETCH],
[
  # $1 is for example mymodule
  # $2 is for example libs/general/libmymod_1_2_3.zip
  # $3 is for example ftp://mybuilddeps.myserver.com/builddeps
  # $4 is for example /localhome/builddeps
  # $5 is the name of the variable into which we store the depdir, eg MYMOD
  # Will download ftp://mybuilddeps.myserver.com/builddeps/libs/general/libmymod_1_2_3.zip and
  # unzip into the directory: /localhome/builddeps/libmymod_1_2_3
  filename=`basename $2`
  filebase=`echo $filename | sed 's/\.[[^\.]]*$//'`
  filebase=${filename%%.*}
  extension=${filename#*.}
  installdir=$4/$filebase
  if test ! -f $installdir/$filename.unpacked; then
    AC_MSG_NOTICE([Downloading build dependency $1 from $3/$2 and installing into $installdir])
    if test ! -d $installdir; then
      mkdir -p $installdir
    fi
    if test ! -d $installdir; then
      AC_MSG_ERROR([Could not create directory $installdir])
    fi
    tmpfile=`mktemp $installdir/$1.XXXXXXXXX`
    touch $tmpfile
    if test ! -f $tmpfile; then
      AC_MSG_ERROR([Could not create files in directory $installdir])
    fi
    BDEPS_FTPGET([$3/$2] , [$tmpfile])
    mv $tmpfile $installdir/$filename
    if test ! -s $installdir/$filename; then
      AC_MSG_ERROR([Could not download $3/$2])
    fi
    case "$extension" in
      zip)  echo "Unzipping $installdir/$filename..."
        (cd $installdir ; rm -f $installdir/$filename.unpacked ; $BDEPS_UNZIP $installdir/$filename > /dev/null && touch $installdir/$filename.unpacked)
        ;;
      tar.gz) echo "Untaring $installdir/$filename..."
        (cd $installdir ; rm -f $installdir/$filename.unpacked ; tar xzf $installdir/$filename && touch $installdir/$filename.unpacked)
        ;;
      tgz) echo "Untaring $installdir/$filename..."
        (cd $installdir ; rm -f $installdir/$filename.unpacked ; tar xzf $installdir/$filename && touch $installdir/$filename.unpacked)
        ;;
      *) AC_MSG_ERROR([Cannot handle build depency archive with extension $extension])
        ;;
    esac
  fi
  if test -f $installdir/$filename.unpacked; then
    $5=$installdir
  fi
])

AC_DEFUN_ONCE([BDEPS_CONFIGURE_BUILDDEPS],
[
  AC_ARG_WITH(builddeps-conf, [AS_HELP_STRING([--with-builddeps-conf],
      [use this configuration file for the builddeps])])

  AC_ARG_WITH(builddeps-server, [AS_HELP_STRING([--with-builddeps-server],
      [download and use build dependencies from this server url])])

  AC_ARG_WITH(builddeps-dir, [AS_HELP_STRING([--with-builddeps-dir],
      [store downloaded build dependencies here @<:@/localhome/builddeps@:>@])],
      [],
      [with_builddeps_dir=/localhome/builddeps])

  AC_ARG_WITH(builddeps-group, [AS_HELP_STRING([--with-builddeps-group],
      [chgrp the downloaded build dependencies to this group])])
])
