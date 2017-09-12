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
 * @test
 * @bug 8036987
 * @run
 */

var factory = Java.type('jdk.nashorn.api.scripting.NashornScriptEngineFactory')
var engine  = new factory().getScriptEngine(function(str){
    return str.indexOf('java.lang.Class') != -1
            || str == 'java.lang.System'
            || str.indexOf('java.util') != -1;
})

function tryEval (str) {
        try {
            print(eval(str))
            print(engine.eval(str))
        } catch (exc) {
            print(exc.message)
        }
}

tryEval("Java.type('java.util.ArrayList')")
tryEval("Java.type('java.lang.String')")
tryEval("java.util.ArrayList")
tryEval("java.lang.String")
tryEval("Java.extend(java.util.ArrayList, {})")
tryEval("Java.extend(java.io.File, {})")
tryEval("new java.lang.NullPointerException();")
tryEval("try { java.lang.System.load(null) } catch (e) { e }")
