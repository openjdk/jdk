/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8143896: java.lang.Long is implicitly converted to double
 *
 * @test
 * @run
 */

Assert.assertTrue(java.lang.Long.valueOf("301077366599181567").toString() === "301077366599181567");
Assert.assertTrue(java.lang.Long.valueOf("-301077366599181567").toString() === "-301077366599181567");
Assert.assertTrue(java.lang.Long.valueOf("301077366599181567") == 301077366599181567);
Assert.assertFalse(java.lang.Long.valueOf("301077366599181567") === 301077366599181567);

Assert.assertTrue(new java.math.BigInteger("301077366599181567").toString() === "301077366599181567");
Assert.assertTrue(new java.math.BigInteger("-301077366599181567").toString() === "-301077366599181567");
Assert.assertTrue(new java.math.BigInteger("301077366599181567") == 301077366599181567);
Assert.assertFalse(new java.math.BigInteger("301077366599181567") === 301077366599181567);


var n = new java.lang.Byte("123");
Assert.assertTrue(typeof n === "number");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertTrue(n === 123);

n = new java.lang.Short("123");
Assert.assertTrue(typeof n === "number");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertTrue(n === 123);

n = new java.lang.Integer("123");
Assert.assertTrue(typeof n === "number");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertTrue(n === 123);

n = new java.lang.Float("123");
Assert.assertTrue(typeof n === "number");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertTrue(n === 123);

n = new java.lang.Double("123");
Assert.assertTrue(typeof n === "number");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertTrue(n === 123);

n = new java.lang.Long("123");
Assert.assertTrue(typeof n === "object");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertFalse(n === 123);

n = new java.util.concurrent.atomic.DoubleAdder();
n.add("123");
Assert.assertTrue(typeof n === "object");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertFalse(n === 123);

n = new java.util.concurrent.atomic.AtomicInteger(123);
Assert.assertTrue(typeof n === "object");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertFalse(n === 123);

n = new java.util.concurrent.atomic.AtomicLong(123);
Assert.assertTrue(typeof n === "object");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertFalse(n === 123);

n = new java.math.BigInteger("123");
Assert.assertTrue(typeof n === "object");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertFalse(n === 123);

n = new java.math.BigDecimal("123");
Assert.assertTrue(typeof n === "object");
Assert.assertTrue(n + 1 === 124);
Assert.assertTrue(n == 123);
Assert.assertFalse(n === 123);
