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
 * SAM interface implemented by a script function.
 *
 * @test
 * @run
 */

// we can pass a script function when an interface with single
// abstract method is required. Here we pass script function for
// a java.lang.Runnable.

var t1 = new java.lang.Thread(function() {
    print("thread t1.run");
}); 

// make sure that we can pass interface implementation as well.
var t2 = new java.lang.Thread(new java.lang.Runnable() {
   run: function() { print("thread t2.run"); }
});

t1.start();
t1.join();

t2.start();
t2.join();
