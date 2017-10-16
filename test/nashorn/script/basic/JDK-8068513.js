/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8068513: Adding elements to a javascript 'object' (a map) is slow
 *
 * @test
 * @run
 */

var map = {};
var keys = [];
var values = [];

for (i = 0; i < 5000; i++) {
    var key = 'key' + i;
    var value = i;
    keys.push(key);
    values.push(value);
    map[key] = value;
}

function testAssertions() {
    Assert.assertTrue(Object.keys(map).length === values.length);

    var c = 0;
    for (var k in map) {
        Assert.assertTrue(k === keys[c]);
        Assert.assertTrue(map[k] === values[c]);
        c++;
    }

    Assert.assertTrue(c === values.length);
}

// redefine existing property
Object.defineProperty(map, "key2000", { enumerable: true, get: function() { return 'new value 2000' } });
values[2000] = 'new value 2000';

testAssertions();

// define new property
Object.defineProperty(map, "defined property", { enumerable: true, configurable: true, get: function() { return 13 } });
keys.push('defined property');
values.push(13);

testAssertions();

// delete and redefine
delete map.key3000;
map.key3000 = 'new value';
keys.splice(3000, 1);
values.splice(3000, 1);
keys.push('key3000');
values.push('new value');

testAssertions();

// delete all properties
while (values.length > 0) {
    values.pop();
    delete map[keys.pop()];
}

testAssertions();

// add a few new ones
for (var i = 0; i < 1000; i++) {
    keys.push('k' + i);
    values.push('v' + i);
    map['k' + i] = 'v' + i;
}

testAssertions();

