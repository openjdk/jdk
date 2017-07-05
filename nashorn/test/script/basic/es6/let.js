/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8051889: Implement block scoping in symbol assignment and scope computation
 *
 * @test
 * @run
 * @option --language=es6
 */

"use strict";

let a = 2;
let c = 2;
print(a, c);

function f(x) {
    let a = 5;
    const c = 10;
    print(a, c);
    if (x) {
        let a = 42;
        const c = 43;
        print(a, c);
    }
    print(a, c);

    function inner() {
        (function() {
            print(a, c);
        })();
    }
    inner();
}

f(true);
f(false);

(function() {
    (function() {
        print(a, c);
    })();
})();

function outer() {
    print(a, c);
}
outer();

