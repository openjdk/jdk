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
 * Tests for convert method of ScriptUtils.
 *
 * @test
 * @run
 */

var ScriptUtils = Java.type("jdk.nashorn.api.scripting.ScriptUtils");
obj = { valueOf: function() { print("hello"); return 43.3; } };

// object to double
print(ScriptUtils.convert(obj, java.lang.Number.class));

// array to List
var arr = [3, 44, 23, 33];
var list = ScriptUtils.convert(arr, java.util.List.class);
print(list instanceof java.util.List)
print(list);

// object to Map
obj = { foo: 333, bar: 'hello'};
var map = ScriptUtils.convert(obj, java.util.Map.class);
print(map instanceof java.util.Map);
for (m in map) {
   print(m + " " + map[m]);
}

// object to String
obj = { toString: function() { print("in toString"); return "foo" } };
print(ScriptUtils.convert(obj, java.lang.String.class));

// array to Java array
var jarr = ScriptUtils.convert(arr, Java.type("int[]"));
print(jarr instanceof Java.type("int[]"));
for (i in jarr) {
    print(jarr[i]);
}

