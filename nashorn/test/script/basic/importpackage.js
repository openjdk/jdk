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
 * Test to check importPackage function.
 *
 * @test
 * @run
 */

try {
    load("nashorn:mozilla_compat.js");
} catch (e) {
}

importPackage(java.util);

var m = new HashMap();
if (!(m instanceof java.util.HashMap)) {
    fail("expected 'm' to be a java.util.HashMap instance");
}

function checkJavaClass(cls) {
    if (! Java.isType(cls)) {
        fail(cls + " is not a Java class");
    }
}

importPackage(java.lang.reflect, javax.script);
checkJavaClass(Method);
checkJavaClass(Field);
checkJavaClass(Constructor);
checkJavaClass(ScriptContext);
checkJavaClass(ScriptEngine);

var bindings = new SimpleBindings();
if (!(bindings instanceof javax.script.SimpleBindings)) {
    fail("expected 'bindings' to be a javax.script.SimpleBindings instance");
}
