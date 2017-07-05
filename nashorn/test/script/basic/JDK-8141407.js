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
 * JDK-8141407: Wrong evaluation of a != a when a = NaN
 *
 * @test
 * @run
 */

function expectNotEqualToSelf(a) {
    Assert.assertFalse(a == a);
    Assert.assertFalse(a === a);
    Assert.assertTrue(a != a);
    Assert.assertTrue(a !== a);
}

// In previous versions of Nashorn this failed only on the second assignment,
// because only then the property slot was widened to object.
var a = NaN;
expectNotEqualToSelf(a);
a = {};
a = NaN;
expectNotEqualToSelf(a);

// We do have to do value-based rather than reference-based comparison for
// java.lang.Double since that class is used to represent primitive numbers
// in JavaScript.
var b = new java.lang.Double(NaN);
expectNotEqualToSelf(b);
b = {};
b = new java.lang.Double(NaN);
expectNotEqualToSelf(b);

// Although float is not used internally by Nashorn, java.lang.Float
// is handled like a primitive number in most of Nashorn, so for consistency
// we treat it like java.lang.Double.
var c = new java.lang.Float(NaN);
expectNotEqualToSelf(c);
c = {};
c = new java.lang.Float(NaN);
expectNotEqualToSelf(c);
