#!/bin/bash

# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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
# Filters output produced by running jpackage test(s).
#

set -eu
set -o pipefail


sed_inplace_option=-i
sed_version_string=$(sed --version 2>&1 | head -1 || true)
if [ "${sed_version_string#sed (GNU sed)}" != "$sed_version_string" ]; then
  # GNU sed, the default
  :
elif [ "${sed_version_string#sed: illegal option}" != "$sed_version_string" ]; then
  # Macos sed
  sed_inplace_option="-i ''"
else
  echo 'WARNING: Unknown sed variant, assume it is GNU compatible'
fi


filterFile () {
  local expressions=(
      # Strip leading log message timestamp `[19:33:44.713] `
      -e 's/^\[[0-9]\{2\}:[0-9]\{2\}:[0-9]\{2\}\.[0-9]\{3\}\] //'

      # Strip log message timestamps `[19:33:44.713]`
      -e 's/\[[0-9]\{2\}:[0-9]\{2\}:[0-9]\{2\}\.[0-9]\{3\}\]//g'

      # Convert variable part of R/O directory path timestamp `#2025-07-24T16:38:13.3589878Z`
      -e 's/#[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}T[0-9]\{2\}:[0-9]\{2\}:[0-9]\{2\}\.[0-9]\{1,\}Z/#<ts>Z/'

      # Strip variable part of temporary directory name `jdk.jpackage5060841750457404688`
      -e 's|\([\/]\)jdk\.jpackage[0-9]\{1,\}\b|\1jdk.jpackage|g'

      # Convert PID value `[PID: 131561]`
      -e 's/\[PID: [0-9]\{1,\}\]/[PID: <pid>]/'

      # Strip a warning message `Windows Defender may prevent jpackage from functioning`
      -e '/Windows Defender may prevent jpackage from functioning/d'

      # Convert variable part of test output directory `out-6268`
      -e 's|\bout-[0-9]\{1,\}\b|out-N|g'

      # Convert variable part of test summary `[       OK ] IconTest(AppImage, ResourceDirIcon, DefaultIcon).test; checks=39`
      -e 's/^\(.*\bchecks=\)[0-9]\{1,\}\(\r\{0,1\}\)$/\1N\2/'

      # Convert variable part of ldd output `libdl.so.2 => /lib64/libdl.so.2 (0x00007fbf63c81000)`
      -e 's/(0x[[:xdigit:]]\{1,\})$/(0xHEX)/'

      # Convert variable part of rpmbuild output `Executing(%build): /bin/sh -e /var/tmp/rpm-tmp.CMO6a9`
      -e 's|/rpm-tmp\...*$|/rpm-tmp.V|'

      # Convert variable part of stack trace entry `at jdk.jpackage.test.JPackageCommand.execute(JPackageCommand.java:863)`
      -e 's/^\(.*\b\.java:\)[0-9]\{1,\}\()\r\{0,1\}\)$/\1N\2/'
  )

  sed $sed_inplace_option "$1" "${expressions[@]}"
}


for f in "$@"; do
  filterFile "$f"
done
