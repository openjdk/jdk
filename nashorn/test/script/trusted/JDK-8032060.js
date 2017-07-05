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
 * JDK-8032060: PropertyMap of Error objects is not stable
 *
 * @test
 * @option -Dnashorn.debug=true
 * @fork
 * @run
 */

function checkMap(e1, e2) {
    if (! Debug.identical(Debug.map(e1), Debug.map(e2))) {
        fail("e1 and e2 have different maps");
    }

    var m1, m2;

    try {
        throw e1
    } catch (e) {
        m1 = Debug.map(e)
    }

    try {
        throw e2
    } catch (e) {
        m2 = Debug.map(e)
    }

    if (! Debug.identical(m1, m2)) {
        fail("e1 and e2 have different maps after being thrown");
    }
}

checkMap(new Error(), new Error());
checkMap(new EvalError(), new EvalError());
checkMap(new RangeError(), new RangeError());
checkMap(new ReferenceError(), new ReferenceError());
checkMap(new SyntaxError(), new SyntaxError());
checkMap(new TypeError(), new TypeError());
checkMap(new URIError(), new URIError());

// now try with message param
checkMap(new Error("x"), new Error("y"));
checkMap(new EvalError("x"), new EvalError("y"));
checkMap(new RangeError("x"), new RangeError("y"));
checkMap(new ReferenceError("x"), new ReferenceError("y"));
checkMap(new SyntaxError("x"), new SyntaxError("y"));
checkMap(new TypeError("x"), new TypeError("y"));
checkMap(new URIError("x"), new URIError("y"));
