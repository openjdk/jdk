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
package jdk.jpackage.internal.cli;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class StandardBundlingOperationTest {

    @Test
    public void testUniqueDescriptors() {
        Stream.of(StandardBundlingOperation.values()).collect(toMap(StandardBundlingOperation::descriptor, x -> x));
    }

    @Test
    public void test_valueOf() {
        for (var op : StandardBundlingOperation.values()) {
            assertSame(op, StandardBundlingOperation.valueOf(op.descriptor()).orElseThrow());
        }

        assertFalse(StandardBundlingOperation.valueOf(BundlingOperationDescriptor.valueOf("WINDOWS:create:foo")).isPresent());
    }

    @Test
    public void test_ofPlatform() {
        var combined = Stream.of(OperatingSystem.WINDOWS, OperatingSystem.MACOS, OperatingSystem.LINUX)
                .map(StandardBundlingOperation::ofPlatform)
                .flatMap(x -> x).sorted().toList();

        var whole = Stream.of(StandardBundlingOperation.values()).sorted().toList();

        assertEquals(whole, combined);
    }

    @Test
    public void test_narrow() {
        assertEquals(
                List.of(StandardBundlingOperation.CREATE_LINUX_APP_IMAGE),
                StandardBundlingOperation.narrow(Stream.of(StandardBundlingOperation.CREATE_LINUX_APP_IMAGE, new OptionScope() {})).toList());

        assertEquals(List.of(), StandardBundlingOperation.narrow(Stream.of(new OptionScope() {})).toList());
        assertEquals(List.of(), StandardBundlingOperation.narrow(Stream.of()).toList());
    }

    @ParameterizedTest
    @MethodSource
    public void testFromOptionName(Map.Entry<String, Set<BundlingOperationOptionScope>> testSpec) {
        final var actualScope = StandardBundlingOperation.fromOptionName(testSpec.getKey());
        assertEquals(testSpec.getValue(), actualScope);
    }

    private static List<Map.Entry<String, Set<BundlingOperationOptionScope>>> testFromOptionName() {
        return List.of(
                Map.entry("foo", StandardBundlingOperation.CREATE_BUNDLE),
                Map.entry("win-foo", StandardBundlingOperation.WINDOWS),
                Map.entry("linux-foo", StandardBundlingOperation.LINUX),
                Map.entry("mac-foo", MAC_CREATE_BUNDLE),
                Map.entry("win-exe-foo", Set.of(StandardBundlingOperation.CREATE_WIN_EXE)),
                Map.entry("win-msi-foo", Set.of(StandardBundlingOperation.CREATE_WIN_MSI)),
                Map.entry("linux-rpm-foo", Set.of(StandardBundlingOperation.CREATE_LINUX_RPM)),
                Map.entry("linux-deb-foo", Set.of(StandardBundlingOperation.CREATE_LINUX_DEB)),
                Map.entry("mac-dmg-foo", Set.of(StandardBundlingOperation.CREATE_MAC_DMG)),
                Map.entry("mac-pkg-foo", Set.of(StandardBundlingOperation.CREATE_MAC_PKG))
        );
    }

    private static final Set<BundlingOperationOptionScope> MAC_CREATE_BUNDLE = Set.of(
            StandardBundlingOperation.CREATE_MAC_APP_IMAGE,
            StandardBundlingOperation.CREATE_MAC_DMG,
            StandardBundlingOperation.CREATE_MAC_PKG);
}
