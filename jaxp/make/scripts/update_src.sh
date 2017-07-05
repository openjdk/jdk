#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

# This script was used to copy the former drop source bundle source into
#   the repository. Exists as a form of documentation.

curdir="`(cd . && pwd)`"

# Whitespace normalizer script is in the top repository.
normalizer="perl ${curdir}/../make/scripts/normalizer.pl"

# Locations for bundle and root of source tree
tmp=/tmp
srcroot=${curdir}/src
mkdir -p ${srcroot}

# Bundle information
drops_dir="/java/devtools/share/jdk8-drops"
url1="http://download.java.net/jaxp/1.4.5"
bundle1="jaxp145_01.zip"
srcdir1="${srcroot}"

# Function to get a bundle and explode it and normalize the source files.
getBundle() # drops_dir url bundlename bundledestdir srcrootdir
{
  # Get the bundle from drops_dir or downloaded
  mkdir -p $4
  rm -f $4/$3
  if [ -f $1/$3 ] ; then
    echo "Copy over bundle: $1/$3"
    cp $1/$3 $4
  else
    echo "Downloading bundle: $2/$3"
    (cd $4 && wget $2/$3)
  fi
  # Fail if it does not exist
  if [ ! -f $4/$3 ] ; then
    echo "ERROR: Could not get $3"
    exit 1
  fi
  # Wipe it out completely
  echo "Cleaning up $5"
  rm -f -r $5
  mkdir -p $5
  echo "Unzipping $4/$3"
  ( cd $5 && unzip -q $4/$3 && mv src/* . && rmdir src && rm LICENSE )
  # Run whitespace normalizer
  echo "Normalizing the sources in $5"
  ( cd $5 && ${normalizer} . )
  # Delete the bundle and leftover files
  rm -f $4/$3 $5/filelist
}

# Process the bundles.
getBundle "${drops_dir}" "${url1}" "${bundle1}" ${tmp} ${srcdir1}
echo "Completed bundle extraction."
echo " "

# Appropriate Mercurial commands needed to run: 
echo "Run: hg addremove src"
echo "Run: ksh ../make/scripts/webrev.ksh -N -o ${HOME}/webrev"
echo "Get reviewer, get CR, then..."
echo "Run:  hg commit"

