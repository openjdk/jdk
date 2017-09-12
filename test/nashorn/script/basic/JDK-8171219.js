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
 * JDK-8171219: Missing checks in sparse array shift() implementation
 *
 * @test
 * @run
 */

var a = [];
a[1048577] = 1;
a.shift();
a[1] = 2;
a.shift();
var ka = Object.keys(a);
Assert.assertTrue(ka.length === 2);
Assert.assertTrue(ka[0] === '0');
Assert.assertTrue(ka[1] === '1048575');
Assert.assertTrue(a.length === 1048576);
Assert.assertTrue(a[0] === 2);
Assert.assertTrue(a[1048575] = 1);

var b = [];
b[1048577] = 1;
b.unshift(2);
b.shift();
b[1] = 3;
b.shift();
var kb = Object.keys(b);
Assert.assertTrue(kb.length === 2);
Assert.assertTrue(kb[0] === '0');
Assert.assertTrue(kb[1] === '1048576');
Assert.assertTrue(b.length === 1048577);
Assert.assertTrue(b[0] === 3);
Assert.assertTrue(b[1048576] = 1);

