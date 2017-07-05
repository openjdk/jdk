/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @option -Dnashorn.debug=true
 * @fork
 */

load(__DIR__ + "maputil.js");

// check that builtin objects share property map

assertSameMap(new Boolean(true), new Boolean(false));
assertSameMap(new Number(3), new Number(Math.PI));
assertSameMap(new String('hello'), new String('world'));
assertSameMap(new Object(), new Object());
assertSameMap(/hello/, /world/);
// try w/without regexp flags
assertSameMap(/hello/i, /world/g);
assertSameMap(new Date(), new Date());
assertSameMap(new Date(2000, 1, 1), new Date(1972, 5, 6));
assertSameMap(Function(), Function());
assertSameMap(Function("x", "return x"), Function("x", "return x*x"));
assertSameMap(new Error(), new Error());
assertSameMap(new Error('foo'), new Error('bar'));
assertSameMap(new EvalError(), new EvalError());
assertSameMap(new EvalError('foo'), new EvalError('bar'));
assertSameMap(new RangeError(), new RangeError());
assertSameMap(new RangeError('foo'), new RangeError('bar'));
assertSameMap(new ReferenceError(), new ReferenceError());
assertSameMap(new ReferenceError('foo'), new ReferenceError('bar'));
assertSameMap(new SyntaxError(), new SyntaxError());
assertSameMap(new SyntaxError('foo'), new SyntaxError('bar'));
assertSameMap(new TypeError(), new TypeError());
assertSameMap(new TypeError('foo'), new TypeError('bar'));
assertSameMap(new URIError(), new URIError());
assertSameMap(new URIError('foo'), new URIError('bar'));
