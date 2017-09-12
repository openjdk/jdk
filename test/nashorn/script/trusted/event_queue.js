/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Debug.eventqueue test - instead of screen scraping, test the concept of asking Debug for
 * an event log of favourable events.
 *
 * @test
 * @fork
 * @option -Dnashorn.debug=true
 * @option --log=recompile:quiet
 * @option --optimistic-types=true
 * @run
 */

print(Debug);
print();

var Reflector     = Java.type("jdk.nashorn.test.models.Reflector");
var forName       = java.lang.Class["forName(String)"];
var RuntimeEvent  = forName("jdk.nashorn.internal.runtime.events.RuntimeEvent").static;
var getValue      = RuntimeEvent.class.getMethod("getValue");
var getValueClass = RuntimeEvent.class.getMethod("getValueClass");

print(RuntimeEvent);

var RewriteException = forName("jdk.nashorn.internal.runtime.RewriteException").static;
var getReturnType    = RewriteException.class.getMethod("getReturnType");

print(RewriteException);

var a = [1.1, 2.2];
function f() {
    var sum = 2;
    for (var i = 0; i < a.length; i++) {
    sum *= a[i];
    }
    return sum;
}

function g() {
    var diff = 17;
    for (var i = 0; i < a.length; i++) {
    diff -= a[i];
    }
    return diff;
}

//kill anything that may already be in the event queue from earlier debug runs
Debug.clearRuntimeEvents();

print();
print(f());
print(g());

print();
events = Debug.getRuntimeEvents();
print("Done with " + events.length + " in the event queue");
//make sure we got runtime events
print("events = " + (events.toString().indexOf("RuntimeEvent") != -1));
print("events.length = " + events.length);

var lastInLoop = undefined;
for (var i = 0; i < events.length; i++) {
    var e = events[i];
    print("event #" + i);
    print("\tevent class=" + e.getClass());
    print("\tvalueClass in event=" + Reflector.invoke(getValueClass, e));
    var v = Reflector.invoke(getValue, e);
    print("\tclass of value=" + v.getClass());
    print("\treturn type=" + Reflector.invoke(getReturnType, v));
    lastInLoop = events[i];
}

print();
print("in loop last class = " + lastInLoop.getClass());
print("in loop last value class = " + Reflector.invoke(getValueClass, lastInLoop));
var rexInLoop = Reflector.invoke(getValue, lastInLoop);
print("in loop rex class = " + rexInLoop.getClass());
print("in loop rex return type = " + Reflector.invoke(getReturnType, rexInLoop));

//try last runtime events
var last = Debug.getLastRuntimeEvent();
//the code after the loop creates additional rewrite exceptions
print();
print(last !== lastInLoop);
print();

print("last class = " + last.getClass());
print("last value class = " + Reflector.invoke(getValueClass, last));
var rex = Reflector.invoke(getValue, last);
print("rex class = " + rex.getClass());
print("rex return type = " + Reflector.invoke(getReturnType, rex));

//try the capacity setter
print();
print(Debug.getEventQueueCapacity());
Debug.setEventQueueCapacity(2048);
print(Debug.getEventQueueCapacity());

//try clear events
print();
Debug.clearRuntimeEvents();
print(Debug.getRuntimeEvents().length);

