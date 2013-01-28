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
 * 8006983: Introduce a command line option to switch off syntactic extensions of nashorn
 *
 * @test
 * @option -scripting
 * @option --no-syntax-extensions
 * @run
 */

try {
    eval("var r = new java.lang.Runnable() { run: function(){} }");
    fail("should have thrown error for anon-class-style new");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected, got " + e);
    }
}

try {
    eval("var sqr = function(x) x*x ");
    fail("should have thrown error for expression closures");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected, got " + e);
    }
}

try {
    eval("function() {};");
    fail("should have thrown error for anonymous function statement");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected, got " + e);
    }
}

try {
    eval("for each (i in [22, 33, 33]) { print(i) }");
    fail("should have thrown error for-each statement");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected, got " + e);
    }
}

try {
    eval("# shell style comment");
    fail("should have thrown error for shell style comment");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected, got " + e);
    }
}

try {
    eval("print(<<EOF);\nhello\nworld\nEOF\n");
    fail("should have thrown error heredoc");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected, got " + e);
    }
}
