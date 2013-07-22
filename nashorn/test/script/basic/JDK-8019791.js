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
 * JDK-8019791: ~ is a unary operator
 *
 * @test
 * @run
 */

// Used to crash instead of SyntaxError
try {
    eval('"" ~ ""');
    print("FAILED: SyntaxError expected for: \"\" ~ \"\"");
} catch (e) {
    print(e.toString().replace(/\\/g, '/'));
}

// Used to crash instead of SyntaxError
try {
    eval("function() { if (1~0) return 0; return 1 }");
    print("FAILED: SyntaxError expected for: if (1~0) ");
} catch (e) {
    print(e.toString().replace(/\\/g, '/'));
}

// The following are valid, but used to crash
Function("0 \n ~ 2 \n ~ 1")();

Function("~ ~ 0 \n ~ ~ 1")();
