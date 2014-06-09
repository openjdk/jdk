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
 * NASHORN-247 : Object gets stale value when inherited accessor property changes to a data property
 *
 * @test
 * @run
 */

var data = "foo1";
var obj = {
  get foo() { return data; },
  set foo(value) { data = value; }
};

var obj2 = Object.create(obj);

function checkObjFoo(expected) {
    if (obj2.foo !== expected) {
        fail("obj2.foo has to be " + expected);
    }
}

checkObjFoo("foo1");

obj2.foo = "foo2";
checkObjFoo("foo2");

// change the property to data property
Object.defineProperty(obj, "foo", {
   value: "foo3"
});
checkObjFoo("foo3");

