/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * this apply with extra arguments
 *
 * @subtest
 */

function func(x, y, z) {
    print(x, y, z);
}

function g() {
    func.apply(this, arguments);
}
function h() {
    func.apply(this, arguments, 23);
}
function i() {
    func.apply(this, arguments, 23, 4711);
}
function j() {
    func.apply(this, arguments, 23, 4711, "apa", "dingo", "gorilla");
}
function k() {
    func.apply(this, arguments, 23);
}
function l() {
    func.apply(this, [23, "apa", "gorilla", "dingo"], 17);
}
function m() {
    func.apply(this, [23, "apa", "gorilla", "dingo"]);
}
function n() {
    func.apply(this, "significant");
}

g(1,2);
g(1,2,3);
g(1,2,3,4);

h(1,2);
h(1,2,3);
h(1,2,3,4);

i(1,2);
i(1,2,3);
i(1,2,3,4);

j(1,2);
j(1,2,3);
j(1,2,3,4);

k(1,2);
k(1,2,3);
k(1,2,3,4);

l(1,2);
l(1,2,3);
l(1,2,3,4);

m(1,2);
m(1,2,3);
m(1,2,3,4);

try {
    n(1,2);
} catch (e) {
    print(e);
}
try {
    n(1,2,3);
} catch (e) {
    print(e);    
}

try {
    n(1,2,3,4);
} catch (e) {
    print(e);   
}
