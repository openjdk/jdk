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
 * JDK-8023784: Object.prototype.toString should contain the class name for all instances
 *
 * @test
 * @run
 */

// two parts to this bug -- typed array don't have proper [[Class]] property

print(Object.prototype.toString.call(new ArrayBuffer(1)));
print(Object.prototype.toString.call(new Int8Array(1)));
print(Object.prototype.toString.call(new Int16Array(1)));
print(Object.prototype.toString.call(new Int32Array(1)));
print(Object.prototype.toString.call(new Uint8Array(1)));
print(Object.prototype.toString.call(new Uint8ClampedArray(1)));
print(Object.prototype.toString.call(new Uint16Array(1)));
print(Object.prototype.toString.call(new Uint32Array(1)));
print(Object.prototype.toString.call(new Float32Array(1)));
print(Object.prototype.toString.call(new Float64Array(1)));

// second part is that Object.prototype.toString does not handle mirror
// in the manner expected.

var global = loadWithNewGlobal({
    name: "test",
    script: "this"
});

print(Object.prototype.toString.call(new global.Object()));
print(Object.prototype.toString.call(new global.Array()));
print(Object.prototype.toString.call(new global.RegExp()));
print(Object.prototype.toString.call(new global.Error("error!")));
print(Object.prototype.toString.call(global.Object));
print(Object.prototype.toString.call(new global.ArrayBuffer(1)));
print(Object.prototype.toString.call(new global.Int8Array(1)));
print(Object.prototype.toString.call(new global.Int16Array(1)));
print(Object.prototype.toString.call(new global.Int32Array(1)));
print(Object.prototype.toString.call(new global.Uint8Array(1)));
print(Object.prototype.toString.call(new global.Uint8ClampedArray(1)));
print(Object.prototype.toString.call(new global.Uint16Array(1)));
print(Object.prototype.toString.call(new global.Uint32Array(1)));
print(Object.prototype.toString.call(new global.Float32Array(1)));
print(Object.prototype.toString.call(new global.Float64Array(1)));
