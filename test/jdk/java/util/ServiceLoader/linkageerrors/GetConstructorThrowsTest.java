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

/*
 * @test
 * @bug 8350481
 * @summary Test ServiceLoader locating a provider when Class.getConstructor throws a LinkageError
 * @compile Provider1.java Provider2.java
 * @run junit/othervm ${test.main.class}
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GetConstructorThrowsTest extends TestBase {

    @Test
    void testGetConstructorThrows() throws Exception {
        // create services configuration file that lists two providers
        Path dir = classesDir();
        Path configFile = dir.resolve("META-INF", "services", "Service");
        Files.createDirectories(configFile.getParent());
        Files.write(configFile, List.of("Provider1", "Provider2"));

        // delete Provider's parameter class, instantiating Provider2 will fail with LinkageError
        Files.delete(dir.resolve("Param.class"));

        // find and collect all service providers and ServiceConfigurationError causes
        var providers = new ArrayList<Service>();
        var linkageErrors = new ArrayList<LinkageError>();
        var otherCauses = new ArrayList<Throwable>();
        forEachProvider(Service.class, providers::add, sce -> {
            Throwable cause = sce.getCause();
            if (cause instanceof LinkageError e) {
                linkageErrors.add(e);
            } else {
                otherCauses.add(cause);
            }
        });

        assertEquals(1, providers.size(), "One provider expected");
        assertEquals(1, linkageErrors.size(), "One LinkagError expected");
        assertEquals(0, otherCauses.size(), "No other errors expected");
    }
}
