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
 * JDK-8060688: Nashorn: Generated script class name fails --verify-code for names with special chars
 *
 * @test
 * @run
 */

var NashornEngineFactory = Java.type("jdk.nashorn.api.scripting.NashornScriptEngineFactory");
var ScriptEngine = Java.type("javax.script.ScriptEngine");
var ScriptContext = Java.type("javax.script.ScriptContext");

var factory = new NashornEngineFactory();

var e = factory.getScriptEngine("--verify-code");

function evalAndCheck(code) {
    try {
        e.eval(code);
    } catch (exp) {
        exp.printStackTrace();
    }
}

// check default name
evalAndCheck("var a = 3");
// check few names with special chars
var scontext = e.context;
scontext.setAttribute(ScriptEngine.FILENAME, "<myscript>", ScriptContext.ENGINE_SCOPE);
evalAndCheck("var h = 'hello'");
scontext.setAttribute(ScriptEngine.FILENAME, "[myscript]", ScriptContext.ENGINE_SCOPE);
evalAndCheck("var foo = 'world'");
scontext.setAttribute(ScriptEngine.FILENAME, ";/\\$.", ScriptContext.ENGINE_SCOPE);
evalAndCheck("var foo = 'helloworld'");
