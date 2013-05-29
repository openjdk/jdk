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
 * JDK-8015349: "abc".lastIndexOf("a",-1) should evaluate to 0 and not -1
 *
 * @test
 * @run
 */

function printEval(code) {
    print(code + " = " + eval(code));
}

printEval("'abc'.lastIndexOf('a', 4)"); 
printEval("'abc'.lastIndexOf('b', Infinity)");
printEval("'abc'.lastIndexOf('a', -1)");
printEval("'abc'.lastIndexOf('a', -Infinity)");
printEval("'oracle'.lastIndexOf('u')");
printEval("'hello'.lastIndexOf('l')");
printEval("'hello'.lastIndexOf('l', 2)");
printEval("'hello'.lastIndexOf('l', 3)");
printEval("'hello'.lastIndexOf('l', 1)");
