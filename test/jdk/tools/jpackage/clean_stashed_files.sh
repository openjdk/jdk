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
# Filters output produced by running jpackage with "jpackage.test.stash-root=<PATH>" property.
#

set -e
set -o pipefail


declare -a stash_dirs
if [ $# -eq 0 ]; then
  stash_dirs=(${0%/*})
else
  stash_dirs=($@)
fi


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


winActions='
'
winMsiActions='
  winMsiRmDotPackage
  winMsiFilterAppImageListing
  winMsiFilterUiWxf
  winMsiFilterBundleWxf
'
winAllActions="${winActions} ${winMsiActions}"

macActions='
  macFormatInfoPlist
  macFormatEntitlements
'
macPkgActions='
  macPkgFormatCplPlist
'
macDmgActions='
  macDmgFilterScpt
'
macAllActions="${macActions} ${macDmgActions} ${macPkgActions}"

linuxActions=
linuxRpmActions=
linuxDebActions=
linuxAllActions="${linuxActions} ${linuxRpmActions} ${linuxDebActions}"

actions='
  rmPostImageScript
  sortAppImageListing
'
allActions="${actions} ${winAllActions} ${macAllActions} ${linuxAllActions}"


rmPostImageScript() {
  # Remove post-image scripts
  find "$stash_dir" '(' -name '*-post-image.sh' -o -name '*-post-image.wsf' ')' -type f -exec rm -f {} \;
}

sortAppImageListing() {
  # Sort app image listings
  find "$stash_dir" -name 'app-image-listing.txt' -type f -exec sort -o {} {} \;
}


#
# MAC:
#

macFormatInfoPlist() {
  # Info.plist
  # - Format
  find "$stash_dir" -name 'Info.plist' -type f -exec xmllint --output '{}' --format '{}' \;
}

macFormatEntitlements() {
  # *.entitlements
  # - Format
  find "$stash_dir" -name '*.entitlements' -type f -exec xmllint --output '{}' --format '{}' \;
}

#
# DMG:
#

macDmgFilterScpt() {
  # *.scpt
  #  - Trim random absolute temp path
  #  - Replace "/dmg-workdir/" (new) with "/images/" (old)
  find "$stash_dir" -name '*.scpt' -type f | xargs -I {} sed $sed_inplace_option \
      -e 's|"/.*/jdk.jpackage[0-9]\{1,\}/|"/jdk.jpackage/|' \
      -e 's|"file:///.*/jdk.jpackage[0-9]\{1,\}/|"file:///jdk.jpackage/|' \
      -e 's|/dmg-workdir/|/images/|' \
      '{}'
}

#
# PKG:
#

macPkgFormatCplPlist() {
  # cpl.plist:
  # - Format and strip <!DOCTYPE ...> (old)
  find "$stash_dir" -name 'cpl.plist' -type f -exec xmllint --output '{}' --dropdtd --format '{}' \;
}


#
# WIN:
#

#
# MSI:
#

winMsiRmDotPackage() {
  # .package
  # - Strip it. Old jpackage created this file in the app image.
  #   New jpackage creates it in the "config" directory.
  #   Value is verified in tests, can ignore it here.
  find "$stash_dir" -name '.package' -type f -exec rm -f {} \;
}

winMsiFilterAppImageListing() {
  # app-image-listing.txt:
  # - Remove "app\.jpackage.xml" and "app\.package" entries. The new jpackage doesn't create them in the app image
  find "$stash_dir" -path '*msi/*/app-image-listing.txt' -type f | xargs -I {} sed $sed_inplace_option \
      -e '/^app\\\.package[\r]\{0,\}$/d' \
      -e '/^app\\\.jpackage\.xml[\r]\{0,\}$/d' \
      '{}'
}

winMsiFilterUiWxf() {
  # ui.wxf:
  # - Order of namespaces is undefined, strip them for simplicity
  find "$stash_dir" -name 'ui.wxf' -type f | xargs -I {} sed $sed_inplace_option \
      -e 's|^<Wix[^>]*>|<Wix>|' \
      '{}'
}

winMsiFilterBundleWxf() {
  # bundle.wxf:
  # - Order of namespaces is undefined, strip them for simplicity (same as with ui.wxf)
  # - Trim paths down to file names in "Source" attributes.
  #   They all are different because the new jpackage doesn't copy app images.
  # - Order of files and directories is undefined, sort xml.
  #   It turns the file into garbage, but all that matters is that the
  #   old and the new jpackage variants should produce the same "garbage"
  find "$stash_dir" -name 'bundle.wxf' -type f | xargs -I {} sed $sed_inplace_option \
      -e 's|^<Wix[^>]*>|<Wix>|' \
      -e 's|Source=".*\\\([^\\]\{1,\}\)"|Source="\1"|' \
      -e 's|Source="\\jdk.jpackage\\images\\win-msi.image\\.*\\app\\\.package"|Source="\\jdk.jpackage\\config\\\.package"|' \
      '{}'
  find "$stash_dir" -name 'bundle.wxf' -type f -exec sort -o {} {} \;
}


for stash_dir in "${stash_dirs[@]}"; do
  printf "In %s:\n" "$stash_dir"
  for a in ${allActions}; do
    echo "  $a..."
    $a
  done
done
