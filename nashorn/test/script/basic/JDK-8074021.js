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
 * JDK-8074021: Indirect eval fails when used as an element of an array or as a property of an object
 *
 * @test
 * @run
 */

var obj = { foo: eval };
Assert.assertTrue(obj.foo("typeof(print) == 'function'"));
Assert.assertTrue(obj.foo("RegExp instanceof Function"));
Assert.assertEquals(obj.foo("String(new Array(2, 4, 3))"), "2,4,3");
obj.foo("print('hello')");

var args = [ eval ];
Assert.assertTrue(args[0]("typeof(print) == 'function'"));
Assert.assertTrue(args[0]("RegExp instanceof Function"));
Assert.assertEquals(args[0]("String(new Array(2, 4, 3))"), "2,4,3");
args[0]("print('hello')");
