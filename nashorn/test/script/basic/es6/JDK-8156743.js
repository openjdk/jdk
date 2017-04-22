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
 * JDK-8156743: ES6 for..of should work for Java Maps and Sets
 *
 * @test
 * @run
 * @option --language=es6
 */

var map = new Map([
    [1, 'one'],
    [2, 'two'],
    [3, 'three']
]);

var set = new Set(['red', 'green', 'blue']);

var HashMap = Java.type("java.util.HashMap");
var jmap = new HashMap();
jmap.put(1, 'one');
jmap.put(2, 'two');
jmap.put(3, 'three');

var HashSet = Java.type("java.util.HashSet");
var jset = new HashSet();
jset.add('red');
jset.add('green');
jset.add('blue');

for(var keyvalue of map){
    print(keyvalue[0],keyvalue[1]);
}

for(var keyvalue of jmap){
    print(keyvalue[0],keyvalue[1]);
}

for(var keyvalue of map){
    print(keyvalue);
}

for(var keyvalue of jmap){
    print(keyvalue);
}

for(var element of set){
    print(element);
}

for(var element of jset){
    print(element);
}

