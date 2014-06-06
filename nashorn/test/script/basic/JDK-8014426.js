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
 * Ensure catchall exceptions from finally inlining are rethrown as is
 *
 * @test
 * @run
 */

function runScriptEngine() {
    var fac    = new Packages.jdk.nashorn.api.scripting.NashornScriptEngineFactory();
    var engine = fac.getScriptEngine();
    engine.eval(
"try {\n\
  doIt();\n\
} finally { \n\
  var x = 17;\n\
}\n\
function doIt() {\n\
  throw new TypeError('en stor graa noshoerning!');\n\
}\n");
}

try {
    runScriptEngine();
} catch(e) {
    print(e);
}
