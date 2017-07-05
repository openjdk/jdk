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
 * JDK-8019814: Add regression test for passing cases
 *
 * @test
 * @run
 */

// java.lang.VerifyError: Bad type on operand stack
Function("switch([]) { case 7: }");

// java.lang.AssertionError: expecting integer type or object for jump, but found double
Function("with(\nnull == (this % {}))( /x/g );");

// java.lang.AssertionError: expecting equivalent types on stack but got double and int 
try {
    eval('Function("/*infloop*/while(((function ()4.)([z1,,], [,,]) - true++))switch(1e+81.x) { default: break; \u0009 }")');
} catch (e) {
    print(e.toString().replace(/\\/g, '/'));
}

// java.lang.VerifyError: get long/double overflows locals
Function("var x = x -= '' ");

// java.lang.AssertionError: object is not compatible with boolean
Function("return (null != [,,] <= this);");

// java.lang.AssertionError: Only return value on stack allowed at return point
// - depth=2 stack = jdk.nashorn.internal.codegen.Label$Stack@4bd0d62f 
Function("x = 0.1, x\ntrue\n~this");

// java.lang.AssertionError: node NaN ~ window class jdk.nashorn.internal.ir.BinaryNode
// has no symbol! [object] function _L1() 
Function("throw NaN\n~window;");

// java.lang.AssertionError: array element type doesn't match array type
Function("if(([(this >>> 4.)].map(gc))) x;");

try {
    eval('Function("if(--) y;")');
} catch (e) {
    print(e.toString().replace(/\\/g, '/'));
}

// java.lang.AssertionError: stacks jdk.nashorn.internal.codegen.Label$Stack@4918f90f
// is not equivalent with jdk.nashorn.internal.codegen.Label$Stack@5f9b21a1 at join point 
Function("if((null ^ [1]) !== (this.yoyo(false))) {var NaN, x;x\n~[,,z1] }");

// java.lang.AssertionError
//    at jdk.nashorn.internal.codegen.Attr.enterFunctionBody(Attr.java:276) 
Function("return (void ({ set each (x2)y }));"); 
