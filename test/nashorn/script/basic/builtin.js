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
 * Make sure that the builtin functions compile in all varieties and not crash
 * or generate invalid bytecode
 *
 * @test
 * @run
 */

function t1() {
    var a = new Array();
}

function t2() {
    var a = new Array(60);
}

function t3() {
    eval("18 + 29");
}

function t4() {
    var r = new RegExp();
}

function t5(p) {
    var r = new RegExp(p);
}

function t6(p,m) {
    var r = new RegExp(p,m);
}

function t7() {
    var r = new RegExp("pattern");
}

function t8() {
    var r = new RegExp("pattern", "gm");
}

t1();
t2();
t3();
t4();
t5();
t6();
t7();
t8();

print("done");
