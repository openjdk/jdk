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
 * NASHORN-207 : implement strict mode
 *
 * @test
 * @run
 */

'use strict';

// cannot delete a variable
try {
    eval("var fooVar = 3; delete fooVar;");
    fail("#1 should have thrown SyntaxError");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("#2 SyntaxError expected but got " + e);
    } 
}

// cannot delete function parameter variable
try {
    eval("function func(a) { delete a; }; func(2);");
    fail("#3 should have thrown SyntaxError");
} catch(e) {
    if (! (e instanceof SyntaxError)) {
        fail("#4 SyntaxError expected but got " + e);
    } 
}

// assignment can't be used to define as new variable
try {
    bar = 3;
    fail("#5 should have thrown ReferenceError");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("#6 ReferenceError expected but got " + e);
    }
}

// repeated property definition is not allowed in an object literal
try {
    eval("var obj = { foo: 33, foo: 44 }");
    fail("#7 should have thrown SyntaxError");
} catch(e) {
    if (! (e instanceof SyntaxError)) {
        fail("#8 SyntaxError expected but got " + e);
    }
}

// can't assign to "eval"
try {
    eval("var eval = 3");
    fail("#9 should have thrown SyntaxError");
} catch(e) {
    if (! (e instanceof SyntaxError)) {
        fail("#10 SyntaxError expected but got " + e);
    }
}

// can't assign to 'arguments'
try {
    eval("function func() { arguments = 34; }");
    fail("#11 should have thrown SyntaxError");
} catch(e) {
    if (! (e instanceof SyntaxError)) {
        fail("#12 SyntaxError expected but got " + e);
    }
}

// can't delete non-configurable properties
try {
    delete Object.prototype;
    fail("#13 should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#14 TypeError expected but got " + e);
    }
}

// cannot re-use function parameter name
try {
    eval("function func(a, a) {}");
    fail("#15 should have thrown SyntaxError");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("#16 SyntaxError expected but got " + e);
    }
}

// strict eval creates own scope and caller's scope is untouched!
eval("var myVar = 4;");
if (typeof myVar !== 'undefined') {
    fail("#17 eval var 'myVar' created in global scope");
}

eval("function myNewFunc() {}");
if (typeof myNewFunc !== 'undefined') {
    fail("#18 eval function 'myNewFunc' created in global scope");
}

// no octal in strict mode
try {
    eval("var x = 010;");
    fail("#19 should have thrown SyntaxError");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("#20 SyntaxError expected but got " + e);
    }
}
