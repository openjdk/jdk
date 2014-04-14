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
 * Tests for java.util.List behavior in Nashorn
 *
 * @test
 * @run
 */
var l = new java.util.ArrayList();
print("l.class.name=" + Java.typeName(l.class)) // Has "class" property like any POJO

l.add("foo")
l.add("bar")

print("l.length=" + l.length) // works, maps to l.size()
print("l.size()=" + l.size()) // this will work

print("l[0]=" + l[0])
print("l[1]=" + l[1])

print("--for each begin--")
for each (i in l) {
  print(i)
}
print("--for each end--")

l[1] = "a"
print("l[0]=" + l[0])
print("l[1]=" + l[1])

print("l[0.9]=" + l[0.9]) // non-integer indices don't round up
print("l['blah']=" + l['blah']) // non-number indices don't retrieve anything...
var size_name = "size"
print("l[size_name]()=" + l[size_name]()) // ... but existing methods can be accessed with []

expectException(2) // Java lists don't auto-expand to accommodate new indices
expectException(java.lang.Double.POSITIVE_INFINITY) // Dynalink will throw IOOBE
expectException(java.lang.Double.NEGATIVE_INFINITY) // Dynalink will throw IOOBE

function expectException(index) {
    try {
        l[index] = "x"
        print("Not caught out-of-bounds assignment for " + index)
    }  catch(e) {
        print(e)
    }
}
