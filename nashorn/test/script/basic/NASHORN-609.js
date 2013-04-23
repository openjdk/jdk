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
 * NASHORN-609: ASSIGN_SHR result is converted to INT (should be UINT32).
 *
 * @test
 * @run
 */
function test() {
    var shr = -1;
    print(shr >>>= 0, shr);
    var shr2 = 0xffffffff;
    print(shr2 >>>= 0, shr2);
    var shr3 = "-1";
    print(shr3 >>>= 0, shr3);
    var shr4 = -1.5;
    print(shr4 >>>= 0, shr4);
    var shr5 = 0xffffffffff;
    print(shr5 >>>= 0, shr5);
}
test();

