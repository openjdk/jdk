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

m4_include([util_paths.m4])

###############################################################################
# Create a function/macro that takes a series of named arguments. The call is
# similar to AC_DEFUN, but the setup of the function looks like this:
# UTIL_DEFUN_NAMED([MYFUNC], [FOO *BAR], [$@], [
# ... do something
#   AC_MSG_NOTICE([Value of BAR is ARG_BAR])
# ])
# A star (*) in front of a named argument means that it is required and it's
# presence will be verified. To pass e.g. the first value as a normal indexed
# argument, use [m4_shift($@)] as the third argument instead of [$@]. These
# arguments are referenced in the function by their name prefixed by ARG_, e.g.
# "ARG_FOO".
#
# The generated function can be called like this:
# MYFUNC(FOO: [foo-val],
#     BAR: [
#         $ECHO hello world
#     ])
# Note that the argument value must start on the same line as the argument name.
#
# Argument 1: Name of the function to define
# Argument 2: List of legal named arguments, with a * prefix for required arguments
# Argument 3: Argument array to treat as named, typically $@
# Argument 4: The main function body
AC_DEFUN([UTIL_DEFUN_NAMED],
[
  AC_DEFUN($1, [
    m4_foreach([arg], m4_split(m4_normalize($2)), [
      m4_if(m4_bregexp(arg, [^\*]), -1,
        [
          m4_set_add(legal_named_args, arg)
        ],
        [
          m4_set_add(legal_named_args, m4_substr(arg, 1))
          m4_set_add(required_named_args, m4_substr(arg, 1))
        ]
      )
    ])

    # Delicate quoting and unquoting sequence to ensure the actual value is passed along unchanged
    # For details on how this works, see https://git.openjdk.org/jdk/pull/11458#discussion_r1038173051
    # WARNING: Proceed at the risk of your own sanity, getting this to work has made me completely
    # incapable of feeling love or any other positive emotion
    # ~Julian
    m4_foreach([arg], m4_dquote(m4_dquote_elt($3)), [
      m4_if(m4_index(arg, [: ]), -1, [m4_define([arg], m4_dquote(m4_bpatsubst(m4_dquote(arg), [:], [: ])))])
      m4_define(arg_name, m4_substr(arg, 0, m4_index(arg, [: ])))
      m4_set_contains(legal_named_args, arg_name, [],[AC_MSG_ERROR([Internal error: m4_if(arg_name, , arg, arg_name) is not a valid named argument to [$1]. Valid arguments are 'm4_set_contents(defined_args, [ ]) m4_set_contents(legal_named_args, [ ])'.])])
      m4_set_remove(required_named_args, arg_name)
      m4_set_remove(legal_named_args, arg_name)
      m4_pushdef([ARG_][]arg_name, m4_bpatsubst(m4_bpatsubst(m4_dquote(m4_dquote(arg)), arg_name[: ]), [^\s*]))
      m4_set_add(defined_args, arg_name)
      m4_undefine([arg_name])
    ])
    m4_set_empty(required_named_args, [], [
      AC_MSG_ERROR([Internal error: Required named arguments are missing for [$1]. Missing arguments: 'm4_set_contents(required_named_args, [ ])'])
    ])
    m4_foreach([arg], m4_indir([m4_dquote]m4_set_listc([legal_named_args])), [
      m4_pushdef([ARG_][]arg, [])
      m4_set_add(defined_args, arg)
    ])
    m4_set_delete(legal_named_args)
    m4_set_delete(required_named_args)

    # Execute function body
    $4

    m4_foreach([arg], m4_indir([m4_dquote]m4_set_listc([defined_args])), [
      m4_popdef([ARG_][]arg)
    ])

    m4_set_delete(defined_args)
  ])
])

###############################################################################
# Assert that a programmatic condition holds. If not, exit with an error message.
# Check that a shell expression gives return code 0
#
# $1: The shell expression to evaluate
# $2: A message to describe the expression in case of failure
# $2: An message to print in case of failure [optional]
#
AC_DEFUN([UTIL_ASSERT_SHELL_TEST],
[
  ASSERTION_MSG="m4_normalize([$3])"
  if $1; then
    $ECHO Assertion failed: $2
    if test "x$3" != x; then
      $ECHO Assertion message: "$3"
    fi
    exit 1
  fi
])


