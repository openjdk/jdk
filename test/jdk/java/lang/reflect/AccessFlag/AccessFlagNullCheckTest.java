/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8371953
 * @summary Null checks for AccessFlag and Location.
 * @run junit AccessFlagNullCheckTest
 */

import java.lang.reflect.AccessFlag;
import java.lang.reflect.ClassFileFormatVersion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessFlagNullCheckTest {
    @Test
    void accessFlagNullChecks() {
        assertThrows(NullPointerException.class, () -> AccessFlag.valueOf(null));
        assertThrows(NullPointerException.class, () -> AccessFlag.PUBLIC.locations(null));
        assertThrows(NullPointerException.class, () -> AccessFlag.maskToAccessFlags(0, null));
        assertThrows(NullPointerException.class, () -> AccessFlag.maskToAccessFlags(0, AccessFlag.Location.CLASS, null));
        assertThrows(NullPointerException.class, () -> AccessFlag.maskToAccessFlags(0, null, ClassFileFormatVersion.RELEASE_1));
    }

    @Test
    void locationNullChecks() {
        assertThrows(NullPointerException.class, () -> AccessFlag.Location.valueOf(null));
        assertThrows(NullPointerException.class, () -> AccessFlag.Location.CLASS.flags(null));
        assertThrows(NullPointerException.class, () -> AccessFlag.Location.CLASS.flagsMask(null));
    }
}
