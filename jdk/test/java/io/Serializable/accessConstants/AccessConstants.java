/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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
@test
@bug 4067964
@clean AccessConstants
@build AccessConstants
@summary Verify that ObjectStreamConstants is public accessible.
         This test will not compile pre-JDK 1.2.
*/

import java.io.ObjectStreamConstants;

public class AccessConstants {
    public static void main(String[] args) {
        byte[] ref = new byte[4];
        ref[0] = ObjectStreamConstants.TC_BASE;
        ref[1] = ObjectStreamConstants.TC_NULL;
        ref[2] = ObjectStreamConstants.TC_REFERENCE;
        ref[3] = ObjectStreamConstants.TC_CLASSDESC;
        int version = ObjectStreamConstants.PROTOCOL_VERSION_1;
    }
}
