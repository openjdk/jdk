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
 * Nashorn parser API -strict option test.
 *
 * @test
 * @option -scripting
 * @run
 */

var Parser = Java.type("jdk.nashorn.api.tree.Parser");
var strictParser = Parser.create("-strict");
var parser = Parser.create();

var withStat = <<EOF

with({}) {}
EOF;

strictParser.parse("with_stat.js", withStat, print);
parser.parse("with_stat1.js", withStat, print);

var repeatParam = <<EOF

function func(x, x) {}
EOF;

strictParser.parse("repeat_param.js", repeatParam, print);
parser.parse("repeat_param1.js", repeatParam, print);

var repeatProp = <<EOF

var obj = { foo: 34, foo: 'hello' };

EOF

strictParser.parse("repeat_prop.js", repeatProp, print);
parser.parse("repeat_prop1.js", repeatProp, print);
