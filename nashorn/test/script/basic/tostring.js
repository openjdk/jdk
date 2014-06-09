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
 * Make sure that then internal ToString procedure works as expected.
 *
 * @test
 * @run
 */

function show(obj) {
    print(typeof(obj) + ", " + obj);
}

var obj = {};
show(obj);
obj = new Boolean(false);
show(obj);
obj = new Number(Math.PI);
show(obj);
obj = new String("hello");
show(obj);
obj = java.util;
show(obj);
obj = java.util.Map;
show(obj);
// For java objects, java.lang.Object.toString is called.
obj = new java.util.HashMap();
// should print "{}" for empty map
show(obj);
// should print "[]" for empty list
obj = new java.util.ArrayList();
show(obj);
