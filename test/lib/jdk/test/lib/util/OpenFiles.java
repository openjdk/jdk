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

package jdk.test.lib.util;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * API for registering files as opened or closed (from code instrumented by agent)
 * and for asserting open status for a file (from tests).
 */
public class OpenFiles {

    private static Map<String, Map<Object, Throwable>> paths = new HashMap<>();
    private static Map<Object, String> files = new HashMap<>();

    /**
     * Register a file path as being opened by the given Object
     *
     * @param file the file to register as opened
     * @param object the object opening the file
     */
    public static void openFile(String path, Object object) {
        openFile(Path.of(path), object);
    }

    /**
     * Register a file as being opened by the given Object
     *
     * @param file the file to register as opened
     * @param object the object opening the file
     */
    public static void openFile(File file,  Object object) {
        openFile(file.getAbsolutePath(), object);
    }

    /**
     * Register a file as being opened by the given Object
     *
     * @param file the file to register as opened
     * @param object the object opening the file
     */
    public static synchronized void openFile(Path file, Object object) {
        String path = file.toAbsolutePath().toString();
        if (!paths.containsKey(path)) {
            paths.put(path, new HashMap<>());
        }
        paths.get(path).put(object, new Throwable("Opening stack trace of " + path));
        files.put(object, path);
    }

    /**
     * Close the file opened by the given object
     * @param object the object opening the file
     */
    public synchronized static void closeFile(Object object) {
        String path = files.get(object);
        if (path != null) {
            files.remove(object);
            Map<Object, Throwable> opens = paths.get(path);
            if (opens != null) {
                opens.remove(object);
                if (opens.isEmpty()) {
                    paths.remove(path);
                }
            }
        }
    }

    /**
     * Assert that the given {@code File} is closed
     *
     * @param file the {@code File} to check
     */
    public static void assertClosed(File file) {
        assertClosed(file.toPath());
    }

    /**
     * Assert that the given file path is closed
     *
     * @param file the file path to check
     */
    public static void assertClosed(String path) {
        assertClosed(Path.of(path));
    }

    /**
     * Assert that the given {@code Path} is closed
     *
     * @param file the {@code Path} to check
     */
    public static synchronized void assertClosed(Path file) {
        String path = file.toAbsolutePath().toString();
        Map<Object, Throwable> opens = paths.get(path);
        if (opens != null && !opens.isEmpty()) {
            for (var e : opens.entrySet()) {
                Object open = e.getKey();
                Throwable stacktrace = e.getValue();
                throw new AssertionError("Expected file to be closed: " + path, stacktrace);
            }
        }
    }

    /**
     * Assert that the given {@code File} is open
     *
     * @param file the {@code File} to check
     */
    public static void assertOpen(File file) {
        assertOpen(file.toPath());
    }

    /**
     * Assert that the given absolute file path is open
     *
     * @param file the path of the file to check
     */
    public  static void assertOpen(String path) {
        assertOpen(Path.of(path));
    }

    /**
     * Assert that the given {@code Path} is open
     *
     * @param file the {@code File} to check
     */
    public static synchronized void assertOpen(Path file) {
        String path = file.toAbsolutePath().toString();
        Map<Object, Throwable> opens = paths.get(path);
        if (opens == null || opens.isEmpty()) {
            throw new AssertionError("Expected file to be open: " + path);
        }
    }

    /**
     * If {@code expectOpen} is true, assert that the given {@code File}
     * is open, otherwise assert that it is closed.
     *
     * @param expectOpen true if the file should be open
     * @param file the {@code File} to check
     */
    public static void assertOpenIf(boolean expectOpen, File file) {
        assertOpenIf(expectOpen, file.toPath());
    }

    /**
     * If {@code expectOpen} is true, assert that the given {@code Path}
     * is open, otherwise assert that it is closed.
     *
     * @param expectOpen true if the file should be open
     * @param file the {@code Path} to check
     */
    public static void assertOpenIf(boolean expectOpen, Path file) {
        assertOpenIf(expectOpen, file.toAbsolutePath().toString());
    }

    /**
     * If {@code expectOpen} is true, assert that the file with the given
     * absolute path is open, otherwise assert that it is closed.
     *
     * @param expectOpen true if the file should be open
     * @param file the path of the file to check
     */
    public static void assertOpenIf(boolean expectOpen, String path) {
        if (expectOpen) {
            assertOpen(path);
        } else {
            assertClosed(path);
        }
    }
}
