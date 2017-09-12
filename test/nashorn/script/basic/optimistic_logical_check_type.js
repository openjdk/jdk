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
 * @run
 */

var inspect = Java.type("jdk.nashorn.test.tools.StaticTypeInspector").inspect
var a = 3, b = true, c = 0;
var x = { a: 2, b: undefined, c: true}

// Testing logical operators
//-- Global variable
print(inspect("cat" && "dog", "object AND object"))
print(inspect(b && a, "true AND non-falsey global int"))
print(inspect(a && b, "non-falsey global int AND true"))
print(inspect(x && b, "non-falsey object AND true"))
print(inspect(b && x, "true AND non-falsey object"))
print(inspect("cat" || "dog", "object OR object"))
print(inspect(b || a, "true OR non-falsey global int"))
print(inspect(a || b, "non-falsey global int OR true"))
print(inspect(x || b, "non-falsey object OR true"))
print(inspect(b || x, "true OR non-falsey object"))
print(inspect(!x.c || b, "false OR true"))
print(inspect(c && b, "falsey global int AND true"))
print(inspect(c || x.b, "falsey global int OR undefined"))
print(inspect(!c || x.b, "logical not falsey global int OR undefined"))
print(inspect(!b || x.b, "false OR undefined"))
print(inspect(!b || c, "false OR falsey global int"))
print(inspect(!c || c, "logical not falsey global int OR falsey global int"))
 //--Local variable
print(inspect(x.b && a, "local undefined AND non-falsey global int"))
print(inspect(b && x.b, "true AND local undefined"))
print(inspect(x.b && x.a, "local undefined AND non-falsey local int"))
print(inspect(x.b || b, "local undefined OR true"))
print(inspect(b || x.b, "true OR local undefined"))
print(inspect(x.a && x.c, "non-falsey local int AND true"))
print(inspect(x.c && x.a, "true AND non-falsey local int"))
print(inspect(x.c && !!x.a, "true AND double logical not non-falsey local int "))
print(inspect(!x.c && x.a, "false AND non-falsey local int"))
print(inspect(x.a || x.c, "non-falsey local int OR true"))
print(inspect(!x.c || x.c, "false OR true"))
