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
 * JDK-8136544: Call site switching to megamorphic causes incorrect property read
 *
 * @test
 * @option --unstable-relink-threshold=8
 * @run
 */

var ScriptContext = Java.type("javax.script.ScriptContext");
var ScriptEngineManager = Java.type("javax.script.ScriptEngineManager");
var m = new ScriptEngineManager();
var e = m.getEngineByName("nashorn");

var scope = e.getBindings(ScriptContext.ENGINE_SCOPE);
var MYVAR = "myvar";

function loopupVar() {
    try {
        e.eval(MYVAR);
        return true;
    } catch (e) {
        return false;
    }
}

// make sure we exercise callsite beyond megamorphic threshold we set
// in this test via nashorn.unstable.relink.threshold property
// In each iteration, callsite is exercised twice (two evals)
// So, LIMIT should be more than 4 to exercise megamorphic callsites.

var LIMIT = 5; // This LIMIT should be more than 4

for (var i = 0; i < LIMIT; i++) {
    // remove the variable and lookup
    delete scope[MYVAR];
    Assert.assertFalse(loopupVar(), "Expected true in iteration " + i);

    // set that variable and check again
    scope[MYVAR] = "foo";
    Assert.assertTrue(loopupVar(), "Expected false in iteration " + i);
}
