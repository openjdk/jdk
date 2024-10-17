/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @test
 * @bug 8030850
 * @summary Tests that setting .level=FINEST for the root logger in logging
 *      configuration file does work.
 * @modules java.base/jdk.internal.access
 *          java.logging
 * @run main/othervm RootLevelInConfigFile
 *
 * @author danielfuchs
 */
public class RootLevelInConfigFile {

    public static final String CONFIG_FILE_KEY = "java.util.logging.config.file";

    public static void main(String[] args) throws IOException {
        System.setProperty(CONFIG_FILE_KEY,
                new File(System.getProperty("test.src", "."),
                        "rootlogger.properties").getAbsolutePath());
        System.out.println(CONFIG_FILE_KEY + "="
                + System.getProperty(CONFIG_FILE_KEY));
        if (! new File(System.getProperty(CONFIG_FILE_KEY)).canRead()) {
            throw new RuntimeException("can't read config file: "
                    + System.getProperty(CONFIG_FILE_KEY));
        }

        final String configFile = System.getProperty(CONFIG_FILE_KEY);

        test("no security");
    }

    public static void test(String conf) throws IOException {

        System.out.println("Testing with " + conf);

        testLoggableLevels();

        LogManager.getLogManager().readConfiguration();

        testLoggableLevels();

    }

    private static void testLoggableLevels() {

        Logger foobar = Logger.getLogger("foo.bar");
        if (!foobar.isLoggable(Level.FINEST)) {
            throw new RuntimeException("Expected FINEST to be loggable in "
                    + foobar.getName());
        }
        if (!foobar.getParent().isLoggable(Level.FINEST)) {
            throw new RuntimeException("Expected FINEST to be loggable in "
                    + foobar.getName());
        }

        Logger global = Logger.getGlobal();
        if (!global.isLoggable(Level.FINEST)) {
            throw new RuntimeException("Expected FINEST to be loggable in "
                    + global.getName());
        }
        if (!global.getParent().isLoggable(Level.FINEST)) {
            throw new RuntimeException("Expected FINEST to be loggable in "
                    + global.getName());
        }

        Logger root = Logger.getLogger("");
        if (!global.isLoggable(Level.FINEST)) {
            throw new RuntimeException("Expected FINEST to be loggable in "
                    + root.getName());
        }
        if (!global.getParent().isLoggable(Level.FINEST)) {
            throw new RuntimeException("Expected FINEST to be loggable in "
                    + root.getName());
        }

        root.setLevel(Level.FINER);

        if (foobar.isLoggable(Level.FINEST)) {
            throw new RuntimeException("Didn't expect FINEST to be loggable in "
                    + foobar.getName());
        }
        if (foobar.getParent().isLoggable(Level.FINEST)) {
            throw new RuntimeException("Didn't expect FINEST to be loggable in "
                    + foobar.getName());
        }
        if (global.isLoggable(Level.FINEST)) {
            throw new RuntimeException("Didn't expect FINEST to be loggable in "
                    + global.getName());
        }
        if (global.getParent().isLoggable(Level.FINEST)) {
            throw new RuntimeException("Didn't expect FINEST to be loggable in "
                    + global.getName());
        }

        if (!foobar.isLoggable(Level.FINER)) {
            throw new RuntimeException("Expected FINER to be loggable in "
                    + foobar.getName());
        }
        if (!foobar.getParent().isLoggable(Level.FINER)) {
            throw new RuntimeException("Expected FINER to be loggable in "
                    + foobar.getName());
        }

        if (!global.isLoggable(Level.FINER)) {
            throw new RuntimeException("Expected FINER to be loggable in "
                    + global.getName());
        }
        if (!global.getParent().isLoggable(Level.FINER)) {
            throw new RuntimeException("Expected FINER to be loggable in "
                    + global.getName());
        }

    }
}
