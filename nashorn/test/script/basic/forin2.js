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
 * Mostly for-in iterations through esoteric collections
 *
 * @test
 * @run
 */

for (y in {}) {
    y = 2;
}

try {
    null['foo'];
    print("error 1");
} catch(e) {
    if ((e instanceof TypeError) !== true) {
    print(e);
    }
}

try {
    with(null) x = 2;
    print("error 2");
} catch(e) {
    if((e instanceof TypeError) !== true) {
    print(e);
    }
}

try {
    for (var y in null) {
    y = 2;
    }
    print("this is ok 1");
} catch(e) {
    if ((e instanceof TypeError) !== true) {
    print(e);
    }
}

// CHECK#4
try {
    for (var z in 'bbb'.match(/aaa/)) {
    z = 2;
    }
    print("this is ok 2");
} catch(e) {
    if((e instanceof TypeError) !== true) {
    print(e);
    }
}


try {
    undefined['foo'];
    print("error 5");
} catch(e) {
    if ((e instanceof TypeError) !== true) {
    print(e);
    }
}

try {
    with(undefined) x = 2;
    print("error 6");
} catch (e) {
    if ((e instanceof TypeError) !== true) {
    print(e);
    }
}

// CHECK#3
try {
    for (var y in undefined) {
    y = 2;
    }
    print("this is ok 3");
} catch (e) {
    if ((e instanceof TypeError) !== true) {
    print(e);
    }
}

try {
    for (var z in this.foo) {
    z = 2;
    }
    print("this is ok 4");
} catch (e) {
    if ((e instanceof TypeError) !== true) {
    print(e);
    }
}
