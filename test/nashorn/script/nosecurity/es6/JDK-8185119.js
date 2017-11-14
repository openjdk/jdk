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
 * JDK-8185119: Uninitialized const when using multiple threads
 *
 * @test
 * @option --language=es6
 * @run
 */

function f() {
    let a;
    const b = {};
    b.crash; // b is sometimes undefined

    function c() {
        a; b;
    }
}

let count = new java.util.concurrent.atomic.AtomicInteger();

let T = Java.extend(Java.type('java.lang.Thread'), {
    run: function() {
        for (let j = 0; j < 100; j++) {
            f();
        }
        count.getAndIncrement();
    }
});

const threads = [new T(), new T(), new T(), new T()];
threads.forEach(t => t.start());
threads.forEach(t => t.join());

Assert.assertEquals(count.intValue(), 4);

