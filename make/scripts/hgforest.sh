#!/bin/sh

#
# Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

# Shell script for a fast parallel forest command

tmp=/tmp/forest.$$
rm -f -r ${tmp}
mkdir -p ${tmp}

# Remove tmp area on A. B. Normal termination
trap 'rm -f -r ${tmp}' KILL
trap 'rm -f -r ${tmp}' EXIT

# Only look in specific locations for possible forests (avoids long searches)
pull_default=""
if [ "$1" = "clone" -o "$1" = "fclone" ] ; then
  subrepos="corba jaxp jaxws langtools jdk hotspot"
  if [ -f .hg/hgrc ] ; then
    pull_default=`hg paths default`
  fi
  if [ "${pull_default}" = "" ] ; then
    echo "ERROR: Need initial clone with 'hg paths default' defined"
    exit 1
  fi
  repos=""
  for i in ${subrepos} ; do
    if [ ! -f ${i}/.hg/hgrc ] ; then
      repos="${repos} ${i}"
    fi
  done
  at_a_time=2
else
  hgdirs=`ls -d ./.hg ./*/.hg ./*/*/.hg ./*/*/*/.hg ./*/*/*/*/.hg 2>/dev/null`
  # Derive repository names from the .hg directory locations
  repos=""
  for i in ${hgdirs} ; do
    repos="${repos} `echo ${i} | sed -e 's@/.hg$@@'`"
  done
  at_a_time=8
fi

# Any repos to deal with?
if [ "${repos}" = "" ] ; then
  echo "No repositories to process."
  exit
fi

# Echo out what repositories we will process
echo "# Repos: ${repos}"

# Run the supplied command on all repos in parallel, save output until end
n=0
for i in ${repos} ; do
  echo "Starting on ${i}"
  n=`expr ${n} '+' 1`
  (
    (
      if [ "$1" = "clone" -o "$1" = "fclone" ] ; then
        cline="hg $* ${pull_default}/${i} ${i}"
        echo "# ${cline}"
        ( eval "${cline}" )
      else
        cline="hg $*"
        echo "# cd ${i} && ${cline}"
        ( cd ${i} && eval "${cline}" )
      fi
      echo "# exit code $?"
    ) > ${tmp}/repo.${n} 2>&1 ; cat ${tmp}/repo.${n} ) &
  if [ `expr ${n} '%' ${at_a_time}` -eq 0 ] ; then
    sleep 5
  fi
done

# Wait for all hg commands to complete
wait

# Cleanup
rm -f -r ${tmp}

# Terminate with exit 0 all the time (hard to know when to say "failed")
exit 0

