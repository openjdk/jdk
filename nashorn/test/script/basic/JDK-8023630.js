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
 * JDK-8023630: Implement Java.super() as the preferred way to call super methods
 *
 * @test
 * @run
 */

var CharArray = Java.type("char[]")
var jString = Java.type("java.lang.String")
var Character = Java.type("java.lang.Character")

function capitalize(s) {
    if(s instanceof CharArray) {
        return new jString(s).toUpperCase()
    }
    if(s instanceof jString) {
        return s.toUpperCase()
    }
    return Character.toUpperCase(s) // must be int
}

var sw = new (Java.type("java.io.StringWriter"))

var FilterWriterAdapter = Java.extend(Java.type("java.io.FilterWriter"))

var cw = new FilterWriterAdapter(sw) {
    write: function(s, off, len) {
        s = capitalize(s)
        // Must handle overloads by arity
        if(off === undefined) {
            cw_super.write(s, 0, s.length())
        } else if (typeof s === "string") {
            cw_super.write(s, off, len)
        }
    }
}
var cw_super = Java.super(cw)

cw.write("abcd")
cw.write("e".charAt(0))
cw.write("fgh".toCharArray())
cw.write("**ijk**", 2, 3)
cw.write("***lmno**".toCharArray(), 3, 4)
cw.flush()
print(sw)

// Can invoke super for Object methods
print("cw_super has hashCode(): " + (typeof cw_super.hashCode === "function"))
print("cw_super has super equals(): " + (typeof cw_super.equals === "function"))
// Can't invoke super for final methods
print("cw_super has no getClass(): " + (typeof cw_super.getClass === "undefined"))
print("cw_super has no wait(): " + (typeof cw_super.wait === "undefined"))

var r = new (Java.type("java.lang.Runnable"))(function() {})
var r_super = Java.super(r)

// Can't invoke super for abstract methods
print("r_super has no run(): " + (typeof r_super.run === "undefined"))
// Interfaces can also invoke super Object methods
print("r_super has hashCode(): " + (typeof r_super.hashCode === "function"))
print("r_super has equals(): " + (typeof r_super.equals === "function"))
// But still can't invoke final methods
print("r_super has no getClass(): " + (typeof r_super.getClass === "undefined"))
print("r_super has no wait(): " + (typeof r_super.wait === "undefined"))

var name = "write"
print("cw_super can access write through [] getter: " + (typeof cw_super[name] === "function"))
var name = "hashCode"
print("cw_super can access hashCode through [] getter: " + (typeof cw_super[name] === "function"))
var name = "getClass"
print("cw_super can not access getClass through [] getter: " + (typeof cw_super[name] === "undefined"))
