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
 * NASHORN-136 :  binary addition operator does not work as per the spec.
 *
 * @test
 * @run
 */

var obj1 = {
    valueOf: function() {
        print("obj1.valueOf");
        return 1;
    }, 

    toString: function() {
        print("obj1.toString");
        return 0;
    }
};

print("obj1 is " + obj1);
print(obj1 + 10);

var obj2 = {
    valueOf: function() {
        print("obj2.valueOf");
        return 2;
    },

    toString: function() {
        print("obj2.toString");
        return "hello";
    }
};

print("obj2 is " + obj2);
print(obj2 + 22);

// valueOf on each object and then add
print(obj1 + obj2);

var obj3 = {
    toString: function() {
        print("obj3.toString");
        return "world";
    }
};

print(obj3 + 12);
print("hello " + obj3);

print(obj1 + obj3);
print(obj2 + obj3);
print(obj3 + obj3);

