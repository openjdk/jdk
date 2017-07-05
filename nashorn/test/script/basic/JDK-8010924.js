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
 * JDK-8010924:  Dealing with undefined property gets you a fatal stack
 *
 * @test
 * @run
 * @option -scripting
 */

load("nashorn:mozilla_compat.js");

if (this.non_existent_foo !== undefined) {
    fail("this.non_existent_foo is defined!");
}

try {
    non_existent_foo;
    fail("should have thrown ReferenceError");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("ReferenceError expected, got " + e);
    }
}

// try the same via script engine

var ScriptEngineManager = Java.type("javax.script.ScriptEngineManager");
var engine = new ScriptEngineManager().getEngineByName("nashorn");

engine.eval("load('nashorn:mozilla_compat.js')");

if (! engine.eval("this.non_existent_foo === undefined")) {
    fail("this.non_existent_foo is not undefined");
}

engine.eval(<<EOF
    try {
        non_existent_foo;
        throw new Error("should have thrown ReferenceError");
    } catch (e) {
        if (! (e instanceof ReferenceError)) {
            throw new Error("ReferenceError expected, got " + e);
        }
    }
EOF);
