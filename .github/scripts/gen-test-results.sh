#!/bin/bash
#
# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

GITHUB_STEP_SUMMARY="$1"

test_suite_name=$(cat build/run-test-prebuilt/test-support/test-last-ids.txt)
results_dir=build/run-test-prebuilt/test-results/$test_suite_name/text
report_dir=build/run-test-prebuilt/test-support/$test_suite_name

failures=$(sed -E -e 's/(.*)\.(java|sh)/\1/' -e '/^#/d' $results_dir/newfailures.txt 2> /dev/null || true)
errors=$(sed -E -e 's/(.*)\.(java|sh)/\1/' -e '/^#/d' $results_dir/other_errors.txt 2> /dev/null || true)

if [[ "$failures" = "" && "$errors" = "" ]]; then
  # If we have nothing to report, exit this step now
  exit 0
fi

echo "### Test output for failed tests" >> $GITHUB_STEP_SUMMARY
for test in $failures $errors; do
  anchor="$(echo "$test" | tr [A-Z/] [a-z_])"
  base_path="$(echo "$test" | tr '#' '_')"
  report_file="$report_dir/$base_path.jtr"
  hs_err_files=$(ls $report_dir/$base_path/hs_err*.log 2> /dev/null || true)
  echo "####  <a id="$anchor">$test"

  echo '<details><summary>View test results</summary>'
  echo ''
  echo '```'
  if [[ -f "$report_file" ]]; then
    cat "$report_file"
  else
    echo "Error: Result file $report_file not found"
  fi
  echo '```'
  echo '</details>'
  echo ''

  if [[ "$hs_err_files" != "" ]]; then
    echo '<details><summary>View HotSpot error log</summary>'
    echo ''
    for hs_err in $hs_err_files; do
      echo '```'
      echo "$hs_err:"
      echo ''
      cat "$hs_err"
      echo '```'
    done

    echo '</details>'
    echo ''
  fi

done >> $GITHUB_STEP_SUMMARY

# With many failures, the summary can easily exceed 1024 kB, the limit set by Github
# Trim it down if so.
summary_size=$(wc -c < $GITHUB_STEP_SUMMARY)
if [[ $summary_size -gt 1000000 ]]; then
  # Trim to below 1024 kB, and cut off after the last detail group
  head -c 1000000 $GITHUB_STEP_SUMMARY | tac | sed -n -e '/<\/details>/,$ p' | tac > $GITHUB_STEP_SUMMARY.tmp
  mv $GITHUB_STEP_SUMMARY.tmp $GITHUB_STEP_SUMMARY
  (
    echo ''
    echo ':x: **WARNING: Summary is too large and has been truncated.**'
    echo ''
  )  >> $GITHUB_STEP_SUMMARY
fi

echo ':arrow_right: To see the entire test log, click the job in the list to the left.'  >> $GITHUB_STEP_SUMMARY
