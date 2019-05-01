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

# All valid JVM features, regardless of platform
VALID_JVM_FEATURES="compiler1 compiler2 zero minimal dtrace jvmti jvmci \
    graal vm-structs jni-check services management cmsgc epsilongc g1gc parallelgc serialgc shenandoahgc zgc nmt cds \
    static-build link-time-opt aot jfr"

# Deprecated JVM features (these are ignored, but with a warning)
DEPRECATED_JVM_FEATURES="trace"

# All valid JVM variants
VALID_JVM_VARIANTS="server client minimal core zero custom"

###############################################################################
# Check if the specified JVM variant should be built. To be used in shell if
# constructs, like this:
# if HOTSPOT_CHECK_JVM_VARIANT(server); then
#
# Only valid to use after HOTSPOT_SETUP_JVM_VARIANTS has setup variants.

# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([HOTSPOT_CHECK_JVM_VARIANT],
[ [ [[ " $JVM_VARIANTS " =~ " $1 " ]] ] ])

###############################################################################
# Check if the specified JVM feature is enabled. To be used in shell if
# constructs, like this:
# if HOTSPOT_CHECK_JVM_FEATURE(jvmti); then
#
# Only valid to use after HOTSPOT_SETUP_JVM_FEATURES has setup features.

# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([HOTSPOT_CHECK_JVM_FEATURE],
[ [ [[ " $JVM_FEATURES " =~ " $1 " ]] ] ])

###############################################################################
# Check if the specified JVM feature is explicitly disabled. To be used in
# shell if constructs, like this:
# if HOTSPOT_IS_JVM_FEATURE_DISABLED(jvmci); then
#
# This function is internal to hotspot.m4, and is only used when constructing
# the valid set of enabled JVM features. Users outside of hotspot.m4 should just
# use HOTSPOT_CHECK_JVM_FEATURE to check if a feature is enabled or not.

# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([HOTSPOT_IS_JVM_FEATURE_DISABLED],
[ [ [[ " $DISABLED_JVM_FEATURES " =~ " $1 " ]] ] ])

###############################################################################
# Check which variants of the JVM that we want to build. Available variants are:
#   server: normal interpreter, and a tiered C1/C2 compiler
#   client: normal interpreter, and C1 (no C2 compiler)
#   minimal: reduced form of client with optional features stripped out
#   core: normal interpreter only, no compiler
#   zero: C++ based interpreter only, no compiler
#   custom: baseline JVM with no default features
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_VARIANTS],
[
  AC_ARG_WITH([jvm-variants], [AS_HELP_STRING([--with-jvm-variants],
      [JVM variants (separated by commas) to build (server,client,minimal,core,zero,custom) @<:@server@:>@])])

  if test "x$with_jvm_variants" = x; then
    with_jvm_variants="server"
  fi
  JVM_VARIANTS_OPT="$with_jvm_variants"

  # Has the user listed more than one variant?
  # Additional [] needed to keep m4 from mangling shell constructs.
  if [ [[ "$JVM_VARIANTS_OPT" =~ "," ]] ]; then
    BUILDING_MULTIPLE_JVM_VARIANTS=true
  else
    BUILDING_MULTIPLE_JVM_VARIANTS=false
  fi
  # Replace the commas with AND for use in the build directory name.
  JVM_VARIANTS_WITH_AND=`$ECHO "$JVM_VARIANTS_OPT" | $SED -e 's/,/AND/g'`

  AC_MSG_CHECKING([which variants of the JVM to build])
  # JVM_VARIANTS is a space-separated list.
  # Also use minimal, not minimal1 (which is kept for backwards compatibility).
  JVM_VARIANTS=`$ECHO $JVM_VARIANTS_OPT | $SED -e 's/,/ /g' -e 's/minimal1/minimal/'`
  AC_MSG_RESULT([$JVM_VARIANTS])

  # Check that the selected variants are valid
  BASIC_GET_NON_MATCHING_VALUES(INVALID_VARIANTS, $JVM_VARIANTS, $VALID_JVM_VARIANTS)
  if test "x$INVALID_VARIANTS" != x; then
    AC_MSG_NOTICE([Unknown variant(s) specified: "$INVALID_VARIANTS"])
    AC_MSG_NOTICE([The available JVM variants are: "$VALID_JVM_VARIANTS"])
    AC_MSG_ERROR([Cannot continue])
  fi

  # All "special" variants share the same output directory ("server")
  VALID_MULTIPLE_JVM_VARIANTS="server client minimal"
  BASIC_GET_NON_MATCHING_VALUES(INVALID_MULTIPLE_VARIANTS, $JVM_VARIANTS, $VALID_MULTIPLE_JVM_VARIANTS)
  if  test "x$INVALID_MULTIPLE_VARIANTS" != x && test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = xtrue; then
    AC_MSG_ERROR([You cannot build multiple variants with anything else than $VALID_MULTIPLE_JVM_VARIANTS.])
  fi

  # The "main" variant is the one used by other libs to link against during the
  # build.
  if test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = "xtrue"; then
    MAIN_VARIANT_PRIO_ORDER="server client minimal"
    for variant in $MAIN_VARIANT_PRIO_ORDER; do
      if HOTSPOT_CHECK_JVM_VARIANT($variant); then
        JVM_VARIANT_MAIN="$variant"
        break
      fi
    done
  else
    JVM_VARIANT_MAIN="$JVM_VARIANTS"
  fi

  AC_SUBST(JVM_VARIANTS)
  AC_SUBST(VALID_JVM_VARIANTS)
  AC_SUBST(JVM_VARIANT_MAIN)

  if HOTSPOT_CHECK_JVM_VARIANT(zero); then
    # zero behaves as a platform and rewrites these values. This is really weird. :(
    # We are guaranteed that we do not build any other variants when building zero.
    HOTSPOT_TARGET_CPU=zero
    HOTSPOT_TARGET_CPU_ARCH=zero
  fi
])

