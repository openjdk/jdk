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
 * JDK-8098578: Global scope is not accessible with indirect load call
 *
 * @test
 * @run
 */

var obj = { foo: 343 };
var global = this;
var x = 434;

// indirect load call
var res = load.call(obj, {
   name: "t.js",
   // global is accessible. All declarations go into
   // intermediate inaccessible scope. "this" is global
   // User's passed object's properties are accessible
   // as variables.
   script: "foo -= 300; var bar = x; Assert.assertTrue(bar == 434); function func() {}; this"
})

// 'this' for the evaluated code is global
Assert.assertTrue(res === global);

// properties of passed object are accessible in evaluated code
Assert.assertTrue(obj.foo == 43);

// vars, functions definined in evaluated code don't go into passed object
Assert.assertTrue(typeof obj.bar == "undefined");
Assert.assertTrue(typeof obj.func == "undefined");

// vars, functions definined in evaluated code don't go leak into global
Assert.assertTrue(typeof bar == "undefined");
Assert.assertTrue(typeof func == "undefined");
Assert.assertTrue(typeof foo == "undefined");

var res = load.call(undefined, {
    name: "t1.js",
    // still global is accessible and 'this' is global
    script: "Assert.assertTrue(x == 434); this"
});

// indirect load with 'undefined' this is same as as direct load
// or load on global itself.
Assert.assertTrue(res === global);

// indirect load with 'undefined' this is same as as direct load
// or load on global itself.
var res = load.call(null, {
    name: "t2.js",
    // still global is accessible and 'this' is global
    script: "Assert.assertTrue(x == 434); this"
});
Assert.assertTrue(res === global);

// indirect load with mirror object
var mirror = loadWithNewGlobal({
    name: "t3.js",
    script: "({ foo: 'hello', x: Math.PI })"
});

var res = load.call(mirror, {
    name: "t4.js",
    script: "Assert.assertTrue(foo == 'hello'); Assert.assertTrue(x == Math.PI); this"
});
Assert.assertTrue(res === global);

// indirect load on non-script object, non-mirror results in TypeError
function tryLoad(obj) {
    try {
        load.call(obj, {
            name: "t5.js", script: "this"
        });
        throw new Error("should thrown TypeError for: " + obj);
    } catch (e if TypeError) {}
}

tryLoad("hello");
tryLoad(Math.E);
tryLoad(true);
tryLoad(false);

// indirect load of a large script
load.call({}, __DIR__ + "JDK-8098807-payload.js");
