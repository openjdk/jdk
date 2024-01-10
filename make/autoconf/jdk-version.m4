#
# Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
  if [ ! [[ "$2" =~ ^0*([1-9][0-9]*)$|^0*(0)$ ]] ] ; then
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
  # Source the version numbers file
  . $TOPDIR/make/conf/version-numbers.conf
  # Source the branding file
  . $TOPDIR/make/conf/branding.conf

  # Some non-version number information is set in that file
  AC_SUBST(LAUNCHER_NAME)
  AC_SUBST(PRODUCT_NAME)
  AC_SUBST(PRODUCT_SUFFIX)
  AC_SUBST(JDK_RC_PLATFORM_NAME)
  AC_SUBST(HOTSPOT_VM_DISTRO)

  # Note: UTIL_ARG_WITH treats empty strings as valid values when OPTIONAL is false!

  # Outer [ ] to quote m4.
  [ USERNAME=`$ECHO "$USER" | $TR -d -c '[a-z][A-Z][0-9]'` ]

  # $USER may be not defined in dockers, so try to check with $WHOAMI
  if test "x$USERNAME" = x && test "x$WHOAMI" != x; then
    [ USERNAME=`$WHOAMI | $TR -d -c '[a-z][A-Z][0-9]'` ]
  fi

  # Setup username (for use in adhoc version strings etc)
  UTIL_ARG_WITH(NAME: build-user, TYPE: string,
    RESULT: USERNAME,
    DEFAULT: $USERNAME,
    DESC: [build username to use in version strings],
    DEFAULT_DESC: [current username, sanitized],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY])
  AC_SUBST(USERNAME)

  # Set the JDK RC name
  # Otherwise calculate from "branding.conf" included above.
  UTIL_ARG_WITH(NAME: jdk-rc-name, TYPE: string,
    DEFAULT: $PRODUCT_NAME $JDK_RC_PLATFORM_NAME,
    DESC: [Set JDK RC name. This is used for FileDescription and ProductName
       properties of MS Windows binaries.],
    DEFAULT_DESC: [from branding.conf],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY_PRINTABLE])
  AC_SUBST(JDK_RC_NAME)

  # The vendor name, if any
  # Only set COMPANY_NAME if '--with-vendor-name' was used and is not empty.
  # Otherwise we will use the value from "branding.conf" included above.
  UTIL_ARG_WITH(NAME: vendor-name, TYPE: string,
    RESULT: COMPANY_NAME,
    DEFAULT: $COMPANY_NAME,
    DESC: [Set vendor name. Among others, used to set the 'java.vendor'
       and 'java.vm.vendor' system properties.],
    DEFAULT_DESC: [from branding.conf],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY_PRINTABLE])
  AC_SUBST(COMPANY_NAME)

  # Set the JDK RC Company name
  # Otherwise uses the value set for "vendor-name".
  UTIL_ARG_WITH(NAME: jdk-rc-company-name, TYPE: string,
    DEFAULT: $COMPANY_NAME,
    DESC: [Set JDK RC company name. This is used for CompanyName properties of MS Windows binaries.],
    DEFAULT_DESC: [from branding.conf],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY_PRINTABLE])
  AC_SUBST(JDK_RC_COMPANY_NAME)

  # The vendor URL, if any
  # Only set VENDOR_URL if '--with-vendor-url' was used and is not empty.
  # Otherwise we will use the value from "branding.conf" included above.
  UTIL_ARG_WITH(NAME: vendor-url, TYPE: string,
    DEFAULT: $VENDOR_URL,
    DESC: [Set the 'java.vendor.url' system property],
    DEFAULT_DESC: [from branding.conf],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY_PRINTABLE])
  AC_SUBST(VENDOR_URL)

  # The vendor bug URL, if any
  # Only set VENDOR_URL_BUG if '--with-vendor-bug-url' was used and is not empty.
  # Otherwise we will use the value from "branding.conf" included above.
  UTIL_ARG_WITH(NAME: vendor-bug-url, TYPE: string,
    RESULT: VENDOR_URL_BUG,
    DEFAULT: $VENDOR_URL_BUG,
    DESC: [Set the 'java.vendor.url.bug' system property],
    DEFAULT_DESC: [from branding.conf],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY_PRINTABLE])
  AC_SUBST(VENDOR_URL_BUG)

  # The vendor VM bug URL, if any
  # Only set VENDOR_URL_VM_BUG if '--with-vendor-vm-bug-url' was used and is not empty.
  # Otherwise we will use the value from "branding.conf" included above.
  UTIL_ARG_WITH(NAME: vendor-vm-bug-url, TYPE: string,
    RESULT: VENDOR_URL_VM_BUG,
    DEFAULT: $VENDOR_URL_VM_BUG,
    DESC: [Sets the bug URL which will be displayed when the VM crashes],
    DEFAULT_DESC: [from branding.conf],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY_PRINTABLE])
  AC_SUBST(VENDOR_URL_VM_BUG)

  # Override version from arguments

  # If --with-version-string is set, process it first. It is possible to
  # override parts with more specific flags, since these are processed later.
  UTIL_ARG_WITH(NAME: version-string, TYPE: string,
    DEFAULT: [],
    DESC: [Set version string],
    DEFAULT_DESC: [calculated],
    CHECK_VALUE: [
      if test "x$RESULT" != x; then
        # Additional [] needed to keep m4 from mangling shell constructs.
        if [ [[ $RESULT =~ ^([0-9]+)(\.([0-9]+))?(\.([0-9]+))?(\.([0-9]+))?(\.([0-9]+))?(\.([0-9]+))?(\.([0-9]+))?(-([a-zA-Z0-9]+))?(((\+)([0-9]*))?(-([-a-zA-Z0-9.]+))?)?$ ]] ]; then
          VERSION_FEATURE=${BASH_REMATCH[[1]]}
          VERSION_INTERIM=${BASH_REMATCH[[3]]}
          VERSION_UPDATE=${BASH_REMATCH[[5]]}
          VERSION_PATCH=${BASH_REMATCH[[7]]}
          VERSION_EXTRA1=${BASH_REMATCH[[9]]}
          VERSION_EXTRA2=${BASH_REMATCH[[11]]}
          VERSION_EXTRA3=${BASH_REMATCH[[13]]}
          VERSION_PRE=${BASH_REMATCH[[15]]}
          version_plus_separator=${BASH_REMATCH[[18]]}
          VERSION_BUILD=${BASH_REMATCH[[19]]}
          VERSION_OPT=${BASH_REMATCH[[21]]}
          # Unspecified numerical fields are interpreted as 0.
          if test "x$VERSION_INTERIM" = x; then
            VERSION_INTERIM=0
          fi
          if test "x$VERSION_UPDATE" = x; then
            VERSION_UPDATE=0
          fi
          if test "x$VERSION_PATCH" = x; then
            VERSION_PATCH=0
          fi
          if test "x$VERSION_EXTRA1" = x; then
            VERSION_EXTRA1=0
          fi
          if test "x$VERSION_EXTRA2" = x; then
            VERSION_EXTRA2=0
          fi
          if test "x$VERSION_EXTRA3" = x; then
            VERSION_EXTRA3=0
          fi
          if test "x$version_plus_separator" != x \
              && test "x$VERSION_BUILD$VERSION_OPT" = x; then
            AC_MSG_ERROR([Version string contains + but both 'BUILD' and 'OPT' are missing])
          fi
          if test "x$VERSION_BUILD" = x0; then
            AC_MSG_WARN([Version build 0 is interpreted as no build number])
            VERSION_BUILD=
          fi
          # Stop the version part process from setting default values.
          # We still allow them to explicitly override though.
          NO_DEFAULT_VERSION_PARTS=true
        else
          FAILURE="--with-version-string fails to parse as a valid version string: $RESULT"
        fi
      fi
    ])

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
      # Only [a-zA-Z0-9] is allowed in the VERSION_PRE. Outer [ ] to quote m4.
      [ VERSION_PRE=`$ECHO "$with_version_pre" | $TR -c -d '[a-zA-Z0-9]'` ]
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
      # Default is to calculate a string like this:
      # 'adhoc.<username>.<base dir name>'
      # Outer [ ] to quote m4.
      [ basedirname=`$BASENAME "$WORKSPACE_ROOT" | $TR -d -c '[a-z][A-Z][0-9].-'` ]
      VERSION_OPT="adhoc.$USERNAME.$basedirname"
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
      if test "x$VERSION_BUILD" = "x0"; then
        AC_MSG_WARN([--with-version-build=0 is interpreted as --without-version-build])
        VERSION_BUILD=
      fi
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is to not have a build number.
      VERSION_BUILD=""
    fi
  fi

  # Default is to get value from version-numbers.conf
  if test "x$NO_DEFAULT_VERSION_PARTS" = xtrue; then
    DEFAULT_VERSION_FEATURE="$VERSION_FEATURE"
  fi

  UTIL_ARG_WITH(NAME: version-feature, TYPE: string,
    DEFAULT: $DEFAULT_VERSION_FEATURE,
    DESC: [Set version 'FEATURE' field (first number)],
    DEFAULT_DESC: [current source value],
    CHECK_VALUE: [
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_FEATURE, $RESULT)
    ])

  AC_ARG_WITH(version-interim, [AS_HELP_STRING([--with-version-interim],
      [Set version 'INTERIM' field (second number) @<:@current source value@:>@])],
      [with_version_interim_present=true], [with_version_interim_present=false])

  if test "x$with_version_interim_present" = xtrue; then
    if test "x$with_version_interim" = xyes; then
      AC_MSG_ERROR([--with-version-interim must have a value])
    elif test "x$with_version_interim" = xno; then
      # Interpret --without-* as empty string (i.e. 0) instead of the literal "no"
      VERSION_INTERIM=0
    elif test "x$with_version_interim" = x; then
      VERSION_INTERIM=0
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_INTERIM, $with_version_interim)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is 0, if unspecified
      VERSION_INTERIM=$DEFAULT_VERSION_INTERIM
    fi
  fi

  AC_ARG_WITH(version-update, [AS_HELP_STRING([--with-version-update],
      [Set version 'UPDATE' field (third number) @<:@current source value@:>@])],
      [with_version_update_present=true], [with_version_update_present=false])

  if test "x$with_version_update_present" = xtrue; then
    if test "x$with_version_update" = xyes; then
      AC_MSG_ERROR([--with-version-update must have a value])
    elif test "x$with_version_update" = xno; then
      # Interpret --without-* as empty string (i.e. 0) instead of the literal "no"
      VERSION_UPDATE=0
    elif test "x$with_version_update" = x; then
      VERSION_UPDATE=0
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_UPDATE, $with_version_update)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      # Default is 0, if unspecified
      VERSION_UPDATE=$DEFAULT_VERSION_UPDATE
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
      VERSION_PATCH=$DEFAULT_VERSION_PATCH
    fi
  fi

  # The 1st version extra number, if any
  AC_ARG_WITH(version-extra1, [AS_HELP_STRING([--with-version-extra1],
      [Set 1st version extra number @<:@not specified@:>@])],
      [with_version_extra1_present=true], [with_version_extra1_present=false])

  if test "x$with_version_extra1_present" = xtrue; then
    if test "x$with_version_extra1" = xyes; then
      AC_MSG_ERROR([--with-version-extra1 must have a value])
    elif test "x$with_version_extra1" = xno; then
      # Interpret --without-* as empty string (i.e. 0) instead of the literal "no"
      VERSION_EXTRA1=0
    elif test "x$with_version_extra1" = x; then
      VERSION_EXTRA1=0
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_EXTRA1, $with_version_extra1)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      VERSION_EXTRA1=$DEFAULT_VERSION_EXTRA1
    fi
  fi

  # The 2nd version extra number, if any
  AC_ARG_WITH(version-extra2, [AS_HELP_STRING([--with-version-extra2],
      [Set 2nd version extra number @<:@not specified@:>@])],
      [with_version_extra2_present=true], [with_version_extra2_present=false])

  if test "x$with_version_extra2_present" = xtrue; then
    if test "x$with_version_extra2" = xyes; then
      AC_MSG_ERROR([--with-version-extra2 must have a value])
    elif test "x$with_version_extra2" = xno; then
      # Interpret --without-* as empty string (i.e. 0) instead of the literal "no"
      VERSION_EXTRA2=0
    elif test "x$with_version_extra2" = x; then
      VERSION_EXTRA2=0
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_EXTRA2, $with_version_extra2)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      VERSION_EXTRA2=$DEFAULT_VERSION_EXTRA2
    fi
  fi

  # The 3rd version extra number, if any
  AC_ARG_WITH(version-extra3, [AS_HELP_STRING([--with-version-extra3],
      [Set 3rd version extra number @<:@not specified@:>@])],
      [with_version_extra3_present=true], [with_version_extra3_present=false])

  if test "x$with_version_extra3_present" = xtrue; then
    if test "x$with_version_extra3" = xyes; then
      AC_MSG_ERROR([--with-version-extra3 must have a value])
    elif test "x$with_version_extra3" = xno; then
      # Interpret --without-* as empty string (i.e. 0) instead of the literal "no"
      VERSION_EXTRA3=0
    elif test "x$with_version_extra3" = x; then
      VERSION_EXTRA3=0
    else
      JDKVER_CHECK_AND_SET_NUMBER(VERSION_EXTRA3, $with_version_extra3)
    fi
  else
    if test "x$NO_DEFAULT_VERSION_PARTS" != xtrue; then
      VERSION_EXTRA3=$DEFAULT_VERSION_EXTRA3
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
  VERSION_NUMBER_FOUR_POSITIONS=$VERSION_FEATURE.$VERSION_INTERIM.$VERSION_UPDATE.$VERSION_PATCH

  # VERSION_NUMBER but always with all positions, with 0 for empty positions.
  VERSION_NUMBER_ALL_POSITIONS=$VERSION_NUMBER_FOUR_POSITIONS.$VERSION_EXTRA1.$VERSION_EXTRA2.$VERSION_EXTRA3

  stripped_version_number=$VERSION_NUMBER_ALL_POSITIONS
  # Strip trailing zeroes from stripped_version_number
  for i in 1 2 3 4 5 6 ; do stripped_version_number=${stripped_version_number%.0} ; done
  VERSION_NUMBER=$stripped_version_number

  # A build number of "0" is interpreted as "no build number".
  if test "x$VERSION_BUILD" = x0; then
    VERSION_BUILD=
  fi

  # Compute the complete version string, with additional build information
  version_with_pre=$VERSION_NUMBER${VERSION_PRE:+-$VERSION_PRE}
  if test "x$VERSION_BUILD" != x || \
      ( test "x$VERSION_OPT" != x && test "x$VERSION_PRE" = x ); then
    # As per JEP 223, if build is set, or if opt is set but not pre,
    # we need a + separator
    version_with_build=$version_with_pre+$VERSION_BUILD
  else
    version_with_build=$version_with_pre
  fi
  VERSION_STRING=$version_with_build${VERSION_OPT:+-$VERSION_OPT}

  # The short version string, just VERSION_NUMBER and PRE, if present.
  VERSION_SHORT=$VERSION_NUMBER${VERSION_PRE:+-$VERSION_PRE}

  # The version date
  UTIL_ARG_WITH(NAME: version-date, TYPE: string,
    DEFAULT: $DEFAULT_VERSION_DATE,
    DESC: [Set version date],
    DEFAULT_DESC: [current source value],
    CHECK_VALUE: [
      if test "x$RESULT" = x; then
        FAILURE="--with-version-date cannot be empty"
      elif [ ! [[ $RESULT =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] ]; then
        FAILURE="\"$RESULT\" is not a valid version date"
      fi
    ])

  # The vendor version string, if any
  # DEFAULT is set to an empty string in the case of --with-vendor-version-string without
  # any value, which would set VENDOR_VERSION_STRING_ENABLED to true and ultimately also
  # cause VENDOR_VERSION_STRING to fall back to the value in DEFAULT
  UTIL_ARG_WITH(NAME: vendor-version-string, TYPE: string,
    DEFAULT: [],
    OPTIONAL: true,
    DESC: [Set vendor version string],
    DEFAULT_DESC: [not specified])

  if test "x$VENDOR_VERSION_STRING_ENABLED" = xtrue; then
    if [ ! [[ $VENDOR_VERSION_STRING =~ ^[[:graph:]]*$ ]] ]; then
      AC_MSG_ERROR([--with--vendor-version-string contains non-graphical characters: $VENDOR_VERSION_STRING])
    fi
  fi

  # Set the MACOSX Bundle Name base
  UTIL_ARG_WITH(NAME: macosx-bundle-name-base, TYPE: string,
    DEFAULT: $MACOSX_BUNDLE_NAME_BASE,
    DESC: [Set the MacOSX Bundle Name base. This is the base name for calculating MacOSX Bundle Names.],
    DEFAULT_DESC: [from branding.conf],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY_PRINTABLE])
  AC_SUBST(MACOSX_BUNDLE_NAME_BASE)

  # If using the default value, append the VERSION_PRE if there is one
  # to make it possible to tell official builds apart from developer builds
  if test "x$VERSION_PRE" != x; then
    MACOSX_BUNDLE_ID_BASE="$MACOSX_BUNDLE_ID_BASE-$VERSION_PRE"
  fi

  # Set the MACOSX Bundle ID base
  UTIL_ARG_WITH(NAME: macosx-bundle-id-base, TYPE: string,
    DEFAULT: $MACOSX_BUNDLE_ID_BASE,
    DESC: [Set the MacOSX Bundle ID base. This is the base ID for calculating MacOSX Bundle IDs.],
    DEFAULT_DESC: [based on branding.conf and VERSION_PRE],
    CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY_PRINTABLE])
  AC_SUBST(MACOSX_BUNDLE_ID_BASE)

  if test "x$VERSION_BUILD" != x; then
    MACOSX_BUNDLE_BUILD_VERSION="$VERSION_BUILD"
  else
    MACOSX_BUNDLE_BUILD_VERSION=0
  fi

  # If VERSION_OPT consists of only numbers and periods, add it.
  if [ [[ $VERSION_OPT =~ ^[0-9\.]+$ ]] ]; then
    MACOSX_BUNDLE_BUILD_VERSION="$MACOSX_BUNDLE_BUILD_VERSION.$VERSION_OPT"
  fi

  # Set the MACOSX CFBundleVersion field
  UTIL_ARG_WITH(NAME: macosx-bundle-build-version, TYPE: string,
    DEFAULT: $MACOSX_BUNDLE_BUILD_VERSION,
    DESC: [Set the MacOSX Bundle CFBundleVersion field. This key is a machine-readable
      string composed of one to three period-separated integers and should represent the
      build version.],
    DEFAULT_DESC: [the build number],
    CHECK_VALUE: [
      if test "x$RESULT" = x; then
        FAILURE="--with-macosx-bundle-build-version must have a value"
      elif [ ! [[ $RESULT =~ ^[0-9\.]*$ ]] ]; then
        FAILURE="--with-macosx-bundle-build-version contains non numbers and periods: $RESULT"
      fi
    ])
  AC_SUBST(MACOSX_BUNDLE_BUILD_VERSION)

  # We could define --with flags for these, if really needed
  VERSION_CLASSFILE_MAJOR="$DEFAULT_VERSION_CLASSFILE_MAJOR"
  VERSION_CLASSFILE_MINOR="$DEFAULT_VERSION_CLASSFILE_MINOR"
  VERSION_DOCS_API_SINCE="$DEFAULT_VERSION_DOCS_API_SINCE"
  JDK_SOURCE_TARGET_VERSION="$DEFAULT_JDK_SOURCE_TARGET_VERSION"

  AC_MSG_CHECKING([for version string])
  AC_MSG_RESULT([$VERSION_STRING])

  AC_SUBST(VERSION_FEATURE)
  AC_SUBST(VERSION_INTERIM)
  AC_SUBST(VERSION_UPDATE)
  AC_SUBST(VERSION_PATCH)
  AC_SUBST(VERSION_EXTRA1)
  AC_SUBST(VERSION_EXTRA2)
  AC_SUBST(VERSION_EXTRA3)
  AC_SUBST(VERSION_PRE)
  AC_SUBST(VERSION_BUILD)
  AC_SUBST(VERSION_OPT)
  AC_SUBST(VERSION_NUMBER)
  AC_SUBST(VERSION_NUMBER_FOUR_POSITIONS)
  AC_SUBST(VERSION_STRING)
  AC_SUBST(VERSION_SHORT)
  AC_SUBST(VERSION_IS_GA)
  AC_SUBST(VERSION_DATE)
  AC_SUBST(VENDOR_VERSION_STRING)
  AC_SUBST(VERSION_CLASSFILE_MAJOR)
  AC_SUBST(VERSION_CLASSFILE_MINOR)
  AC_SUBST(VERSION_DOCS_API_SINCE)
  AC_SUBST(JDK_SOURCE_TARGET_VERSION)
])
