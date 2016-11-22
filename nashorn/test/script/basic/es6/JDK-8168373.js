/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8168373: don't emit conversions for symbols outside their lexical scope
 *
 * @test
 * @run
 * @option --language=es6
 */

function p() { return false } // "predicate"
function r(x) { return x } // "read"

(function() {
  try { // Try creates control flow edges from assignments into catch blocks.
    // Lexically scoped, never read int variable (undefined at catch block) but still with a cf edge into catch block.
    // Since it's never read, it's not written either (Nashorn optimizes some dead writes).
    let x = 0; 
    if (p()) { throw {}; } // We need `p()` so this block doesn't get optimized away, for possibility of a `throw` 
    x = 0.0; // change the type of x to double
    r(x); // read x otherwise it's optimized away
  } catch (e) {} // under the bug, "throw" will try to widen unwritten int x to double for here and cause a verifier error
})()
