/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8161579: Array-like AbstractJSObject-based instance not treated as array by native array functions
 *
 * @test
 * @run
 */


var AbstractJSObject = Java.type("jdk.nashorn.api.scripting.AbstractJSObject");
var JavaStringArray = Java.type("java.lang.String[]");
var JavaArrayList = Java.type("java.util.ArrayList");

var arrayLikeJSObject = new AbstractJSObject() {
    hasMember: function(name) { return name == "length"; },
    getMember: function(name) { return name == "length" ? 3 : null; },
    hasSlot: function(slot) { return slot >= 0 && slot <= 2; },
    getSlot: function(slot) { return "abc"[slot]; },
    isArray: function() { return true; }
}

var javaStringArray = new JavaStringArray(3);
javaStringArray[0] = "x";
javaStringArray[1] = "y";
javaStringArray[2] = "z";

var javaArrayList = new JavaArrayList();
javaArrayList.add("i");
javaArrayList.add("j");
javaArrayList.add("k");

Assert.assertEquals([1, 2, 3].concat(arrayLikeJSObject).join(), "1,2,3,a,b,c");
Assert.assertEquals([1, 2, 3].concat(javaStringArray).join(), "1,2,3,x,y,z");
Assert.assertEquals([1, 2, 3].concat(javaArrayList).join(), "1,2,3,i,j,k");
Assert.assertEquals([1, 2, 3].concat("foo").join(), "1,2,3,foo");
Assert.assertEquals([1, 2, 3].concat(4).join(), "1,2,3,4");
Assert.assertEquals([1, 2, 3].concat(false).join(), "1,2,3,false");

