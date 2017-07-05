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
 * NASHORN-101:  switch statement should use === rather than == to compare case values against switch expression.
 *
 * @test
 * @run
 */

function func(x) {
    switch(x) {
        case 0:
            print("x === 0"); break;
        case 1:
            print("x === 1"); break;
        case true:
            print("x === true"); break;
        case false:
            print("x === false"); break;
        case null:
            print("x === null"); break;
        case undefined:
            print("x === undefined"); break;
        default:
            print("default case x " + x); break;
    }
}

func(0);
func(1);
func(2);
func("hello");
func(null);
func(undefined);
func(true);
func(false);
func({});
