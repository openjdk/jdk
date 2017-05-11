/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.Unsafe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract handler for path.
 * Concrete subclasses should implement method {@link #process()}.
 */
public abstract class PathHandler {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final AtomicLong CLASS_COUNT = new AtomicLong(0L);
    private static volatile boolean CLASSES_LIMIT_REACHED = false;
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
            } else if (isJimageFile(p)) {
                return new ClassPathJimageEntry(p, executor);
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

    private static boolean isJimageFile(Path path) {
        String filename = path.getFileName().toString();
        return Files.isRegularFile(path)
                && ("modules".equals(filename)
                || Utils.endsWithIgnoreCase(filename, ".jimage"));
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
     * Processes specified class.
     * @param name fully qualified name of class to process
     */
    protected final void processClass(String name) {
        Objects.requireNonNull(name);
        if (CLASSES_LIMIT_REACHED) {
            return;
        }
        long id = CLASS_COUNT.incrementAndGet();
        if (id > Utils.COMPILE_THE_WORLD_STOP_AT) {
            CLASSES_LIMIT_REACHED = true;
            return;
        }
        if (id >= Utils.COMPILE_THE_WORLD_START_AT) {
            try {
                Class<?> aClass = loader.loadClass(name);
                if (!"sun.reflect.misc.Trampoline".equals(name)
                        // workaround for JDK-8159155
                        && !"sun.tools.jconsole.OutputViewer".equals(name)) {
                    UNSAFE.ensureClassInitialized(aClass);
                }
                CompileTheWorld.OUT.printf("[%d]\t%s%n", id, name);
                Compiler.compileClass(aClass, id, executor);
            } catch (ClassNotFoundException e) {
                CompileTheWorld.OUT.printf("Class %s loading failed : %s%n",
                        name, e.getMessage());
            }
        }
    }

    /**
     * @return count of processed classes
     */
    public static long getClassCount() {
        long id = CLASS_COUNT.get();
        if (id < Utils.COMPILE_THE_WORLD_START_AT) {
            return 0;
        }
        if (id > Utils.COMPILE_THE_WORLD_STOP_AT) {
            return Utils.COMPILE_THE_WORLD_STOP_AT - Utils.COMPILE_THE_WORLD_START_AT + 1;
        }
        return id - Utils.COMPILE_THE_WORLD_START_AT + 1;
    }

    /**
     * @return {@code true} if classes limit is reached and processing should be stopped
     */
    public static boolean isFinished() {
        return CLASSES_LIMIT_REACHED;
    }

}

