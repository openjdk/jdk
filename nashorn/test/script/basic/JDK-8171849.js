/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8171849: Collection and Queue conversions not prioritized for Arrays
 *
 * @test
 * @run
 */

var acp = new (Java.type("jdk.nashorn.test.models.ArrayConversionPreferences"))

var a = [1, "", {}]

Assert.assertTrue(acp.testCollectionOverMap(a))
Assert.assertTrue(acp.testCollectionOverArray(a))
Assert.assertTrue(acp.testListOverMap(a))
Assert.assertTrue(acp.testListOverArray(a))
Assert.assertTrue(acp.testListOverCollection(a))
Assert.assertTrue(acp.testQueueOverMap(a))
Assert.assertTrue(acp.testQueueOverArray(a))
Assert.assertTrue(acp.testQueueOverCollection(a))
Assert.assertTrue(acp.testDequeOverMap(a))
Assert.assertTrue(acp.testDequeOverArray(a))
Assert.assertTrue(acp.testDequeOverCollection(a))
Assert.assertTrue(acp.testDequeOverQueue(a))
Assert.assertTrue(acp.testArrayOverMap(a))
