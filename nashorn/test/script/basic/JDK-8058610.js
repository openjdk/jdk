/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8058610: must not let long operations overflow
 *
 * @test
 * @run
 */

function mul(x) { 
    return x.foo * x.bar; 
} 
print("=== mul ===")
print(mul({foo: 2147483647,  bar: 2147483647})); // 2^31
print(mul({foo: 17179869184, bar: 2147483647})); // 2^34

function self_mul(x) {
    return x.foo *= x.bar; 
}
print("=== self_mul ===")
print(self_mul({foo: 2147483647,  bar: 2147483647})); // 2^31
print(self_mul({foo: 17179869184, bar: 2147483647})); // 2^34

// We'll need to use this function to obtain long values larger in 
// magnitude than those precisely representable in a double (2^53), 
// as Nashorn's parser will reify such literals as a double. For 
// overflow on add and sub we need (2^63)-1.
var parseLong = Java.type("java.lang.Long").parseLong;

function sub(x) {
    return x.foo - x.bar;
}
print("=== sub ===")
print(sub({foo: 2147483647,  bar: -2147483647})); // 2^31
print(sub({foo: parseLong("9223372036854775807"), bar: parseLong("-9223372036854775807")})); // 2^63-1

function self_sub(x) {
    return x.foo -= x.bar;
}
print("=== self_sub ===")
print(self_sub({foo: 2147483647,  bar: -2147483647})); // 2^31
print(self_sub({foo: parseLong("9223372036854775807"), bar: parseLong("-9223372036854775807")})); // 2^63-1

function add(x) {
    return x.foo + x.bar;
}
print("=== add ===")
print(add({foo: 2147483647,  bar: 2147483647})); // 2^31
print(add({foo: parseLong("9223372036854775807"), bar: parseLong("9223372036854775807")})); // 2^63-1

function self_add(x) {
    return x.foo += x.bar;
}
print("=== self_add ===")
print(self_add({foo: 2147483647,  bar: 2147483647})); // 2^31
print(self_add({foo: parseLong("9223372036854775807"), bar: parseLong("9223372036854775807")})); // 2^63-1
