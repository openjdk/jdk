#!/bin/sh

#
# Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

#
# @test
# @bug 4833089 4992454
# @summary Check for proper handling of uncaught exceptions
# @author Martin Buchholz
#
# @run shell UncaughtExceptions.sh

# To run this test manually, simply do ./UncaughtExceptions.sh

 java="${TESTJAVA+${TESTJAVA}/bin/}java"
javac="${COMPILEJAVA+${COMPILEJAVA}/bin/}javac"

failed=""
Fail() { echo "FAIL: $1"; failed="${failed}."; }

Die() { printf "%s\n" "$*"; exit 1; }

Sys() {
    "$@"; rc="$?";
    test "$rc" -eq 0 || Die "Command \"$*\" failed with exitValue $rc";
}

HorizontalRule() {
    echo "-----------------------------------------------------------------"
}

Bottom() {
    test "$#" = 1 -a "$1" = "Line" || Die "Usage: Bottom Line"

    HorizontalRule
    if test -n "$failed"; then
	count=`printf "%s" "$failed" | wc -c | tr -d ' '`
	echo "FAIL: $count tests failed"
	exit 1
    else
	echo "PASS: all tests gave expected results"
	exit 0
    fi
}

Cleanup() { Sys rm -f Seppuku* OK.class; }

set -u

checkOutput() {
    name="$1" expected="$2" got="$3"
    printf "$name:\n"; cat "$got"
    if test -z "$expected"; then
	test "`cat $got`" != "" && \
	    Fail "Unexpected $name: `cat $got`"
    else
	grep "$expected" "$got" >/dev/null || \
	    Fail "Expected \"$expected\", got `cat $got`"
    fi
}

CheckCommandResults() {
    expectedRC="$1" expectedOut="$2" expectedErr="$3"; shift 3
    saveFailed="${failed}"
    "$@" >TmpTest.Out 2>TmpTest.Err; rc="$?";
    printf "==> %s (rc=%d)\n" "$*" "$rc"
    checkOutput "stdout" "$expectedOut" "TmpTest.Out"
    checkOutput "stderr" "$expectedErr" "TmpTest.Err"
    test "${saveFailed}" = "${failed}" && \
	echo "PASS: command completed as expected"
    Sys rm -f TmpTest.Out TmpTest.Err
}

Run() {
    expectedRC="$1" expectedOut="$2" expectedErr="$3" mainBody="$4"
    cat > Seppuku.java <<EOJAVA
import static java.lang.Thread.*;
import static java.lang.System.*;

class OK implements UncaughtExceptionHandler {
    public void uncaughtException(Thread t, Throwable e) {
	out.println("OK");
    }
}

class NeverInvoked implements UncaughtExceptionHandler {
    public void uncaughtException(Thread t, Throwable e) {
	err.println("Test failure: This handler should never be invoked!");
    }
}

public class Seppuku extends Thread implements Runnable {
    public static void seppuku() { throw new RuntimeException("Seppuku!"); }

    public void run() {	seppuku(); }

    public static void main(String[] args) throws Exception {
	$mainBody
    }
}
EOJAVA

    Sys "$javac" ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} "Seppuku.java"
    CheckCommandResults "$expectedRC" "$expectedOut" "$expectedErr" \
	"$java" "Seppuku"
    Cleanup
}

#----------------------------------------------------------------
# A thread is never alive after you've join()ed it.
#----------------------------------------------------------------
Run 0 "OK" "Exception in thread \"Thread-0\".*Seppuku" "
    Thread t = new Seppuku();
    t.start(); t.join();
    if (! t.isAlive())
	out.println(\"OK\");"

#----------------------------------------------------------------
# Even the main thread is mortal - here it terminates "abruptly"
#----------------------------------------------------------------
Run 1 "OK" "Exception in thread \"main\".*Seppuku" "
    final Thread mainThread = currentThread();
    new Thread() { public void run() {
	try { mainThread.join(); }
	catch (InterruptedException e) {}
	if (! mainThread.isAlive())
	    out.println(\"OK\");
    }}.start();
    seppuku();"

#----------------------------------------------------------------
# Even the main thread is mortal - here it terminates normally.
#----------------------------------------------------------------
Run 0 "OK" "" "
    final Thread mainThread = currentThread();
    new Thread() { public void run() {
	try { mainThread.join(); }
	catch (InterruptedException e) {}
	if (! mainThread.isAlive())
	    out.println(\"OK\");
    }}.start();"

#----------------------------------------------------------------
# Check uncaught exception handler mechanism on the main thread.
# Check that thread-level handler overrides global default handler.
#----------------------------------------------------------------
Run 1 "OK" "" "
    currentThread().setUncaughtExceptionHandler(new OK());
    setDefaultUncaughtExceptionHandler(new NeverInvoked());
    seppuku();"

Run 1 "OK" "" "
    setDefaultUncaughtExceptionHandler(new OK());
    seppuku();"

#----------------------------------------------------------------
# Check uncaught exception handler mechanism on non-main threads.
#----------------------------------------------------------------
Run 0 "OK" "" "
    Thread t = new Seppuku();
    t.setUncaughtExceptionHandler(new OK());
    t.start();"

Run 0 "OK" "" "
    setDefaultUncaughtExceptionHandler(new OK());
    new Seppuku().start();"

#----------------------------------------------------------------
# Test ThreadGroup based uncaught exception handler mechanism.
# Since the handler for the main thread group cannot be changed,
# there are no tests for the main thread here.
#----------------------------------------------------------------
Run 0 "OK" "" "
    setDefaultUncaughtExceptionHandler(new NeverInvoked());
    new Thread(
	new ThreadGroup(\"OK\") {
	    public void uncaughtException(Thread t, Throwable e) {
		out.println(\"OK\");}},
	new Seppuku()
    ).start();"

Cleanup

Bottom Line
