#
# Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
#
# @summary The RMI benchmark test.  This script is only
#          used to run the test under JTREG.
#
# @build bench.BenchInfo bench.HtmlReporter bench.Util bench.Benchmark 
# @build bench.Reporter bench.XmlReporter bench.ConfigFormatException 
# @build bench.Harness bench.TextReporter bench.rmi.BenchServer 
# @build bench.rmi.DoubleArrayCalls bench.rmi.LongCalls bench.rmi.ShortCalls
# @build bench.rmi.BenchServerImpl bench.rmi.DoubleCalls 
# @build bench.rmi.Main bench.rmi.SmallObjTreeCalls
# @build bench.rmi.BooleanArrayCalls bench.rmi.ExceptionCalls 
# @build bench.rmi.NullCalls bench.rmi.BooleanCalls bench.rmi.ExportObjs 
# @build bench.rmi.ObjArrayCalls bench.rmi.ByteArrayCalls 
# @build bench.rmi.FloatArrayCalls bench.rmi.ObjTreeCalls
# @build bench.rmi.ByteCalls bench.rmi.FloatCalls bench.rmi.ProxyArrayCalls
# @build bench.rmi.CharArrayCalls bench.rmi.IntArrayCalls 
# @build bench.rmi.RemoteObjArrayCalls bench.rmi.CharCalls bench.rmi.IntCalls
# @build bench.rmi.ClassLoading bench.rmi.LongArrayCalls 
# @build bench.rmi.ShortArrayCalls bench.rmi.altroot.Node
#
# @run shell/timeout=1800 runRmiBench.sh
#
# @author Mike Warres, Nigel Daley

echo "Starting RMI benchmark server "

$TESTJAVA/bin/java \
    -server \
    -cp $TESTCLASSES \
    -Djava.security.policy=$TESTSRC/bench/rmi/policy.all \
    bench.rmi.Main \
    -server 2007 \
    -c $TESTSRC/bench/rmi/config &

sleep 10
echo "Starting RMI benchmark client "

$TESTJAVA/bin/java \
    -client \
    -cp $TESTCLASSES \
    -Djava.security.policy=$TESTSRC/bench/rmi/policy.all \
    bench.rmi.Main \
    -client localhost:2007 \
    -c $TESTSRC/bench/rmi/config

