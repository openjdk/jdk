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
 * NASHORN-768 :  Implement cross context property/function access and browser JSObject access by JSObject dynalink linker
 *
 * @test
 * @option -scripting
 * @run
 */

// create another nashorn engine
var m = new javax.script.ScriptEngineManager();
var engine = m.getEngineByName("nashorn");

// our global var 'id'
var id = "global-id";

engine.eval(<<CODE

// code evaluated in engine

// engine code's id
var id = "engine-global-id";

// global object
var obj = {
    foo: 42,
    bar: function(x) {
        if (id != "engine-global-id") {
            throw "id != 'engine-global-id'";
        }
        // check this.foo is accessible
        if (this.foo != 42) {
            throw "this.foo != 42";
        }

        return x;
    }
};

// global function
function func(callback) {
    var res = callback(id);
    if (res != id) {
        fail("result of callback is wrong");
    }
}

CODE);

var obj = engine.get("obj");
if (obj.bar("hello") != "hello") {
    fail("obj.bar('hello') return value is wrong");
}

if (obj.foo != 42) {
    fail("obj.foo != 42");
}

var func = engine.get("func");
var inside_f = false;
function f(arg) {
    inside_f = true;
    if (id != "global-id") {
        fail("id != 'global-id'");
    }
    return arg;
}
func(f);
if (! inside_f) {
    fail("function 'f' was not called");
}
