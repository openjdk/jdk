/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.DottedVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PlatformVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "0.0",
        "255.255",
        "0.0.0",
        "255.255.65535",
        "0.0.0.0",
        "255.255.65535.999999"
    })
    public void testValidMsiProductVersion(String version) {
        testImpl(PlatformVersion.WIN_MSI_PRODUCT_VERSION_CLASS, version, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0",
        "256.01",
        "255.256",
        "255.255.65536",
        "1.2.3.4.5"
    })
    public void testInvalidMsiProductVersion(String version) {
        testImpl(PlatformVersion.WIN_MSI_PRODUCT_VERSION_CLASS, version, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.2", "1.2.3"})
    public void testValidCfBundleVersion(String version) {
        testImpl(PlatformVersion.MAC_CFBUNDLE_VERSION_CLASS, version, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.1", "1.2.3.4"})
    public void testInvalidCfBundleVersion(String version) {
        testImpl(PlatformVersion.MAC_CFBUNDLE_VERSION_CLASS, version, false);
    }

    private static void testImpl(PlatformVersion parser, String version, boolean valid) {
        assumeTrue(parser.parser != null);
        if (valid) {
            assertEquals(parser.parse(version).toString(), version);
        } else {
            assertThrowsExactly(IllegalArgumentException.class, () -> parser.parse(version));
        }
    }

    enum PlatformVersion {
        MAC_CFBUNDLE_VERSION_CLASS("jdk.jpackage.internal.CFBundleVersion", OperatingSystem.MACOS),
        WIN_MSI_PRODUCT_VERSION_CLASS("jdk.jpackage.internal.model.MsiVersion", OperatingSystem.WINDOWS);

        PlatformVersion(String className, OperatingSystem os) {
            if (os.equals(OperatingSystem.current())) {
                parser = getParser(className);
            } else {
                parser = null;
            }
        }

        DottedVersion parse(String versionString) {
            return parser.apply(versionString);
        }

        private Function<String, DottedVersion> parser;
    }

    private static Function<String, DottedVersion> getParser(String className) {
        try {
            Method method = Class.forName(className).getDeclaredMethod("of",
                    String.class);
            return (str) -> {
                try {
                    return (DottedVersion) method.invoke(null, str);
                } catch (IllegalAccessException | IllegalArgumentException ex) {
                    throw new RuntimeException(ex);
                } catch (InvocationTargetException ex) {
                    Throwable causeEx = ex.getCause();
                    if (causeEx instanceof RuntimeException rtEx) {
                        throw rtEx;
                    }
                    throw new RuntimeException(causeEx);
                }
            };
        } catch (SecurityException | NoSuchMethodException | ClassNotFoundException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

}
