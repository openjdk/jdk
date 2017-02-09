#!/bin/sh
#
# Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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

# Shell script for a fast parallel forest/trees command

usage() {
      echo "usage: $0 [-h|--help] [-q|--quiet] [-v|--verbose] [-s|--sequential] [--] <command> [commands...]" > ${status_output}
      echo "command format : mercurial-command [ "jdk" ] [ extra-url ]"
      echo "command option: jdk : used only with clone command to request just the extra repos for JDK-only builds"
      echo "command option : extra-url : server hosting the extra repositories"
      echo "Environment variables which modify behaviour:"
      echo "   HGFOREST_QUIET      : (boolean) If 'true' then standard output is redirected to /dev/null"
      echo "   HGFOREST_VERBOSE    : (boolean) If 'true' then Mercurial asked to produce verbose output"
      echo "   HGFOREST_SEQUENTIAL : (boolean) If 'true' then repos are processed sequentially. Disables concurrency"
      echo "   HGFOREST_GLOBALOPTS : (string, must begin with space) Additional Mercurial global options"
      echo "   HGFOREST_REDIRECT   : (file path) Redirect standard output to specified file"
      echo "   HGFOREST_FIFOS      : (boolean) Default behaviour for FIFO detection. Does not override FIFOs disabled"
      echo "   HGFOREST_CONCURRENCY: (positive integer) Number of repos to process concurrently"
      echo "   HGFOREST_DEBUG      : (boolean) If 'true' then temp files are retained"
      exit 1
}

global_opts="${HGFOREST_GLOBALOPTS:-}"
status_output="${HGFOREST_REDIRECT:-/dev/stdout}"
qflag="${HGFOREST_QUIET:-false}"
vflag="${HGFOREST_VERBOSE:-false}"
sflag="${HGFOREST_SEQUENTIAL:-false}"
while [ $# -gt 0 ]
do
  case $1 in
    -h | --help )
      usage
      ;;

    -q | --quiet )
      qflag="true"
      ;;

    -v | --verbose )
      vflag="true"
      ;;

    -s | --sequential )
      sflag="true"
      ;;

    '--' ) # no more options
      shift; break
      ;;

    -*)  # bad option
      usage
      ;;

     * )  # non option
      break
      ;;
  esac
  shift
done

# debug mode
if [ "${HGFOREST_DEBUG:-false}" = "true" ] ; then
  global_opts="${global_opts} --debug"
fi

# silence standard output?
if [ ${qflag} = "true" ] ; then
  global_opts="${global_opts} -q"
  status_output="/dev/null"
fi

# verbose output?
if [ ${vflag} = "true" ] ; then
  global_opts="${global_opts} -v"
fi

