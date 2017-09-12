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
 * NASHORN-37 :  object and array properties defined with special keys can be accessed be by special or string keys
 *
 * @test
 * @run
 */

var obj = {};

obj[true] = "hello";
print(obj["true"]);
print(obj[true]);

obj["false"] = 33;
print(obj[false]);
print(obj['false']);

obj[null] = "foo";
print(obj["null"]);
print(obj[null]);

obj[undefined] = "bar";
print(obj["undefined"]);
print(obj["undefined"]);

obj[33] = 343;
print(obj[33]);
print(obj['33']);

obj['2'] = 3.14;
print(obj[2]);
print(obj['2']);

var key = {
    toString: function() {
        print("key.toString called");
        return 'key';
    }
};

obj[key] = 'value';
print(obj[key]);
print(obj['key']);

var array = [ "foo", "bar" ];

print(array[0]);
print(array["0"]);

print(array[1]);
print(array["1"]);

