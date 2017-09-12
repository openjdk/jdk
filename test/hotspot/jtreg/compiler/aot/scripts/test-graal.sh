# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

JAR="dacapo-9.12-bach.jar"

if [ ! -f $JAR ]; then
    echo "$JAR not found."
    exit 1
fi

pushd `dirname $0` > /dev/null
DIR=`pwd`
popd > /dev/null

APP="-jar $JAR -s small -n 5"

JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:+UseJVMCICompiler -XX:-BootstrapJVMCI -XX:-TieredCompilation -Xmx4g -XX:+UseCompressedOops -XX:+UseG1GC"

MODULE_OPTS="--add-modules jdk.internal.vm.ci --add-exports jdk.internal.vm.ci/jdk.vm.ci.hotspot=ALL-UNNAMED"

$JAVA_HOME/bin/java $JAVA_OPTS -version || exit 1

$JAVA_HOME/bin/javac $MODULE_OPTS InitGraal.java || exit 1

PATTERN="(aot library|DONE:.*HotSpotVMConfig|DONE:.*HotSpotJVMCIRuntime|DONE:.*HotSpotGraalRuntime)"
echo "----------"
$JAVA_HOME/bin/java $JAVA_OPTS $MODULE_OPTS -XX:+TieredCompilation -XX:-UseAOT -XX:+PrintAOT -Djvmci.InitTimer=true InitGraal | grep -E "$PATTERN" || exit 1
echo "----------"
$JAVA_HOME/bin/java $JAVA_OPTS $MODULE_OPTS -XX:+TieredCompilation             -XX:+PrintAOT -Djvmci.InitTimer=true InitGraal | grep -E "$PATTERN" || exit 1
echo "----------"
$JAVA_HOME/bin/java $JAVA_OPTS $MODULE_OPTS                                    -XX:+PrintAOT -Djvmci.InitTimer=true InitGraal | grep -E "$PATTERN" || exit 1
echo "----------"

rm -f InitGraal.class

# eclipse started to fail again with JDK 9.
#BENCHMARKS="avrora batik eclipse fop jython h2 luindex lusearch pmd sunflow tradebeans tradesoap xalan"
BENCHMARKS="avrora batik fop jython h2 luindex lusearch pmd sunflow xalan"

for i in $BENCHMARKS; do
    $JAVA_HOME/bin/java $JAVA_OPTS $APP $i || exit 1
    rm -rf ./scratch
done

