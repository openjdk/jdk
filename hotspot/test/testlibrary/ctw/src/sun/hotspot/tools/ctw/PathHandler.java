/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.hotspot.tools.ctw;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.Executor;

/**
 * Abstract handler for path.
 * <p/>
 * Concrete subclasses should implement method {@link #process()}.
 *
 * @author igor.ignatyev@oracle.com
 */
public abstract class PathHandler {
    private static final Pattern JAR_IN_DIR_PATTERN
            = Pattern.compile("^(.*[/\\\\])?\\*$");
    protected final Path root;
    protected final Executor executor;
    private ClassLoader loader;

    /**
     * @param root     root path to process
     * @param executor executor used for process task invocation
     * @throws NullPointerException if {@code root} or {@code executor} is
     *                              {@code null}
     */
    protected PathHandler(Path root, Executor executor) {
        Objects.requireNonNull(root);
        Objects.requireNonNull(executor);
        this.root = root.normalize();
        this.executor = executor;
        this.loader = ClassLoader.getSystemClassLoader();
    }

   /**
     * Factory method. Construct concrete handler in depends from {@code path}.
     *
     * @param path     the path to process
     * @param executor executor used for compile task invocation
     * @throws NullPointerException if {@code path} or {@code executor} is
     *                              {@code null}
     */
    public static PathHandler create(String path, Executor executor) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(executor);
        Matcher matcher = JAR_IN_DIR_PATTERN.matcher(path);
        if (matcher.matches()) {
            path = matcher.group(1);
            path = path.isEmpty() ? "." : path;
            return new ClassPathJarInDirEntry(Paths.get(path), executor);
        } else {
            path = path.isEmpty() ? "." : path;
            Path p = Paths.get(path);
            if (isJarFile(p)) {
                return new ClassPathJarEntry(p, executor);
            } else if (isListFile(p)) {
                return new ClassesListInFile(p, executor);
            } else {
                return new ClassPathDirEntry(p, executor);
            }
        }
    }

    private static boolean isJarFile(Path path) {
        if (Files.isRegularFile(path)) {
            String name = path.toString();
            return Utils.endsWithIgnoreCase(name, ".zip")
                    || Utils.endsWithIgnoreCase(name, ".jar");
        }
        return false;
    }

    private static boolean isListFile(Path path) {
        if (Files.isRegularFile(path)) {
            String name = path.toString();
            return Utils.endsWithIgnoreCase(name, ".lst");
        }
        return false;
    }

    /**
     * Processes all classes in specified path.
     */
    public abstract void process();

   /**
     * Sets class loader, that will be used to define class at
     * {@link #processClass(String)}.
     *
     * @param loader class loader
     * @throws NullPointerException if {@code loader} is {@code null}
     */
    protected final void setLoader(ClassLoader loader) {
        Objects.requireNonNull(loader);
        this.loader = loader;
    }

    /**
     * Processes specificed class.
     * @param name fully qualified name of class to process
     */
    protected final void processClass(String name) {
        try {
            Class aClass = Class.forName(name, true, loader);
            Compiler.compileClass(aClass, executor);
        } catch (ClassNotFoundException | LinkageError e) {
            System.out.printf("Class %s loading failed : %s%n", name,
                e.getMessage());
        }
    }

    /**
     * @return {@code true} if processing should be stopped
     */
    public static boolean isFinished() {
        return Compiler.isLimitReached();
    }

}

