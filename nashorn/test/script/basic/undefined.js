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
 * Check that trying to operate on results in exception.
 *
 * @test
 * @run
 */

var x;

print("x undefined? " + (x === undefined));

try {
    print(x.foo);
} catch (e) {
    print(e);
}

try {
    x.bar = 34;
} catch (e) {
    print(e);
}

try {
    print(x[1]);
} catch (e) {
    print(e);
}

try {
    x[0] = 12;
} catch (e) {
    print(e);
}

try {
    x.func();
} catch(e) {
    print(e);
}

try {
    delete x[0];
} catch (e) {
    print(e);
}

try {
    delete x.foo;
} catch (e) {
    print(e);
}

try {
    print(Object.getPrototypeOf(x));
} catch (e) {
    print(e);
}

// iteration should be empty
for (y in x) {
    print(y);
}

try {
    with (x) {
        print(foo);
    }
} catch (e) {
    print(e);
}
