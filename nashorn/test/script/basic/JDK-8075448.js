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
 * JDK-8075448: nashorn parser API returns init variable tree object of a for
 * loop after for loop statement tree object 
 *
 * @test
 * @option -scripting
 * @run
 */

var Parser = Java.type("jdk.nashorn.api.tree.Parser");
var ForLoopTree = Java.type("jdk.nashorn.api.tree.ForLoopTree");
var VariableTree = Java.type("jdk.nashorn.api.tree.VariableTree");
var parser = Parser.create();

var code = <<EOF
for (var i = 0; i < 10; i++)
    print("hello");
EOF;

var ast = parser.parse("test.js", code, print);
var stats = ast.sourceElements;

Assert.assertTrue(stats[0] instanceof VariableTree);
Assert.assertEquals(stats[0].binding.name, "i");
Assert.assertTrue(stats[1] instanceof ForLoopTree);

