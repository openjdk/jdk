/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing PackageDesc.
 * @run junit PackageDescTest
 */
import java.lang.constant.PackageDesc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackageDescTest {
    @ParameterizedTest
    @ValueSource(strings = {"a/b.d", "a[]", "a;"})
    void testInvalidPackageNames(String pkg) {
        assertThrows(IllegalArgumentException.class, () -> PackageDesc.of(pkg));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a/b.d", "a[]", "a;"})
    void testInvalidInternalPackageNames(String pkg) {
        assertThrows(IllegalArgumentException.class, () -> PackageDesc.ofInternalName(pkg));
    }

    @Test
    void testNullInternalPackageNames() {
        assertThrows(NullPointerException.class, () -> PackageDesc.ofInternalName(null));
    }

    @Test
    void testValidPackageNames() {
        assertEquals(PackageDesc.of(""), PackageDesc.ofInternalName(""));
        assertEquals(PackageDesc.of("a"), PackageDesc.ofInternalName("a"));
        assertEquals(PackageDesc.of("a.b"), PackageDesc.ofInternalName("a/b"));
        assertEquals(PackageDesc.of("a.b.c"), PackageDesc.ofInternalName("a/b/c"));
        assertEquals(PackageDesc.of("a").name(), PackageDesc.ofInternalName("a").name());
        assertEquals(PackageDesc.of("a.b").name(), PackageDesc.ofInternalName("a/b").name());
        assertEquals(PackageDesc.of("a.b.c").name(), PackageDesc.ofInternalName("a/b/c").name());
        assertEquals(PackageDesc.of("a").internalName(), PackageDesc.ofInternalName("a").internalName());
        assertEquals(PackageDesc.of("a.b").internalName(), PackageDesc.ofInternalName("a/b").internalName());
        assertEquals(PackageDesc.of("a.b.c").internalName(), PackageDesc.ofInternalName("a/b/c").internalName());
    }
}
