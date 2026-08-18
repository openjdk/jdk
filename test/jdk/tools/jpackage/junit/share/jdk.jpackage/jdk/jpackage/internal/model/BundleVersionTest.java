/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BundleVersionTest {

    @ParameterizedTest
    @MethodSource("data")
    void test_of_DottedVersion(String ver) {

        var dottedVer = DottedVersion.lazy(ver);

        var bundleVer = BundleVersion.of(dottedVer);

        assertSame(dottedVer, bundleVer.asDottedVersion().orElseThrow());
        assertEquals(ver, bundleVer.asDottedVersion().orElseThrow().toString());
        assertEquals(ver, bundleVer.toString());
    }

    @Test
    void test_of_DottedVersion_null() {
        assertThrowsExactly(NullPointerException.class, () -> {
            BundleVersion.of((DottedVersion)null);
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    void test_of_String(String ver) {

        var bundleVer = BundleVersion.of(ver);

        assertEquals(ver, bundleVer.asDottedVersion().orElseThrow().toString());
        assertEquals(ver, bundleVer.toString());
    }

    @Test
    void test_of_String_null() {
        assertThrowsExactly(NullPointerException.class, () -> {
            BundleVersion.of((String)null);
        });
    }

    static Stream<Object> data() {
        return Stream.of(
                "''",
                "' '",
                "abc",
                "1.",
                "1.b.2",
                "1.02.3",
                "1",
                "1-foo").map(Arguments::of);
    }
}
