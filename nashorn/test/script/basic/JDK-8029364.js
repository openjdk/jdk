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
 * JDK-8029364: NashornException to expose thrown object
 *
 * @test
 * @run
 */

var m = new javax.script.ScriptEngineManager();
var e = m.getEngineByName("nashorn");
var g = e.eval("this");
try {
    e.eval("var e = new Error('foo'); e.bar = 33; throw e");
} catch (se) {
    // ScriptException instance's cause is a NashornException
    print(se.getClass());
    var cause = se.cause;
    print(cause.getClass());
    // NashornException instance has 'ecmaError' bean getter
    print(cause.ecmaError);
    // access to underlying ECMA Error object
    print(cause.ecmaError instanceof g.Error);
    print(cause.ecmaError.name);
    print(cause.ecmaError.message);
    print(cause.ecmaError.bar);
}

