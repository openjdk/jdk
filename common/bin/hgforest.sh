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

# Python always buffers stdout significantly, thus we will not see any output from hg clone jdk,
# until a lot of time has passed! By passing -u to python, we get incremental updates
# on stdout. Much nicer.
whichhg="`which hg 2> /dev/null | grep -v '^no hg in'`"

if [ "${whichhg}" = "" ] ; then
  echo Cannot find hg!
  exit 1
fi

if [ "" = "$command" ] ; then
  echo No command to hg supplied!
  exit 1
fi

has_hash_bang="`head -n 1 "${whichhg}" | cut -b 1-2`"
python=""
bpython=""

if [ "#!" = "$has_hash_bang" ] ; then
   python="`head -n 1 ${whichhg} | cut -b 3- | sed -e 's/^[ \t]*//;s/[ \t]*$//'`"
   bpython="`basename "$python"`"
fi

if [ -x "$python" -a ! -d "$python" -a "`${python} -V 2>&1 | cut -f 1 -d ' '`" = "Python" ] ; then
  hg="${python} -u ${whichhg}"
else
  echo Cannot find python from hg launcher. Running plain hg, which probably has buffered stdout.
  hg="hg"
fi

# Clean out the temporary directory that stores the pid files.
tmp=/tmp/forest.$$
rm -f -r ${tmp}
mkdir -p ${tmp}

safe_interrupt () {
  if [ -d ${tmp} ]; then
    if [ "`ls ${tmp}/*.pid`" != "" ]; then
      echo "Waiting for processes ( `cat ${tmp}/*.pid | tr '\n' ' '`) to terminate nicely!"
      sleep 1
      # Pipe stderr to dev/null to silence kill, that complains when trying to kill
      # a subprocess that has already exited.
      kill -TERM `cat ${tmp}/*.pid | tr '\n' ' '` 2> /dev/null
      wait
      echo Interrupt complete!
    fi
  fi
  rm -f -r ${tmp}
  exit 1
}

nice_exit () {
  if [ -d ${tmp} ]; then
    if [ "`ls ${tmp}`" != "" ]; then
      wait
    fi
  fi
  rm -f -r ${tmp}
}

trap 'safe_interrupt' INT QUIT
trap 'nice_exit' EXIT

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
    exit
  fi
else
  hgdirs=`ls -d ./.hg ./*/.hg ./*/*/.hg ./*/*/*/.hg ./*/*/*/*/.hg 2>/dev/null`
  # Derive repository names from the .hg directory locations
  for i in ${hgdirs} ; do
    repos="${repos} `echo ${i} | sed -e 's@/.hg$@@'`"
  done
  for i in ${repos} ; do
    if [ -h ${i}/.hg/store/lock -o -f ${i}/.hg/store/lock ] ; then
      locked="${i} ${locked}"
    fi
  done
  at_a_time=8
  # Any repos to deal with?
  if [ "${repos}" = "" ] ; then
    echo "No repositories to process."
    exit
  fi
  if [ "${locked}" != "" ] ; then
    echo "These repositories are locked: ${locked}"
    exit
  fi
fi

# Echo out what repositories we do a command on.
echo "# Repositories: ${repos} ${repos_extra}"
echo

# Run the supplied command on all repos in parallel.
n=0
for i in ${repos} ${repos_extra} ; do
  n=`expr ${n} '+' 1`
  repopidfile=`echo ${i} | sed -e 's@./@@' -e 's@/@_@g'`
  reponame=`echo ${i} | sed -e :a -e 's/^.\{1,20\}$/ &/;ta'`
  pull_base="${pull_default}"
  for j in $repos_extra ; do
      if [ "$i" = "$j" ] ; then
          pull_base="${pull_extra}"
      fi
  done
  (
    (
      if [ "${command}" = "clone" -o "${command}" = "fclone" ] ; then
        pull_newrepo="`echo ${pull_base}/${i} | sed -e 's@\([^:]/\)//*@\1@g'`"
        echo ${hg} clone ${pull_newrepo} ${i}
        path="`dirname ${i}`"
        if [ "${path}" != "." ] ; then
          times=0
          while [ ! -d "${path}" ]   ## nested repo, ensure containing dir exists
          do
            times=`expr ${times} '+' 1`
            if [ `expr ${times} '%' 10` -eq 0 ] ; then
              echo ${path} still not created, waiting...
            fi
            sleep 5
          done
        fi
        (${hg} clone ${pull_newrepo} ${i}; echo "$?" > ${tmp}/${repopidfile}.pid.rc )&
      else
        echo "cd ${i} && ${hg} $*"
        cd ${i} && (${hg} "$@"; echo "$?" > ${tmp}/${repopidfile}.pid.rc )&
      fi
      echo $! > ${tmp}/${repopidfile}.pid
    ) 2>&1 | sed -e "s@^@${reponame}:   @") &

  if [ `expr ${n} '%' ${at_a_time}` -eq 0 ] ; then
    sleep 2
    echo Waiting 5 secs before spawning next background command.
    sleep 3
  fi
done
# Wait for all hg commands to complete
wait

# Terminate with exit 0 only if all subprocesses were successful
ec=0
if [ -d ${tmp} ]; then
  for rc in ${tmp}/*.pid.rc ; do
    exit_code=`cat ${rc} | tr -d ' \n\r'`
    if [ "${exit_code}" != "0" ] ; then
      echo "WARNING: ${rc} exited abnormally."
      ec=1
    fi
  done
fi
exit ${ec}
