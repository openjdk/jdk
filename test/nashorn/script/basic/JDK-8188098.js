/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8188098:NPE in SimpleTreeVisitorES6 visitor when parsing a tagged template literal
 *
 * @test
 * @run
 */

var Parser = Java.type("jdk.nashorn.api.tree.Parser");
var MemberSelectTree = Java.type("jdk.nashorn.api.tree.MemberSelectTree");
var SimpleTreeVisitor = Java.type("jdk.nashorn.api.tree.SimpleTreeVisitorES6");
var parser = Parser.create("--language=es6");

var ast = parser.parse("hello.js", "foo`PI (${Math.PI}) is transcendental`", print);

var reachedCall = false;
ast.accept(new (Java.extend(SimpleTreeVisitor)) {
    visitFunctionCall: function(node, extra) {
        reachedCall = true;
        Assert.assertTrue(node.functionSelect.name == "foo");
        var args = node.arguments;
        Assert.assertTrue(args.size() == 2);
        var strs = args.get(0).elements;
        Assert.assertTrue(String(strs.get(0).value) == "PI (");
        Assert.assertTrue(String(strs.get(1).value) == ") is transcendental");
        var expr = args.get(1);
        Assert.assertTrue(expr instanceof MemberSelectTree);
        Assert.assertTrue(expr.expression.name == "Math");
        Assert.assertTrue(expr.identifier == "PI");
    }
}, null);

Assert.assertTrue(reachedCall);
