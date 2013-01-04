#!/bin/bash
#
# Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

#best pass rate at test 262 known
TEST262_PASS_AT_LEAST=435

RUN_TEST="true"
RUN_TEST262="true"
RUN_NODE="true"
KEEP_OUTPUT="true"
CLEAN_AND_BUILD_NASHORN="true"

#the stable node version to sync against
NODE_LAST_STABLE=v0.6.18

#parse args
for arg in $*
do
    if [ $arg = "--no-test" ]; then
	RUN_TEST="false"
	echo "**** WARNING - you have disabled 'ant test', which is a minimum checkin requirement..."
    elif [ $arg = "--no-262" ]; then
	RUN_TEST262="false"
    elif [ $arg = "--no-node" ]; then
	RUN_NODE="false"
    elif [ $arg = "--no-build" ]; then
	CLEAN_AND_BUILD_NASHORN="false"
    elif [ $arg = "--no-logs" ]; then
	KEEP_OUTPUT="false"
    fi
done

function lastpart() {        
    arr=$(echo $1 | tr "/" "\n")
    for x in $arr
    do
	_last=$x
    done
    echo $_last
}

function check_installed() {
    which $1 >/dev/null
    if [ $? -ne 0 ]; then
	echo "Error $1 not installed: $?"
	exit 2
    fi
}

check_installed hg
check_installed git
check_installed mv
check_installed git

PWD=$(pwd);

while [ -z $NASHORN_ROOT ]
do
    if [ -e $PWD/.hg ]; then
	NASHORN_ROOT=${PWD}
	break
    fi
    PWD=$(dirname ${PWD})
done

echo "Nashorn root detected at ${NASHORN_ROOT}"

COMMON_ROOT=$(dirname $NASHORN_ROOT)
echo "Common root is ${COMMON_ROOT}"

echo "Running checkintest..."

ABSOLUTE_NASHORN_HOME=$COMMON_ROOT/$(lastpart $NASHORN_ROOT)

if [ $CLEAN_AND_BUILD_NASHORN != "false" ]; then
    echo "Cleaning and building nashorn at $ABSOLUTE_NASHORN_HOME/nashorn..."
    $(cd $ABSOLUTE_NASHORN_HOME/nashorn; ant clean >/dev/null 2>/dev/null)
    $(cd $ABSOLUTE_NASHORN_HOME/nashorn; ant jar >/dev/null 2>/dev/null)
    echo "Done."
fi

function failure_check() {
    while read line
    do
	LINE=$(echo $line | grep "Tests run")    
	if [ "${LINE}" != "" ]; then
	    RESULT=$(echo $line | grep "Failures: 0" | grep "Errors: 0")
	    if [ "${RESULT}" == "" ]; then
		TESTNAME=$2
		echo "There were errors in ${TESTNAME} : ${LINE}"
		exit 1
	    fi
	fi
    done < $1
}

function test() {
    TEST_OUTPUT=$ABSOLUTE_NASHORN_HOME/$(mktemp tmp.XXXXX)
    echo "Running 'ant test' on nashorn from ${ABSOLUTE_NASHORN_HOME}/nashorn..."
    $(cd $ABSOLUTE_NASHORN_HOME/nashorn; ant test >$TEST_OUTPUT)
    echo "Done."

    failure_check $TEST_OUTPUT

    echo "**** SUCCESS: 'ant test' successful"

    if [ $KEEP_OUTPUT == "true" ]; then
	cp $TEST_OUTPUT ./checkintest.test.log
	rm -fr $TEST_OUTPUT
    fi
}

if [ $RUN_TEST != "false" ]; then
    test;
fi

function test262() {

    echo "Running 'ant test262parallel' on nashorn from ${ABSOLUTE_NASHORN_HOME}/nashorn..."
    TEST262_OUTPUT=$ABSOLUTE_NASHORN_HOME/$(mktemp tmp.XXXXX)

    echo "Looking for ${ABSOLUTE_NASHORN_HOME}/test/test262..."

    if [ ! -e $ABSOLUTE_NASHORN_HOME/nashorn/test/test262 ]; then
	echo "test262 is missing... looking in $COMMON_ROOT..."
	if [ ! -e $COMMON_ROOT/test262 ]; then
	    echo "... not there either... cloning from repo..."
	    hg clone http://hg.ecmascript.org/tests/test262 $COMMON_ROOT/test262 >/dev/null 2>/dev/null
	    echo "Done."
	fi
	echo "Adding soft link ${COMMON_ROOT}/test262 -> ${ABSOLUTE_NASHORN_HOME}/test/test262..."
	ln -s $COMMON_ROOT/test262 $ABSOLUTE_NASHORN_HOME/nashorn/test/test262
	echo "Done."
    fi

    echo "Ensuring test262 is up to date..."
    $(cd $ABSOLUTE_NASHORN_HOME/nashorn/test/test262; hg pull -u >/dev/null 2>/dev/null)
    echo "Done."

    echo "Running test262..."
    $(cd $ABSOLUTE_NASHORN_HOME/nashorn; ant test262parallel > $TEST262_OUTPUT)
    
    FAILED=$(cat $TEST262_OUTPUT|grep "Tests run:"| cut -d ' ' -f 15 |tr -cd '"[[:digit:]]')
    if [ $FAILED -gt $TEST262_PASS_AT_LEAST ]; then 
	echo "FAILURE: There are ${FAILED} failures in test262 and can be no more than ${TEST262_PASS_AT_LEAST}"
	cp $TEST262_OUTPUT ./checkintest.test262.log
	echo "See ./checkintest.test262.log"
	echo "Terminating due to error"
	exit 1
    elif [ $FAILED -lt $TEST262_PASS_AT_LEAST ]; then
	echo "There seem to have been fixes to 262. ${FAILED} < ${TEST262_PASS_AT_LEAST}. Please update limit in bin/checkintest.sh"
    fi
    
    echo "**** SUCCESS: Test262 passed with no more than ${TEST262_PASS_AT_LEAST} failures."

    if [ $KEEP_OUTPUT == "true" ]; then
	cp $TEST262_OUTPUT ./checkintest.test262.log
	rm -fr $TEST262_OUTPUT
    fi    
}

