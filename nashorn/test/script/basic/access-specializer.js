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
 * This is a simple test that checks that access specialization in FinalizeTypes is consistent.
 * Here, a2 = 0 will be turned int {I}a2 = 0, and all would be fine and well, only we can't change
 * the symbol type for a2 from double, and we can't as it's not a temporary. Either we have to put 
 * a temporary in at the late finalize stage and add another assignment, or we genericize the check
 * in CodeGenerator#Store so we detect whether a target is of the wrong type before storing. It 
 * is hopefully very rare, and will only be a problem when assignment results that have been
 * specialized live on the stack
 *
 * @test
 * @run
 */

function f() {
    var a0 = a1 = a2 = 0;
    a0 = 16.1;
    a1 = 17.1;
    a2 = 18.1;
}
f();
