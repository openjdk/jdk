/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8047057: Add a regression test for the passing test cases from JDK-8042304
 *
 * @test
 * @run
 */

// commented out makeFuncAndCall calls are still result in crash
// Tests commented with //** fail only when assertions are turned on

function makeFuncAndCall(code) {
    Function(code)();
}

function makeFuncExpectError(code, ErrorType) {
    try {
        makeFuncAndCall(code);
    } catch (e) {
        if (! (e instanceof ErrorType)) {
            fail(ErrorType.name + " expected, got " + e);
        }
    }
}

makeFuncAndCall("switch(0) { default: {break;} return }");
makeFuncAndCall("L: { { break L; } return; }");
makeFuncAndCall("L: { while(0) break L; return; }");
makeFuncExpectError("L: {while(0) break L; return [](); }", TypeError);
makeFuncAndCall("do with({}) break ; while(0);");
makeFuncAndCall("while(0) with({}) continue ;");
makeFuncAndCall("eval([]);");
makeFuncAndCall("try{} finally{[]}");
makeFuncAndCall("try { } catch(x if 1) { try { } catch(x2) { } }");
makeFuncAndCall("try { } catch(x if 1) { try { return; } catch(x2) { { } } }");
makeFuncAndCall("Error() * (false)[-0]--");
makeFuncAndCall("try { var x = 1, x = null; } finally { }");
makeFuncAndCall("try { var x = {}, x = []; } catch(x3) { }");
makeFuncAndCall("[delete this]");
makeFuncAndCall("if(eval('', eval('', function() {}))) { }");
makeFuncAndCall("if(eval('', eval('', function() {}))) { }");
makeFuncAndCall("eval(\"[,,];\", [11,12,13,14].some)");
makeFuncAndCall("eval(\"1.2e3\", ({})[ /x/ ])");
makeFuncExpectError("eval(\"x4\", x3);", ReferenceError);
makeFuncAndCall("with({5.0000000000000000000000: String()}){(false); }");
makeFuncAndCall("try { var x = undefined, x = 5.0000000000000000000000; } catch(x) { x = undefined; }");
makeFuncAndCall("(function (x){ x %= this}(false))");
makeFuncAndCall("eval.apply.apply(function(){ eval('') })");
makeFuncAndCall("(false % !this) && 0");
makeFuncAndCall("with({8: 'fafafa'.replace()}){ }");
makeFuncAndCall("(function (x) '' )(true)");
makeFuncExpectError("new eval(function(){})", TypeError);
makeFuncAndCall('eval("23", ({})[/x/])');