# Make sure we have a command.
if [ ${#} -lt 1 -o -z "${1:-}" ] ; then
  echo "ERROR: No command to hg supplied!" > ${status_output}
  usage > ${status_output}
fi

# grab command
command="${1}"; shift

if [ ${vflag} = "true" ] ; then
  echo "# Mercurial command: ${command}" > ${status_output}
fi

# At this point all command options and args are in "$@".
# Always use "$@" (within double quotes) to avoid breaking
# args with spaces into separate args.

if [ ${vflag} = "true" ] ; then
  echo "# Mercurial command argument count: $#" > ${status_output}
  for cmdarg in "$@" ; do
    echo "# Mercurial command argument: ${cmdarg}" > ${status_output}
  done
fi

# Clean out the temporary directory that stores the pid files.
tmp=/tmp/forest.$$
rm -f -r ${tmp}
mkdir -p ${tmp}


if [ "${HGFOREST_DEBUG:-false}" = "true" ] ; then
  # ignores redirection.
  echo "DEBUG: temp files are in: ${tmp}" >&2
fi

# Check if we can use fifos for monitoring sub-process completion.
echo "1" > ${tmp}/read
while_subshell=1
while read line; do
  while_subshell=0
  break;
done < ${tmp}/read
rm ${tmp}/read

on_windows=`uname -s | egrep -ic -e 'cygwin|msys'`

if [ ${while_subshell} = "1" -o ${on_windows} = "1" ]; then
  # cygwin has (2014-04-18) broken (single writer only) FIFOs
  # msys has (2014-04-18) no FIFOs.
  # older shells create a sub-shell for redirect to while
  have_fifos="false"
else
  have_fifos="${HGFOREST_FIFOS:-true}"
fi

safe_interrupt () {
  if [ -d ${tmp} ]; then
    if [ "`ls ${tmp}/*.pid`" != "" ]; then
      echo "Waiting for processes ( `cat ${tmp}/.*.pid ${tmp}/*.pid 2> /dev/null | tr '\n' ' '`) to terminate nicely!" > ${status_output}
      sleep 1
      # Pipe stderr to dev/null to silence kill, that complains when trying to kill
      # a subprocess that has already exited.
      kill -TERM `cat ${tmp}/*.pid | tr '\n' ' '` 2> /dev/null
      wait
      echo "Interrupt complete!" > ${status_output}
    fi
    rm -f -r ${tmp}
  fi
  exit 130
}

nice_exit () {
  if [ -d ${tmp} ]; then
    if [ "`ls -A ${tmp} 2> /dev/null`" != "" ]; then
      wait
    fi
    if [ "${HGFOREST_DEBUG:-false}" != "true" ] ; then
      rm -f -r ${tmp}
    fi
  fi
}

trap 'safe_interrupt' INT QUIT
trap 'nice_exit' EXIT

subrepos="corba jaxp jaxws langtools jdk hotspot nashorn"
jdk_subrepos_extra="closed jdk/src/closed jdk/make/closed jdk/test/closed hotspot/make/closed hotspot/src/closed hotspot/test/closed"
subrepos_extra="$jdk_subrepos_extra deploy install sponsors pubs"

# Only look in specific locations for possible forests (avoids long searches)
pull_default=""
repos=""
repos_extra=""
if [ "${command}" = "clone" -o "${command}" = "fclone" -o "${command}" = "tclone" ] ; then
  # we must be a clone
  if [ ! -f .hg/hgrc ] ; then
    echo "ERROR: Need initial repository to use this script" > ${status_output}
    exit 1
  fi

  # the clone must know where it came from (have a default pull path).
  pull_default=`hg paths default`
  if [ "${pull_default}" = "" ] ; then
    echo "ERROR: Need initial clone with 'hg paths default' defined" > ${status_output}
    exit 1
  fi

  # determine which sub repos need to be cloned.
  for i in ${subrepos} ; do
    if [ ! -f ${i}/.hg/hgrc ] ; then
      repos="${repos} ${i}"
    fi
  done

  pull_default_tail=`echo ${pull_default} | sed -e 's@^.*://[^/]*/\(.*\)@\1@'`

  if [ $# -gt 0 ] ; then
    if [ "x${1}" = "xjdk" ] ; then
       subrepos_extra=$jdk_subrepos_extra
       echo "subrepos being cloned are $subrepos_extra"
       shift
    fi
    # if there is an "extra sources" path then reparent "extra" repos to that path
    if [ "x${pull_default}" = "x${pull_default_tail}" ] ; then
      echo "ERROR: Need initial clone from non-local source" > ${status_output}
      exit 1
    fi
    # assume that "extra sources" path is the first arg
    pull_extra="${1}/${pull_default_tail}"

    # determine which extra subrepos need to be cloned.
    for i in ${subrepos_extra} ; do
      if [ ! -f ${i}/.hg/hgrc ] ; then
        repos_extra="${repos_extra} ${i}"
      fi
    done
  else
    if [ "x${pull_default}" = "x${pull_default_tail}" ] ; then
      # local source repo. Clone the "extra" subrepos that exist there.
      for i in ${subrepos_extra} ; do
        if [ -f ${pull_default}/${i}/.hg/hgrc -a ! -f ${i}/.hg/hgrc ] ; then
          # sub-repo there in source but not here
          repos_extra="${repos_extra} ${i}"
        fi
      done
    fi
  fi

  # Any repos to deal with?
  if [ "${repos}" = "" -a "${repos_extra}" = "" ] ; then
    echo "No repositories to process." > ${status_output}
    exit
  fi

  # Repos to process concurrently. Clone does better with low concurrency.
  at_a_time="${HGFOREST_CONCURRENCY:-2}"
else
  # Process command for all of the present repos
  for i in . ${subrepos} ${subrepos_extra} ; do
    if [ -d ${i}/.hg ] ; then
      repos="${repos} ${i}"
    fi
  done

  # Any repos to deal with?
  if [ "${repos}" = "" ] ; then
    echo "No repositories to process." > ${status_output}
    exit
  fi

  # any of the repos locked?
  locked=""
  for i in ${repos} ; do
    if [ -h ${i}/.hg/store/lock -o -f ${i}/.hg/store/lock ] ; then
      locked="${i} ${locked}"
    fi
  done
  if [ "${locked}" != "" ] ; then
    echo "ERROR: These repositories are locked: ${locked}" > ${status_output}
    exit 1
  fi

  # Repos to process concurrently.
  at_a_time="${HGFOREST_CONCURRENCY:-8}"
fi

# Echo out what repositories we do a command on.
echo "# Repositories: ${repos} ${repos_extra}" > ${status_output}

if [ "${command}" = "serve" ] ; then
  # "serve" is run for all the repos as one command.
  (
    (
      cwd=`pwd`
      serving=`basename ${cwd}`
      (
        echo "[web]"
        echo "description = ${serving}"
        echo "allow_push = *"
        echo "push_ssl = False"

        echo "[paths]"
        for i in ${repos} ; do
          if [ "${i}" != "." ] ; then
            echo "/${serving}/${i} = ${i}"
          else
            echo "/${serving} = ${cwd}"
          fi
        done
      ) > ${tmp}/serve.web-conf

      echo "serving root repo ${serving}" > ${status_output}

      echo "hg${global_opts} serve ${@}" > ${status_output}
      (PYTHONUNBUFFERED=true hg${global_opts} serve -A ${status_output} -E ${status_output} --pid-file ${tmp}/serve.pid --web-conf ${tmp}/serve.web-conf "${@}"; echo "$?" > ${tmp}/serve.pid.rc ) 2>&1 &
    ) 2>&1 | sed -e "s@^@serve:   @" > ${status_output}
  ) &
else
  # Run the supplied command on all repos in parallel.

  # n is the number of subprocess started or which might still be running.
  n=0
  if [ ${have_fifos} = "true" ]; then
    # if we have fifos use them to detect command completion.
    mkfifo ${tmp}/fifo
    exec 3<>${tmp}/fifo
  fi

  # iterate over all of the subrepos.
  for i in ${repos} ${repos_extra} ; do
    n=`expr ${n} '+' 1`
    repopidfile=`echo ${i} | sed -e 's@./@@' -e 's@/@_@g'`
    reponame=`echo ${i} | sed -e :a -e 's/^.\{1,20\}$/ &/;ta'`
    pull_base="${pull_default}"

    # regular repo or "extra" repo?
    for j in ${repos_extra} ; do
      if [ "${i}" = "${j}" ] ; then
        # it's an "extra"
        if [ -n "${pull_extra}" ]; then
          # if no pull_extra is defined, assume that pull_default is valid
          pull_base="${pull_extra}"
        fi
      fi
    done

    # remove trailing slash
    pull_base="`echo ${pull_base} | sed -e 's@[/]*$@@'`"

    # execute the command on the subrepo
    (
      (
        if [ "${command}" = "clone" -o "${command}" = "fclone" -o "${command}" = "tclone" ] ; then
          # some form of clone
          clone_newrepo="${pull_base}/${i}"
          parent_path="`dirname ${i}`"
          if [ "${parent_path}" != "." ] ; then
            times=0
            while [ ! -d "${parent_path}" ] ; do  ## nested repo, ensure containing dir exists
              if [ "${sflag}" = "true" ] ; then
                # Missing parent is fatal during sequential operation.
                echo "ERROR: Missing parent path: ${parent_path}" > ${status_output}
                exit 1
              fi
              times=`expr ${times} '+' 1`
              if [ `expr ${times} '%' 10` -eq 0 ] ; then
                echo "${parent_path} still not created, waiting..." > ${status_output}
              fi
              sleep 5
            done
          fi
          # run the clone command.
          echo "hg${global_opts} clone ${clone_newrepo} ${i}" > ${status_output}
          (PYTHONUNBUFFERED=true hg${global_opts} clone ${clone_newrepo} ${i}; echo "$?" > ${tmp}/${repopidfile}.pid.rc ) 2>&1 &
        else
          # run the command.
          echo "cd ${i} && hg${global_opts} ${command} ${@}" > ${status_output}
          cd ${i} && (PYTHONUNBUFFERED=true hg${global_opts} ${command} "${@}"; echo "$?" > ${tmp}/${repopidfile}.pid.rc ) 2>&1 &
        fi

        echo $! > ${tmp}/${repopidfile}.pid
      ) 2>&1 | sed -e "s@^@${reponame}:   @" > ${status_output}
      # tell the fifo waiter that this subprocess is done.
      if [ ${have_fifos} = "true" ]; then
        echo "${i}" >&3
      fi
    ) &

    if [ "${sflag}" = "true" ] ; then
      # complete this task before starting another.
      wait
    else
      if [ "${have_fifos}" = "true" ]; then
        # check on count of running subprocesses and possibly wait for completion
        if [ ${n} -ge ${at_a_time} ] ; then
          # read will block until there are completed subprocesses
          while read repo_done; do
            n=`expr ${n} '-' 1`
            if [ ${n} -lt ${at_a_time} ] ; then
              # we should start more subprocesses
              break;
            fi
          done <&3
        fi
      else
        # Compare completions to starts
        completed="`(ls -a1 ${tmp}/*.pid.rc 2> /dev/null | wc -l) || echo 0`"
        while [ `expr ${n} '-' ${completed}` -ge ${at_a_time} ] ; do
          # sleep a short time to give time for something to complete
          sleep 1
          completed="`(ls -a1 ${tmp}/*.pid.rc 2> /dev/null | wc -l) || echo 0`"
        done
      fi
    fi
  done

  if [ ${have_fifos} = "true" ]; then
    # done with the fifo
    exec 3>&-
  fi
fi

# Wait for all subprocesses to complete
wait

# Terminate with exit 0 only if all subprocesses were successful
# Terminate with highest exit code of subprocesses
ec=0
if [ -d ${tmp} ]; then
  rcfiles="`(ls -a ${tmp}/*.pid.rc 2> /dev/null) || echo ''`"
  for rc in ${rcfiles} ; do
    exit_code=`cat ${rc} | tr -d ' \n\r'`
    if [ "${exit_code}" != "0" ] ; then
      if [ ${exit_code} -gt 1 ]; then
        # mercurial exit codes greater than "1" signal errors.
      repo="`echo ${rc} | sed -e 's@^'${tmp}'@@' -e 's@/*\([^/]*\)\.pid\.rc$@\1@' -e 's@_@/@g'`"
      echo "WARNING: ${repo} exited abnormally (${exit_code})" > ${status_output}
      fi
      if [ ${exit_code} -gt ${ec} ]; then
        # assume that larger exit codes are more significant
        ec=${exit_code}
      fi
    fi
  done
fi
exit ${ec}
