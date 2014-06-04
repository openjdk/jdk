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
 * Check behavior of class-level overrides.
 *
 * @test
 * @run
 */


// Make two classes with class overrides

var R1 = Java.extend(java.lang.Runnable, {
    run: function() {
        print("R1.run() invoked")
    }
})

var R2 = Java.extend(java.lang.Runnable, {
    run: function() {
        print("R2.run() invoked")
    }
})

var r1 = new R1
var r2 = new R2
// Create one with an instance-override too
var R3 = Java.extend(R2)
var r3 = new R3({ run: function() { print("r3.run() invoked") }})

// Run 'em - we're passing them through a Thread to make sure they indeed
// are full-blown Runnables
function runInThread(r) {
    var t = new java.lang.Thread(r)
    t.start()
    t.join()
}
runInThread(r1)
runInThread(r2)
runInThread(r3)

// Two class-override classes differ
print("r1.class !== r2.class: " + (r1.class !== r2.class))
// instance-override class also differs
print("r2.class !== r3.class: " + (r2.class !== r3.class))

function checkAbstract(r) {
    try {
        r.run()
        print("Expected to fail!")
    } catch(e) {
        print("Got exception: " + e)
    }
}

// Check we're hitting UnsupportedOperationException if neither class
// overrides nor instance overrides are present
var RAbstract = Java.extend(java.lang.Runnable, {})
checkAbstract(new RAbstract()) // class override (empty)
checkAbstract(new (Java.extend(RAbstract))() {}) // class+instance override (empty)

// Check we delegate to superclass if neither class
// overrides nor instance overrides are present
var ExtendsList = Java.extend(java.util.ArrayList, {})
print("(new ExtendsList).size() = " + (new ExtendsList).size())
print("(new (Java.extend(ExtendsList)){}).size() = " + (new (Java.extend(ExtendsList)){}).size())
