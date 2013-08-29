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
 * JDK-8023368: Instance __proto__ property should exist and be writable.
 *
 * @test
 * @run
 */

// check that Object.setPrototypeOf works for mirror objects as well.

var global = loadWithNewGlobal({
    name: "test",
    script: "var obj = {}; this"
});

var proto = global.eval("({ foo: 323 })");

Object.setPrototypeOf(global.obj, proto);

function func(obj) {
    // check proto inherited value
    print("obj.foo = " + obj.foo);
}

func(global.obj);

var newProto = global.eval("({ foo: 'hello' })");
Object.setPrototypeOf(global.obj, newProto);

func(global.obj);