###############################################################################
# Assert that a programmatic condition holds. If not, exit with an error message.
# Check that two strings are equal.
#
# $1: The actual string found
# $2: The expected string
# $3: An message to print in case of failure [optional]
#
AC_DEFUN([UTIL_ASSERT_STRING_EQUALS],
[
  UTIL_ASSERT_SHELL_TEST(
      [test "x[$1]" != "x[$2]"],
      [Actual value '[$1]' \("[$1]"\) did not match expected value '[$2]' \("[$2]"\)],
      $3)
])

###############################################################################
# Assert that a programmatic condition holds. If not, exit with an error message.
# Check that two strings not are equal.
#
# $1: The actual string found
# $2: The expected string
# $3: An message to print in case of failure [optional]
#
AC_DEFUN([UTIL_ASSERT_STRING_NOT_EQUALS],
[
  UTIL_ASSERT_SHELL_TEST(
      [test "x[$1]" = "x[$2]"],
      [Actual value '[$1]' \("[$1]"\) unexpectedly matched '[$2]' \("[$2]"\)],
      $3)
])

###############################################################################
# Assert that a programmatic condition holds. If not, exit with an error message.
# Check that the given expression evaluates to the string 'true'
#
# $1: The expression to evaluate
# $2: An message to print in case of failure [optional]
#
AC_DEFUN([UTIL_ASSERT_TRUE],
[
  UTIL_ASSERT_STRING_EQUALS($1, true, $3)
])

###############################################################################
# Assert that a programmatic condition holds. If not, exit with an error message.
# Check that the given expression does not evaluate to the string 'true'
#
# $1: The expression to evaluate
# $2: An message to print in case of failure [optional]
#
AC_DEFUN([UTIL_ASSERT_NOT_TRUE],
[
  UTIL_ASSERT_STRING_NOT_EQUALS($1, true, $3)
])

