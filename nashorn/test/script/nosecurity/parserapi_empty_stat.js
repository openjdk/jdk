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
 * Nashorn parser API --empty-statements option test.
 *
 * @test
 * @run
 */

var SimpleTreeVisitor = Java.type("jdk.nashorn.api.tree.SimpleTreeVisitorES5_1");
var Parser = Java.type("jdk.nashorn.api.tree.Parser");

// with --empty-statements parse tree should contain
// EmptyStatement tree nodes. Without this option, empty
// statement Tree nodes may be optimized away by Parser.
var emptyStatParser = Parser.create("--empty-statements");

var emptyStat = ";";
emptyStatParser.parse("empty.js", emptyStat, print).
    accept(new (Java.extend(SimpleTreeVisitor)) {
        visitEmptyStatement: function(node, p) {
            print("inside EmptyStatement visit");
        }
    }, null); 
