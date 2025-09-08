/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;

/**
 * @test
 * @bug 8025690
 * @summary tests that an empty or null pattern always result in an exception.
 * @run main/othervm FileHandlerPatternExceptions
 * @author danielfuchs
 * @key randomness
 */
public class FileHandlerPatternExceptions {

    // We will test null/empty pattern
    public static void run(Properties propertyFile) throws Exception {
        setUp(propertyFile);
        test(propertyFile.getProperty("test.name"));
    }


    private static final String PREFIX =
            "FileHandler-" + UUID.randomUUID() + ".log";
    private static final String userDir = System.getProperty("user.dir", ".");
    private static final boolean userDirWritable = Files.isWritable(Paths.get(userDir));

    private static final List<Properties> properties;
    static {
        Properties props1 = new Properties();
        Properties props2 = new Properties();
        props1.setProperty("test.name", "with count=1");
        props1.setProperty(FileHandler.class.getName() + ".pattern", "");
        props1.setProperty(FileHandler.class.getName() + ".count", "1");
        props2.setProperty("test.name", "with count=2");
        props2.setProperty(FileHandler.class.getName() + ".pattern", "");
        props2.setProperty(FileHandler.class.getName() + ".count", "2");
        properties = Collections.unmodifiableList(Arrays.asList(
                    props1,
                    props2));
    }

    public static void main(String... args) throws Exception {

        try {
            for (Properties propertyFile : properties) {
                run(propertyFile);
            }
        } finally {
            if (userDirWritable) {
                // cleanup - delete files that have been created
                try {
                    Files.list(Paths.get(userDir))
                        .filter((f) -> f.toString().contains(PREFIX))
                        .forEach((f) -> {
                            try {
                                System.out.println("deleting " + f);
                                Files.delete(f);
                            } catch(Throwable t) {
                                System.err.println("Failed to delete " + f + ": " + t);
                            }
                        });
                } catch(Throwable t) {
                    System.err.println("Cleanup failed to list files: " + t);
                    t.printStackTrace();
                }
            }
        }
    }

    static void setUp(Properties propertyFile) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            propertyFile.store(bytes, propertyFile.getProperty("test.name"));
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes.toByteArray());
            LogManager.getLogManager().readConfiguration(bais);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @FunctionalInterface
    public static interface FileHandlerSupplier {
        public FileHandler test() throws Exception;
    }

    private static void checkException(Class<? extends Exception> type, FileHandlerSupplier test) {
        Throwable t = null;
        FileHandler f = null;
        try {
            f = test.test();
        } catch (Throwable x) {
            t = x;
        }
        try {
            if (type != null && t == null) {
                throw new RuntimeException("Expected " + type.getName() + " not thrown");
            } else if (type != null && t != null) {
                if (type.isInstance(t)) {
                    System.out.println("Recieved expected exception: " + t);
                } else {
                    throw new RuntimeException("Exception type mismatch: "
                        + type.getName() + " expected, "
                        + t.getClass().getName() + " received.", t);
                }
            } else if (t != null) {
                throw new RuntimeException("Unexpected exception received: " + t, t);
            }
        } finally {
            if (f != null) {
                // f should always be null when an exception is expected,
                // but in case the test doesn't behave as expected we will
                // want to close f.
                try { f.close(); } catch (Throwable x) {};
            }
        }
    }

    public static void test(String name) throws Exception {
        System.out.println("Testing: " + name);
        checkException(RuntimeException.class, () -> new FileHandler());
        checkException(IllegalArgumentException.class, () -> new FileHandler(""));
        checkException(NullPointerException.class, () -> new FileHandler(null));

        checkException(IllegalArgumentException.class, () -> new FileHandler("", true));
        checkException(IllegalArgumentException.class, () -> new FileHandler("", false));
        checkException(NullPointerException.class, () -> new FileHandler(null, true));
        checkException(NullPointerException.class, () -> new FileHandler(null, false));

        checkException(IllegalArgumentException.class, () -> new FileHandler("", 1, 1));
        checkException(IllegalArgumentException.class, () -> new FileHandler(PREFIX, 0, 0));
        checkException(IllegalArgumentException.class, () -> new FileHandler(PREFIX, -1, 1));
        checkException(IllegalArgumentException.class, () -> new FileHandler("", 0, 0));
        checkException(IllegalArgumentException.class, () -> new FileHandler("", -1, 1));

        checkException(IllegalArgumentException.class, () -> new FileHandler("", 1, 1, true));
        checkException(IllegalArgumentException.class, () -> new FileHandler(PREFIX, 0, 0, true));
        checkException(IllegalArgumentException.class, () -> new FileHandler(PREFIX, -1, 1, true));
        checkException(IllegalArgumentException.class, () -> new FileHandler("", 0, 0, true));
        checkException(IllegalArgumentException.class, () -> new FileHandler("", -1, 1, true));

        checkException(IllegalArgumentException.class, () -> new FileHandler("", 1, 1, false));
        checkException(IllegalArgumentException.class, () -> new FileHandler(PREFIX, 0, 0, false));
        checkException(IllegalArgumentException.class, () -> new FileHandler(PREFIX, -1, 1, false));
        checkException(IllegalArgumentException.class, () -> new FileHandler("", 0, 0, false));
        checkException(IllegalArgumentException.class, () -> new FileHandler("", -1, 1, false));

        if (userDirWritable) {
            // These calls will create files in user.dir in the UNSECURE case.
            // The file name contain a random UUID (PREFIX) which identifies them
            // and allow us to remove them cleanly at the end (see finally block
            // in main()).
            checkException(null,
                           () -> new FileHandler(PREFIX, 0, 1, true));
            checkException(null,
                           () -> new FileHandler(PREFIX, 1, 2, true));
            checkException(null,
                           () -> new FileHandler(PREFIX, 0, 1, false));
            checkException(null,
                           () -> new FileHandler(PREFIX, 1, 2, false));
        }
        System.out.println("Success for: " + name);
    }
}
