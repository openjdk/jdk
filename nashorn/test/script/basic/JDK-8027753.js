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
 * JDK-8027753: Support ScriptObject to JSObject, ScriptObjectMirror, Map, Bindings auto-conversion as well as explicit wrap, unwrap
 *
 * @test
 * @run
 */

var ScriptUtils = Java.type("jdk.nashorn.api.scripting.ScriptUtils");
var ScriptObjectMirror = Java.type("jdk.nashorn.api.scripting.ScriptObjectMirror");

var obj = { foo: 34, bar: 'hello' };

var wrapped = ScriptUtils.wrap(obj);
if (! (wrapped instanceof ScriptObjectMirror)) {
    fail("ScriptUtils.wrap does not return a ScriptObjectMirror");
}

print("wrapped.foo = " + wrapped.foo);
print("wrapped.bar = " + wrapped.bar);

var unwrapped = ScriptUtils.unwrap(wrapped);
if (! (unwrapped instanceof Object)) {
    fail("ScriptUtils.unwrap does not return a ScriptObject");
}

// same object unwrapped?
print(unwrapped === obj);
