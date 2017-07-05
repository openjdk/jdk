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
 * JDK-8072596: Arrays.asList results in ClassCastException with a JS array
 *
 * @test
 * @run
 */
var arr = java.util.Arrays.asList("hello world".split(' '));
// We split it into a list of two elements: [hello, world]
Assert.assertTrue(arr instanceof java.util.List);
Assert.assertEquals(arr.length, 2);
Assert.assertEquals(arr[0], "hello");
Assert.assertEquals(arr[1], "world");

var Jdk8072596TestSubject = Java.type("jdk.nashorn.test.models.Jdk8072596TestSubject");
var testSubject = new Jdk8072596TestSubject({bar: 0});
testSubject.test1(true, {foo: 1}, {bar: 2});
testSubject.test2(true, {foo: 1}, {bar: 2}, {baz: 3}, {bing: 4});
var h = "h";
var ello = "ello";
testSubject.test3(true, {foo: 5}, /* ConsString, why not */ h + ello, [6, 7], 8);
Jdk8072596TestSubject.test4({foo: 9});

// Test wrapping setters arguments and unwrapping getters return values on list.
var list = new java.util.ArrayList();
list.add(null);
var obj0 = {valueOf: function() { return 0; }};
var obj1 = {foo: 10};
list[obj0] = obj1;
testSubject.testListHasWrappedObject(list);
// NOTE: can't use Assert.assertSame(obj1, list[obj0]), as the arguments would end up being wrapped...
Assert.assertTrue(obj1 === list[obj0]);

// Test wrapping setters arguments and unwrapping getters return values on array.
var arr2 = new (Java.type("java.lang.Object[]"))(1);
var obj2 = {bar: 11};
arr2[obj0] = obj2;
testSubject.testArrayHasWrappedObject(arr2);
Assert.assertTrue(obj2 === arr2[obj0]);

// Test wrapping setters index and arguments and getters index, and unwrapping getters return values on map.
// Since ScriptObjectMirror.equals() uses underlying ScriptObject identity, using them as map keys works.
var map = new java.util.HashMap();
var obj3 = {bar: 12};
map[obj0] = obj3;
testSubject.testMapHasWrappedObject(map, obj0);
Assert.assertTrue(obj3 === map[obj0]);
