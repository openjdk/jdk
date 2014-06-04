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
 * JDK-8026367: Add a sync keyword to mozilla_compat
 *
 * @test
 * @run
 */

if (typeof sync === "undefined") {
    load("nashorn:mozilla_compat.js");
}

var obj = {
    count: 0,
    // Sync called with one argument will synchronize on this-object of invocation
    inc: sync(function(d) {
        this.count += d;
    }),
    // Pass explicit object to synchronize on as second argument
    dec: sync(function(d) {
        this.count -= d;
    }, obj)
};

var t1 = new java.lang.Thread(function() {
    for (var i = 0; i < 100000; i++) obj.inc(1);
});
var t2 = new java.lang.Thread(function() {
    for (var i = 0; i < 100000; i++) obj.dec(1);
});

t1.start();
t2.start();
t1.join();
t2.join();

if (obj.count !== 0) {
    throw new Error("Expected count == 0, got " + obj.count);
}
