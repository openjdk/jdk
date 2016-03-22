#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8137317 8139238
# @summary Visibility tests for ResourceBundle.getBundle with and without
# an unnamed module argument.


set -e
STATUS=0

runJava()
{
    echo "Executing java $@"
    $JAVA $@ || STATUS=1
    echo
}

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA="$1"; shift
  COMPILEJAVA="${TESTJAVA}"
  TESTSRC="`pwd`"
  TESTCLASSES="`pwd`"
fi

JAVAC="$COMPILEJAVA/bin/javac"
JAVA="$TESTJAVA/bin/java"

rm -rf mods classes

MODS=`cd $TESTSRC/src; find . -name module-info.java -exec dirname {} \; | sed 's:\./::'`

for M in $MODS
do
    mkdir -p mods/$M
    CLASSES="`find $TESTSRC/src/$M -name '*.java'`"
    if [ "x$CLASSES" != x ]; then
        $JAVAC -g -d mods -modulesourcepath $TESTSRC/src $CLASSES
    fi
    PROPS="`(cd $TESTSRC/src/$M; find . -name '*.properties')`"
    if [ "x$PROPS" != x ]; then
        for P in $PROPS
        do
            D=`dirname $P`
            mkdir -p mods/$M/$D
            cp $TESTSRC/src/$M/$P mods/$M/$D/
        done
    fi
done

# Package jdk.test is in named module "test".
# Package jdk.embargo is in named module "embargo".

# jdk.{test,embargo}.TestWithUnnamedModuleArg call:
#     ResourceBundle.getBundle(basename, classloader.getUnnamedModule())
#     where classloader is the TCCL or system class loader.
# jdk.{test,embargo}.TestWithNoModuleArg call:
#     ResourceBundle.getBundle(basename)

# jdk.test.resources[.exported].classes.* are class-based resource bundles.
# jdk.test.resources[.exported].props.* are properties file-based resource bundles.

# Packages jdk.test.resources.{classes,props} in named module "named.bundles"
# are exported only to named module "test".
# Packages jdk.test.resources.exported.{classes,props} in named module
# "exported.named.bundle" are exported to unnamed modules.

########################################
# Test cases with jdk.test.resources.* #
########################################

# Tests using jdk.test.TestWithNoModuleArg and jdk.embargo.TestWithNoModuleArg
# neither of which specifies an unnamed module with ResourceBundle.getBundle().

# jdk.test.resources.{classes,props}.* are available only to named module "test"
# by ResourceBundleProvider.
runJava -mp mods -m test/jdk.test.TestWithNoModuleArg \
    jdk.test.resources.classes.MyResources true
runJava -mp mods -m test/jdk.test.TestWithNoModuleArg \
    jdk.test.resources.props.MyResources true
runJava -mp mods -m embargo/jdk.embargo.TestWithNoModuleArg \
    jdk.test.resources.classes.MyResources false
runJava -mp mods -m embargo/jdk.embargo.TestWithNoModuleArg \
    jdk.test.resources.props.MyResources false

# Add mods/named.bundles to the class path.
runJava -cp mods/named.bundles -mp mods -m test/jdk.test.TestWithNoModuleArg \
    jdk.test.resources.classes.MyResources true
runJava -cp mods/named.bundles -mp mods -m test/jdk.test.TestWithNoModuleArg \
    jdk.test.resources.props.MyResources true
runJava -cp mods/named.bundles -mp mods -m embargo/jdk.embargo.TestWithNoModuleArg \
    jdk.test.resources.classes.MyResources false
runJava -cp mods/named.bundles -mp mods -m embargo/jdk.embargo.TestWithNoModuleArg \
    jdk.test.resources.props.MyResources false

# Tests using jdk.test.TestWithUnnamedModuleArg and jdk.embargo.TestWithUnnamedModuleArg
# both of which specify an unnamed module with ResourceBundle.getBundle.

# jdk.test.resources.classes is exported to named module "test".
# IllegalAccessException is thrown in ResourceBundle.Control.newBundle().
runJava -mp mods -m test/jdk.test.TestWithUnnamedModuleArg \
    jdk.test.resources.classes.MyResources false

# jdk.test.resources.props is exported to named module "test".
# loader.getResource() doesn't find jdk.test.resources.props.MyResources.
runJava -mp mods -m test/jdk.test.TestWithUnnamedModuleArg \
    jdk.test.resources.props.MyResources false

# IllegalAccessException is thrown in ResourceBundle.Control.newBundle().
runJava -mp mods -m embargo/jdk.embargo.TestWithUnnamedModuleArg \
    jdk.test.resources.classes.MyResources false
# jdk.test.resources.props is exported to named module "test".
# loader.getResource() doesn't find jdk.test.resources.props.MyResources.
runJava -mp mods -m embargo/jdk.embargo.TestWithUnnamedModuleArg \
    jdk.test.resources.props.MyResources false

# Add mods/named.bundles to the class path

# IllegalAccessException is thrown in ResourceBundle.Control.newBundle().
runJava -cp mods/named.bundles -mp mods -m test/jdk.test.TestWithUnnamedModuleArg \
        jdk.test.resources.classes.MyResources false
# loader.getResource() finds jdk.test.resources.exported.props.MyResources.
runJava -cp mods/named.bundles -mp mods -m test/jdk.test.TestWithUnnamedModuleArg \
        jdk.test.resources.props.MyResources true

