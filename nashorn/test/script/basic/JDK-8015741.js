/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8015741 : Need a global.load function that starts with a new global scope.
 *
 * @test
 * @run
 */

var Thread = java.lang.Thread;

myGlobal = "#0";
var script1 = {name: "script 1", script: 'myGlobal = "#1"; print(myGlobal);'};
var script2 = {name: "script 2", script: 'myGlobal = "#2"; print(myGlobal);'};
var script3 = {name: "script 3", script: 'myGlobal = "#3"; print(myGlobal);'};
var script4 = {name: "script 4", script: 'myGlobal = "#4"; print(myGlobal);'};

print(myGlobal);
load(script1);
print(myGlobal);

print(myGlobal);
var thread1 = new Thread(function() { load(script2); });
thread1.start();
thread1.join();
print(myGlobal);

print(myGlobal);
loadWithNewGlobal(script3);
print(myGlobal);

print(myGlobal);
var thread2 = new Thread(function() { loadWithNewGlobal(script4); });
thread2.start();
thread2.join();
print(myGlobal);
