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
 * JDK-8027302: Identifiers containing unicode escapes are not recognized as reserved words
 *
 * @test
 * @run
 * @option --language=es6
 */

// keywords containing escapes

try {
    eval("v\\u0061r i;");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("\\u0069f (true) ;");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("if (true) ; \\u0065lse ;");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("f\\u0075nction x() {}");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("var f = f\\u0075nction() {}");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("var o = { f: f\\u0075nction() {}}");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("var a = [f\\u0075nction() {}]");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

// keywords as identifiers, with and without escapes

try {
    eval("function break() {}");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("function bre\\u0061k() {}");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("function f(break) {}");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("function f(bre\\u0061k) {}");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("var break = 3");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("'use strict'; var break = 3");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("var bre\\u0061k = 3");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("'use strict'; var bre\\u0061k = 3");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("var package = 3");
} catch (e) {
    fail("Unexpected error");
}

try {
    eval("'use strict'; var package = 3");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

try {
    eval("var p\\u0061ckage = 3");
} catch (e) {
    fail("Unexpected error");
}

try {
    eval("'use strict'; var p\\u0061ckage = 3");
    fail("Expected error");
} catch (e) {
    Assert.assertTrue(e instanceof SyntaxError);
}

