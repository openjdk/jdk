#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

###############################################################################
#
# Setup version numbers
#

# Verify that a given string represents a valid version number, and assign it
# to a variable.

# Argument 1: the variable to assign to
# Argument 2: the value given by the user
AC_DEFUN([JDKVER_CHECK_AND_SET_NUMBER],
[
  # Additional [] needed to keep m4 from mangling shell constructs.
  if [ ! [[ "$2" =~ ^0*([1-9][0-9]*)|(0)$ ]] ] ; then
    AC_MSG_ERROR(["$2" is not a valid numerical value for $1])
  fi
  # Extract the version number without leading zeros.
  cleaned_value=${BASH_REMATCH[[1]]}
  if test "x$cleaned_value" = x; then
    # Special case for zero
    cleaned_value=${BASH_REMATCH[[2]]}
  fi

  if test $cleaned_value -gt 255; then
    AC_MSG_ERROR([$1 is given as $2. This is greater than 255 which is not allowed.])
  fi
  if test "x$cleaned_value" != "x$2"; then
    AC_MSG_WARN([Value for $1 has been sanitized from '$2' to '$cleaned_value'])
  fi
  $1=$cleaned_value
])

AC_DEFUN_ONCE([JDKVER_SETUP_JDK_VERSION_NUMBERS],
[
  # Warn user that old version arguments are deprecated.
  BASIC_DEPRECATED_ARG_WITH([milestone])
  BASIC_DEPRECATED_ARG_WITH([update-version])
  BASIC_DEPRECATED_ARG_WITH([user-release-suffix])
  BASIC_DEPRECATED_ARG_WITH([build-number])

  # Source the version numbers file
  . $AUTOCONF_DIR/version-numbers

  # Some non-version number information is set in that file
  AC_SUBST(LAUNCHER_NAME)
  AC_SUBST(PRODUCT_NAME)
  AC_SUBST(PRODUCT_SUFFIX)
  AC_SUBST(JDK_RC_PLATFORM_NAME)
  AC_SUBST(COMPANY_NAME)
  AC_SUBST(MACOSX_BUNDLE_NAME_BASE)
  AC_SUBST(MACOSX_BUNDLE_ID_BASE)

  # Override version from arguments

  # If --with-version-string is set, process it first. It is possible to
  # override parts with more specific flags, since these are processed later.
  AC_ARG_WITH(version-string, [AS_HELP_STRING([--with-version-string],
      [Set version string @<:@calculated@:>@])])
  if test "x$with_version_string" = xyes; then
    AC_MSG_ERROR([--with-version-string must have a value])
  elif test "x$with_version_string" != x; then
    # Additional [] needed to keep m4 from mangling shell constructs.
    if [ [[ $with_version_string =~ ^([0-9]+)(\.([0-9]+))?(\.([0-9]+))?(\.([0-9]+))?(-([a-zA-Z]+))?((\+)([0-9]+)?(-([-a-zA-Z0-9.]+))?)?$ ]] ]; then
      VERSION_MAJOR=${BASH_REMATCH[[1]]}
      VERSION_MINOR=${BASH_REMATCH[[3]]}
      VERSION_SECURITY=${BASH_REMATCH[[5]]}
      VERSION_PATCH=${BASH_REMATCH[[7]]}
      VERSION_PRE=${BASH_REMATCH[[9]]}
      version_plus_separator=${BASH_REMATCH[[11]]}
      VERSION_BUILD=${BASH_REMATCH[[12]]}
      VERSION_OPT=${BASH_REMATCH[[14]]}
      # Unspecified numerical fields are interpreted as 0.
      if test "x$VERSION_MINOR" = x; then
        VERSION_MINOR=0
      fi
      if test "x$VERSION_SECURITY" = x; then
        VERSION_SECURITY=0
      fi
      if test "x$VERSION_PATCH" = x; then
        VERSION_PATCH=0
      fi
      if test "x$version_plus_separator" != x \
          && test "x$VERSION_BUILD$VERSION_OPT" = x; then
        AC_MSG_ERROR([Version string contains + but both 'BUILD' and 'OPT' are missing])
      fi
      # Stop the version part process from setting default values.
      # We still allow them to explicitely override though.
      NO_DEFAULT_VERSION_PARTS=true
    else
      AC_MSG_ERROR([--with-version-string fails to parse as a valid version string: $with_version_string])
    fi
  fi

  AC_ARG_WITH(version-pre, [AS_HELP_STRING([--with-version-pre],
      [Set the base part of the version 'PRE' field (pre-release identifier) @<:@'internal'@:>@])],
      [with_version_pre_present=true], [with_version_pre_present=false])

  if test "x$with_version_pre_present" = xtrue; then
    if test "x$with_version_pre" = xyes; then
      AC_MSG_ERROR([--with-version-pre must have a value])
    elif test "x$with_version_pre" = xno; then
      # Interpret --without-* as empty string instead of the literal "no"
      VERSION_PRE=
    else
      # Only [a-zA-Z] is allowed in the VERSION_PRE. Outer [ ] to quote m4.
      [ VERSION_PRE=`$ECHO "$with_version_pre" | $TR -c -d '[a-z][A-Z]'` ]
      if test "x$VERSION_PRE" != "x$with_version_pre"; then
        AC_MSG_WARN([--with-version-pre value has been sanitized from '$with_version_pre' to '$VERSION_PRE'])
      fi
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is to use "internal" as pre
      VERSION_PRE="internal"
    fi
  fi

  AC_ARG_WITH(version-opt, [AS_HELP_STRING([--with-version-opt],
      [Set version 'OPT' field (build metadata) @<:@<timestamp>.<user>.<dirname>@:>@])],
      [with_version_opt_present=true], [with_version_opt_present=false])

  if test "x$with_version_opt_present" = xtrue; then
    if test "x$with_version_opt" = xyes; then
      AC_MSG_ERROR([--with-version-opt must have a value])
    elif test "x$with_version_opt" = xno; then
      # Interpret --without-* as empty string instead of the literal "no"
      VERSION_OPT=
    else
      # Only [-.a-zA-Z0-9] is allowed in the VERSION_OPT. Outer [ ] to quote m4.
      [ VERSION_OPT=`$ECHO "$with_version_opt" | $TR -c -d '[a-z][A-Z][0-9].-'` ]
      if test "x$VERSION_OPT" != "x$with_version_opt"; then
        AC_MSG_WARN([--with-version-opt value has been sanitized from '$with_version_opt' to '$VERSION_OPT'])
      fi
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is to calculate a string like this <timestamp>.<username>.<base dir name>
      timestamp=`$DATE '+%Y-%m-%d-%H%M%S'`
      # Outer [ ] to quote m4.
      [ basedirname=`$BASENAME "$TOPDIR" | $TR -d -c '[a-z][A-Z][0-9].-'` ]
      VERSION_OPT="$timestamp.$USERNAME.$basedirname"
    fi
  fi

  AC_ARG_WITH(version-build, [AS_HELP_STRING([--with-version-build],
      [Set version 'BUILD' field (build number) @<:@not specified@:>@])],
      [with_version_build_present=true], [with_version_build_present=false])

  if test "x$with_version_build_present" = xtrue; then
    if test "x$with_version_build" = xyes; then
      AC_MSG_ERROR([--with-version-build must have a value])
    elif test "x$with_version_build" = xno; then
      # Interpret --without-* as empty string instead of the literal "no"
      VERSION_BUILD=
    elif test "x$with_version_build" = x; then
      VERSION_BUILD=
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_BUILD, $with_version_build)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is to not have a build number.
      VERSION_BUILD=""
      # FIXME: Until all code can cope with an empty VERSION_BUILD, set it to 0.
      VERSION_BUILD=0
    fi
  fi

  AC_ARG_WITH(version-major, [AS_HELP_STRING([--with-version-major],
      [Set version 'MAJOR' field (first number) @<:@current source value@:>@])],
      [with_version_major_present=true], [with_version_major_present=false])

  if test "x$with_version_major_present" = xtrue; then
    if test "x$with_version_major" = xyes; then
      AC_MSG_ERROR([--with-version-major must have a value])
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_MAJOR, $with_version_major)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is to get value from version-numbers
      VERSION_MAJOR="$DEFAULT_VERSION_MAJOR"
    fi
  fi

  AC_ARG_WITH(version-minor, [AS_HELP_STRING([--with-version-minor],
      [Set version 'MINOR' field (second number) @<:@current source value@:>@])],
      [with_version_minor_present=true], [with_version_minor_present=false])

  if test "x$with_version_minor_present" = xtrue; then
    if test "x$with_version_minor" = xyes; then
      AC_MSG_ERROR([--with-version-minor must have a value])
    elif test "x$with_version_minor" = xno; then
      # Interpret --without-* as empty string (i.e. 0) instead of the literal "no"
      VERSION_MINOR=0
    elif test "x$with_version_minor" = x; then
      VERSION_MINOR=0
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_MINOR, $with_version_minor)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is 0, if unspecified
      VERSION_MINOR=0
    fi
  fi

  AC_ARG_WITH(version-security, [AS_HELP_STRING([--with-version-security],
      [Set version 'SECURITY' field (third number) @<:@current source value@:>@])],
      [with_version_security_present=true], [with_version_security_present=false])

  if test "x$with_version_security_present" = xtrue; then
    if test "x$with_version_security" = xyes; then
      AC_MSG_ERROR([--with-version-security must have a value])
    elif test "x$with_version_security" = xno; then
      # Interpret --without-* as empty string (i.e. 0) instead of the literal "no"
      VERSION_SECURITY=0
    elif test "x$with_version_security" = x; then
      VERSION_SECURITY=0
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_SECURITY, $with_version_security)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is 0, if unspecified
      VERSION_SECURITY=0
    fi
  fi

  AC_ARG_WITH(version-patch, [AS_HELP_STRING([--with-version-patch],
      [Set version 'PATCH' field (fourth number) @<:@not specified@:>@])],
      [with_version_patch_present=true], [with_version_patch_present=false])

  if test "x$with_version_patch_present" = xtrue; then
    if test "x$with_version_patch" = xyes; then
      AC_MSG_ERROR([--with-version-patch must have a value])
    elif test "x$with_version_patch" = xno; then
      # Interpret --without-* as empty string (i.e. 0) instead of the literal "no"
      VERSION_PATCH=0
    elif test "x$with_version_patch" = x; then
      VERSION_PATCH=0
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_PATCH, $with_version_patch)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is 0, if unspecified
      VERSION_PATCH=0
    fi
  fi

  # Calculate derived version properties

  # Set VERSION_IS_GA based on if VERSION_PRE has a value
  if test "x$VERSION_PRE" = x; then
    VERSION_IS_GA=true
  else
    VERSION_IS_GA=false
  fi

  # VERSION_NUMBER but always with exactly 4 positions, with 0 for empty positions.
  VERSION_NUMBER_FOUR_POSITIONS=$VERSION_MAJOR.$VERSION_MINOR.$VERSION_SECURITY.$VERSION_PATCH

  stripped_version_number=$VERSION_NUMBER_FOUR_POSITIONS
  # Strip trailing zeroes from stripped_version_number
  for i in 1 2 3 ; do stripped_version_number=${stripped_version_number%.0} ; done
  VERSION_NUMBER=$stripped_version_number

  # The complete version string, with additional build information
  if test "x$VERSION_BUILD$VERSION_OPT" = x; then
    VERSION_STRING=$VERSION_NUMBER${VERSION_PRE:+-$VERSION_PRE}
  else
    # If either build or opt is set, we need a + separator
    VERSION_STRING=$VERSION_NUMBER${VERSION_PRE:+-$VERSION_PRE}+$VERSION_BUILD${VERSION_OPT:+-$VERSION_OPT}
  fi

  # The short version string, just VERSION_NUMBER and PRE, if present.
  VERSION_SHORT=$VERSION_NUMBER${VERSION_PRE:+-$VERSION_PRE}

  AC_MSG_CHECKING([for version string])
  AC_MSG_RESULT([$VERSION_STRING])

  AC_SUBST(VERSION_MAJOR)
  AC_SUBST(VERSION_MINOR)
  AC_SUBST(VERSION_SECURITY)
  AC_SUBST(VERSION_PATCH)
  AC_SUBST(VERSION_PRE)
  AC_SUBST(VERSION_BUILD)
  AC_SUBST(VERSION_OPT)
  AC_SUBST(VERSION_NUMBER)
  AC_SUBST(VERSION_NUMBER_FOUR_POSITIONS)
  AC_SUBST(VERSION_STRING)
  AC_SUBST(VERSION_SHORT)
  AC_SUBST(VERSION_IS_GA)
])
