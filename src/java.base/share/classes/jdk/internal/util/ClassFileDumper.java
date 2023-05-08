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
import sun.security.action.GetPropertyAction;

import java.io.FilePermission;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HexFormat;
import java.util.Objects;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ClassFile dumper utility class to log normal and hidden classes.
 *
 * @implNote
 * Because this class is called by MethodHandleStatics, LambdaForms generation
 * and LambdaMetafactory, make use of lambda lead to recursive calls cause stack overflow.
 */
public final class ClassFileDumper {
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
     * dump path. To enable dumping of the generated classes
     *     -D<key> or -D<key>=true
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
            String value = GetPropertyAction.privilegedGetProperty(key);
            boolean enabled = value != null && value.isEmpty()
                                    ? true : Boolean.parseBoolean(value);
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

    public Path pathname(String internalName) {
        return dumpDir.resolve(encodeForFilename(internalName) + ".class");
    }

    /**
     * This method determines the path name from the given name and {@code Class}
     * object.  If it is a hidden class, it will dump the given bytes at
     * a path of the given name with a suffix "." concatenated
     * with the suffix of the hidden class name.
     */
    public void dumpClass(String name, Class<?> c, byte[] bytes) {
        if (!isEnabled()) return;

        String cn = c.getName();
        int suffixIdx = cn.lastIndexOf('/');
        if (suffixIdx > 0) {
            name += '.' + cn.substring(suffixIdx + 1);
        }
        write(pathname(name), bytes);
    }

    /**
     * This method dumps the given bytes at a path of the given name with
     * a suffix ".failed-$COUNTER" where $COUNTER will be incremented
     * for each time this method is called.
     */
    public void dumpFailedClass(String name, byte[] bytes) {
        if (!isEnabled()) return;

        write(pathname(name + ".failed-" + counter.incrementAndGet()), bytes);
    }

    @SuppressWarnings("removal")
    private void write(Path path, byte[] bytes) {
        AccessController.doPrivileged(new PrivilegedAction<>() {
                @Override public Void run() {
                    try {
                        Path dir = path.getParent();
                        Files.createDirectories(dir);
                        Files.write(path, bytes);
                    } catch (Exception ex) {
                        if (VM.isModuleSystemInited()) {
                            // log only when lambda is ready to use
                            System.getLogger(ClassFileDumper.class.getName())
                                  .log(System.Logger.Level.WARNING, "Exception writing to " +
                                          path.toString() + " " + ex.getMessage());
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
                    try {
                        Files.createDirectories(path);
                    } catch (IOException ex) {
                        throw new UncheckedIOException("Fail to create " + path, ex);
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

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final Set<Character> BAD_CHARS = Set.of('\\', ':', '*', '?', '"', '<', '>', '|');

    private static String encodeForFilename(String className) {
        int len = className.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = className.charAt(i);
            // control characters
            if (c <= 31 || BAD_CHARS.contains(c)) {
                sb.append('%');
                HEX.toHexDigits(sb, (byte)c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
