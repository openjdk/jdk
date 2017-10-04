/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8186815: Java.from has a bug, when element is ScriptObject
 *
 * @test
 * @run
 */

var list = new java.util.ArrayList();
var obj = { x: 1 };
list.add(obj);

Assert.assertTrue(list.get(0) === obj);
Assert.assertTrue(list.get(0) instanceof Object);

var fromList = Java.from(list);
Assert.assertTrue(fromList[0] === obj);
Assert.assertTrue(fromList[0] instanceof Object);

var fromArray = Java.from(list.toArray());
Assert.assertTrue(fromArray[0] === obj);
Assert.assertTrue(fromArray[0] instanceof Object);
