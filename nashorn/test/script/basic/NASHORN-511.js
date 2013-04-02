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
 * NASHORN-511 : var has no effect on existing globals.
 *
 * @test
 * @run
 */

var y = 23;
if (delete y != false) {
    fail('#1: can delete declared var y');
}
if (delete this.y != false) {
    fail('#2: can delete declared var using this.y');
}

var parseInt = 23;
if (delete parseInt !== true) {
    fail('#3: delete parseInt !== true');
}
if (delete this.parseInt !== true) {
    fail('#4: delete this.parseInt !== true');
}

z = 24;
eval("var z = 25;\
if (delete z !== true) {\
    fail('#5: delete z !== true');\
}\
if (delete this.z !== true) {\
    fail('#6: delete this.z !== true');\
}\
");