###############################################################################
# Check if a list of space-separated words are selected only from a list of
# space-separated legal words. Typical use is to see if a user-specified
# set of words is selected from a set of legal words.
#
# Sets the specified variable to list of non-matching (offending) words, or to
# the empty string if all words are matching the legal set.
#
# $1: result variable name
# $2: list of values to check
# $3: list of legal values
AC_DEFUN([UTIL_GET_NON_MATCHING_VALUES],
[
  # grep filter function inspired by a comment to http://stackoverflow.com/a/1617326
  # Notice that the original variant fails on SLES 10 and 11
  # Some grep versions (at least bsd) behaves strangely on the base case with
  # no legal_values, so make it explicit.
  values_to_check=`$ECHO $2 | $TR ' ' '\n'`
  legal_values=`$ECHO $3 | $TR ' ' '\n'`
  if test -z "$legal_values"; then
    $1="$2"
  else
    result=`$GREP -Fvx -- "$legal_values" <<< "$values_to_check" | $GREP -v '^$'`
    $1=${result//$'\n'/ }
  fi
])

###############################################################################
# Check if a list of space-separated words contains any word(s) from a list of
# space-separated illegal words. Typical use is to see if a user-specified
# set of words contains any from a set of illegal words.
#
# Sets the specified variable to list of matching illegal words, or to
# the empty string if no words are matching the illegal set.
#
# $1: result variable name
# $2: list of values to check
# $3: list of illegal values
AC_DEFUN([UTIL_GET_MATCHING_VALUES],
[
  # grep filter function inspired by a comment to http://stackoverflow.com/a/1617326
  # Notice that the original variant fails on SLES 10 and 11
  # Some grep versions (at least bsd) behaves strangely on the base case with
  # no legal_values, so make it explicit.
  values_to_check=`$ECHO $2 | $TR ' ' '\n'`
  illegal_values=`$ECHO $3 | $TR ' ' '\n'`
  if test -z "$illegal_values"; then
    $1=""
  else
    result=`$GREP -Fx -- "$illegal_values" <<< "$values_to_check" | $GREP -v '^$'`
    $1=${result//$'\n'/ }
  fi
])

###############################################################################
# Converts an ISO-8601 date/time string to a unix epoch timestamp. If no
# suitable conversion method was found, an empty string is returned.
#
# Sets the specified variable to the resulting list.
#
# $1: result variable name
# $2: input date/time string
AC_DEFUN([UTIL_GET_EPOCH_TIMESTAMP],
[
  if test "x$IS_GNU_DATE" = xyes; then
    # GNU date
    timestamp=$($DATE --utc --date=$2 +"%s" 2> /dev/null)
  else
    # BSD date
    timestamp=$($DATE -u -j -f "%F %T" "$2" "+%s" 2> /dev/null)
    if test "x$timestamp" = x; then
      # Perhaps the time was missing
      timestamp=$($DATE -u -j -f "%F %T" "$2 00:00:00" "+%s" 2> /dev/null)
      # If this did not work, we give up and return the empty string
    fi
  fi
  $1=$timestamp
])

###############################################################################
# Sort a space-separated list, and remove duplicates.
#
# Sets the specified variable to the resulting list.
#
# $1: result variable name
# $2: list of values to sort
AC_DEFUN([UTIL_SORT_LIST],
[
  values_to_sort=`$ECHO $2 | $TR ' ' '\n'`
  result=`$SORT -u <<< "$values_to_sort" | $GREP -v '^$'`
  $1=${result//$'\n'/ }
])

###############################################################################
# Test if $1 is a valid argument to $3 (often is $JAVA passed as $3)
# If so, then append $1 to $2 \
# Also set JVM_ARG_OK to true/false depending on outcome.
AC_DEFUN([UTIL_ADD_JVM_ARG_IF_OK],
[
  $ECHO "Check if jvm arg is ok: $1" >&AS_MESSAGE_LOG_FD
  $ECHO "Command: $3 $1 -version" >&AS_MESSAGE_LOG_FD
  OUTPUT=`$3 $1 $USER_BOOT_JDK_OPTIONS -version 2>&1`
  FOUND_WARN=`$ECHO "$OUTPUT" | $GREP -i warn`
  FOUND_VERSION=`$ECHO $OUTPUT | $GREP " version \""`
  if test "x$FOUND_VERSION" != x && test "x$FOUND_WARN" = x; then
    $2="[$]$2 $1"
    JVM_ARG_OK=true
  else
    $ECHO "Arg failed:" >&AS_MESSAGE_LOG_FD
    $ECHO "$OUTPUT" >&AS_MESSAGE_LOG_FD
    JVM_ARG_OK=false
  fi
])

###############################################################################
# Register a --with argument but mark it as deprecated
# $1: The name of the with argument to deprecate, not including --with-
AC_DEFUN([UTIL_DEPRECATED_ARG_WITH],
[
  AC_ARG_WITH($1, [AS_HELP_STRING([--with-$1],
      [Deprecated. Option is kept for backwards compatibility and is ignored])],
      [AC_MSG_WARN([Option --with-$1 is deprecated and will be ignored.])])
])

###############################################################################
# Register a --enable argument but mark it as deprecated
# $1: The name of the with argument to deprecate, not including --enable-
AC_DEFUN([UTIL_DEPRECATED_ARG_ENABLE],
[
  AC_ARG_ENABLE($1, [AS_HELP_STRING([--enable-$1],
      [Deprecated. Option is kept for backwards compatibility and is ignored])],
      [AC_MSG_WARN([Option --enable-$1 is deprecated and will be ignored.])])
])

###############################################################################
# Register an --enable-* argument as an alias for another argument.
# $1: The name of the enable argument for the new alias, not including --enable-
# $2: The full name of the argument of which to make this an alias, including
#     --enable- or --with-.
AC_DEFUN([UTIL_ALIASED_ARG_ENABLE],
[
  AC_ARG_ENABLE($1, [AS_HELP_STRING([--enable-$1], [alias for $2])], [
    # Use m4 to strip initial -- from target ($2), convert - to _, prefix enable_
    # to new alias name, and create a shell variable assignment,
    # e.g.: enable_old_style="$enable_new_alias"
    m4_translit(m4_bpatsubst($2, --), -, _)="$[enable_]m4_translit($1, -, _)"
  ])
])

###############################################################################
# Creates a command-line option using the --enable-* pattern. Will return a
# value of 'true' or 'false' in the RESULT variable, depending on whether the
# option was enabled or not by the user. The option can not be turned on if it
# is not available, as specified by AVAILABLE and/or CHECK_AVAILABLE.
#
# Arguments:
#   NAME: The base name of this option (i.e. what follows --enable-). Required.
#   RESULT: The name of the variable to set to the result. Defaults to
#     <NAME in uppercase>_ENABLED.
#   DEFAULT: The default value for this option. Can be true, false or auto.
#     Defaults to true.
#   AVAILABLE: If true, this option is allowed to be selected. Defaults to true.
#   DESC: A description of this option. Defaults to a generic and unhelpful
#     string.
#   DEFAULT_DESC: A message describing the default value, for the help. Defaults
#     to the literal value of DEFAULT.
#   CHECKING_MSG: The message to present to user when checking this option.
#     Defaults to a generic message.
#   CHECK_AVAILABLE: An optional code block to execute to determine if the
#     option should be available. Must set AVAILABLE to 'false' if not.
#   IF_GIVEN:  An optional code block to execute if the option was given on the
#     command line (regardless of the value).
#   IF_NOT_GIVEN:  An optional code block to execute if the option was not given
#     on the command line (regardless of the value).
#   IF_ENABLED:  An optional code block to execute if the option is turned on.
#   IF_DISABLED:  An optional code block to execute if the option is turned off.
#
UTIL_DEFUN_NAMED([UTIL_ARG_ENABLE],
    [*NAME RESULT DEFAULT AVAILABLE DESC DEFAULT_DESC CHECKING_MSG
    CHECK_AVAILABLE IF_GIVEN IF_NOT_GIVEN IF_ENABLED IF_DISABLED], [$@],
[
  ##########################
  # Part 1: Set up m4 macros
  ##########################

  # If DEFAULT is not specified, set it to 'true'.
  m4_define([ARG_DEFAULT], m4_if(ARG_DEFAULT, , true, ARG_DEFAULT))

  # If AVAILABLE is not specified, set it to 'true'.
  m4_define([ARG_AVAILABLE], m4_if(ARG_AVAILABLE, , true, ARG_AVAILABLE))

  # If DEFAULT_DESC is not specified, calculate it from DEFAULT.
  m4_define([ARG_DEFAULT_DESC], m4_if(ARG_DEFAULT_DESC, , m4_if(ARG_DEFAULT, true, enabled, m4_if(ARG_DEFAULT, false, disabled, ARG_DEFAULT)), ARG_DEFAULT_DESC))

  # If RESULT is not specified, set it to 'ARG_NAME[_ENABLED]'.
  m4_define([ARG_RESULT], m4_if(ARG_RESULT, , m4_translit(ARG_NAME, [a-z-], [A-Z_])[_ENABLED], ARG_RESULT))
  # Construct shell variable names for the option
  m4_define(ARG_OPTION, [enable_]m4_translit(ARG_NAME, [-], [_]))
  m4_define(ARG_GIVEN, m4_translit(ARG_NAME, [a-z-], [A-Z_])[_GIVEN])

  # If DESC is not specified, set it to a generic description.
  m4_define([ARG_DESC], m4_if(m4_quote(ARG_DESC), , [[Enable the ARG_NAME feature]], [m4_normalize(ARG_DESC)]))

  # If CHECKING_MSG is not specified, set it to a generic description.
  m4_define([ARG_CHECKING_MSG], m4_if(m4_quote(ARG_CHECKING_MSG), , [[for --enable-ARG_NAME]], [m4_normalize(ARG_CHECKING_MSG)]))

  # If the code blocks are not given, set them to the empty statements to avoid
  # tripping up bash.
  m4_if(ARG_CHECK_AVAILABLE, , [m4_define([ARG_CHECK_AVAILABLE], [:])])
  m4_if(ARG_IF_GIVEN, , [m4_define([ARG_IF_GIVEN], [:])])
  m4_if(ARG_IF_NOT_GIVEN, , [m4_define([ARG_IF_NOT_GIVEN], [:])])
  m4_if(ARG_IF_ENABLED, , [m4_define([ARG_IF_ENABLED], [:])])
  m4_if(ARG_IF_DISABLED, , [m4_define([ARG_IF_DISABLED], [:])])

  ##########################
  # Part 2: Set up autoconf shell code
  ##########################

  # Check that DEFAULT has a valid value
  if test "[x]ARG_DEFAULT" != xtrue && test "[x]ARG_DEFAULT" != xfalse && \
      test "[x]ARG_DEFAULT" != xauto ; then
    AC_MSG_ERROR([Internal error: Argument DEFAULT to [UTIL_ARG_ENABLE] can only be true, false or auto, was: 'ARG_DEFAULT'])
  fi

  # Check that AVAILABLE has a valid value
  if test "[x]ARG_AVAILABLE" != xtrue && test "[x]ARG_AVAILABLE" != xfalse; then
    AC_MSG_ERROR([Internal error: Argument AVAILABLE to [UTIL_ARG_ENABLE] can only be true or false, was: 'ARG_AVAILABLE'])
  fi

  AC_ARG_ENABLE(ARG_NAME, AS_HELP_STRING([--enable-]ARG_NAME,
      [ARG_DESC [ARG_DEFAULT_DESC]]), [ARG_GIVEN=true], [ARG_GIVEN=false])

  # Check if the option is available
  AVAILABLE=ARG_AVAILABLE
  # Run the available check block (if any), which can overwrite AVAILABLE.
  ARG_CHECK_AVAILABLE

  # Check if the option should be turned on
  AC_MSG_CHECKING(ARG_CHECKING_MSG)
  if test x$ARG_GIVEN = xfalse; then
    if test ARG_DEFAULT = auto; then
      # If not given, and default is auto, set it to true iff it's available.
      ARG_RESULT=$AVAILABLE
      REASON="from default 'auto'"
    else
      ARG_RESULT=ARG_DEFAULT
      REASON="default"
    fi
  else
    if test x$ARG_OPTION = xyes; then
      ARG_RESULT=true
      REASON="from command line"
    elif test x$ARG_OPTION = xno; then
      ARG_RESULT=false
      REASON="from command line"
    elif test x$ARG_OPTION = xauto; then
      if test ARG_DEFAULT = auto; then
        # If both given and default is auto, set it to true iff it's available.
        ARG_RESULT=$AVAILABLE
      else
        ARG_RESULT=ARG_DEFAULT
      fi
      REASON="from command line 'auto'"
    else
      AC_MSG_ERROR([Option [--enable-]ARG_NAME can only be 'yes', 'no' or 'auto'])
    fi
  fi

  if test x$ARG_RESULT = xtrue; then
    AC_MSG_RESULT([enabled, $REASON])
    if test x$AVAILABLE = xfalse; then
      AC_MSG_ERROR([Option [--enable-]ARG_NAME is not available])
    fi
  else
    AC_MSG_RESULT([disabled, $REASON])
  fi

  # Execute result payloads, if present
  if test x$ARG_GIVEN = xtrue; then
    ARG_IF_GIVEN
  else
    ARG_IF_NOT_GIVEN
  fi

  if test x$ARG_RESULT = xtrue; then
    ARG_IF_ENABLED
  else
    ARG_IF_DISABLED
  fi
])

###############################################################################
# Helper functions for ARG_WITH, to validate different types of argument

# Dispatcher to call the correct UTIL_CHECK_TYPE_* function depending on the ARG_TYPE
AC_DEFUN([UTIL_CHECK_TYPE],
[
  UTIL_CHECK_TYPE_$1($2)
])

AC_DEFUN([UTIL_CHECK_TYPE_string],
[
  # All strings always passes
])

AC_DEFUN([UTIL_CHECK_TYPE_integer],
[
  # Check that the argument is an integer
  # Additional [] needed to keep m4 from mangling shell constructs.
  [ if [[ ! "$1" =~ ^[0-9]+$ ]] ; then ]
    FAILURE="Not an integer: $1"
  fi
])

AC_DEFUN([UTIL_CHECK_TYPE_file],
[
  # Check that the argument is an existing file
  if test ! -f "$1" ; then
    FAILURE="File $1 does not exist or is not readable"
  fi
])

AC_DEFUN([UTIL_CHECK_TYPE_directory],
[
  # Check that the argument is an existing directory
  if test ! -d "$1" ; then
    FAILURE="Directory $1 does not exist or is not readable"
  fi

  if test "[x]ARG_CHECK_FOR_FILES" != x; then
    for file in ARG_CHECK_FOR_FILES; do
      found_files=$($ECHO $(ls $1/$file 2> /dev/null))
      if test "x$found_files" = x; then
        FAILURE="Directory $1 does not contain $file"
        break
      elif ! test -e "$found_files"; then
        FAILURE="Directory $1 contains multiple $file: $found_files"
        break
      fi
    done
  fi
])

AC_DEFUN([UTIL_CHECK_TYPE_literal],
[
  # Check if it contains a space between non-space characters
  # Additional [] needed to keep m4 from mangling shell constructs.
  [ if [[ "$1" =~ [^' ']' '+[^' '] ]] ; then ]
    FAILURE="Multiple words: $1"
  fi

  # Check that the selected variants are valid
  UTIL_GET_NON_MATCHING_VALUES(invalid_value, $1, \
      ARG_VALID_VALUES)
  if test "x$invalid_value" != x; then
    FAILURE="Invalid value: $invalid_value. Valid values are: ARG_VALID_VALUES"
  fi
])

AC_DEFUN([UTIL_CHECK_TYPE_multivalue],
[
  # We accept either space or comma as separator, but use space internally
  values=`$ECHO $1 | $SED -e 's/,/ /g'`

  # Check that the selected variants are valid
  UTIL_GET_NON_MATCHING_VALUES(invalid_value, $values, \
      ARG_VALID_VALUES)
  if test "x$invalid_value" != x; then
    FAILURE="Invalid value(s): $invalid_value. Valid values are: ARG_VALID_VALUES"
  fi

  # Update to version without comma
  ARG_RESULT=$($ECHO $values)
])

AC_DEFUN([UTIL_CHECK_TYPE_features],
[
  # We accept either space or comma as separator, but use space internally
  feature_list=`$ECHO $1 | $SED -e 's/,/ /g'`
  features_enabled=`$ECHO $feature_list | \
    $AWK '{ for (i=1; i<=NF; i++) if (!match($i, /^-.*/)) printf("%s ", $i) }'`
  features_disabled=`$ECHO $feature_list | \
    $AWK '{ for (i=1; i<=NF; i++) if (match($i, /^-.*/)) printf("%s ", substr($i, 2))}'`

  # Check that the selected features are valid
  UTIL_GET_NON_MATCHING_VALUES(invalid_features, $features_enabled \
        $features_disabled, ARG_VALID_VALUES)
  if test "x$invalid_features" != x; then
    FAILURE="Invalid feature(s): $invalid_features. Valid values are: ARG_VALID_VALUES"
  fi

  # Update to version without comma
  ARG_RESULT=$($ECHO $feature_list)
])

###############################################################################
# Creates a command-line option using the --with-* pattern. Will return a
# string in the RESULT variable with the option provided by the user, or the
# empty string if the --with-* option was not given. The option can not be given
# if it is not available, as specified by AVAILABLE and/or CHECK_AVAILABLE.
#
# Arguments:
#   NAME: The base name of this option (i.e. what follows --with-). Required.
#   TYPE: The type of the value. Can be one of "string", "integer", "file",
#     "directory", "literal", "multivalue" or "features". Required.
#   DEFAULT: The default value for this option. Can be any valid string.
#     Required.
#   OPTIONAL: If this feature can be disabled. Defaults to false. If true,
#     the feature can be disabled using --without-FOO, --with-FOO=no, or
#     --with-FOO=. Check the ENABLED_RESULT variable for the enabled/disabled
#     state.
#   RESULT: The name of the variable to set to the result. Defaults to
#     <NAME in uppercase>. Set to empty if ENABLED_RESULT is false.
#   ENABLED_DEFAULT: If the value is enabled by default. Defaults to false. Only
#     relevant if OPTIONAL is true.
#   ENABLED_RESULT: The name of the variable to set to the enabled/disabled
#     result state. Defaults to <NAME in uppercase>_ENABLED.
#   AVAILABLE: If true, this option is allowed to be selected. Defaults to true.
#   DESC: A description of this option. Defaults to a generic and unhelpful
#     string.
#   DEFAULT_DESC: A message describing the default value, for the help. Defaults
#     to the literal value of DEFAULT, or "<none>" if DEFAULT is empty.
#   CHECKING_MSG: The message to present to user when checking this option.
#     Defaults to a generic message.
#   CHECK_AVAILABLE: An optional code block to execute to determine if the
#     option should be available. Must set AVAILABLE to 'false' if not.
#   VALID_VALUES: A list of literals that are the allowed values. Only valid if
#     TYPE is "literal", "multivalue" or "features".
#   CHECK_VALUE: An optional code block to execute to determine if the value
#     is correct. Must set FAILURE to a non-empty string if not. This string
#     will be displayed. The value is given in $RESULT.
#   CHECK_FOR_FILES: A list of files to verify the presence for. Only valid if
#     TYPE is "directory". Paths are relative the directory given as value.
#     Wildcards are accepted. Exactly one matching file must be found, for each
#     listed file, or FAILURE is set.
#   IF_AUTO: An optional code block to execute if the value is "auto", either by
#     default or given by the command line. Must set RESULT to the calculated
#     value.
#   IF_GIVEN:  An optional code block to execute if the option was given on the
#     command line (regardless of the value).
#   IF_NOT_GIVEN:  An optional code block to execute if the option was not given
#     on the command line (regardless of the value).
#
UTIL_DEFUN_NAMED([UTIL_ARG_WITH],
    [*NAME *TYPE *DEFAULT OPTIONAL RESULT ENABLED_DEFAULT ENABLED_RESULT
    AVAILABLE DESC DEFAULT_DESC CHECKING_MSG CHECK_AVAILABLE VALID_VALUES
    CHECK_VALUE CHECK_FOR_FILES IF_AUTO IF_GIVEN IF_NOT_GIVEN], [$@],
[
  ##########################
  # Part 1: Set up m4 macros
  ##########################

  # If ENABLED_DEFAULT is not specified, set it to 'false'.
  m4_define([ARG_ENABLED_DEFAULT], m4_if(ARG_ENABLED_DEFAULT, , false, ARG_ENABLED_DEFAULT))

  # If AVAILABLE is not specified, set it to 'true'.
  m4_define([ARG_AVAILABLE], m4_if(ARG_AVAILABLE, , true, ARG_AVAILABLE))

  # If OPTIONAL is not specified, set it to 'false'.
  m4_define([ARG_OPTIONAL], m4_if(ARG_OPTIONAL, , false, ARG_OPTIONAL))

  # If DEFAULT_DESC is not specified, calculate it from DEFAULT.
  m4_define([ARG_DEFAULT_DESC], m4_if(ARG_DEFAULT_DESC, , m4_if(ARG_DEFAULT, , <none>, ARG_DEFAULT), ARG_DEFAULT_DESC))

  # If RESULT is not specified, set it to 'ARG_NAME'.
  m4_define([ARG_RESULT], m4_if(ARG_RESULT, , m4_translit(ARG_NAME, [a-z-], [A-Z_]), ARG_RESULT))

  # If ENABLED_RESULT is not specified, set it to 'ARG_NAME[_ENABLED]'.
  m4_define([ARG_ENABLED_RESULT], m4_if(ARG_ENABLED_RESULT, , m4_translit(ARG_NAME, [a-z-], [A-Z_])[_ENABLED], ARG_ENABLED_RESULT))

  # Construct shell variable names for the option
  m4_define(ARG_OPTION, [with_]m4_translit(ARG_NAME, [-], [_]))
  m4_define(ARG_GIVEN, m4_translit(ARG_NAME, [a-z-], [A-Z_])[_GIVEN])

  # If DESC is not specified, set it to a generic description.
  m4_define([ARG_DESC], m4_if(m4_quote(ARG_DESC), , [[Give a value for the ARG_NAME feature]], [m4_normalize(ARG_DESC)]))

  # If CHECKING_MSG is not specified, set it to a generic description.
  m4_define([ARG_CHECKING_MSG], m4_if(m4_quote(ARG_CHECKING_MSG), , [[for --with-ARG_NAME]], [m4_normalize(ARG_CHECKING_MSG)]))

  m4_define([ARG_HAS_AUTO_BLOCK], m4_if(ARG_IF_AUTO, , false, true))

  # If the code blocks are not given, set them to the empty statements to avoid
  # tripping up bash.
  m4_if(ARG_CHECK_AVAILABLE, , [m4_define([ARG_CHECK_AVAILABLE], [:])])
  m4_if(ARG_CHECK_VALUE, , [m4_define([ARG_CHECK_VALUE], [:])])
  m4_if(ARG_CHECK_FOR_FILES, , [m4_define([ARG_CHECK_FOR_FILES], [:])])
  m4_if(ARG_IF_AUTO, , [m4_define([ARG_IF_AUTO], [:])])
  m4_if(ARG_IF_GIVEN, , [m4_define([ARG_IF_GIVEN], [:])])
  m4_if(ARG_IF_NOT_GIVEN, , [m4_define([ARG_IF_NOT_GIVEN], [:])])

  ##########################
  # Part 2: Set up autoconf shell code
  ##########################

  # Check that OPTIONAL has a valid value
  if test "[x]ARG_OPTIONAL" != xtrue && test "[x]ARG_OPTIONAL" != xfalse ; then
    AC_MSG_ERROR([Internal error: Argument OPTIONAL to [UTIL_ARG_WITH] can only be true or false, was: 'ARG_OPTIONAL'])
  fi

  # Check that ENABLED_DEFAULT has a valid value
  if test "[x]ARG_ENABLED_DEFAULT" != xtrue && test "[x]ARG_ENABLED_DEFAULT" != xfalse ; then
    AC_MSG_ERROR([Internal error: Argument ENABLED_DEFAULT to [UTIL_ARG_WITH] can only be true or false, was: 'ARG_ENABLED_DEFAULT'])
  fi

  # Check that AVAILABLE has a valid value
  if test "[x]ARG_AVAILABLE" != xtrue && test "[x]ARG_AVAILABLE" != xfalse; then
    AC_MSG_ERROR([Internal error: Argument AVAILABLE to [UTIL_ARG_WITH] can only be true or false, was: 'ARG_AVAILABLE'])
  fi

  # Check that TYPE has a valid value
  # Need to assign since we can't expand ARG TYPE inside the m4 quoted if statement
  TEST_TYPE="ARG_TYPE"
  # Additional [] needed to keep m4 from mangling shell constructs.
  [ if [[ ! "$TEST_TYPE" =~ ^(string|integer|file|directory|literal|multivalue|features)$ ]] ; then ]
    AC_MSG_ERROR([Internal error: Argument TYPE to [UTIL_ARG_WITH] must be a valid type, was: 'ARG_TYPE'])
  fi

  AC_ARG_WITH(ARG_NAME, AS_HELP_STRING([--with-]ARG_NAME,
      [ARG_DESC [ARG_DEFAULT_DESC]]), [ARG_GIVEN=true], [ARG_GIVEN=false])

  # Check if the option is available
  AVAILABLE=ARG_AVAILABLE
  # Run the available check block (if any), which can overwrite AVAILABLE.
  ARG_CHECK_AVAILABLE

  # Check if the option should be turned on
  AC_MSG_CHECKING(ARG_CHECKING_MSG)

  if test x$AVAILABLE = xfalse; then
    ARG_RESULT="$ARG_OPTION"
    ARG_ENABLED_RESULT=false
    REASON="not available"
  else
    if test x$ARG_GIVEN = xfalse; then
      ARG_RESULT="ARG_DEFAULT"
      if test "[x]ARG_OPTIONAL" = xtrue; then
        ARG_ENABLED_RESULT=ARG_ENABLED_DEFAULT
      else
        ARG_ENABLED_RESULT=true
      fi
      REASON="default"

    else # ARG_GIVEN is true
      # Special treatment of "yes" and "no" for "--with-ARG" and "--without-ARG"
      if test "x$ARG_OPTION" = xyes || test "x$ARG_OPTION" = xno || test "x$ARG_OPTION" = x ; then
        if test "[x]ARG_OPTIONAL" = xfalse; then
          if test "x$ARG_OPTION" = x; then
            # If not optional, the empty string is a valid value
            ARG_RESULT=""
            ARG_ENABLED_RESULT=true
            REASON="from command line"
          else
            AC_MSG_RESULT([invalid])
            AC_MSG_ERROR([Option [--with-]ARG_NAME must have a specified value])
          fi
        else
          if test "x$ARG_OPTION" = xyes; then
            ARG_RESULT="ARG_DEFAULT"
            ARG_ENABLED_RESULT=true
            REASON="default as enabled from command line"
          else
            # For optional values, both --without-FOO and --with-FOO= disables
            ARG_RESULT=""
            ARG_ENABLED_RESULT=false
            REASON="from command line"
          fi
        fi
      else
        # The most common case -- the user gives a value for the option.
        ARG_RESULT="$ARG_OPTION"
        ARG_ENABLED_RESULT=true
        REASON="from command line"
      fi
    fi
  fi

  if test "x$ARG_ENABLED_RESULT" = xfalse; then
    if test "x$REASON" = "xnot available"; then
      AC_MSG_RESULT([<invalid>, $REASON])
      if test "x$ARG_RESULT" != "x" && test "x$ARG_RESULT" != "xno" ; then
        AC_MSG_WARN([Option [--with-]ARG_NAME is not available for this configuration])
      fi
    else
      AC_MSG_RESULT([<disabled>, $REASON])
    fi
    ARG_RESULT=""
  else
    if test [x]ARG_HAS_AUTO_BLOCK = xtrue && test "x$ARG_RESULT" = xauto; then
      # Execute "auto" payload
      ARG_IF_AUTO

      ARG_RESULT="$RESULT"
      REASON="$REASON (calculated from 'auto')"
    fi

    if test "x$ARG_RESULT" = x; then
      AC_MSG_RESULT([<none>, $REASON])
    else
      AC_MSG_RESULT([$ARG_RESULT, $REASON])
    fi
  fi

  # Verify value
  # First use our dispatcher to verify that type requirements are satisfied
  UTIL_CHECK_TYPE(ARG_TYPE, $ARG_RESULT)

  if test "x$FAILURE" = x; then
    # Execute custom verification payload, if present
    RESULT="$ARG_RESULT"

    ARG_CHECK_VALUE

    ARG_RESULT="$RESULT"
  fi

  if test "x$FAILURE" != x; then
    AC_MSG_NOTICE([Invalid value for [--with-]ARG_NAME: "$ARG_RESULT"])
    AC_MSG_NOTICE([$FAILURE])
    AC_MSG_ERROR([Cannot continue])
  fi

  # Execute result payloads, if present
  if test x$ARG_GIVEN = xtrue; then
    ARG_IF_GIVEN
  else
    ARG_IF_NOT_GIVEN
  fi
])

###############################################################################
# Helper functions for CHECK_VALUE in ARG_WITH.
AC_DEFUN([UTIL_CHECK_STRING_NON_EMPTY],
[
  if test "x$RESULT" = "x"; then
    FAILURE="Value cannot be empty"
  fi
])
