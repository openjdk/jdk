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

/*
 * NASHORN-565: ADD specialization is broken.
 *
 * @test
 * @run
 */

var s = "42";
var t = 3.14;
var u = (s + t) - 0; print(u);
var v = (s + 2) | 0; print(v);
var w = (new Number(s) + Number.POSITIVE_INFINITY) | 0; print(w); // check correct int32 conversion
var x = (new Number(s) + Number.POSITIVE_INFINITY) >>> 0; print(x); // check correct uint32 conversion
var y = (parseInt(0xdeadf00d) + 0xdeadffff00d) >>> 1; print(y); // check correct long->uint32 conversion
var z = (new Number(s) + Number.POSITIVE_INFINITY) - 0; print(z); // check correct double conversion
