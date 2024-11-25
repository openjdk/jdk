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
import java.io.File;
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
 * @bug 8059269
 * @summary tests that using a simple (non composite) pattern does not lead
 *        to NPE when the lock file already exists.
 * @run main/othervm FileHandlerPath
 * @author danielfuchs
 * @key randomness
 */
public class FileHandlerPath {

    // We will test the simple pattern
    public static void run(Properties propertyFile) throws Exception {
        setUp(propertyFile);
        test(propertyFile.getProperty("test.name"), propertyFile);
    }


    // Use a random name provided by UUID to avoid collision with other tests
    static final String logFile = FileHandlerPath.class.getSimpleName() + "_"
                + UUID.randomUUID().toString() + ".log";
    static final String tmpLogFile;
    static final String userDir = System.getProperty("user.dir");
    static final String tmpDir = System.getProperty("java.io.tmpdir");
    private static final List<Properties> properties;
    static {
        tmpLogFile = new File(tmpDir, logFile).toString();
        Properties props1 = new Properties();
        Properties props2 = new Properties();
        props1.setProperty("test.name", "relative file");
        props1.setProperty("test.file.name", logFile);
        props1.setProperty(FileHandler.class.getName() + ".pattern", logFile);
        props1.setProperty(FileHandler.class.getName() + ".count", "1");
        props2.setProperty("test.name", "absoluste file");
        props2.setProperty("test.file.name", tmpLogFile);
        props2.setProperty(FileHandler.class.getName() + ".pattern", "%t/" + logFile);
        props2.setProperty(FileHandler.class.getName() + ".count", "1");
        properties = Collections.unmodifiableList(Arrays.asList(
                    props1,
                    props2));
    }

    public static void main(String... args) throws Exception {

        // Sanity checks

        if (!Files.isWritable(Paths.get(userDir))) {
            throw new RuntimeException(userDir +
                    ": user.dir is not writable - can't run test.");
        }
        if (!Files.isWritable(Paths.get(tmpDir))) {
            throw new RuntimeException(tmpDir +
                    ": java.io.tmpdir is not writable - can't run test.");
        }

        File[] files = {
            new File(logFile),
            new File(tmpLogFile),
            new File(logFile+".1"),
            new File(tmpLogFile+".1"),
            new File(logFile+".lck"),
            new File(tmpLogFile+".lck"),
            new File(logFile+".1.lck"),
            new File(tmpLogFile+".1.lck")
        };

        for (File log : files) {
            if (log.exists()) {
                throw new Exception(log +": file already exists - can't run test.");
            }
        }

        // Now start the real test

        try {
            for (Properties propertyFile : properties) {
                run(propertyFile);
            }
        } finally {
            // Cleanup...
            for(File log : files) {
                try {
                    final boolean isLockFile = log.getName().endsWith(".lck");
                    // lock file should already be deleted, except if the
                    // test failed in exception.
                    // log file should all be present, except if the test
                    // failed in exception.
                    if (log.exists()) {
                        if (!isLockFile) {
                            System.out.println("deleting "+log.toString());
                        } else {
                            System.err.println("deleting lock file "+log.toString());
                        }
                        log.delete();
                    } else {
                        if (!isLockFile) {
                            System.err.println(log.toString() + ": not found.");
                        }
                    }
                } catch (Throwable t) {
                    // should not happen
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

    public static void test(String name, Properties props) throws Exception {
        System.out.println("Testing: " + name);
        String file = props.getProperty("test.file.name");
        // create the lock files first - in order to take the path that
        // used to trigger the NPE
        Files.createFile(Paths.get(file + ".lck"));
        Files.createFile(Paths.get(file + ".1.lck"));
        final FileHandler f1 = new FileHandler();
        final FileHandler f2 = new FileHandler();
        f1.close();
        f2.close();
        System.out.println("Success for: " + name);
    }
}
