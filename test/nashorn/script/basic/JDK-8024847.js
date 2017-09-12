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
 * JDK-8024847: Java.to should accept mirror and external JSObjects as array-like objects as well
 *
 * @test
 * @run
 */

var global = loadWithNewGlobal({ name: "test", script:"this" });
var arr = new global.Array(2, 4, 6, 8);
var jarr = Java.to(arr, "int[]");
for (var i in jarr) {
    print(jarr[i]);
}

arr = null;
jarr = null;

// external JSObjects
var JSObject = Java.type("jdk.nashorn.api.scripting.JSObject");
var arr = new JSObject() {
    getMember: function(name) {
        return name == "length"? 4 : undefined;
    },

    hasMember: function(name) {
        return name == "length";
    },

    getSlot: function(idx) {
        return idx*idx;
    },

    hasSlot: function(idx) {
        return true;
    }
};

var jarr = Java.to(arr, "int[]");
for (var i in jarr) {
    print(jarr[i]);
}

arr = null;
jarr = null;

// List conversion
var arr = global.Array("hello", "world");
var jlist = Java.to(arr, java.util.List);
print(jlist instanceof java.util.List);
print(jlist);

arr = null;
jlist = null;

// external JSObject
var __array__ =  [ "nashorn", "js" ];

var obj = new JSObject() {

    hasMember: function(name) {
        return name in __array__;
    },

    hasSlot: function(idx) {
        return idx in __array__;
    },

    getMember: function(name) {
        return __array__[name];
    },

    getSlot: function(idx) {
        return __array__[idx];
    }
}

var jlist = Java.to(obj, java.util.List);
print(jlist instanceof java.util.List);
print(jlist);

var obj = new JSObject() {
    getMember: function(name) {
        if (name == "valueOf") {
            return new JSObject() {
                isFunction: function() {
                    return true;
                },
                call: function(thiz) {
                    return 42;
                }
            };
        }
    }
};

print(32 + obj);
