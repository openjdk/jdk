/*
 * Copyright (c) 2024, Red Hat, Inc.
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import tests.Helper;

public abstract class ModifiedFilesTest extends AbstractLinkableRuntimeTest {

    abstract String initialImageName();
    abstract void testAndAssert(Path modifiedFile, Helper helper, Path initialImage) throws Exception;

    @Override
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .name(initialImageName())
                .addModule("java.base")
                .validatingModule("java.base")
                .helper(helper);
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        Path initialImage = createRuntimeLinkImage(builder.build());

        Path netPropertiesFile = modifyFileInImage(initialImage);

        testAndAssert(netPropertiesFile, helper, initialImage);
    }

    protected Path modifyFileInImage(Path jmodLessImg)
            throws IOException, AssertionError {
        // modify net.properties config file
        Path netPropertiesFile = jmodLessImg.resolve("conf").resolve("net.properties");
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(netPropertiesFile)) {
            props.load(is);
        }
        String prevVal = (String)props.put("java.net.useSystemProxies", Boolean.TRUE.toString());
        if (prevVal == null || Boolean.getBoolean(prevVal) != false) {
            throw new AssertionError("Expected previous value to be false!");
        }
        try (OutputStream out = Files.newOutputStream(netPropertiesFile)) {
            props.store(out, "Modified net.properties file!");
        }
        return netPropertiesFile;
    }
}
