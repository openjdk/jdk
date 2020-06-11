#!/bin/bash

#
# Complete testing of jpackage platform-specific packaging.
#
# The script does the following:
# 1. Create packages.
# 2. Install created packages.
# 3. Verifies packages are installed.
# 4. Uninstall created packages.
# 5. Verifies packages are uninstalled.
#
# For the list of accepted command line arguments see `run_tests.sh` script.
#

# Fail fast
set -e; set -o pipefail;

# Script debug
dry_run=${JPACKAGE_TEST_DRY_RUN}

# Default directory where jpackage should write bundle files
output_dir=~/jpackage_bundles


set_args ()
{
  args=()
  local arg_is_output_dir=
  local arg_is_mode=
  local output_dir_set=
  local with_append_actions=yes
  for arg in "$@"; do
    if [ "$arg" == "-o" ]; then
      arg_is_output_dir=yes
      output_dir_set=yes
    elif [ "$arg" == "-m" ]; then
      arg_is_mode=yes
      continue
    elif [ "$arg" == '--' ]; then
      append_actions
      with_append_actions=
      continue
    elif ! case "$arg" in -Djpackage.test.action=*) false;; esac; then
      continue
    elif [ -n "$arg_is_output_dir" ]; then
      arg_is_output_dir=
      output_dir="$arg"
    elif [ -n "$arg_is_mode" ]; then
      arg_is_mode=
      continue
    fi

    args+=( "$arg" )
  done
  [ -n "$output_dir_set" ] || args=( -o "$output_dir" "${args[@]}" )
  [ -z "$with_append_actions" ] || append_actions
}


append_actions ()
{
  args+=( '--' '-Djpackage.test.action=create,install,verify-install,uninstall,verify-uninstall' )
}


exec_command ()
{
  if [ -n "$dry_run" ]; then
    echo "$@"
  else
    eval "$@"
  fi
}

set_args "$@"
basedir="$(dirname $0)"
exec_command ${SHELL} "$basedir/run_tests.sh" -m create "${args[@]}"
