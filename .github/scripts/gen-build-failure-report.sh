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
BUILD_DIR="$(ls -d build/*)"

# Send signal to the do-build action that we failed
touch "$BUILD_DIR/build-failure"

(
  echo '### :boom: Build failure summary'
  echo ''
  echo 'The build failed. Here follows the failure summary from the build.'
  echo '<details><summary><b>View build failure summary</b></summary>'
  echo ''
  echo '```'
  if [[ -f "$BUILD_DIR/make-support/failure-summary.log" ]]; then
    cat "$BUILD_DIR/make-support/failure-summary.log"
  else
    echo "Failure summary ($BUILD_DIR/make-support/failure-summary.log) not found"
  fi
  echo '```'
  echo '</details>'
  echo ''

  echo ''
  echo ':arrow_right: To see the entire test log, click the job in the list to the left. To download logs, see the `failure-logs` [artifact above](#artifacts).'
) >> $GITHUB_STEP_SUMMARY
