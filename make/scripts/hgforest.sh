#!/bin/sh

#
# Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
command="$1"
pull_extra_base="$2"

tmp=/tmp/forest.$$
rm -f -r ${tmp}
mkdir -p ${tmp}

# Remove tmp area on A. B. Normal termination
trap 'rm -f -r ${tmp}' KILL
trap 'rm -f -r ${tmp}' EXIT

# Only look in specific locations for possible forests (avoids long searches)
pull_default=""
repos=""
repos_extra=""
if [ "${command}" = "clone" -o "${command}" = "fclone" ] ; then
  subrepos="corba jaxp jaxws langtools jdk hotspot nashorn"
  if [ -f .hg/hgrc ] ; then
    pull_default=`hg paths default`
    if [ "${pull_default}" = "" ] ; then
      echo "ERROR: Need initial clone with 'hg paths default' defined"
      exit 1
    fi
  fi
  if [ "${pull_default}" = "" ] ; then
    echo "ERROR: Need initial repository to use this script"
    exit 1
  fi
  for i in ${subrepos} ; do
    if [ ! -f ${i}/.hg/hgrc ] ; then
      repos="${repos} ${i}"
    fi
  done
  if [ "${pull_extra_base}" != "" ] ; then
    subrepos_extra="jdk/src/closed jdk/make/closed jdk/test/closed hotspot/make/closed hotspot/src/closed hotspot/test/closed deploy install sponsors pubs"
    pull_default_tail=`echo ${pull_default} | sed -e 's@^.*://[^/]*/\(.*\)@\1@'`
    pull_extra="${pull_extra_base}/${pull_default_tail}"
    for i in ${subrepos_extra} ; do
      if [ ! -f ${i}/.hg/hgrc ] ; then
        repos_extra="${repos_extra} ${i}"
      fi
    done
  fi
  at_a_time=2
  # Any repos to deal with?
  if [ "${repos}" = "" -a "${repos_extra}" = "" ] ; then
    echo "No repositories to clone."
    exit
  fi
else
  hgdirs=`ls -d ./.hg ./*/.hg ./*/*/.hg ./*/*/*/.hg ./*/*/*/*/.hg 2>/dev/null`
  # Derive repository names from the .hg directory locations
  for i in ${hgdirs} ; do
    repos="${repos} `echo ${i} | sed -e 's@/.hg$@@'`"
  done
  at_a_time=8
  # Any repos to deal with?
  if [ "${repos}" = "" ] ; then
    echo "No repositories to process."
    exit
  fi
fi

# Echo out what repositories we will clone
echo "# Repos: ${repos} ${repos_extra}"

# Run the supplied command on all repos in parallel, save output until end
n=0
for i in ${repos} ; do
  echo "Starting on ${i}"
  n=`expr ${n} '+' 1`
  (
    (
      if [ "${command}" = "clone" -o "${command}" = "fclone" ] ; then
        pull_newrepo="`echo ${pull_default}/${i} | sed -e 's@\([^:]/\)//*@\1@g'`"
        cline="hg clone ${pull_newrepo} ${i}"
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

if [ "${repos_extra}" != "" ] ; then
  for i in ${repos_extra} ; do
    echo "Starting on ${i}"
    n=`expr ${n} '+' 1`
    (
      (
          pull_newextrarepo="`echo ${pull_extra}/${i} | sed -e 's@\([^:]/\)//*@\1@g'`"
          cline="hg clone ${pull_newextrarepo} ${i}"
          echo "# ${cline}"
          ( eval "${cline}" )
        echo "# exit code $?"
      ) > ${tmp}/repo.${n} 2>&1 ; cat ${tmp}/repo.${n} ) &
    if [ `expr ${n} '%' ${at_a_time}` -eq 0 ] ; then
      sleep 5
    fi
  done
  # Wait for all hg commands to complete
  wait
fi

# Cleanup
rm -f -r ${tmp}

# Terminate with exit 0 all the time (hard to know when to say "failed")
exit 0

