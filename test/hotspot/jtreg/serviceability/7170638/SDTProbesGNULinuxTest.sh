# 
#  Copyright (c) 2012, Red Hat, Inc.
#  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
# 
#  This code is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License version 2 only, as
#  published by the Free Software Foundation.
# 
#  This code is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
#  version 2 for more details (a copy is included in the LICENSE file that
#  accompanied this code).
# 
#  You should have received a copy of the GNU General Public License version
#  2 along with this work; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
# 
#  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
#  or visit www.oracle.com if you need additional information or have any
#  questions.
# 

# @test SDTProbesGNULinuxTest.sh
# @bug 7170638
# @summary Test SDT probes available on GNU/Linux when DTRACE_ENABLED
# @run shell SDTProbesGNULinuxTest.sh

# This test only matters on GNU/Linux, others trivially PASS.
OS=`uname -s`
case "$OS" in
  Linux )
    ;;
  *)
    echo "Not testing on anything but GNU/Linux. PASSED"
    exit 0;
    ;;
esac

# Where is our java (parent) directory? 
if [ "${TESTJAVA}" = "" ]; then
  PARENT=$(dirname $(readlink -f $(which java)))
  TESTJAVA=`dirname ${PARENT}`
  echo "TESTJAVA directory not set, using " ${TESTJAVA}
fi

# This test only matters when build with DTRACE_ENABLED. 
${TESTJAVA}/bin/java -XX:+ExtendedDTraceProbes -version
if [ "$?" != "0" ]; then
  echo "Not build using DTRACE_ENABLED. PASSED"
  exit 0
fi

# Test all available libjvm.so variants
for libjvm in $(find ${TESTJAVA} -name libjvm.so); do
  echo "Testing ${libjvm}"
  # Check whether the SDT probes are compiled in.
  readelf -S ${libjvm} | grep '.note.stapsdt'
  if [ "$?" != "0" ]; then
    echo "Failed: ${libjvm} doesn't contain SDT probes."
    exit 1
  fi
  # We could iterate over all SDT probes and test them individually
  # with readelf -n, but older readelf versions don't understand them.
done

echo "Passed."
exit 0
