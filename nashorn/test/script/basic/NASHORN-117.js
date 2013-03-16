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
 * NASHORN-117 :  StackOverflowError because of recursive class loading involving LayoutGenerator
 *
 * @test
 * @run
 */

// The following code results in StackOverflowError 

var i0 = "";
var o0 = "";
var i1 = "";
var o1 = "";
var i2 = "";
var o2 = "";
var i3 = "";
var o3 = "";
var i4 = "";
var o4 = "";
var i5 = "";
var o5 = "";
var i6 = "";
var o6 = "";
var i7 = "";
var o7 = "";
var i8 = "";
var o8 = "";
var i9 = "";
var o9 = "";
var i10 = "";
var o10 = "";
var i11 = "";
var o11 = "";
var i12 = "";
var o12 = "";
var i13 = "";
var o13 = "";
var i14 = "";
var o14 = "";
var i15 = "";
var o15 = "";
var i16 = "";
var o16 = "";
var i17 = "";
var o17 = "";
var i18 = "";
var o18 = "";
var i19 = "";
var o19 = "";
var i20 = "";
var o20 = "";
var i21 = "";
var o21 = "";
var i22 = "";
var o22 = "";
var i23 = "";
var o23 = "";
var i24 = "";
var o24 = "";
var i25 = "";
var o25 = "";
var i26 = "";
var o26 = "";
var i27 = "";
var o27 = "";
var i28 = "";
var o28 = "";
var i29 = "";
var o29 = "";
var i30 = "";
var o30 = "";
var i31 = "";
var o31 = "";
var i32 = "";
var o32 = "";
var i33 = "";
var o33 = "";
var i34 = "";
var o34 = "";
var i35 = "";
var o35 = "";
var i36 = "";
var o36 = "";
var i37 = "";
var o37 = "";
var i38 = "";
var o38 = "";
var i39 = "";
var o39 = "";
var i40 = "";
var o40 = "";
var i41 = "";
var o41 = "";
var i42 = "";
var o42 = "";
var i43 = "";
var o43 = "";
var i44 = "";
var o44 = "";
var i45 = "";
var o45 = "";
var i46 = "";
var o46 = "";
var i47 = "";
var o47 = "";
var i48 = "";
var o48 = "";
var i49 = "";
var o49 = "";
var i50 = "";
var o50 = "";
var i51 = "";
var o51 = "";
var i52 = "";
var o52 = "";
var i53 = "";
var o53 = "";
var i54 = "";
var o54 = "";
var i55 = "";
var o55 = "";
var i56 = "";
var o56 = "";
var i57 = "";
var o57 = "";
var i58 = "";
var o58 = "";
var i59 = "";
var o59 = "";
var i60 = "";
var o60 = "";
var i61 = "";
var o61 = "";
var i62 = "";
var o62 = "";
var i63 = "";
var o63 = "";
