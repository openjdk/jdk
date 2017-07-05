#!/bin/sh

#
# Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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


# Support functions to start, stop, wait for or kill a given SimpleApplication

# Starts a given app as background process, usage:
#   startApplication <class> port-file [args...]
#
# The following variables are set:
#
# appJavaPid  - application's Java pid
# appOtherPid - pid associated with the app other than appJavaPid
# appPidList  - all pids associated with the app
# appOutput   - file containing stdout and stderr from the app
#
# Waits for at least one line of output from the app to indicate
# that it is up and running.
#
startApplication()
{
  appOutput="${TESTCLASSES}/Application.out"

  ${JAVA} -XX:+UsePerfData -classpath "${TESTCLASSES}" "$@" > "$appOutput" 2>&1 &
  appJavaPid="$!"
  appOtherPid=
  appPidList="$appJavaPid"

  echo "INFO: waiting for $1 to initialize..."
  _cnt=0
  while true; do
    # if the app doesn't start then the JavaTest/JTREG timeout will
    # kick in so this isn't really a endless loop
    sleep 1
    out=`tail -1 "$appOutput"`
    if [ -n "$out" ]; then
      # we got some output from the app so it's running
      break
    fi
    _cnt=`expr $_cnt + 1`
    echo "INFO: waited $_cnt second(s) ..."
  done
  unset _cnt

  if $isWindows; then
    # Windows requires special handling
    appOtherPid="$appJavaPid"

    if $isCygwin; then
      appJavaPid=`ps -p "$appOtherPid" \
        | sed -n '
          # See if $appOtherPid is in PID column; there are sometimes
          # non-blanks in column 1 (I and S observed so far)
          /^.'"${PATTERN_WS}${PATTERN_WS}*${appOtherPid}${PATTERN_WS}"'/{
            # strip PID column
            s/^.'"${PATTERN_WS}${PATTERN_WS}*${appOtherPid}${PATTERN_WS}${PATTERN_WS}"'*//
            # strip PPID column
            s/^[1-9][0-9]*'"${PATTERN_WS}${PATTERN_WS}"'*//
            # strip PGID column
            s/^[1-9][0-9]*'"${PATTERN_WS}${PATTERN_WS}"'*//
            # strip everything after WINPID column
            s/'"${PATTERN_WS}"'.*//
            p
            q
          }
        '`
      echo "INFO: Cygwin pid=$appOtherPid maps to Windows pid=$appJavaPid"
    else
      # show PID, PPID and COMM columns only
      appJavaPid=`ps -o pid,ppid,comm \
        | sed -n '
          # see if appOtherPid is in either PID or PPID columns
          /'"${PATTERN_WS}${appOtherPid}${PATTERN_WS}"'/{
            # see if this is a java command
            /java'"${PATTERN_EOL}"'/{
              # strip leading white space
              s/^'"${PATTERN_WS}${PATTERN_WS}"'*//
              # strip everything after the first word
              s/'"${PATTERN_WS}"'.*//
              # print the pid and we are done
              p
              q
            }
          }
        '`
      echo "INFO: MKS shell pid=$appOtherPid; Java pid=$appJavaPid"
    fi

    if [ -z "$appJavaPid" ]; then
      echo "ERROR: could not find app's Java pid." >&2
      killApplication
      exit 2
    fi
    appPidList="$appOtherPid $appJavaPid"
  fi

  echo "INFO: $1 is process $appJavaPid"
  echo "INFO: $1 output is in $appOutput"
}


# Stops a simple application by invoking ShutdownSimpleApplication
# class with a specific port-file, usage:
#   stopApplication port-file
#
# Note: When this function returns, the SimpleApplication (or a subclass)
# may still be running because the application has not yet reached the
# shutdown check.
#
stopApplication()
{
  $JAVA -XX:+UsePerfData -classpath "${TESTCLASSES}" ShutdownSimpleApplication $1
}


# Wait for a simple application to stop running.
#
waitForApplication() {
  if [ $isWindows = false ]; then
    # non-Windows is easy; just one process
    echo "INFO: waiting for $appJavaPid"
    set +e
    wait "$appJavaPid"
    set -e

  elif $isCygwin; then
    # Cygwin pid and not the Windows pid
    echo "INFO: waiting for $appOtherPid"
    set +e
    wait "$appOtherPid"
    set -e

  else # implied isMKS
    # MKS has intermediate shell and Java process
    echo "INFO: waiting for $appJavaPid"

    # appJavaPid can be empty if pid search in startApplication() failed
    if [ -n "$appJavaPid" ]; then
      # only need to wait for the Java process
      set +e
      wait "$appJavaPid"
      set -e
    fi
  fi
}


# Kills a simple application by sending a SIGTERM to the appropriate
# process(es); on Windows SIGQUIT (-9) is used.
#
killApplication()
{
  if [ $isWindows = false ]; then
    # non-Windows is easy; just one process
    echo "INFO: killing $appJavaPid"
    set +e
    kill -TERM "$appJavaPid"  # try a polite SIGTERM first
    sleep 2
    # send SIGQUIT (-9) just in case SIGTERM didn't do it
    # but don't show any complaints
    kill -QUIT "$appJavaPid" > /dev/null 2>&1
    wait "$appJavaPid"
    set -e

  elif $isCygwin; then
    # Cygwin pid and not the Windows pid
    echo "INFO: killing $appOtherPid"
    set +e
    kill -9 "$appOtherPid"
    wait "$appOtherPid"
    set -e

  else # implied isMKS
    # MKS has intermediate shell and Java process
    echo "INFO: killing $appPidList"
    set +e
    kill -9 $appPidList
    set -e

    # appJavaPid can be empty if pid search in startApplication() failed
    if [ -n "$appJavaPid" ]; then
      # only need to wait for the Java process
      set +e
      wait "$appJavaPid"
      set -e
    fi
  fi
}
