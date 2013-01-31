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
 * Test to check JavaAdapter constructor.
 *
 * @test
 * @run
 */

try {
    load("nashorn:mozilla_compat.js");
} catch (e) {
}

// try various JavaAdapter cases

// Single interface
var runReached = false;
var r = new JavaAdapter(java.lang.Runnable) {
    run: function() {
        runReached = true;
    }
};

r.run();
if (! runReached) {
    fail("run was not called");
}

if (! (r instanceof java.lang.Runnable)) {
    fail("r is not a Runnable");
}

// Multiple intefaces
var runReached = false;
var actionPerformedReached = false;

var obj = new JavaAdapter(java.awt.event.ActionListener, java.lang.Runnable) {
    actionPerformed : function(e) {
        actionPerformedReached = true;
    },

    run: function() {
        runReached = true;
    }
};

obj.actionPerformed(null);
if (! actionPerformedReached) {
    fail("actionPerformed was not called");
}

obj.run();
if (! runReached) {
    fail("run was not called");
}

if (! (obj instanceof java.lang.Runnable)) {
    fail("obj is not a Runnable");
}

if (! (obj instanceof java.awt.event.ActionListener)) {
    fail("obj is not an ActionListener");
}

// Single class
var obj = new JavaAdapter(java.lang.Object) {
    toString: function() { return "I am an Object"; }
};

if (! (obj instanceof java.lang.Object)) {
    fail("obj is not an instance of java.lang.Object");
}

if (obj.toString() != "I am an Object") {
    fail("Object.toString did not get called");
}

// Single class and single interface
var runReached = false;
var obj = new JavaAdapter(java.lang.Object, java.lang.Runnable) {
    run: function() {
        runReached = true;
    },

    hashCode: function() {
        return 12;
    }
};

obj.run();
if (! runReached) {
    fail("run was not called");
}

if (obj.hashCode() != 12) {
    fail("hashCode does not return 12");
}
