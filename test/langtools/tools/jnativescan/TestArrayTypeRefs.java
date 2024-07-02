/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib .. ./cases/modules
 * @build JNativeScanTestBase
 *     cases.classpath.arrayref.App
 * @run junit TestArrayTypeRefs
 */

import jdk.test.lib.util.JarUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class TestArrayTypeRefs extends JNativeScanTestBase {

    static Path ARRAY_REF;

    @BeforeAll
    public static void before() throws IOException {
        ARRAY_REF = Path.of("arrayref.jar");
        Path testClasses = Path.of(System.getProperty("test.classes", ""));
        JarUtils.createJarFile(ARRAY_REF, testClasses, Path.of("arrayref", "App.class"));
    }

    @Test
    public void testSingleJarClassPath() {
        assertSuccess(jnativescan("--class-path", ARRAY_REF.toString()))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("<no restricted methods>");
    }
}
