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
 * JDK-8008305: ScriptEngine.eval should offer the ability to provide a codebase *
 * @test
 * @run
 */

var URLReader = Java.type("jdk.nashorn.api.scripting.URLReader");
var File = Java.type("java.io.File");
var FileReader = Java.type("java.io.FileReader");
var ScriptEngineManager = Java.type("javax.script.ScriptEngineManager");
var SecurityException = Java.type("java.lang.SecurityException");

var m = new ScriptEngineManager();
var e = m.getEngineByName("nashorn");


// subtest script file
var scriptFile = new File(__DIR__ + "JDK-8008305_subtest.js");

// evaluate the subtest via a URLReader
var res = e.eval(new URLReader(scriptFile.toURI().toURL()));

// subtest should execute with AllPermission and so return absolute path
if (! res.equals(new File(".").getAbsolutePath())) {
    fail("eval result is not equal to expected value");
}

// try same subtest without URLReader and so it runs with null code source
try {
    e.eval(new FileReader(scriptFile));
    fail("Expected SecurityException from script!");
} catch (e) {
    if (! (e instanceof SecurityException)) {
        fail("Expected SecurityException, but got " + e);
    }
}
