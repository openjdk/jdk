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
 * JDK-8157680: Callback parameter of any JS builtin implementation should accept any Callable
 *
 * @option -scripting
 * @test
 * @run
 */

var SM = Java.type("javax.script.ScriptEngineManager")
var engine = new SM().getEngineByName("nashorn")

engine.put("output", print);
var reviver = engine.eval(<<EOF
function(name, value) {
   if (name == "") return value
   output(name + " = " + value)
   return value
}
EOF)

// reviver function from mirror world!
JSON.parse('{ "foo" : 44, "bar" : "hello" }', reviver)

var AJO = Java.type("jdk.nashorn.api.scripting.AbstractJSObject")
// reviver function as a JSObject function
JSON.parse('{ "nashorn" : "hello" }', new AJO() {
    isFunction: function() true,
    call: function(thiz, args) {
        var name = args[0], value = args[1]
        if (name == "") return value
        print(name + " -> " + value)
        return value
    } 
})

// compare function from the mirror world
var arr = [34,567,-3, 53].sort(engine.eval(<<EOF
    function(x, y) x < y? -1 : ((x > y)? 1 : 0)
EOF))
print(arr)

// compare function as a JSObject function
arr = [34,57,-3, 53, 670, 33].sort(new AJO() {
    isFunction: function() true,
    call: function(thiz, args) {
        var x = args[0], y = args[1]
        return x < y? -1 : ((x > y)? 1 : 0)
    }
})
print(arr)

// replacer function from mirror world
var str = "hello".replace(/l/g, engine.eval(<<EOF
    function() "_"
EOF))
print(str)

// replacer function as a JSObject function
str = "hello".replace(/[el]/g, new AJO() {
    isFunction: function() true,
    call: function(thiz, args) {
        var match = args[0]
        return match.toUpperCase()
    }
})
print(str)