###############################################################################
# Check if dtrace should be enabled and has all prerequisites present.
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_DTRACE],
[
  # Test for dtrace dependencies
  AC_ARG_ENABLE([dtrace], [AS_HELP_STRING([--enable-dtrace@<:@=yes/no/auto@:>@],
      [enable dtrace. Default is auto, where dtrace is enabled if all dependencies
      are present.])])

  DTRACE_DEP_MISSING=false

  AC_MSG_CHECKING([for dtrace tool])
  if test "x$DTRACE" != "x" && test -x "$DTRACE"; then
    AC_MSG_RESULT([$DTRACE])
  else
    AC_MSG_RESULT([not found, cannot build dtrace])
    DTRACE_DEP_MISSING=true
  fi

  AC_CHECK_HEADERS([sys/sdt.h], [DTRACE_HEADERS_OK=yes],[DTRACE_HEADERS_OK=no])
  if test "x$DTRACE_HEADERS_OK" != "xyes"; then
    DTRACE_DEP_MISSING=true
  fi

  AC_MSG_CHECKING([if dtrace should be built])
  if test "x$enable_dtrace" = "xyes"; then
    if test "x$DTRACE_DEP_MISSING" = "xtrue"; then
      AC_MSG_RESULT([no, missing dependencies])
      HELP_MSG_MISSING_DEPENDENCY([dtrace])
      AC_MSG_ERROR([Cannot enable dtrace with missing dependencies. See above. $HELP_MSG])
    else
      INCLUDE_DTRACE=true
      AC_MSG_RESULT([yes, forced])
    fi
  elif test "x$enable_dtrace" = "xno"; then
    INCLUDE_DTRACE=false
    AC_MSG_RESULT([no, forced])
  elif test "x$enable_dtrace" = "xauto" || test "x$enable_dtrace" = "x"; then
    if test "x$DTRACE_DEP_MISSING" = "xtrue"; then
      INCLUDE_DTRACE=false
      AC_MSG_RESULT([no, missing dependencies])
    else
      INCLUDE_DTRACE=true
      AC_MSG_RESULT([yes, dependencies present])
    fi
  else
    AC_MSG_ERROR([Invalid value for --enable-dtrace: $enable_dtrace])
  fi
])

