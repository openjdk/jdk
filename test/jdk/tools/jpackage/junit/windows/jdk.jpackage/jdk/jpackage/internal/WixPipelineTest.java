/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import jdk.internal.util.Architecture;
import jdk.jpackage.internal.model.ConfigException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests proving the mapping from {@link Architecture} to the value of
 * the WiX Toolset "-arch" command line argument used to build MSI packages.
 */
class WixPipelineTest {

    @ParameterizedTest
    @EnumSource(value = Architecture.class, names = {"X86", "X64", "AARCH64"})
    void test_wix4ArchArg_supported(Architecture arch) {
        var expected = switch (arch) {
            case X86 -> "x86";
            case X64 -> "x64";
            case AARCH64 -> "arm64";
            default -> throw new IllegalArgumentException();
        };
        assertEquals(expected, WixPipeline.wix4ArchArg(arch));
    }

    @ParameterizedTest
    @EnumSource(value = Architecture.class, names = {"X86", "X64", "AARCH64"}, mode = EnumSource.Mode.EXCLUDE)
    void test_wix4ArchArg_unsupported(Architecture arch) {
        // Unsupported architectures must be reported with a self-contained,
        // user-facing ConfigException, not a raw stack trace (see the
        // jdk.jpackage.internal.cli.Main error reporter, which only omits
        // the stack trace for exceptions annotated with
        // jdk.jpackage.internal.model.SelfContainedException).
        assertThrowsExactly(ConfigException.class, () -> {
            WixPipeline.wix4ArchArg(arch);
        });
    }

    @ParameterizedTest
    @EnumSource(value = Architecture.class, names = {"X86", "X64"})
    void test_wix3ArchArg_supported(Architecture arch) {
        var expected = switch (arch) {
            case X86 -> "x86";
            case X64 -> "x64";
            default -> throw new IllegalArgumentException();
        };
        assertEquals(expected, WixPipeline.wix3ArchArg(arch));
    }

    @ParameterizedTest
    @EnumSource(value = Architecture.class, names = {"X86", "X64"}, mode = EnumSource.Mode.EXCLUDE)
    void test_wix3ArchArg_unsupported(Architecture arch) {
        // WiX v3 doesn't support arm64 (nor any other architecture); it must
        // be rejected explicitly and clearly, including AArch64, with a
        // self-contained, user-facing ConfigException (see above).
        assertThrowsExactly(ConfigException.class, () -> {
            WixPipeline.wix3ArchArg(arch);
        });
    }
}
