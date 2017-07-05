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
 * JDK-8028210: Missing conversions on array index expression
 *
 * @test
 * @run
 */

var array = [1, 2];
var key1 = [[[0]]];
var key2 = new String("1");
var key3 = {
    toString: function() {
        print("toString called");
        return "2";
    }
};

print(array[key1]);
print(array[key2]);
array[key3] = 3;
print(array[key3]);
print(key3 in array);
print(array.hasOwnProperty(key3));
print(delete array[key3]);
print(array[key3]);

// string access
print("abc"[key1]);
print("abc"[key2]);
print("abc"[key3]);

// arguments object
(function(a, b, c) {
    print(arguments[key3]);
    delete arguments[key3];
    print(arguments[key3], c);
})(1, 2, 3);

// int keys
array = [];
array[4294967294] = 1;
print(array[-2]);
print(array[4294967294]);
print(-2 in array);
print(4294967294 in array);
print(delete(array[-2]));
print(array[4294967294]);
print(delete(array[4294967294]));
print(array[4294967294]);

array = [];
array[-2] = 1;
print(array[-2]);
print(array[4294967294]);
print(-2 in array);
print(4294967294 in array);
print(delete(array[4294967294]));
print(array[-2]);
print(delete(array[-2]));
print(array[-2]);
