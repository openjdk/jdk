/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8156896: Script stack trace should display function names
 *
 * @test
 * @run
 */

function checkNamedFunction(stack) {
    Assert.assertTrue(stack.indexOf("Error\n\tat bar (") === 0);
}

function checkAnonymousFunction(stack) {
    Assert.assertTrue(stack.indexOf("Error\n\tat <anonymous> (") === 0);
}

// Named functions
function bar() { try { throw new Error(); } catch(e) { return e.stack; } }
checkNamedFunction(bar());

bar = function() { try { throw new Error(); } catch(e) { return e.stack; } };
checkNamedFunction(bar());

f = (function() {return function bar() { try { throw new Error(); } catch(e) { return e.stack; } } })();
checkNamedFunction(f());

f = new Function("return function bar() { try { throw new Error(); } catch(e) { return e.stack; } }")();
checkNamedFunction(f());

// Anonymous functions
checkAnonymousFunction((function() { try { throw new Error(); } catch(e) { return e.stack; } })());

f = (function() {return function() { try { throw new Error(); } catch(e) { return e.stack; } } })();
checkAnonymousFunction(f());

f = new Function("return function() { try { throw new Error(); } catch(e) { return e.stack; } }")();
checkAnonymousFunction(f());
