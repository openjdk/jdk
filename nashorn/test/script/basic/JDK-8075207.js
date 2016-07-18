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
 * JDK-8075207: Nashorn parser API returns StatementTree objects in out of order
 *
 * @test
 * @option -scripting
 * @run
 */

var Parser = Java.type("jdk.nashorn.api.tree.Parser");
var ExpressionStatementTree = Java.type("jdk.nashorn.api.tree.ExpressionStatementTree");
var FunctionDeclarationTree = Java.type("jdk.nashorn.api.tree.FunctionDeclarationTree");
var VariableTree = Java.type("jdk.nashorn.api.tree.VariableTree");

var parser = Parser.create();

var ast = parser.parse("hello.js", <<CODE

var hello = 'hello';

function print_hello() {
    var x = 2;
    print(hello);
    function inner_func() {}
    var y = function() {
        var PI = Math.PI;
        function inner2() {}
        var E = Math.E;
    }
}

var hello = "hello 2";

CODE, print);

var stats = ast.sourceElements;
Assert.assertTrue(stats.get(0) instanceof VariableTree);
Assert.assertTrue(stats.get(1) instanceof FunctionDeclarationTree);
Assert.assertTrue(stats.get(2) instanceof VariableTree);

var print_hello = stats.get(1);
Assert.assertEquals(print_hello.name.name, "print_hello");
var print_hello_stats = print_hello.body.statements;
Assert.assertTrue(print_hello_stats.get(0) instanceof VariableTree);
Assert.assertTrue(print_hello_stats.get(1) instanceof ExpressionStatementTree);
Assert.assertTrue(print_hello_stats.get(2) instanceof FunctionDeclarationTree);
Assert.assertTrue(print_hello_stats.get(3) instanceof VariableTree);

var anonFunc = print_hello_stats.get(3).initializer;
var anonFunc_stats = anonFunc.body.statements;
Assert.assertTrue(anonFunc_stats.get(0) instanceof VariableTree);
Assert.assertTrue(anonFunc_stats.get(1) instanceof FunctionDeclarationTree);
Assert.assertTrue(anonFunc_stats.get(2) instanceof VariableTree);

