/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8135178: importPackage not working even with load "Mozilla compatibility script"
 *
 * @test
 * @run
 */

var ScriptContext = javax.script.ScriptContext;
var manager = new javax.script.ScriptEngineManager();

var engine1 = manager.getEngineByName("nashorn");
engine1.eval("load('nashorn:mozilla_compat.js')");
manager.setBindings(engine1.getBindings(ScriptContext.ENGINE_SCOPE));

var engine2 = manager.getEngineByName("nashorn");
engine2.eval("load('nashorn:mozilla_compat.js');");
engine2.eval("importPackage(java.util);");

engine2.eval("var al = new ArrayList()");
Assert.assertTrue(engine2.eval("al instanceof java.util.ArrayList"));
Assert.assertTrue(engine2.get("al") instanceof java.util.ArrayList);

engine2.eval("var hm = new HashMap()");
Assert.assertTrue(engine2.eval("hm instanceof java.util.HashMap"));
Assert.assertTrue(engine2.get("hm") instanceof java.util.HashMap);
