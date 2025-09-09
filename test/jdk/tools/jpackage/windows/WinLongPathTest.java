/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
/* @test
 * @bug 8289771
 * @summary jpackage with long paths on windows
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @compile -Xlint:all -Werror WinLongPathTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-space-subst=*
 *  --jpt-exclude=WinLongPathTest(false,*--temp)
 *  --jpt-run=WinLongPathTest
 */

public record WinLongPathTest(Boolean appImage, String optionName) {

    @Parameters
    public static List<Object[]> input() {
        List<Object[]> data = new ArrayList<>();
        for (var appImage : List.of(Boolean.TRUE, Boolean.FALSE)) {
            for (var option : List.of("--dest", "--temp")) {
                data.add(new Object[]{appImage, option});
            }
        }
        return data;
    }

    @Test
    public void test() throws IOException {
        verifyDosNamesSupported();

        if (appImage) {
            var cmd = JPackageCommand.helloAppImage();
            setOptionLongPath(cmd, optionName);
            cmd.executeAndAssertHelloAppImageCreated();
        } else {
            new PackageTest()
                    .forTypes(PackageType.WINDOWS)
                    .configureHelloApp()
                    .addInitializer(cmd -> setOptionLongPath(cmd, optionName))
                    .run(Action.CREATE_AND_UNPACK);
        }
    }

    private static void setOptionLongPath(JPackageCommand cmd, String option) throws IOException {
        var root = TKit.createTempDirectory("long-path");
        // 261 characters in total, which alone is above the 260 threshold
        var longPath = root.resolve(Path.of("a".repeat(80), "b".repeat(90), "c".repeat(91)));
        Files.createDirectories(longPath);
        cmd.setArgumentValue(option, longPath);
    }

    private static void verifyDosNamesSupported() throws IOException {
        // Pick the file's name long enough to make Windows shorten it.
        final var probeDosNameFile = TKit.createTempFile(Path.of("probeDosName"));

        // The output should be a DOS variant of the `probeDosNameFile` path.
        // The filename should differ if the volume owning `probeDosNameFile` file supports DOS names.
        final var dosPath = new Executor()
                .addArguments("/c", String.format("for %%P in (\"%s\") do @echo %%~sP", probeDosNameFile))
                .setExecutable("cmd")
                .dumpOutput()
                .executeAndGetFirstLineOfOutput();

        if (Path.of(dosPath).getFileName().equals(probeDosNameFile.getFileName())) {
            TKit.throwSkippedException(String.format("The volume %s owning the test work directory doesn't support DOS paths",
                    probeDosNameFile.toAbsolutePath().getRoot()));
        }
    }

}
