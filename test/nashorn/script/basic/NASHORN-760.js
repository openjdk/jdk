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
 * NASHORN-111 :  ClassCastException from JSON.stringify
 *
 * @test
 * @run
 */
// problem 1
// the conversions in TernaryNode are not necessary, but they should not cause problems. They did
// this was because the result of Global.allocate(Object[])Object which returns a NativeObject.
// was tracked as an object type on our stack. The type system did not recognize this as an array.
// Then the explicit conversions became "convert NativeArray->Object[]" which is a checkccast Object[]
// which naturally failed.

// I pushed the appropriate arraytype on the stack for Global.allocate.

// I also removed the conversions in CodeGen, all conversions should be done in Lower, as
// NASHORN-706 states.

var silent = false;
var stdio = silent ? ['pipe', 'pipe', 'pipe', 'ipc'] : [0, 1, 2, 'ipc'];

// This made the test pass, but it's still not correct to pick widest types for array
// and primitives. Widest(Object[], int) gave us Object[] which makes no sense. This is used
// by lower to type the conversions, so function b below also failed until I made a change
// ty type widest to actually return the widest common denominator, if both aren't arrays

function b() {
    var silent2 = false;
    var stdio2 = silent2 ? [1,2,3] : 17;
}

