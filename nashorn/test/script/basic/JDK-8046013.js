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
 * JDK-8046013: TypeError: Cannot apply "with" to non script object
 *
 * @test
 * @run
 */

var obj = loadWithNewGlobal({
    script: "({ f: 33 })",
    name: "test"
});

with (obj) {
   print("f = " + f);
}

var obj2 = loadWithNewGlobal({
    script: "var obj = Object.create({ foo: 42 }); obj.bar = 'hello'; obj",
    name: "test2"
});

with (obj2) {
    print("foo = " + foo);
    print("bar = " + bar);
}

var obj3 = loadWithNewGlobal({
    script: "({ f: 33, func: function() { print('this.f =', this.f); } })",
    name: "test"
});

with(obj3) {
    func();
}
