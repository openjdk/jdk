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
 * JDK-8014647: Allow class-based overrides to be initialized with a ScriptFunction
 *
 * @test
 * @run
 */

var RunnableImpl1 = Java.extend(java.lang.Runnable, function() { print("I'm runnable 1!") })
var RunnableImpl2 = Java.extend(java.lang.Runnable, function() { print("I'm runnable 2!") })
var r1 = new RunnableImpl1()
var r2 = new RunnableImpl2()
var RunnableImpl3 = Java.extend(RunnableImpl2);
var r3 = new RunnableImpl3({ run: function() { print("I'm runnable 3!") }})
r1.run()
r2.run()
r3.run()
print("r1.class !== r2.class: " + (r1.class !== r2.class))
print("r2.class !== r3.class: " + (r2.class !== r3.class))
