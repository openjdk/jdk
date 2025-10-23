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

      # Strip variable part of temporary directory name `jdk.jpackage.test217379316521032539`
      -e 's|\([\/]\)jdk\.jpackage\.test[0-9]\{1,\}\b|\1jdk.jpackage.test|g'

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

      # Whipe out entire output of /usr/bin/hdiutil command.
      # It is of little to no interest and contains too many variable parts to deal with individually.
      -e '/^Running \/usr\/bin\/hdiutil/,/^Returned:/{
            //,/^Output:/!d
          }'

      # Zip stack traces.
      -e $'/^\tat /{
            :a
            g
            N
            s/.*\\n//
            /^\tat /ba
            s/\\(^\t... \\)[0-9]\\{1,\\}\\( more\\)/\\1N\\2/
            s/\(.*\)/\tat <stacktrace>\\n\\1/
            P
            D
          }'

      # Convert PID value in `taskkill /F /PID 5640`
      -e 's|taskkill /F /PID [0-9]\{1,\}|taskkill /F /PID <pid>|'

      # Convert PID value in `The process with PID 5640 has been terminated`
      -e 's|\(The process with PID \)[0-9]\{1,\}\( has been terminated\)|\1<pid>\2|'

      # Convert timeout value in `Check timeout value 57182ms is positive`
      -e 's|\(Check timeout value \)[0-9]\{1,\}\(ms is positive\)|\1<timeout>\2|'

      # Convert variable part of /usr/bin/osascript output `jdk.jpackage/config/SigningRuntimeImagePackageTest-dmg-setup.scpt:455:497: execution error: Finder got an error: Can’t set 1 to icon view. (-10006)`
      -e 's|\(-dmg-setup.scpt:\)[0-9]\{1,\}:[0-9]\{1,\}\(: execution error: \)|\1<N:M>\2|'

      # Use the same name for all exceptions.
      -e 's|[^ ]\{1,\}\.[^ ]\{1,\}\Exception:|<Exception>:|g'
      -e 's|[^ ]\{1,\}\.[^ ]\{1,\}\ExceptionBox:|<Exception>:|g'
  )

  sed $sed_inplace_option "$1" "${expressions[@]}"
}


for f in "$@"; do
  filterFile "$f"
done
