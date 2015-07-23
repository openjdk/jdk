/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8131683: Delete fails over multiple scopes
 *
 * @test
 * @run
 */

a = 1;
b = 2;
c = 3;

var A = 1;
var B = 2;
var C = 3;
function D() {}

print((function() {
    var x; // force creation of scope
    (function() { x; })();
    return delete a;
})());

print((function() {
    eval("");
    return delete b;
})());

print((function() {
    return eval("delete c");
})());

print((function() {
    eval("d = 4");
    return eval("delete d");
})());

print(typeof a);
print(typeof b);
print(typeof c);
print(typeof d);

print((function() {
    var x; // force creation of scope
    (function() { x; })();
    return delete A;
})());

print((function() {
    eval("");
    return delete B;
})());

print((function() {
    return eval("delete C");
})());

print((function() {
    eval("");
    return delete D;
})());

print(typeof A);
print(typeof B);
print(typeof C);
print(typeof D);

