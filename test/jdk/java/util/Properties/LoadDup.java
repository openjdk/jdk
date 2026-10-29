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
 * @summary Test that the latest entry is stored when duplicate entries are
 *          loaded from a properties file
 * @bug 8293451
 * @run junit LoadDup
 */

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class LoadDup {
    private static Properties createBaseProps() {
        Properties props = new Properties();
        props.setProperty("1", "one");
        props.setProperty("2", "two");
        props.setProperty("3", "three");
        return props;
    }

    /**
     * Test that when loading a property file containing duplicate entries, the
     * latest value gets stored.
     */
    @Test
    public void testDupInFile() throws IOException {
        Properties baseProps = createBaseProps();
        checkPropValue("DupInFile Start", baseProps, "2", "two");

        // Write base props file, and append a duplicate entry
        Path propsFile = Paths.get("DupPropFile.properties");
        Files.deleteIfExists(propsFile);
        try (Writer writer = Files.newBufferedWriter(propsFile)) {
            baseProps.store(writer, "Properties With Duplicate");
            writer.write("2=zwei\n"); // append duplicate
        }

        Properties testProps = new Properties();
        // Read props file into empty testProps
        try (Reader reader = Files.newBufferedReader(propsFile)) {
            testProps.load(reader);
        }

        checkPropValue("DupInFile After load", testProps, "2", "zwei");
    }

    /**
     * Test that loading properties from a file will overwrite values already
     * present in the Properties.
     */
    @Test
    public void testPreExistingDup() throws IOException {
        Properties testProps = new Properties();
        testProps.setProperty("2", "zwei"); // should be overwritten by load
        checkPropValue("ExistingDup Start", testProps, "2", "zwei");

        // Write base props file
        Path propsFile = Paths.get("BasePropFile.properties");
        Files.deleteIfExists(propsFile);
        try (Writer writer = Files.newBufferedWriter(propsFile)) {
            createBaseProps().store(writer, "Base Properties");
        }

        // Read props file into testProps containing duplicate
        try (final Reader reader = Files.newBufferedReader(propsFile)) {
            testProps.load(reader);
        }

        checkPropValue("ExistingDup After load", testProps, "2", "two");
    }

    private static void checkPropValue(String msg, Properties props, String key, String expectedVal) {
        String value = props.getProperty(key);
        System.out.println(msg + " value for " + key + ":" + value);
        Assertions.assertEquals(expectedVal, value);
    }
}