# jdk.test.resources.exported.classes.MyResources is treated
# as if the class is in an unnamed module.
runJava -cp mods/named.bundles -mp mods -m embargo/jdk.embargo.TestWithUnnamedModuleArg \
        jdk.test.resources.classes.MyResources true
# loader.getResource() finds jdk.test.resources.exported.props.MyResources.
runJava -cp mods/named.bundles -mp mods -m embargo/jdk.embargo.TestWithUnnamedModuleArg \
        jdk.test.resources.props.MyResources true

#################################################
# Test cases with jdk.test.resources.exported.* #
#################################################
# Tests using jdk.test.TestWithNoModuleArg and jdk.embargo.TestWithNoModuleArg
# neither of which specifies an unnamed module with ResourceBundle.getBundle.

# None of jdk.test.resources.exported.** is available to the named modules.
runJava -mp mods -m test/jdk.test.TestWithNoModuleArg \
    jdk.test.resources.exported.classes.MyResources false
runJava -mp mods -m test/jdk.test.TestWithNoModuleArg \
    jdk.test.resources.exported.props.MyResources false
runJava -mp mods -m embargo/jdk.embargo.TestWithNoModuleArg \
    jdk.test.resources.exported.classes.MyResources false
runJava -mp mods -m embargo/jdk.embargo.TestWithNoModuleArg \
    jdk.test.resources.exported.props.MyResources false

# Add mods/exported.named.bundles to the class path.
runJava -cp mods/exported.named.bundles -mp mods -m test/jdk.test.TestWithNoModuleArg \
    jdk.test.resources.exported.classes.MyResources false
runJava -cp mods/exported.named.bundles -mp mods -m test/jdk.test.TestWithNoModuleArg \
    jdk.test.resources.exported.props.MyResources false
runJava -cp mods/exported.named.bundles -mp mods -m embargo/jdk.embargo.TestWithNoModuleArg \
    jdk.test.resources.exported.classes.MyResources false
runJava -cp mods/exported.named.bundles -mp mods -m embargo/jdk.embargo.TestWithNoModuleArg \
    jdk.test.resources.exported.props.MyResources false

# Tests using jdk.test.TestWithUnnamedModuleArg and jdk.embargo.TestWithUnnamedModuleArg
# which specify an unnamed module with ResourceBundle.getBundle.

# loader.loadClass() doesn't find jdk.test.resources.exported.classes.MyResources
# and throws a ClassNotFoundException.
runJava -mp mods -m test/jdk.test.TestWithUnnamedModuleArg \
        jdk.test.resources.exported.classes.MyResources false
# The properties files in jdk.test.resources.exported.props are not found with loader.getResource().
runJava -mp mods -m test/jdk.test.TestWithUnnamedModuleArg \
        jdk.test.resources.exported.props.MyResources false


# loader.loadClass() doesn't find jdk.test.resources.exported.classes.MyResources
# and throws a ClassNotFoundException.
runJava -mp mods -m embargo/jdk.embargo.TestWithUnnamedModuleArg \
        jdk.test.resources.exported.classes.MyResources false
# The properties files in jdk.test.resources.exported.props are not found
# with loader.getResource().
runJava -mp mods -m embargo/jdk.embargo.TestWithUnnamedModuleArg \
        jdk.test.resources.exported.props.MyResources false

# Add mods/exported.named.bundles to the class path.

# jdk.test.resources.exported.classes.MyResources.getModule().isNamed() returns false.
runJava -cp mods/exported.named.bundles -mp mods -m test/jdk.test.TestWithUnnamedModuleArg \
        jdk.test.resources.exported.classes.MyResources true
# loader.getResource() finds jdk.test.resources.exported.props.MyResources.
runJava -cp mods/exported.named.bundles -mp mods -m test/jdk.test.TestWithUnnamedModuleArg \
        jdk.test.resources.exported.props.MyResources true

# jdk.test.resources.exported.classes.MyResources.getModule().isNamed() returns false.
runJava -cp mods/exported.named.bundles -mp mods -m embargo/jdk.embargo.TestWithUnnamedModuleArg \
        jdk.test.resources.exported.classes.MyResources true
# loader.getResource() finds jdk.test.resources.exported.props.MyResources.
runJava -cp mods/exported.named.bundles -mp mods -m embargo/jdk.embargo.TestWithUnnamedModuleArg \
        jdk.test.resources.exported.props.MyResources true

#######################################
# Test cases with jdk.pkg.resources.* #
#######################################
# Prepare resource bundles in an unnamed module
PKG=$TESTSRC/src/pkg
mkdir -p classes/jdk/pkg/resources/props
$JAVAC -g -d classes $PKG/jdk/pkg/test/Main.java $PKG/jdk/pkg/resources/classes/MyResources.java
cp $PKG/jdk/pkg/resources/props/MyResources.properties classes/jdk/pkg/resources/props

# jdk.pkg.resources.* are in an unnamed module.
# jdk.pkg.test.Main calls ResourceBundle.getBundle with an unnamed module.
runJava -cp classes jdk.pkg.test.Main jdk.pkg.resources.classes.MyResources true
runJava -cp classes jdk.pkg.test.Main jdk.pkg.resources.props.MyResources true

exit $STATUS
