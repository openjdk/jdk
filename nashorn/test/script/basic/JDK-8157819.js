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
 * JDK-8157819: TypeError when a java.util.Comparator object is invoked as a function
 *
 * @test
 * @run
 */

var compare = java.util.Comparator.naturalOrder()
Assert.assertTrue(compare("nashorn", "ecmascript") > 0)
Assert.assertTrue(compare("abc", "xyz") < 0)
Assert.assertTrue(compare("hello", "hello") == 0)

var rcompare = java.util.Comparator.reverseOrder()
Assert.assertTrue(rcompare("nashorn", "ecmascript") < 0)
Assert.assertTrue(rcompare("abc", "xyz") > 0)
Assert.assertTrue(rcompare("hello", "hello") == 0)

var arr = [ "nashorn", "JavaScript", "ECMAScript", "ecmascript", "js" ]
Assert.assertEquals(arr.sort(compare).join(),
    "ECMAScript,JavaScript,ecmascript,js,nashorn")
Assert.assertEquals(arr.sort(rcompare).join(),
    "nashorn,js,ecmascript,JavaScript,ECMAScript")

