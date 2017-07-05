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
 * NASHORN-703
 *
 * Self modifying assignments and ++/-- operators had two issues
 * 1) the base was loaded twice
 * 2) if the base was anything but an IdentNode, AccessNode or IndexNode and in the scope, it would
 *    only be evaluated once, leading to bytecode stack underflow
 *
 * This file is split into two tests as the presence of eval affects the whole script
 *
 * @test
 * @run 
 */

function template() {
    this.count = 17;
}

//self assignment to accessnode in scope
function test1() {
    a.count++;
}

function test2() {
    a2[0].count++;
}

function test3() {
    a3[0]++;
}

function test4() {
    a4 *= 17;
}

function test5() {
    a5.count *= 17;
}

function test6() {
    a6[0].count *= 17;
}

function test7() {
    a7[0] *= 17;
}

function tpl() {
    return new template();
}

function count() {
    tpl().count++;
}

var a = new template();
test1();
print(a.count);

var a2 = [new template()];
test2();
print(a2[0].count);

var a3 = [1];
test3();
print(a3);

var a4 = 4711;
test4();
print(a4);

var a5 = new template();
test5();
print(a5.count);

var a6 = [new template()];
test6();
print(a6[0].count);

var a7 = [1];
test7();
print(a7[0]);








