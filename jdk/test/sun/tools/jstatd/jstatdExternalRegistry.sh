#
# Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 4990825 7092186
# @run shell/timeout=90 jstatdExternalRegistry.sh
# @summary Test functionality of 'jstatd -p<port>&' with an external RMI registry

. ${TESTSRC-.}/../../jvmstat/testlibrary/utils.sh

setup
verify_os

cleanup() {
  kill_proc ${RMIREGISTRY_PID}
  kill_proc ${JSTATD_PID}
}

trap 'cleanup' 0 HUP INT QUIT TERM

RMIREGISTRY="${TESTJAVA}/bin/rmiregistry"
JSTATD="${TESTJAVA}/bin/jstatd"
JPS="${TESTJAVA}/bin/jps"
JSTAT="${TESTJAVA}/bin/jstat"

HOSTNAME=`uname -n`
PORT=`freePort`
if [ "${PORT}" = "0" ] ; then
  echo "Cannot get free port"
  exit 1
fi

RMIREGISTRY_OUT="rmiregistry_$$.out"
JSTATD_OUT="jstatd_$$.out"

${RMIREGISTRY} -J-XX:+UsePerfData ${PORT} > ${RMIREGISTRY_OUT} 2>&1 &
RMIREGISTRY_PID=$!

echo "rmiregistry started on port ${PORT} as pid ${RMIREGISTRY_PID}"
sleep 3

${JSTATD} -J-XX:+UsePerfData -J-Djava.security.policy=${TESTSRC}/all.policy -p ${PORT} > ${JSTATD_OUT} 2>&1 &
JSTATD_PID=$!

echo "jstatd started as pid ${JSTATD_PID}"
sleep 3

${JPS} -J-XX:+UsePerfData ${HOSTNAME}:${PORT} 2>&1 | awk -f ${TESTSRC}/jpsOutput1.awk

if [ $? -ne 0 ]
then
    echo "Output of jps differs from expected output. Failed."
    exit 1
fi

${JSTAT} -J-XX:+UsePerfData -J-Duser.language=en -gcutil ${JSTATD_PID}@${HOSTNAME}:${PORT} 250 5 2>&1 | awk -f ${TESTSRC}/jstatGcutilOutput1.awk
RC=$?

if [ ${RC} -ne 0 ]
then
    echo "jstat output differs from expected output"
fi

if [ -s ${JSTATD_OUT} ]
then
    echo "jstatd generated unexpected output: see ${JSTATD_OUT}"
    RC=1
fi

if [ -s ${RMIREGISTRY_OUT} ]
then
    echo "rmiregistry generated unexpected output: see ${RMIREGISTRY_OUT}"
    RC=1
fi

exit ${RC}
