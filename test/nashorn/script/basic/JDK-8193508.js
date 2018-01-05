/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8193508: Expressions in split literals must never be optimistic
 *
 * @test
 * @run
 * @option -Dnashorn.compiler.splitter.threshold=100
 * @fork
 */

function f() {
    return 'a';
}

var o = {
    a: f(),
    b: 1,
    c: 2,
    d: 3,
    e: 4,
    f: 5,
    g: f(),
    h: 1,
    i: 2,
    j: 3,
    k: 4,
    l: 5,
    m: f(),
    n: 1,
    o: 2,
    p: 3,
    q: 4,
    r: 5,
    s: f(),
    t: 1,
    u: 2,
    v: 3,
    w: 4,
    x: 5,
    y: f(),
    z: 1,
    A: 2,
    B: 3,
    C: 4,
    D: 5,
    E: f(),
    F: 1,
    G: 2,
    H: 3,
    I: 4,
    J: 5,
    K: f(),
    L: 1,
    M: 2,
    N: 3,
    O: 4,
    P: 5,
    Q: f(),
    R: 1,
    S: 2,
    T: 3,
    U: 4,
    V: 5,
    W: f(),
    X: 1,
    Y: 2,
    Z: 3
};

Assert.assertTrue(o.a === 'a');

