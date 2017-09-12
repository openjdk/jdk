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
 * JDK-8130853: Non-extensible global is not handled property
 *
 * @test
 * @run
 */

// don't allow extensions to global
Object.preventExtensions(this);
try {
    eval("var x = 34;");
    throw new Error("should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        throw e;
    }
}

try {
    eval("function func() {}");
    throw new Error("should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        throw e;
    }
}

function checkLoad(code) {
    try {
        load({ name: "test", script: code });
        throw new Error("should have thrown TypeError for load: " + code);
    } catch (e) {
        if (! (e instanceof TypeError)) {
            throw e;
        }
    }
}

checkLoad("var y = 55");
checkLoad("function f() {}");

// check script engine eval
var ScriptEngineManager = Java.type("javax.script.ScriptEngineManager");
var e = new ScriptEngineManager().getEngineByName("nashorn");
var global = e.eval("this");
e.eval("Object.preventExtensions(this);");
try {
    e.eval("var myVar = 33;");
    throw new Error("should have thrown TypeError");
} catch (e) {
    if (! (e.cause.ecmaError instanceof global.TypeError)) {
        throw e;
    }
}

// Object.bindProperties on arbitrary non-extensible object
var obj = {};
Object.preventExtensions(obj);
try {
    Object.bindProperties(obj, { foo: 434 });
    throw new Error("should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        throw e;
    }
}
