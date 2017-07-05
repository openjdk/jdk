/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8068573: POJO setter using [] syntax throws an exception
 *
 * @test
 * @run
 */

// Invoke a setter using []. It's important that the setter returns void.
var pb = new (Java.type("jdk.nashorn.test.models.PropertyBind"))
var n = "writeOnly";
pb[n] = 2;
Assert.assertEquals(pb.peekWriteOnly(), 2);

// Invoke an overloaded setter using []. It's important that one of the 
// overloads returns void.
var os = new (Java.type("jdk.nashorn.test.models.OverloadedSetter"))
var n2 = "color";
os[n2] = 3; // exercise int overload
Assert.assertEquals(os.peekColor(), "3");
os[n2] = "blue";  // exercise string overload
Assert.assertEquals(os.peekColor(), "blue");
for each(var x in [42, "42"]) {
  os[n2] = x; // exercise both overloads in the same call site
  Assert.assertEquals(os.peekColor(), "42");
}

// Invoke an overloaded method using [], repeatedly in the same call 
// site. It's important that one of the overloads returns void.
var n3="foo";
var param=["xyz", 1, "zyx", 2];
var expected=["boo", void 0, "boo", void 0];
for(var i in param) {
  Assert.assertEquals(os[n3](param[i]), expected[i]);
}
