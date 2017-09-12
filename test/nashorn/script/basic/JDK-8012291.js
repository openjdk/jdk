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
 * JDK-8012291: NativeArray is inconsistent in using long for length and index in some places and int for the same in other places
 *
 * @test
 * @run
 */

// Make sure JSON parser correctly handles large array keys
var obj = JSON.parse('{"4294967294": 1}');
print(obj[4294967294]);

// Make sure Array.prototype.sort handles large index correctly
obj.length = 4294967295;
Array.prototype.sort.call(obj);
print(obj[0]);
print(obj[4294967294]);
print(obj.length);

var arr = [];
arr[4294967294] = 1;
try {
    new Int32Array(arr);
} catch (e) {
    print(e);
}
