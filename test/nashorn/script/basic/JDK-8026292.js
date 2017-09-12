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
 * JDK-8026292: Megamorphic setter fails with boolean value
 *
 * @test
 * @run
 */

function megamorphic(o) {
    o.w = true;
    if (!o.w)
        throw new Error();
}

// Calls below must exceed megamorphic callsite threshhold
for (var i = 0; i < 10; i++) {
    megamorphic({a: 1});
    megamorphic({b: 1});
    megamorphic({c: 1});
    megamorphic({d: 1});
    megamorphic({e: 1});
    megamorphic({f: 1});
    megamorphic({g: 1});
    megamorphic({h: 1});
    megamorphic({i: 1});
    megamorphic({j: 1});
    megamorphic({k: 1});
    megamorphic({l: 1});
    megamorphic({m: 1});
    megamorphic({n: 1});
    megamorphic({o: 1});
    megamorphic({p: 1});
    megamorphic({q: 1});
    megamorphic({r: 1});
    megamorphic({s: 1});
    megamorphic({t: 1});
    megamorphic({u: 1});
    megamorphic({v: 1});
    megamorphic({w: 1});
    megamorphic({x: 1});
    megamorphic({y: 1});
    megamorphic({z: 1});
}
