/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Java inner classes access
 *
 * @test
 * @run
 */

// Do it with Java.type()
var outer = new (Java.type("jdk.nashorn.test.models.OuterClass"))("apple")
print(outer)
var innerStatic = new (Java.type("jdk.nashorn.test.models.OuterClass$InnerStaticClass"))("orange")
print(innerStatic)
var innerNonStatic = new (Java.type("jdk.nashorn.test.models.OuterClass$InnerNonStaticClass"))(outer, "pear")
print(innerNonStatic)

// Now do it with Packages and explicit $ names
var outer = new Packages.jdk.nashorn.test.models.OuterClass("red")
print(outer)
var innerStatic = new Packages.jdk.nashorn.test.models.OuterClass$InnerStaticClass("green")
print(innerStatic)
var innerNonStatic = new Packages.jdk.nashorn.test.models.OuterClass$InnerNonStaticClass(outer, "blue")
print(innerNonStatic)

// Now do it with Packages and nested properties
var outer = new Packages.jdk.nashorn.test.models.OuterClass("sweet")
print(outer)
var innerStatic = new Packages.jdk.nashorn.test.models.OuterClass.InnerStaticClass("sour")
print(innerStatic)
var innerNonStatic = new Packages.jdk.nashorn.test.models.OuterClass.InnerNonStaticClass(outer, "bitter")
print(innerNonStatic)
