/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8073846: Javascript for-in loop returned extra keys
 *
 * @test
 * @run
 */

var obj = {};

var list = [
    '2100000',
    '420000',
    '430000'
];

for (var i = 0; i < list.length; i++) {
    if (obj[list[i]]) print("duplicate: " + list[i]);
    obj[list[i]] = 'obj' + list[i]
}

var count = 0;
for (var a in obj) {
    count++;
    if ('obj' + a !== obj[a]) {
        throw 'wrong key or value: ' + a + ', ' + obj[a];
    }
}

if (count !== 3) {
    throw 'wrong entry count: ' + count;
}
