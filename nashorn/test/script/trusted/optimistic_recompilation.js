/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * Ask Debug for an event log of favourable events instead of using --log flags printing to screen
 * @test
 * @bug 8037086,8038398
 * @fork
 * @option -Dnashorn.debug=true
 * @option --log=recompile:quiet
 * @option --optimistic-types=true
 * @run
 */

var forName       = java.lang.Class["forName(String)"];
var RuntimeEvent  = forName("jdk.nashorn.internal.runtime.events.RuntimeEvent").static;
var getValue      = RuntimeEvent.class.getMethod("getValue");
var RewriteException = forName("jdk.nashorn.internal.runtime.RewriteException").static;
var getReturnType    = RewriteException.class.getMethod("getReturnType");
var RecompilationEvent = forName("jdk.nashorn.internal.runtime.events.RecompilationEvent").static;
var getReturnValue     = RecompilationEvent.class.getMethod("getReturnValue");
var setReturnTypeAndValue = [];
var expectedValues = [];

function checkExpectedRecompilation(f, expectedValues, testCase) {
    Debug.clearRuntimeEvents();
    print(f());
    events = Debug.getRuntimeEvents();
    //make sure we got runtime events
    print("events = " + (events.toString().indexOf("RuntimeEvent") != -1));
    if (events.length ==  expectedValues.length) {
        for (var i in events) {
            var e = events[i];
            var returnValue = getReturnValue.invoke(e);
            if (typeof returnValue != 'undefined') {
            setReturnTypeAndValue[i] = [getReturnType.invoke(getValue.invoke(e)), returnValue];
            } else {
                returnValue = "undefined";
                setReturnTypeAndValue[i] = [getReturnType.invoke(getValue.invoke(e)), returnValue];
            }
            if (!setReturnTypeAndValue[i].toString().equals(expectedValues[i].toString())) {
                fail("The return values are not as expected. Expected value: " + expectedValues[i] + " and got: " + setReturnTypeAndValue[i] + " in test case: " + f);
            }
        }
    } else {
        fail("Number of Deoptimizing recompilation is not correct, expected: " + expectedValues.length + " and found: " + events.length + " in test case: " + f);
    }
}

checkExpectedRecompilation(function divisionByZeroTest() {var x = { a: 2, b:1 }; x.a = Number.POSITIVE_INFINITY; x.b = 0; print(x.a/x.b); return 1;},
                           expectedValues =[['double', 'Infinity']]);
checkExpectedRecompilation(function divisionWithRemainderTest() {var x = { a: 7, b:2 }; print(x.a/x.b); return 1;}, expectedValues =[['double', '3.5']]);
checkExpectedRecompilation(function infinityMultiplicationTest() {var x = { a: Number.POSITIVE_INFINITY, b: Number.POSITIVE_INFINITY}; print(x.a*x.b); return 1;},
                           expectedValues =[['double', 'Infinity']]);
checkExpectedRecompilation(function maxValueMultiplicationTest() {var x = { a: Number.MAX_VALUE, b: Number.MAX_VALUE}; print(x.a*x.b); return 1;},
                           expectedValues =[['double', '1.7976931348623157e+308']]);
checkExpectedRecompilation(function divisionByInfinityTest() {var x = { a: -1, b: Number.POSITIVE_INFINITY}; print(x.a/x.b); return 1;},
                           expectedValues =[['double', 'Infinity']]);
checkExpectedRecompilation(function divisionByStringTest() {var x = { a: Number.POSITIVE_INFINITY, b: 'Hello'}; print(x.a/x.b); return 1;},
                           expectedValues =[['double', 'Infinity']]);
checkExpectedRecompilation(function nestedFunctionTest() {var a=3,b,c; function f() {var x = 2, y =1; function g(){var y = x; var z = a; z = x*y; print(a*b)} g()}f(); return 1;},
                           expectedValues =[['object', 'undefined']]);
checkExpectedRecompilation(function functionTest(a,b,c) { d = (a + b) * c; print(d); return 1;}, expectedValues =[['double', 'NaN']]);
checkExpectedRecompilation(function andTest(a,b) { d = a && b; print(d); return 1;}, expectedValues =[['object', 'undefined']]);