################################################################################
# Check if AOT should be enabled
#
AC_DEFUN_ONCE([HOTSPOT_ENABLE_DISABLE_AOT],
[
  AC_ARG_ENABLE([aot], [AS_HELP_STRING([--enable-aot@<:@=yes/no/auto@:>@],
      [enable ahead of time compilation feature. Default is auto, where aot is enabled if all dependencies are present.])])

  if test "x$enable_aot" = "x" || test "x$enable_aot" = "xauto"; then
    ENABLE_AOT="true"
  elif test "x$enable_aot" = "xyes"; then
    ENABLE_AOT="true"
  elif test "x$enable_aot" = "xno"; then
    ENABLE_AOT="false"
  else
    AC_MSG_ERROR([Invalid value for --enable-aot: $enable_aot])
  fi

  if test "x$ENABLE_AOT" = "xtrue"; then
    # Only enable AOT on X64 platforms.
    if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
      if test -e "${TOPDIR}/src/jdk.aot"; then
        if test -e "${TOPDIR}/src/jdk.internal.vm.compiler"; then
          ENABLE_AOT="true"
        else
          ENABLE_AOT="false"
          if test "x$enable_aot" = "xyes"; then
            AC_MSG_ERROR([Cannot build AOT without src/jdk.internal.vm.compiler sources. Remove --enable-aot.])
          fi
        fi
      else
        ENABLE_AOT="false"
        if test "x$enable_aot" = "xyes"; then
          AC_MSG_ERROR([Cannot build AOT without src/jdk.aot sources. Remove --enable-aot.])
        fi
      fi
    else
      ENABLE_AOT="false"
      if test "x$enable_aot" = "xyes"; then
        AC_MSG_ERROR([AOT is currently only supported on x86_64 and aarch64. Remove --enable-aot.])
      fi
    fi
  fi

  AC_SUBST(ENABLE_AOT)
])

################################################################################
# Allow to disable CDS
#
AC_DEFUN_ONCE([HOTSPOT_ENABLE_DISABLE_CDS],
[
  AC_ARG_ENABLE([cds], [AS_HELP_STRING([--enable-cds@<:@=yes/no/auto@:>@],
      [enable class data sharing feature in non-minimal VM. Default is auto, where cds is enabled if supported on the platform.])])

  if test "x$enable_cds" = "x" || test "x$enable_cds" = "xauto"; then
    ENABLE_CDS="true"
  elif test "x$enable_cds" = "xyes"; then
    ENABLE_CDS="true"
  elif test "x$enable_cds" = "xno"; then
    ENABLE_CDS="false"
  else
    AC_MSG_ERROR([Invalid value for --enable-cds: $enable_cds])
  fi

  AC_SUBST(ENABLE_CDS)
])

