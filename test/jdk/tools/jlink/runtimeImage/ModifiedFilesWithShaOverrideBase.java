/*
 * Copyright (c) 2025, Red Hat, Inc.
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;
import tests.Helper;

/*
 * Base class for sha overrides tests
 */
public abstract class ModifiedFilesWithShaOverrideBase extends ModifiedFilesTest {

    protected static final String SHA_OVERRIDE_FLAG = "--sha-overrides";

    @Override
    protected Path modifyFileInImage(Path jmodLessImg) {
        try {
            Path libJVM = jmodLessImg.resolve("lib").resolve("server").resolve(System.mapLibraryName("jvm"));
            String shaBefore = buildSHA512(libJVM);
            List<String> objcopy = new ArrayList<>();
            objcopy.add("objcopy");
            // The OpenJDK build doesn't strip all symbols by default. In order
            // to get a different libjvm.so file, we strip everything. The
            // expectation is for the sha to be different before and after the
            // stripping of the file.
            objcopy.add("--strip-all");
            objcopy.add(libJVM.toString());
            ProcessBuilder builder = new ProcessBuilder(objcopy);
            Process p = builder.start();
            int returnVal = p.waitFor();
            if (returnVal != 0) {
                throw new SkippedException("Stripping of libjvm failed. Is objcopy installed?");
            }
            String shaAfter = buildSHA512(libJVM);
            if (shaBefore.equals(shaAfter)) {
                throw new SkippedException("Binary file would be the same before after - test skipped");
            }
            return libJVM;
        } catch (IOException | InterruptedException e) {
            throw new SkippedException("Stripping of libjvm failed: " + e.getMessage());
        }
    }

    @Override
    void testAndAssert(Path modifiedFile, Helper helper, Path initialImage) throws Exception {
        String extraJlinkOption = getShaOverrideOption(modifiedFile, initialImage);
        CapturingHandler handler = new CapturingHandler();
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(initialImage)
                                .name(getTargetName())
                                .addModule("java.base")
                                .validatingModule("java.base")
                                .extraJlinkOpt(extraJlinkOption) // allow for the modified sha
                                .build(), handler);
        OutputAnalyzer out = handler.analyzer();
        // verify we don't get a warning message for the modified file
        out.stdoutShouldNotMatch(".* has been modified");
        out.stdoutShouldNotContain("java.lang.IllegalArgumentException");
        out.stdoutShouldNotContain("IOException");
    }

    public abstract String getTargetName();
    public abstract String getShaOverrideOption(Path modifiedFile, Path initialImage);

    protected String buildSHA512(Path modifiedFile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            try (InputStream is = Files.newInputStream(modifiedFile)) {
                byte[] buf = new byte[1024];
                int readBytes = -1;
                while ((readBytes = is.read(buf)) != -1) {
                    digest.update(buf, 0, readBytes);
                }
            }
            byte[] actual = digest.digest();
            return HexFormat.of().formatHex(actual);
        } catch (Exception e) {
            throw new AssertionError("SHA-512 sum generation failed");
        }
    }
}
