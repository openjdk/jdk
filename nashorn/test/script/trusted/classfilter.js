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
 * ClassFilter to filter out java classes in a script engine.
 *
 * @test
 * @run
 */

var NashornScriptEngineFactory = Java.type("jdk.nashorn.api.scripting.NashornScriptEngineFactory");

var fac = new NashornScriptEngineFactory();
// allow only "java.*" classes to be accessed
var e = fac.getScriptEngine(
    function(name) name.startsWith("java."));

function evalIt(str) {
    print(str + " evalutes to " + e.eval(str));
}

function evalExpectError(str) {
    try {
        print(e.eval(str));
        fail("expected error for: " + str);
    } catch(exp) {
        print(str + " throws " + exp);
    }
}

evalIt("typeof javax.script.ScriptContext");
evalIt("typeof javax.script.ScriptEngine");
evalIt("typeof java.util.Vector");
evalIt("typeof java.util.Map");
evalIt("typeof java.util.HashMap");
// should be able to call methods, create objects of java.* classes
evalIt("var m = new java.util.HashMap(); m.put('foo', 42); m");
evalIt("java.lang.System.out.println");
evalIt("java.lang.System.exit");

evalExpectError("new javax.script.SimpleBindings");
evalExpectError("Java.type('javax.script.ScriptContext')");
evalExpectError("java.lang.Class.forName('javax.script.ScriptContext')");

try {
    fac["getScriptEngine(ClassFilter)"](null);
    fail("should have thrown NPE");
} catch (e) {
    if (! (e instanceof java.lang.NullPointerException)) {
        fail("NPE expected, got " + e);
    }
}
