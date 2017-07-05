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
 * JDK-8114838: Anonymous functions escape to surrounding scope when defined under "with" statement
 *
 * @test
 * @run
 */

// do *not* introduce new lines! The next line should be 32
with({}) { function () {} }
if (typeof this["L:32"] != 'undefined') {
    throw new Error("anonymous name spills into global scope");
}

var func = eval("function() {}");
if (typeof func != 'function') {
    throw new Error("eval of anonymous function does not work!");
}

var ScriptEngineManager = Java.type("javax.script.ScriptEngineManager");
var engine = new ScriptEngineManager().getEngineByName("nashorn");
var func2 = engine.eval("function() {}");
if (typeof func2 != 'function') {
    throw new Error("eval of anonymous function does not work from script engine!");
}
