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
 * JDK-8015355: Array.prototype functions don't honour non-writable length and / or index properties
 * 
 * @test
 * @run
 */

function fail(msg) {
    print(msg);
}

function check(callback) {
    try {
        callback();
        fail("TypeError expected for " + callback);
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("TypeError expected, got " + e);
        }
    }
}

var array = Object.defineProperty([],"length", { writable: false });

check(function() {
    array.push(0)
});

check(function() {
    array.pop()
});

check(function() {
    Object.defineProperty([,,],"0",{ writable: false }).reverse();
});

check(function() {
    array.shift()
});

check(function() {
    array.splice(0)
});

check(function() {
    array.unshift()
});

// try the above via call

check(function() {
    Array.prototype.push.call(array, 0);
});

check(function() {
    Array.prototype.pop.call(array);
});

check(function() {
    Array.prototype.shift.call(array);
});

check(function() {
    Array.prototype.unshift.call(array);
});

check(function() {
    Array.prototype.splice.call(array, 0);
});

check(function() {
    Array.prototype.reverse.call(Object.defineProperty([,,],"0",{ writable: false }));
});

// try the above via apply

check(function() {
    Array.prototype.push.apply(array, [ 0 ]);
});

check(function() {
    Array.prototype.pop.apply(array);
});

check(function() {
    Array.prototype.shift.apply(array);
});

check(function() {
    Array.prototype.unshift.apply(array);
});

check(function() {
    Array.prototype.splice.apply(array, [ 0 ]);
});

check(function() {
    Array.prototype.reverse.apply(Object.defineProperty([,,],"0",{ writable: false }));
});
