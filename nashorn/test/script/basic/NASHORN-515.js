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

/*
 * NASHORN-515 : Switch default handling was broken.
 *
 * @test
 * @run
 */

function a() {
    var x = (3.14-2.14);

    switch (x) {
    case 1:
    print("--1");
    break;
    case 2:
    print("--2");
    break;
    default:
    print("default");
    break;
    }
}

//NASHORN-529 - original fix was incomplete for primitive types
function b() {
    var index = 256.3;
    switch (index) {
    case 0x00:
    case 0x01:
    print("one zero");
    break;
    default:
    print("default");
    break;
    }
}

//NASHORN-529 - original fix was incomplete for primitive types
function c() {
    var index = 0x1fffffffff;
    switch (index) {
    case 0x00:
    case 0x01:
    print("one zero");
    break;
    default:
    print("default");
    }
    --index;
}

function d() {
    var x = (3.14-1.14);

    switch(x) {
    case 1:
    print("--1"); break;
    case 2:
    print("--2"); break;
    case 3:
    print("--3"); break;
    case 4:
    print("--4"); break;
    default:
    print("default");
    }
}

function e() {
    var y = 2147483647;

    switch(y) {
    case -2147483648:
    print("MIN_INT"); break;
    case -2147483647:
    print("MIN_INT+1"); break;
    case 2147483647:
    print("MAX_INT"); break;
    case 1:
    print("--1"); break;
    case 2:
    print("--2"); break;
    case 3:
    print("--3"); break;
    case 4:
    print("--4"); break;
    default:
    print("default");
    }
}

function f() {
    var z = 2;

    switch(z) {
    case -2147483648:
    print("MIN_INT"); break;
    case -2147483647:
    print("MIN_INT+1"); break;
    case 2147483647:
    print("MAX_INT"); break;
    case 1:
    print("--1"); break;
    case 2:
    print("--2 first"); break;
    case 2:
    print("--2 second"); break;
    case 3:
    print("--3"); break;
    case 4:
    print("--4"); break;
    default:
    print("default");
    }
}

a();
b();
c();
d();
e();
f();
