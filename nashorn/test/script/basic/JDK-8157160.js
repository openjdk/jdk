/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8157160: JSON.stringify does not work on ScriptObjectMirror objects
 *
 * @test
 * @option -scripting
 * @run
 */

var SM = Java.type("javax.script.ScriptEngineManager");
var AJSO = Java.type("jdk.nashorn.api.scripting.AbstractJSObject");
var Supplier = Java.type("java.util.function.Supplier");

var engine = new SM().getEngineByName("nashorn");

// JSON stringify ScriptObjectMirror instances
print(JSON.stringify(engine.eval("({ foo : 42 })")));
print(JSON.stringify(engine.eval("([5, 6, 76, 7])")));
print(JSON.stringify(engine.eval(<<EOF
 ({
     toJSON: function() "hello"
 })
EOF
)));

print(JSON.stringify(engine.eval(<<EOF
obj = {
    name: 'nashorn',
    versions: [ 'es5.1', 'es6' ]
}
EOF
)));

var dm = engine.eval("new Date()");
print('"' + dm.toJSON() + '"' == JSON.stringify(dm));

// JSON stringifying an arbitrary JSObject impl.
var jsObj = new AJSO() {
    keySet: function() {
        var keys = new java.util.HashSet();
        keys.add("x");
        keys.add("y");
        return keys;
    },

    getMember: function(name) {
        if (name == "x") {
            return 42;
        } else if (name == "y") {
            return "hello";
        }
    }
};
print(JSON.stringify(jsObj));

// try toJSON implementation on JSObject
var jsObj2 = new AJSO() {
    getMember: function(name) {
        if (name == 'toJSON') {
            return function() {
                return "my json representation";
            }
        }
    }
};
print(JSON.stringify(jsObj2));

var jsObj3 = new AJSO() {
    getMember: function(name) {
        if (name == 'toJSON') {
            return new Supplier() {
                "get": function() {
                    return "value from toJSON function";
                }
            };
        }
    }
};
print(JSON.stringify(jsObj3));

// replacer function from another script world
print(JSON.stringify({
   foo: "hello"
}, engine.eval(<<EOF
    function (key, value) {
       if (key == "foo") {
           return value.toUpperCase()
       }
       return value;
    }
EOF)));
