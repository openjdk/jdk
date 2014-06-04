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
 * JDK-8041998: RegExp implementation is not thread-safe
 *
 * @test
 * @run
 */

var Thread = java.lang.Thread;

function run() {
    var line = 'content-type: text/html';
    for (var i = 0; i < 300; i++) {
        Thread.sleep(1);
        line.split(/: /);
    }
    print("done");
}

var threads = [];

for (var i = 0; i < 4; i++) {
    var thread = new Thread(run);
    thread.start();
    threads.push(thread);
}

for (var i = 0; i < 4; i++) {
    threads[i].join();
}
