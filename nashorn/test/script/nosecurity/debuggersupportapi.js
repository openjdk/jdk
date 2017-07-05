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
 * JDK-8044798: API for debugging Nashorn
 *
 * @test
 * @run
 */

// Basic API class, method, field existence checks.

// The following classes and the associated methods and fields are used as
// private debugger interface. Though private/implementation defined, nashorn
// code should not be changed to remove these classes, fields and methods.
// The test takes signatures of debugger interface and stores in .EXPECTED file.
// If any incompatible change is made to nashorn to break any of these, this
// test will fail.

var Arrays = Java.type("java.util.Arrays");
var DebuggerSupport = Java.type("jdk.nashorn.internal.runtime.DebuggerSupport");

print(DebuggerSupport.class);
print();
var methods = DebuggerSupport.class.declaredMethods;
Arrays.sort(methods, function(m1, m2) m1.name.compareTo(m2.name));
for each (var mth in methods) {
    switch (mth.name) {
        case "eval":
        case "notifyInvoke":
        case "getSourceInfo":
        case "valueAsString":
        case "valueInfos":
            print(mth);
            break;
        case "valueInfo":
            if (mth.parameterCount == 3) {
                print(mth);
            }
            break;
    }
}
print();

var DebuggerValueDesc = Java.type("jdk.nashorn.internal.runtime.DebuggerSupport.DebuggerValueDesc");
print(DebuggerValueDesc.class);
print();
var fields = DebuggerValueDesc.class.declaredFields;
Arrays.sort(fields, function(f1, f2) f1.name.compareTo(f2.name));
for each (var fld in fields) {
    switch (fld.name) {
        case "key":
        case "expandable":
        case "valueAsObject":
        case "valueAsString":
            print(fld);
    }
}
print();

var SourceInfo = Java.type("jdk.nashorn.internal.runtime.DebuggerSupport.SourceInfo");
print(SourceInfo.class);
print();
var fields = SourceInfo.class.declaredFields;
Arrays.sort(fields, function(f1, f2) f1.name.compareTo(f2.name));
for each (var fld in fields) {
    switch (fld.name) {
        case "name":
        case "hash":
        case "url":
        case "content":
            print(fld);
    }
}
