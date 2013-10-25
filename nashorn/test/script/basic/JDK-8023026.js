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
 * JDK-8023026: Array.prototype iterator functions like forEach, reduce should work for Java arrays, lists
 *
 * @test
 * @run
 */

function checkIterations(obj) {
    if (typeof obj.getClass == 'function') {
        print("iterating on an object of " + obj.getClass());
    } else {
        print("iterating on " + String(obj));
    }

    Array.prototype.forEach.call(obj,
        function(x) { print("forEach " + x); });

    print("left sum " + Array.prototype.reduce.call(obj,
        function(x, y) { print("reduce", x, y); return x + y; }));

    print("right sum " + Array.prototype.reduceRight.call(obj,
        function(x, y) { print("reduceRight", x, y); return x + y; }));

    print("squared " + Array.prototype.map.call(obj,
        function(x) x*x));
}

var array = new (Java.type("int[]"))(4);
for (var i in array) {
    array[i] = i;
}

checkIterations(array);

var list = new java.util.ArrayList();
list.add(1);
list.add(3);
list.add(5);
list.add(7);

checkIterations(list);

var mirror = loadWithNewGlobal({
    name: "test",
    script: "[2, 4, 6, 8]"
});

checkIterations(mirror);
