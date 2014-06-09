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
 * finally-catchalls: should not be able to read, write, call properties from null literal.
 *
 * @test
 * @run
 */

function test1() {
    try {
    print("try");
    throw "ex";
    } finally {
    print("finally");
    }
}

function test2() {
    try {
    print("try");
    } finally {
    print("finally");
    }
}

function test3() {
    try {
    print("try");
    return;
    } finally {
    print("finally");
    }
}

function test4() {
    var i = 0;
    while (i<10) {
    try {
        print("try "+i);
        i++;
        continue;
    } finally {
        print("finally "+i);
    }
    }
    print(i);
}

function test5() {
    var i = 0;
    while (i<10) {
    try {
        print("try "+i);
        i++;
        break;
    } finally {
        print("finally "+i);
    }
    }
    print(i);
}

function test6() {
    var i = 0;
    while (i<10) {
    try {
        print("try "+i);
        if (i == 5)
        break;
        i++;
    } finally {
        print("finally "+i);
    }
    }
    print(i);
}

print("\ntest 1\n");
try {
    test1();
} catch (e) {
    print("got e");
}

print("\ntest 2\n");
test2();

print("\ntest 3\n");
test3();

print("\ntest 4\n");
test4();

print("\ntest 5\n");
test5();

print("\ntest 6\n");
test6();

