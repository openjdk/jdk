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
 * arity test.
 *
 * @test
 * @run
 */

function func1(a) { print(a); }
function func2(a, b) { print(a, b); }
function func3(a, b, c) { print(a, b, c); }
function func4(a, b, c, d) { print(a, b, c, d); }
function func5(a, b, c, d, e) { print(a, b, c, d, e); }
function func6(a, b, c, d, e, f) { print(a, b, c, d, e, f); }
function func7(a, b, c, d, e, f, g) { print(a, b, c, d, e, f, g); }
function func8(a, b, c, d, e, f, g, h) { print(a, b, c, d, e, f, g, h); }
function func9(a, b, c, d, e, f, g, h, i) { print(a, b, c, d, e, f, g, h, i); }
function func10(a, b, c, d, e, f, g, h, i, j) { print(a, b, c, d, e, f, g, h, i, j); }
function func11(a, b, c, d, e, f, g, h, i, j, k) { print(a, b, c, d, e, f, g, h, i, j, k); }
function func12(a, b, c, d, e, f, g, h, i, j, k, l) { print(a, b, c, d, e, f, g, h, i, j, k, l); }
function func13(a, b, c, d, e, f, g, h, i, j, k, l, m) { print(a, b, c, d, e, f, g, h, i, j, k, l, m); }

function vfunc1(a) { print(a); arguments; }
function vfunc2(a, b) { print(a, b); arguments; }
function vfunc3(a, b, c) { print(a, b, c); arguments; }
function vfunc4(a, b, c, d) { print(a, b, c, d); arguments; }
function vfunc5(a, b, c, d, e) { print(a, b, c, d, e); arguments; }
function vfunc6(a, b, c, d, e, f) { print(a, b, c, d, e, f); arguments; }
function vfunc7(a, b, c, d, e, f, g) { print(a, b, c, d, e, f, g); arguments; }
function vfunc8(a, b, c, d, e, f, g, h) { print(a, b, c, d, e, f, g, h); arguments; }
function vfunc9(a, b, c, d, e, f, g, h, i) { print(a, b, c, d, e, f, g, h, i); arguments; }
function vfunc10(a, b, c, d, e, f, g, h, i, j) { print(a, b, c, d, e, f, g, h, i, j); arguments; }
function vfunc11(a, b, c, d, e, f, g, h, i, j, k) { print(a, b, c, d, e, f, g, h, i, j, k); arguments; }
function vfunc12(a, b, c, d, e, f, g, h, i, j, k, l) { print(a, b, c, d, e, f, g, h, i, j, k, l); arguments; }
function vfunc13(a, b, c, d, e, f, g, h, i, j, k, l, m) { print(a, b, c, d, e, f, g, h, i, j, k, l, m); arguments; }

func1(1);
func2(1, 2);
func3(1, 2, 3);
func4(1, 2, 3, 4);
func5(1, 2, 3, 4, 5);
func6(1, 2, 3, 4, 5, 6);
func7(1, 2, 3, 4, 5, 6, 7);
func8(1, 2, 3, 4, 5, 6, 7, 8);
func9(1, 2, 3, 4, 5, 6, 7, 8, 9);
func10(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
func11(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
func12(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
func13(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);

func1(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
func13(1);

vfunc1(1);
vfunc2(1, 2);
vfunc3(1, 2, 3);
vfunc4(1, 2, 3, 4);
vfunc5(1, 2, 3, 4, 5);
vfunc6(1, 2, 3, 4, 5, 6);
vfunc7(1, 2, 3, 4, 5, 6, 7);
vfunc8(1, 2, 3, 4, 5, 6, 7, 8);
vfunc9(1, 2, 3, 4, 5, 6, 7, 8, 9);
vfunc10(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
vfunc11(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
vfunc12(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
vfunc13(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);

vfunc1(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
vfunc13(1);
