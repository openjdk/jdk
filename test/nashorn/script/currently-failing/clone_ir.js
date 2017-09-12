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
 * clone_ir : Check that functionNode.clone copies all nodes and that they
 * are not the same references
 *
 * @test
 * @run
 */

var js1 = "var tuple = { func : function f(x) { if (x) { print('true'); { print('block_under-true'); } } else { print('false'); } } }";

var Parser            = Java.type("jdk.nashorn.internal.parser.Parser");
var ASTWriter         = Java.type("jdk.nashorn.internal.ir.debug.ASTWriter");
var Context           = Java.type("jdk.nashorn.internal.runtime.Context");
var ScriptEnvironment = Java.type("jdk.nashorn.internal.runtime.ScriptEnvironment");
var Source            = Java.type("jdk.nashorn.internal.runtime.Source");
var FunctionNode      = Java.type("jdk.nashorn.internal.ir.FunctionNode");
var ThrowErrorManager = Java.type("jdk.nashorn.internal.runtime.Context$ThrowErrorManager");
var System            = Java.type("java.lang.System");

var toArrayMethod = ASTWriter.class.getMethod("toArray");
var parseMethod  = Parser.class.getMethod("parse");

function toString(obj) {
    var output = "{ ";
    for (property in obj) {
    output += property + ': ' + obj[property]+'; ';
    }
    return output + '}'
}

function flatten(func) {
    var writer   = new ASTWriter(func);
    var funcList = toArrayMethod.invoke(writer);

    var res = [];
    for each (x in funcList) {
        res.push({ name: x.getClass().getName(), id: System.identityHashCode(x) });
    }
    return res;
}

function check(contents) {
    return check_src(new Source("<no name>", contents));
}

function check_src(src) {
    var parser  = new Parser(Context.getContext().getEnv(), src, new ThrowErrorManager());

    var func = parseMethod.invoke(parser);
    print(func);
    var func2 = func.clone();

    var f1 = flatten(func);
    var f2 = flatten(func2);

    print(f1.map(toString));
    print(f2.map(toString));

    if (f1.length != f2.length) {
    print("length difference between original and clone " + f1.length + " != " + f2.length);
    return false;
    }

    for (var i = 0; i < f1.length; i++) {
    if (f1[i].name !== f2[i].name) {
        print("name conflict at " + i + " " + f1[i].name + " != " + f2[i].name);
        return false;
    } else if (f1[i].id === f2[i].id) {
        print("id problem at " + i + " " + toString(f1[i]) + " was not deep copied to " + toString(f2[i]) + " became " + f1[i].id + " != " + f2[i].id);
        return false;
    }
    }

    return true;
}

print(check(js1));
