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
 * JDK-8008197: Cross script engine function calls do not work as expected
 *
 * @test
 * @run
 */


var m = new javax.script.ScriptEngineManager();
var e = m.getEngineByName("nashorn");

var obj = {
    func: function(str) { 
        return /hello/.exec(str);
    }
};

// set our object as object to the engine created
e.put("obj", obj);

// try to invoke method from the other engine
if (e.eval("obj.func('hello')") == null) {
    fail("FAILED: #1 obj.func('hello') returns null");
}

// define an object in the engine
 e.eval("var foo = { callMe: function(callback) { return callback() } }");

// try to invoke a script method from here but callback is from this engine
var res = e.invokeMethod(e.get("foo"), "callMe", function() {
    return /nashorn/;
});

if (! res.exec("nashorn")) {
    fail("FAILED: #2 /nashorn/ does not match 'nashorn'");
}

// invoke a script method from here with callback from this engine.
// This uses JSObject.call interface
res = e.get("foo").callMe(function() {
    return /ecmascript/;
});

if (! res.exec("ecmascript")) {
    fail("FAILED: #3 /ecmascript/ does not match 'ecmascript'");
}
