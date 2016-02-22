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
 * Make sure ECMAScript 6 features are not available in ES5 mode.
 *
 * @test
 * @run
 */

function checkUndefined(name, object) {
    if (typeof object[name] !== 'undefined' || name in object) {
        Assert.fail(name + ' is defined in ' + object);
    }
}

checkUndefined('Symbol', this);
checkUndefined('Map', this);
checkUndefined('Set', this);
checkUndefined('WeakMap', this);
checkUndefined('WeakSet', this);
checkUndefined('getOwnPropertySymbols', Object);
checkUndefined('entries', Array.prototype);
checkUndefined('values', Array.prototype);
checkUndefined('keys', Array.prototype);

function expectError(src, msg, error) {
    try {
        eval(src);
        Assert.fail(msg);
    } catch (e) {
        if (e.name !== error) {
            Assert.fail('Unexpected error: ' + e);
        }
    }
}

expectError('let i = 0', 'let', 'SyntaxError');
expectError('const i = 0', 'const', 'SyntaxError');
expectError('for (let i = 0; i < 10; i++) print(i)', 'for-let', 'SyntaxError');
expectError('0b0', 'numeric literal', 'SyntaxError');
expectError('0o0', 'numeric litera', 'SyntaxError');
expectError('`text`', 'template literal', 'SyntaxError');
expectError('`${ x }`', 'template literal', 'SyntaxError');
expectError('`text ${ x } text`', 'template literal', 'SyntaxError');
expectError('f`text`', 'template literal', 'SyntaxError');
