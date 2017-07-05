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
 * Nashorn parser API -nse option test.
 *
 * @test
 * @option -scripting
 * @run
 */

var Parser = Java.type("jdk.nashorn.api.tree.Parser");
var noExtParser = Parser.create("-nse");
var parser = Parser.create();
var scriptingParser = Parser.create("-scripting");

var condCatch = <<EOF

try {
   that();
} catch (e if e instanceof ReferenceError) {}

EOF;

noExtParser.parse("cond_catch.js", condCatch, print);
parser.parse("cond_catch1.js", condCatch, print);

var funcClosure = <<EOF

function square(x) x*x;

EOF;

noExtParser.parse("func_closure.js", funcClosure, print);
parser.parse("func_closure1.js", funcClosure, print);

var forEach = <<EOF

for each (arg in arguments) print(arg);

EOF;

noExtParser.parse("for_each.js", forEach, print);
parser.parse("for_each1.js", forEach, print);

var anonNew = <<EOF

var r = new java.lang.Runnable() {
     run: function() { print("hello") }
};

EOF;

noExtParser.parse("anon_new.js", anonNew, print);
parser.parse("anon_new1.js", anonNew, print);

var anonFuncStat = <<EOF

function () { print("hello") }

EOF;

noExtParser.parse("anon_func_stat.js", anonFuncStat, print);
parser.parse("anon_func_stat1.js", anonFuncStat, print);

// These lexer (scripting) extensions should also be not parsed
// by "no extensions parser" ( as well as "default" parser )

var backquote = "`ls`";
noExtParser.parse("backquote.js", backquote, print);
parser.parse("backquote1.js", backquote, print);
scriptingParser.parse("backquote2.js", backquote, print);

var heredoc = "var str = <<EOF\nprint('hello')\nEOF\n";
noExtParser.parse("heredoc.js", heredoc, print);
parser.parse("heredoc1.js", heredoc, print);
scriptingParser.parse("heredoc2.js", heredoc, print);

var hashComment = "#comment\nprint('hello')";
noExtParser.parse("hashcomment.js", hashComment, print);
parser.parse("hashcomment1.js", hashComment, print);
scriptingParser.parse("hashcomment2.js", hashComment, print);
