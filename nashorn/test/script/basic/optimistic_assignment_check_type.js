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
 * @bug 8036987, 8037572
 * @summary Implement tests that checks static types in the compiled code
 * @option --optimistic-types=true
 * @run
 */

var inspect = Java.type("jdk.nashorn.test.tools.StaticTypeInspector").inspect
var a = b = 3;
var c;
var x = { a: 2, b:1, c: 7, d: -1, e: 1}
var y = { a: undefined, b: undefined}

// Testing assignment operators
//-- Global variable
print(inspect(a=c, "global int assignment to global variable"))
print(inspect(a=b, "undefined assignment to global int"))
print(inspect(a=y.a, "global int assignment to undefined"))
print(inspect(a+=b, "undefined addition assignment to global int"))
print(inspect(b=b+b, "global int addition global int"))
print(inspect(b+= y.a, "global int addition assignment undefined"))
//--Local variable
print(inspect(x.a+= y.a, "local int addition assignment local undefined"))
print(inspect(x.b=y.a, "local int assignment to undefined"))
print(inspect(y.a+=y.a, "local undefined addition assignment local undefined"))
print(inspect(x.c-=x.d, "local int substraction assignment local int"))
print(inspect(x.c*=x.d, "local int multiplication assignment local int"))
print(inspect(x.c/=x.d, "local int division assignment local int"))
print(inspect(y.b=x.c, "local undefined assignment to local int"))
print(inspect(y.c=x.c, "local boolean assignment to local int"))