###############################################################################
# Set up all JVM features for each JVM variant.
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_FEATURES],
[
  # Prettify the VALID_JVM_FEATURES string
  BASIC_SORT_LIST(VALID_JVM_FEATURES, $VALID_JVM_FEATURES)

  # The user can in some cases supply additional jvm features. For the custom
  # variant, this defines the entire variant.
  AC_ARG_WITH([jvm-features], [AS_HELP_STRING([--with-jvm-features],
      [JVM features to enable (foo) or disable (-foo), separated by comma. Use '--help' to show possible values @<:@none@:>@])])
  if test "x$with_jvm_features" != x; then
    AC_MSG_CHECKING([user specified JVM feature list])
    USER_JVM_FEATURE_LIST=`$ECHO $with_jvm_features | $SED -e 's/,/ /g'`
    AC_MSG_RESULT([$user_jvm_feature_list])
    # These features will be added to all variant defaults
    JVM_FEATURES=`$ECHO $USER_JVM_FEATURE_LIST | $AWK '{ for (i=1; i<=NF; i++) if (!match($i, /^-.*/)) printf("%s ", $i) }'`
    # These features will be removed from all variant defaults
    DISABLED_JVM_FEATURES=`$ECHO $USER_JVM_FEATURE_LIST | $AWK '{ for (i=1; i<=NF; i++) if (match($i, /^-.*/)) printf("%s ", substr($i, 2))}'`

    # Verify that the user has provided valid features
    BASIC_GET_NON_MATCHING_VALUES(INVALID_FEATURES, $JVM_FEATURES $DISABLED_JVM_FEATURES, $VALID_JVM_FEATURES $DEPRECATED_JVM_FEATURES)
    if test "x$INVALID_FEATURES" != x; then
      AC_MSG_NOTICE([Unknown JVM features specified: "$INVALID_FEATURES"])
      AC_MSG_NOTICE([The available JVM features are: "$VALID_JVM_FEATURES"])
      AC_MSG_ERROR([Cannot continue])
    fi

    # Check if the user has provided deprecated features
    BASIC_GET_MATCHING_VALUES(DEPRECATED_FEATURES, $JVM_FEATURES $DISABLED_JVM_FEATURES, $DEPRECATED_JVM_FEATURES)
    if test "x$DEPRECATED_FEATURES" != x; then
      AC_MSG_WARN([Deprecated JVM features specified (will be ignored): "$DEPRECATED_FEATURES"])
      # Filter out deprecated features
      BASIC_GET_NON_MATCHING_VALUES(JVM_FEATURES, $JVM_FEATURES, $DEPRECATED_FEATURES)
      BASIC_GET_NON_MATCHING_VALUES(DISABLED_JVM_FEATURES, $DISABLED_JVM_FEATURES, $DEPRECATED_FEATURES)
    fi

  fi

  # Override hotspot cpu definitions for ARM platforms
  if test "x$OPENJDK_TARGET_CPU" = xarm; then
    HOTSPOT_TARGET_CPU=arm_32
    HOTSPOT_TARGET_CPU_DEFINE="ARM32"
  fi

  # Verify that dependencies are met for explicitly set features.
  if HOTSPOT_CHECK_JVM_FEATURE(jvmti) && ! HOTSPOT_CHECK_JVM_FEATURE(services); then
    AC_MSG_ERROR([Specified JVM feature 'jvmti' requires feature 'services'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(management) && ! HOTSPOT_CHECK_JVM_FEATURE(nmt); then
    AC_MSG_ERROR([Specified JVM feature 'management' requires feature 'nmt'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(jvmci) && ! (HOTSPOT_CHECK_JVM_FEATURE(compiler1) || HOTSPOT_CHECK_JVM_FEATURE(compiler2)); then
    AC_MSG_ERROR([Specified JVM feature 'jvmci' requires feature 'compiler2' or 'compiler1'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(cmsgc) && ! HOTSPOT_CHECK_JVM_FEATURE(serialgc); then
    AC_MSG_ERROR([Specified JVM feature 'cmsgc' requires feature 'serialgc'])
  fi

  # Enable JFR by default, except for Zero, linux-sparcv9 and on minimal.
  if ! HOTSPOT_CHECK_JVM_VARIANT(zero); then
    if test "x$OPENJDK_TARGET_OS" != xaix; then
      if test "x$OPENJDK_TARGET_OS" != xlinux || test "x$OPENJDK_TARGET_CPU" != xsparcv9; then
        NON_MINIMAL_FEATURES="$NON_MINIMAL_FEATURES jfr"
      fi
    fi
  fi

  # Only enable Shenandoah on supported arches
  AC_MSG_CHECKING([if shenandoah can be built])
  if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
    AC_MSG_RESULT([yes])
  else
    DISABLED_JVM_FEATURES="$DISABLED_JVM_FEATURES shenandoahgc"
    AC_MSG_RESULT([no, platform not supported])
  fi

  # Only enable ZGC on supported platforms
  AC_MSG_CHECKING([if zgc can be built])
  if test "x$OPENJDK_TARGET_OS" = "xlinux" && test "x$OPENJDK_TARGET_CPU" = "xx86_64"; then
    AC_MSG_RESULT([yes])
  else
    DISABLED_JVM_FEATURES="$DISABLED_JVM_FEATURES zgc"
    AC_MSG_RESULT([no, platform not supported])
  fi

  # Disable unsupported GCs for Zero
  if HOTSPOT_CHECK_JVM_VARIANT(zero); then
    DISABLED_JVM_FEATURES="$DISABLED_JVM_FEATURES epsilongc g1gc zgc shenandoahgc"
  fi

  # Turn on additional features based on other parts of configure
  if test "x$INCLUDE_DTRACE" = "xtrue"; then
    JVM_FEATURES="$JVM_FEATURES dtrace"
  else
    if HOTSPOT_CHECK_JVM_FEATURE(dtrace); then
      AC_MSG_ERROR([To enable dtrace, you must use --enable-dtrace])
    fi
  fi

  if test "x$STATIC_BUILD" = "xtrue"; then
    JVM_FEATURES="$JVM_FEATURES static-build"
  else
    if HOTSPOT_CHECK_JVM_FEATURE(static-build); then
      AC_MSG_ERROR([To enable static-build, you must use --enable-static-build])
    fi
  fi

  if ! HOTSPOT_CHECK_JVM_VARIANT(zero); then
    if HOTSPOT_CHECK_JVM_FEATURE(zero); then
      AC_MSG_ERROR([To enable zero, you must use --with-jvm-variants=zero])
    fi
  fi

  AC_MSG_CHECKING([if jvmci module jdk.internal.vm.ci should be built])
  # Check if jvmci is diabled
  if HOTSPOT_IS_JVM_FEATURE_DISABLED(jvmci); then
    AC_MSG_RESULT([no, forced])
    JVM_FEATURES_jvmci=""
    INCLUDE_JVMCI="false"
  else
    # Only enable jvmci on x86_64 and aarch64
    if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || \
       test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
      AC_MSG_RESULT([yes])
      JVM_FEATURES_jvmci="jvmci"
      INCLUDE_JVMCI="true"
    else
      AC_MSG_RESULT([no])
      JVM_FEATURES_jvmci=""
      INCLUDE_JVMCI="false"
      if HOTSPOT_CHECK_JVM_FEATURE(jvmci); then
        AC_MSG_ERROR([JVMCI is currently not supported on this platform.])
      fi
    fi
  fi

  AC_SUBST(INCLUDE_JVMCI)

  AC_MSG_CHECKING([if graal module jdk.internal.vm.compiler should be built])
  # Check if graal is diabled
  if HOTSPOT_IS_JVM_FEATURE_DISABLED(graal); then
    AC_MSG_RESULT([no, forced])
    JVM_FEATURES_graal=""
    INCLUDE_GRAAL="false"
  else
    if HOTSPOT_CHECK_JVM_FEATURE(graal); then
      AC_MSG_RESULT([yes, forced])
      if test "x$JVM_FEATURES_jvmci" != "xjvmci" ; then
        AC_MSG_ERROR([Specified JVM feature 'graal' requires feature 'jvmci'])
      fi
      JVM_FEATURES_graal="graal"
      INCLUDE_GRAAL="true"
    else
      # By default enable graal build on x64 or where AOT is available.
      # graal build requires jvmci.
      if test "x$JVM_FEATURES_jvmci" = "xjvmci" && \
          (test "x$OPENJDK_TARGET_CPU" = "xx86_64" || \
           test "x$ENABLE_AOT" = "xtrue") ; then
        AC_MSG_RESULT([yes])
        JVM_FEATURES_graal="graal"
        INCLUDE_GRAAL="true"
      else
        AC_MSG_RESULT([no])
        JVM_FEATURES_graal=""
        INCLUDE_GRAAL="false"
      fi
    fi
  fi

  AC_SUBST(INCLUDE_GRAAL)

  # Disable aot with '--with-jvm-features=-aot'
  if HOTSPOT_IS_JVM_FEATURE_DISABLED(aot); then
    ENABLE_AOT="false"
  fi

  AC_MSG_CHECKING([if aot should be enabled])
  if test "x$ENABLE_AOT" = "xtrue"; then
    if test "x$JVM_FEATURES_graal" != "xgraal"; then
      if test "x$enable_aot" = "xyes" || HOTSPOT_CHECK_JVM_FEATURE(aot); then
        AC_MSG_RESULT([yes, forced])
        AC_MSG_ERROR([Specified JVM feature 'aot' requires feature 'graal'])
      else
        AC_MSG_RESULT([no])
      fi
      JVM_FEATURES_aot=""
      ENABLE_AOT="false"
    else
      if test "x$enable_aot" = "xyes" || HOTSPOT_CHECK_JVM_FEATURE(aot); then
        AC_MSG_RESULT([yes, forced])
      else
        AC_MSG_RESULT([yes])
      fi
      JVM_FEATURES_aot="aot"
    fi
  else
    if test "x$enable_aot" = "xno" || HOTSPOT_IS_JVM_FEATURE_DISABLED(aot); then
      AC_MSG_RESULT([no, forced])
    else
      AC_MSG_RESULT([no])
    fi
    JVM_FEATURES_aot=""
    if HOTSPOT_CHECK_JVM_FEATURE(aot); then
      AC_MSG_ERROR([To enable aot, you must use --enable-aot])
    fi
  fi

  AC_SUBST(ENABLE_AOT)

  if test "x$OPENJDK_TARGET_CPU" = xarm ; then
    # Default to use link time optimizations on minimal on arm
    JVM_FEATURES_link_time_opt="link-time-opt"
  else
    JVM_FEATURES_link_time_opt=""
  fi

  # All variants but minimal (and custom) get these features
  NON_MINIMAL_FEATURES="$NON_MINIMAL_FEATURES cmsgc g1gc parallelgc serialgc epsilongc shenandoahgc jni-check jvmti management nmt services vm-structs zgc"

  # Disable CDS on AIX.
  if test "x$OPENJDK_TARGET_OS" = "xaix"; then
    ENABLE_CDS="false"
    if test "x$enable_cds" = "xyes"; then
      AC_MSG_ERROR([CDS is currently not supported on AIX. Remove --enable-cds.])
    fi
  fi

  # Disable CDS if user requested it with --with-jvm-features=-cds.
  if HOTSPOT_IS_JVM_FEATURE_DISABLED(cds); then
    ENABLE_CDS="false"
    if test "x$enable_cds" = "xyes"; then
      AC_MSG_ERROR([CDS was disabled by --with-jvm-features=-cds. Remove --enable-cds.])
    fi
  fi

  # Disable CDS for zero, minimal, core..
  if HOTSPOT_CHECK_JVM_VARIANT(zero) || HOTSPOT_CHECK_JVM_VARIANT(minimal) || HOTSPOT_CHECK_JVM_VARIANT(core); then
    # ..except when the user explicitely requested it with --enable-jvm-features
    if ! HOTSPOT_CHECK_JVM_FEATURE(cds); then
      ENABLE_CDS="false"
      if test "x$enable_cds" = "xyes"; then
        AC_MSG_ERROR([CDS not implemented for variants zero, minimal, core. Remove --enable-cds.])
      fi
    fi
  fi

  AC_MSG_CHECKING([if cds should be enabled])
  if test "x$ENABLE_CDS" = "xtrue"; then
    if test "x$enable_cds" = "xyes"; then
      AC_MSG_RESULT([yes, forced])
    else
      AC_MSG_RESULT([yes])
    fi
    NON_MINIMAL_FEATURES="$NON_MINIMAL_FEATURES cds"
  else
    if test "x$enable_cds" = "xno"; then
      AC_MSG_RESULT([no, forced])
    else
      AC_MSG_RESULT([no])
    fi
  fi

  # Enable features depending on variant.
  JVM_FEATURES_server="compiler1 compiler2 $NON_MINIMAL_FEATURES $JVM_FEATURES $JVM_FEATURES_jvmci $JVM_FEATURES_aot $JVM_FEATURES_graal"
  JVM_FEATURES_client="compiler1 $NON_MINIMAL_FEATURES $JVM_FEATURES"
  JVM_FEATURES_core="$NON_MINIMAL_FEATURES $JVM_FEATURES"
  JVM_FEATURES_minimal="compiler1 minimal serialgc $JVM_FEATURES $JVM_FEATURES_link_time_opt"
  JVM_FEATURES_zero="zero $NON_MINIMAL_FEATURES $JVM_FEATURES"
  JVM_FEATURES_custom="$JVM_FEATURES"

  AC_SUBST(JVM_FEATURES_server)
  AC_SUBST(JVM_FEATURES_client)
  AC_SUBST(JVM_FEATURES_core)
  AC_SUBST(JVM_FEATURES_minimal)
  AC_SUBST(JVM_FEATURES_zero)
  AC_SUBST(JVM_FEATURES_custom)

  # Used for verification of Makefiles by check-jvm-feature
  AC_SUBST(VALID_JVM_FEATURES)

  # --with-cpu-port is no longer supported
  BASIC_DEPRECATED_ARG_WITH(with-cpu-port)
])

###############################################################################
# Finalize JVM features once all setup is complete, including custom setup.
#
AC_DEFUN_ONCE([HOTSPOT_FINALIZE_JVM_FEATURES],
[
  for variant in $JVM_VARIANTS; do
    AC_MSG_CHECKING([JVM features for JVM variant '$variant'])
    features_var_name=JVM_FEATURES_$variant
    JVM_FEATURES_FOR_VARIANT=${!features_var_name}

    # Filter out user-requested disabled features
    BASIC_GET_NON_MATCHING_VALUES(JVM_FEATURES_FOR_VARIANT, $JVM_FEATURES_FOR_VARIANT, $DISABLED_JVM_FEATURES)

    # Keep feature lists sorted and free of duplicates
    BASIC_SORT_LIST(JVM_FEATURES_FOR_VARIANT, $JVM_FEATURES_FOR_VARIANT)

    # Update real feature set variable
    eval $features_var_name='"'$JVM_FEATURES_FOR_VARIANT'"'
    AC_MSG_RESULT(["$JVM_FEATURES_FOR_VARIANT"])

    # Verify that we have at least one gc selected
    GC_FEATURES=`$ECHO $JVM_FEATURES_FOR_VARIANT | $GREP gc`
    if test "x$GC_FEATURES" = x; then
      AC_MSG_WARN([Invalid JVM features: No gc selected for variant $variant.])
    fi

    # Validate features (for configure script errors, not user errors)
    BASIC_GET_NON_MATCHING_VALUES(INVALID_FEATURES, $JVM_FEATURES_FOR_VARIANT, $VALID_JVM_FEATURES)
    if test "x$INVALID_FEATURES" != x; then
      AC_MSG_ERROR([Internal configure script error. Invalid JVM feature(s): $INVALID_FEATURES])
    fi
  done
])

################################################################################
# Check if gtest should be built
#
AC_DEFUN_ONCE([HOTSPOT_ENABLE_DISABLE_GTEST],
[
  AC_ARG_ENABLE([hotspot-gtest], [AS_HELP_STRING([--disable-hotspot-gtest],
      [Disables building of the Hotspot unit tests @<:@enabled@:>@])])

  if test -e "${TOPDIR}/test/hotspot/gtest"; then
    GTEST_DIR_EXISTS="true"
  else
    GTEST_DIR_EXISTS="false"
  fi

  AC_MSG_CHECKING([if Hotspot gtest unit tests should be built])
  if test "x$enable_hotspot_gtest" = "xyes"; then
    if test "x$GTEST_DIR_EXISTS" = "xtrue"; then
      AC_MSG_RESULT([yes, forced])
      BUILD_GTEST="true"
    else
      AC_MSG_ERROR([Cannot build gtest without the test source])
    fi
  elif test "x$enable_hotspot_gtest" = "xno"; then
    AC_MSG_RESULT([no, forced])
    BUILD_GTEST="false"
  elif test "x$enable_hotspot_gtest" = "x"; then
    if test "x$GTEST_DIR_EXISTS" = "xtrue"; then
      AC_MSG_RESULT([yes])
      BUILD_GTEST="true"
    else
      AC_MSG_RESULT([no])
      BUILD_GTEST="false"
    fi
  else
    AC_MSG_ERROR([--enable-gtest must be either yes or no])
  fi

  AC_SUBST(BUILD_GTEST)
])
