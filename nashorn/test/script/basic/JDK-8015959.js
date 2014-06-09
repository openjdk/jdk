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
 * JDK-8015959: Can't call foreign constructor
 *
 * @test
 * @run
 */

function check(global) {
    var obj = new global.Point(344, 12);
    print("obj.x " + obj.x);
    print("obj.y " + obj.y);
    print("obj instanceof global.Point? " + (obj instanceof global.Point))

    var P = global.Point;
    var p = new P(343, 54);
    print("p.x " + p.x);
    print("p.y " + p.y);
    print("p instanceof P? " + (p instanceof P))
}

print("check with loadWithNewGlobal");
check(loadWithNewGlobal({
   name: "myscript",
   script: "function Point(x, y) { this.x = x; this.y = y }; this"
}));

print("check with script engine");
var m = new javax.script.ScriptEngineManager();
var e = m.getEngineByName('nashorn');
check(e.eval("function Point(x, y) { this.x = x; this.y = y }; this"));

