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
 * JDK-8173480: in operator should work on java objects and classes
 *
 * @test
 * @run
 */

var hash = "hash"; // for testing ConsString keys

var obj = new java.lang.Object();
Assert.assertTrue("hashCode" in obj);
Assert.assertTrue(hash + "Code" in obj);
Assert.assertTrue("class" in obj);
Assert.assertFalse("x" in obj);
Assert.assertFalse(1 in obj);
Assert.assertFalse("1" in obj);

var map = new java.util.HashMap();
map["k"] = true;
Assert.assertTrue(map["k"]);
Assert.assertTrue("k" in map);
Assert.assertTrue("hashCode" in map);
Assert.assertTrue(hash + "Code" in map);
Assert.assertTrue("class" in map);
Assert.assertFalse("x" in map);
Assert.assertFalse(1 in map);
Assert.assertFalse("1" in map);

var list = new java.util.ArrayList();
list.add(true);
Assert.assertTrue(list[0]);
Assert.assertTrue(list["0"]);
Assert.assertTrue(0 in list);
Assert.assertTrue("0" in list);
Assert.assertTrue("hashCode" in list);
Assert.assertTrue(hash + "Code" in list);
Assert.assertTrue("class" in list);
Assert.assertFalse("x" in list);
Assert.assertFalse(1 in list);
Assert.assertFalse("1" in list);

var objectArray = new (Java.type("java.lang.Object[]"))(1);
objectArray[0] = true;
Assert.assertTrue(objectArray[0]);
Assert.assertTrue(objectArray["0"]);
Assert.assertTrue(0 in objectArray);
Assert.assertTrue("0" in objectArray);
Assert.assertTrue("hashCode" in objectArray);
Assert.assertTrue(hash + "Code" in objectArray);
Assert.assertTrue("class" in objectArray);
Assert.assertFalse("x" in objectArray);
Assert.assertFalse(1 in objectArray);
Assert.assertFalse("1" in objectArray);

var RuntimeClass = Java.type("java.lang.Runtime");
Assert.assertTrue("getRuntime" in RuntimeClass);
Assert.assertTrue("runtime" in RuntimeClass);
Assert.assertTrue("class" in RuntimeClass);
Assert.assertFalse("hashCode" in RuntimeClass);
Assert.assertFalse(hash + "Code" in RuntimeClass);
Assert.assertFalse("x" in RuntimeClass);
Assert.assertFalse(1 in RuntimeClass);
Assert.assertFalse("1" in RuntimeClass);

