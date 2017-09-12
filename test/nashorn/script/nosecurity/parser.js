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
  * @test
  * @bug 8068303
  * @option -scripting
  * @run
  */

load(__DIR__ + "/../assert.js")

var Parser = Java.type('jdk.nashorn.api.tree.Parser')
var Nashorn = Java.type('jdk.nashorn.api.scripting.NashornScriptEngineFactory')
var File = java.io.File
var Reader = java.io.FileReader


var test_dir = __DIR__ + "/parsertests"
var files = new File(test_dir).listFiles()

// File source
for (var i in files) {
    var parser = Parser.create("-scripting", "--const-as-var", "-doe")
    try {
        var tree = parser.parse(files[i], null);
        Assert.assertNotNull(tree);
    }
    catch (e) {
        fail("Parser failed with message :" + e)
    }
}

// Reader source
for (var i in files) {
    var parser =  Parser.create("-scripting", "--const-as-var", "-doe")
    try {
        var tree = parser.parse(files[i].getName(), new Reader(files[i]), null)
        Assert.assertNotNull(tree);
    } catch (e) {
        fail("Parser failed with message :" + e)
    }

}

// URL source
for (var i in files) {
    var parser =  Parser.create("-scripting", "--const-as-var", "-doe")
    try {
        var tree = parser.parse(files[i].toURI().toURL(), null)
        Assert.assertNotNull(tree);
    } catch (e) {
        fail("Parser failed with message :" + e)
    }
}

// ScriptObjectMirror

for (var i in files) {
    var parser =  Parser.create("-scripting", "--const-as-var", "-doe")
    var engine = new Nashorn().getScriptEngine("-scripting", "--const-as-var", "-doe")
    try {
        engine.compile(new Reader(files[i]))
        var mirror = engine.createBindings()
        mirror['name'] = mirror['script'] = files[i].getName()
        var tree = parser.parse(mirror, null)
        Assert.assertNotNull(tree);
    } catch (e) {
        fail("Parser failed with message :" + e)
    }
}

