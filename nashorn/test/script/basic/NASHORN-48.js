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
 * NASHORN-48
 *
 * @test
 * @run
 */

function loop3() {
    for (var i = 0; i < 5; i++) {
    print(i);
    throw "ERROR";
    }
    print("dead");
}

try {
    loop3();
} catch (e) {
    print(e);
}

function loop4() {
    var i = 0;
    while (i++ < 5) {
    print(i);
    throw "ERROR";
    }
    print("dead");
}

try {
    loop4();
} catch (e) {
    print(e);
}

function loop5() {
    var i = 0;
    do {
    print(i);
    throw "ERROR";
    } while (i++ < 5);
    print("dead");
}

try {
    loop5();
} catch (e) {
    print(e);
}
