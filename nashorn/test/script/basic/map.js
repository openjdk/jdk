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
 * Tests for java.util.Map behavior in Nashorn
 *
 * @test
 * @run
 */
var m = new (Java.type("java.util.LinkedHashMap"));
print("m.class.name=" + Java.typeName(m.class)) // Has "class" property like any POJO

var empty_key = "empty"

print("m = " + m)
print("m.empty = " + m.empty) // prints "true"
print("m['empty'] = " + m['empty']) // prints "true"; item not found, default to property
print("m[empty_key] = " + m[empty_key]) // prints "true"; item not found, default to property

m.put("empty", "foo")

print("m = " + m)
print("m.empty = " + m.empty) // prints "false"
print("m['empty'] = " + m['empty'])
print("m[empty_key] = " + m[empty_key]) // prints "foo"

print("m.bwah = " + m.bwah) // prints "undefined"
print("m['bwah'] = " + m['bwah']) // prints "undefined"

m.put("twonk", "ding")
print("m.twonk = " + m.twonk) // prints "ding"
print("m['twonk'] = " + m['twonk']) // prints "ding"

print("m.size()=" + m.size())

print("--for each begin--")
for each (i in m.keySet()) {
  print(i)
}
print("--for each end--")
