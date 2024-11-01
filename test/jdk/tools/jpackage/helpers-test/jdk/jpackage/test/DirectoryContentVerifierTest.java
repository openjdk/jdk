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
package jdk.jpackage.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import static jdk.jpackage.test.DirectoryContentVerifierTest.AssertType.CONTAINS;
import static jdk.jpackage.test.DirectoryContentVerifierTest.AssertType.MATCH;
import jdk.jpackage.test.TKit.DirectoryContentVerifier;
import static jdk.jpackage.test.TKit.assertAssert;

/*
 * @test
 * @summary Test TKit.DirectoryContentVerifier from jpackage test library
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile DirectoryContentVerifierTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=jdk.jpackage.test.DirectoryContentVerifierTest
 */
public class DirectoryContentVerifierTest {

    enum AssertType {
        MATCH(DirectoryContentVerifier::match),
        CONTAINS(DirectoryContentVerifier::contains),
        ;

        AssertType(BiConsumer<DirectoryContentVerifier, Set<Path>> assertFunc) {
            this.assertFunc = assertFunc;
        }

        private final BiConsumer<DirectoryContentVerifier, Set<Path>> assertFunc;
    }

    private static ArgsBuilder buildArgs() {
        return new ArgsBuilder();
    }

    private static class ArgsBuilder {

        void applyTo(List<Object[]> data) {
            data.add(new Object[]{expectedPaths, actualPaths, assertOp, success});
        }

        void applyVariantsTo(List<Object[]> data) {
            applyTo(data);
            boolean pathGroupsEqual = List.of(expectedPaths).equals(List.of(actualPaths));
            if (assertOp == MATCH) {
                if (!pathGroupsEqual) {
                    data.add(new Object[]{actualPaths, expectedPaths, MATCH, success});
                }
                if (success) {
                    data.add(new Object[]{expectedPaths, actualPaths, CONTAINS, success});
                    if (!pathGroupsEqual) {
                        data.add(new Object[]{actualPaths, expectedPaths, CONTAINS, success});
                    }
                }
            }
        }

        ArgsBuilder expectedPaths(String... paths) {
            expectedPaths = paths;
            return this;
        }

        ArgsBuilder actualPaths(String... paths) {
            actualPaths = paths;
            return this;
        }

        ArgsBuilder assertOp(AssertType v) {
            assertOp = v;
            return this;
        }

        ArgsBuilder expectFail() {
            success = false;
            return this;
        }

        private String[] expectedPaths = new String[0];
        private String[] actualPaths = new String[0];
        private AssertType assertOp = MATCH;
        private boolean success = true;
    }

    @Parameters
    public static Collection input() {
        List<Object[]> data = new ArrayList<>();
        buildArgs().applyVariantsTo(data);
        buildArgs().actualPaths("foo").assertOp(CONTAINS).applyTo(data);
        buildArgs().actualPaths("zoo").expectFail().applyVariantsTo(data);
        buildArgs().actualPaths("boo").expectedPaths("boo").applyVariantsTo(data);
        if (TKit.isWindows()) {
            buildArgs().actualPaths("moo").expectedPaths("Moo").applyVariantsTo(data);
        } else {
            buildArgs().actualPaths("moo").expectedPaths("Moo").expectFail().applyVariantsTo(data);
        }
        buildArgs().actualPaths("hello").expectedPaths().expectFail().applyVariantsTo(data);
        buildArgs().actualPaths("123").expectedPaths("456").expectFail().applyVariantsTo(data);
        buildArgs().actualPaths("a", "b", "c").expectedPaths("b", "a", "c").applyVariantsTo(data);
        buildArgs().actualPaths("AA", "BB", "CC").expectedPaths("BB", "AA").expectFail().applyVariantsTo(data);
        buildArgs().actualPaths("AA", "BB", "CC").expectedPaths("BB", "AA").assertOp(CONTAINS).applyTo(data);
        buildArgs().actualPaths("AA", "BB", "CC").expectedPaths("BB", "DD", "AA").expectFail().assertOp(CONTAINS).applyTo(data);
        buildArgs().actualPaths("AA", "BB", "CC").expectedPaths("BB", "DD", "AA").expectFail().applyTo(data);
        return data;
    }

    public DirectoryContentVerifierTest(String[] expectedPaths, String[] actualPaths,
            AssertType assertOp, Boolean success) {
        this.expectedPaths = conv(expectedPaths);
        this.actualPaths = conv(actualPaths);
        this.assertOp = assertOp;
        this.success = success;
    }

    @Test
    public void test() {
        TKit.withTempDirectory("basedir", this::test);
    }

    private void test(Path basedir) throws IOException {
        for (var path : actualPaths) {
            Files.createFile(basedir.resolve(path));
        }

        var testee = TKit.assertDirectoryContent(basedir);

        assertAssert(success, () -> assertOp.assertFunc.accept(testee, expectedPaths));
    }

    private static Set<Path> conv(String... paths) {
        return Stream.of(paths).map(Path::of).collect(toSet());
    }

    private final Set<Path> expectedPaths;
    private final Set<Path> actualPaths;
    private final AssertType assertOp;
    private final boolean success;
}