if [ $RUN_TEST262 != "false" ]; then
    test262;    
fi;

function testnode() {
    TESTNODEJAR_OUTPUT=$ABSOLUTE_NASHORN_HOME/$(mktemp tmp.XXXXX)
   
    echo "Running node tests..."
#replace node jar properties nashorn with this nashorn
    
    NODEJAR_PROPERTIES=~/nodejar.properties
    
    NODE_HOME=$(cat $NODEJAR_PROPERTIES | grep ^node.home | cut -f2 -d=)    
    NASHORN_HOME=$(cat $NODEJAR_PROPERTIES | grep ^nashorn.home | cut -f2 -d=)
    
    ABSOLUTE_NODE_HOME=$COMMON_ROOT/$(lastpart $NODE_HOME)    
    
    echo "Writing nodejar.properties..."

    cat > $NODEJAR_PROPERTIES << EOF
node.home=../node
nashorn.home=../$(lastpart $NASHORN_ROOT)
EOF
    echo "Done."
    echo "Checking node home ${ABSOLUTE_NODE_HOME}..."

    if [ ! -e $ABSOLUTE_NODE_HOME ]; then
	echo "Node base dir not found. Cloning node..."    
	$(cd $COMMON_ROOT; git clone https://github.com/joyent/node.git $(lastpart $NODE_HOME) >/dev/null 2>/dev/null)
	echo "Done."
	echo "Updating to last stable version ${NODE_LAST_STABLE}..."
	$(cd $ABSOLUTE_NODE_HOME; git checkout $NODE_LAST_STABLE >/dev/null 2>/dev/null)
	echo "Done."
	echo "Running configure..."
	$(cd $ABSOLUTE_NODE_HOME; ./configure >/dev/null 2>/dev/null)
	echo "Done."
    fi
    
    echo "Ensuring node is built..."
#make sure node is built
    $(cd $ABSOLUTE_NODE_HOME; make >/dev/null 2>/dev/null)
    echo "Done."

    NODEJAR_HOME=$COMMON_ROOT/nodejar

    if [ ! -e $NODEJAR_HOME ]; then
	echo "No node jar home found. cloning from depot..."
	$(cd $COMMON_ROOT; hg clone https://hg.kenai.com/hg/nodejs~source nodejar >/dev/null 2>/dev/null) 
	$(cd $COMMON_ROOT/nodejar; ant >/dev/null)
	echo "Done."
	echo "Copying node files..."
	$(cd $COMMON_ROOT/nodejar; ant copy-node-files >/dev/null 2>/dev/null)
	echo "Patching node files..."
	$(cd $COMMON_ROOT/nodejar; ant patch-node-files >/dev/null 2>/dev/null)
	echo "Done."
    fi
    
    echo "Ensuring node.jar is up to date from source depot..."
    $(cd $COMMON_ROOT/nodejar; hg pull -u >/dev/null 2>/dev/null)
    echo "Done."

    echo "Installing nashorn..."
    $(cd $COMMON_ROOT/nodejar; ant >/dev/null)
    echo "Done."

    echo "Running node.jar test..."
    $(cd $COMMON_ROOT/nodejar; mvn clean verify >$TESTNODEJAR_OUTPUT)
    echo "Done."

    failure_check $TESTNODEJAR_OUTPUT
    
    echo "**** SUCCESS: Node test successful."

    if [ $KEEP_OUTPUT == "true" ]; then
	rm -fr $TESTNODEJAR_OUTPUT
	cp $TESTNODEJAR_OUTPUT ./checkintest.nodejar.log
    fi
}

if [ $RUN_NODE != "false" ]; then
    testnode;
fi;

echo "Finished"
