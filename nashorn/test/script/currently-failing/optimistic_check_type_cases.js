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
 * @test
 * @bug 8035652,8037858
 * @summary Implement tests that checks static types in the compiled code
 * @run
 */

var inspect = Java.type("jdk.nashorn.test.tools.StaticTypeInspector").inspect
var a = b = c = 3;

//JDK-8035652
print(inspect(a/a, "global int division by global int"))
print(inspect(a%a, "global int modulus by global int"))
print(inspect(b+=b, "global int addition assignment global int"))
//JDK-8037858
print(inspect(b-=b, "global int substraction assignment global int"))
print(inspect(c*=a, "global int multiplication assignment global int"))
print(inspect(a/=a, "global int division assignment global int"))
print(inspect(c%=c, "global int modulo assignment global int"))
