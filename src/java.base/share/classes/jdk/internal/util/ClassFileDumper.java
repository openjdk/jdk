/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.util;

import jdk.internal.misc.VM;
import sun.security.action.GetBooleanAction;
import sun.security.action.GetPropertyAction;
import sun.util.logging.PlatformLogger;

import java.io.FilePermission;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.PropertyPermission;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class to log normal and hidden classes defined via Lookup::defineClass
 * and Lookup::defineHiddenClass API
 *
 * @implNote
 * <p> Because this class is called by MethodHandleStatics, LambdaForms generation
 * and LambdaMetafactory, make use of lambda lead to recursive calls cause stack overflow.
 */
public final class ClassFileDumper {
    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
    private static final char[] BAD_CHARS = {
        '\\', ':', '*', '?', '"', '<', '>', '|'
    };
    private static final String[] REPLACEMENT = {
        "%5C", "%3A", "%2A", "%3F", "%22", "%3C", "%3E", "%7C"
    };

    private static final ConcurrentHashMap<String, ClassFileDumper> DUMPER_MAP
            = new ConcurrentHashMap<>();

    /**
     * Returns a ClassFileDumper instance for the given key.  To enable
     * dumping of the generated classes, set the system property via
     * -D<key>=<path>.
     *
     * The system property is read only once when it is the first time
     * the dumper instance for the given key is created.
     *
     * If not enabled, this method returns ClassFileDumper with null
     * dump path.
     */
    public static ClassFileDumper getInstance(String key) {
        Objects.requireNonNull(key);

        var dumper = DUMPER_MAP.get(key);
        if (dumper == null) {
            String path = GetPropertyAction.privilegedGetProperty(key);
            Path dir;
            if (path == null || path.trim().isEmpty()) {
                dir = null;
            } else {
                dir = validateDumpDir(Path.of(path.trim()));
            }
            var newDumper = new ClassFileDumper(key, dir);
            var v = DUMPER_MAP.putIfAbsent(key, newDumper);
            dumper = v != null ? v : newDumper;
        }
        return dumper;
    }

    /**
     * Returns a ClassFileDumper instance for the given key with a given
     * dump path. To enable dumping of the generated classes, -D<key>=true.
     *
     * The system property is read only once when it is the first time
     * the dumper instance for the given key is created.
     *
     * If not enabled, this method returns ClassFileDumper with null
     * dump path.
     */
    public static ClassFileDumper getInstance(String key, Path path) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(path);

        var dumper = DUMPER_MAP.get(key);
        if (dumper == null) {
            boolean enabled = GetBooleanAction.privilegedGetProperty(key);
            Path dir = enabled ? validateDumpDir(path) : null;
            var newDumper = new ClassFileDumper(key, dir);
            var v = DUMPER_MAP.putIfAbsent(key, newDumper);
            dumper = v != null ? v : newDumper;
        }

        if (dumper.isEnabled() && !path.equals(dumper.dumpPath())) {
            throw new IllegalArgumentException("mismatched dump path for " + key);
        }
        return dumper;
    }

    private final String key;
    private final Path dumpDir;
    private final AtomicInteger counter = new AtomicInteger();

    private ClassFileDumper(String key, Path path) {
        this.key = key;
        this.dumpDir = path;
    }

    public String key() {
        return key;
    }
    public boolean isEnabled() {
        return dumpDir != null;
    }

    public Path dumpPath() {
        return dumpDir;
    }

    public int incrementAndGetCounter() {
        return counter.incrementAndGet();
    }

    public Path pathname(String internalName) {
        return dumpDir.resolve(encodeForFilename(internalName) + ".class");
    }

    @SuppressWarnings("removal")
    public void dumpClass(String internalName, final byte[] classBytes) {
        if (!isEnabled()) return;

        AccessController.doPrivileged(new PrivilegedAction<>() {
                @Override public Void run() {
                    Path file = pathname(internalName);
                    try {
                        Path dir = file.getParent();
                        Files.createDirectories(dir);
                        Files.write(file, classBytes);
                    } catch (Exception ex) {
                        if (VM.isModuleSystemInited()) {
                            // log only when lambda is ready to use
                            System.getLogger(ClassFileDumper.class.getName())
                                  .log(System.Logger.Level.WARNING, "Exception writing to " +
                                          file.toString() + " " + ex.getMessage());
                        }
                        // simply don't care if this operation failed
                    }
                    return null;
                }},
                null,
                new FilePermission("<<ALL FILES>>", "read, write"),
                // createDirectories may need it
                new PropertyPermission("user.dir", "read"));
    }

    @SuppressWarnings("removal")
    private static Path validateDumpDir(Path path) {
            return AccessController.doPrivileged(new PrivilegedAction<>() {
                @Override
                public Path run() {
                    if (!Files.exists(path)) {
                        try {
                            Files.createDirectory(path);
                        } catch (IOException ex) {
                            throw new UncheckedIOException("Fail to create " + path, ex);
                        }
                    }
                    if (!Files.isDirectory(path)) {
                        throw new IllegalArgumentException("Path " + path + " is not a directory");
                    } else if (!Files.isWritable(path)) {
                        throw new IllegalArgumentException("Directory " + path + " is not writable");
                    }
                    return path;
                }
            });
    }


    private static String encodeForFilename(String className) {
        final int len = className.length();
        StringBuilder sb = new StringBuilder(len);

        for (int i = 0; i < len; i++) {
            char c = className.charAt(i);
            // control characters
            if (c <= 31) {
                sb.append('%');
                sb.append(HEX[c >> 4 & 0x0F]);
                sb.append(HEX[c & 0x0F]);
            } else {
                int j = 0;
                for (; j < BAD_CHARS.length; j++) {
                    if (c == BAD_CHARS[j]) {
                        sb.append(REPLACEMENT[j]);
                        break;
                    }
                }
                if (j >= BAD_CHARS.length) {
                    sb.append(c);
                }
            }
        }

        return sb.toString();
    }
}
