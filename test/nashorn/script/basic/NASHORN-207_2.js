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
 * NASHORN-207 : Implement strict mode.
 *
 * @test
 * @run
 */

// make sure that 'use strict' as first directive inside eval
// also works the same way (as eval called from strict mode caller).

try {
    eval("'use strict'; foo = 4;");
    fail("#1 should have thrown ReferenceError");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("#2 expected ReferenceError but got " + e);
    }
}

if (typeof foo !== 'undefined') {
    fail("#3 strict mode eval defined var in global scope!");
}

try {
    eval("'use strict'; var let = 23;");
    fail("#4 should have thrown SyntaxError");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("#5 SyntaxError expected but got " + e);
    }
}

// non-strict mode, some of the future reserved words can be used
// as identifiers. These include "let", "implements", "yield" etc.
var let = 30;
var implements = "hello";
function yield() {}
function public() {}
var private = false;
var protected = "hello";
var interface = "interface";
function f(package) {}
function static() {}

// in strict mode, arguments does not alias named access
function func(x, y) {
    'use strict';

    if (x !== arguments[0]) {
        fail("#6 arguments[0] !== x");
    }

    if (y !== arguments[1]) {
        fail("#7 arguments[1] !== y");
    }

    arguments[0] = 1;
    arguments[1] = 2;

    if (x === arguments[0]) {
        fail("#8 arguments[0] === x after assignment to it");
    }

    if (y === arguments[1]) {
        fail("#9 arguments[1] === y after assignment to it ");
    }
}

func();

// functions can not be declared everywhere!!
try {
    eval("'use strict'; if (true) { function func() {} }");
    fail("#10 should have thrown SyntaxError");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("#11 SyntaxError expected got " + e);
    }
}

// arguments.caller and arguments.callee can't read or written in strict mode
function func2() {
    'use strict';

    try {
        print(arguments.callee);
        fail("#12 arguments.callee should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#13 TypeError expected, got " + e);
        }
    }

    try {
        print(arguments.caller);
        fail("#14 arguments.caller should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#15 TypeError expected, got " + e);
        }
    }

    try {
        arguments.caller = 10;
        fail("#16 arguments.caller assign should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#17 TypeError expected, got " + e);
        }
    }

    try {
        arguments.callee = true;
        fail("#18 arguments.callee assign should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#19 TypeError expected, got " + e);
        }
    }
}

func2();

// func.caller and func.arguments can't read or written in strict mode
function func3() {
    'use strict';

    try {
        print(func3.arguments);
        fail("#20 func.arguments should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#21 TypeError expected, got " + e);
        }
    }

    try {
        print(func3.caller);
        fail("#22 func3.caller should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#23 TypeError expected, got " + e);
        }
    }

    try {
        func3.arguments = 10;
        fail("#24 func3.arguments assign should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#25 TypeError expected, got " + e);
        }
    }

    try {
        func3.caller = true;
        fail("#26 func3.caller assign should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#27 TypeError expected, got " + e);
        }
    }
}

func3();

try {
    eval("function eval() { 'use strict'; }");
    fail("#28 should have thrown SyntaxError");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("#29 SyntaxError expected, got " + e);
    }
}

function func4() {
  'use \
strict';

    // The use strict directive can't contain line continuation.
    // So this is not a strict mode function!!
    with({}) {}
}

func4();

function func5() {
   'use\u2028strict';

    // The use strict directive can't contain unicode whitespace escapes
    // So this is not a strict mode function!!
    with({}) {}
}

func5();

function func6() {
   'use\u2029strict';

    // The use strict directive can't contain unicode whitespace escapes
    // So this is not a strict mode function!!
    with({}) {}
}

func6();

try {
    eval("'bogus directive'; 'use strict'; eval = 10");
    fail("#30 SyntaxError expected from eval");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("#31 SyntaxError expected but got " + e);
    }
}
