/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc.collect;

import jdk.tools.jaotc.LogPrinter;
import jdk.tools.jaotc.Main;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static java.nio.file.FileVisitResult.CONTINUE;

public class ClassCollector {
    private final Main.Options options;
    private final LogPrinter log;

    public ClassCollector(Main.Options options, LogPrinter log) {
        this.options = options;
        this.log = log;
    }

    /**
     * Collect all class names passed by the user.
     *
     * @return array list of classes
     */
    public Set<Class<?>> collectClassesToCompile() {
        Set<Class<?>> classes = new HashSet<>();
        List<String> filesToScan = new LinkedList<>(options.files);

        if (options.module != null) {
            classes.addAll(scanModule(filesToScan));
        }

        classes.addAll(scanFiles(filesToScan));
        return classes;
    }

    private Set<Class<?>> scanModule(List<String> filesToScan) {
        String module = options.module;
        // Search module in standard JDK installation.
        Path dir = getModuleDirectory(options.modulepath, module);

        if (Files.isDirectory(dir)) {
            return loadFromModuleDirectory(dir);
        } else {
            findFilesToScan(filesToScan, module);
            return new HashSet<>();
        }
    }

    private Set<Class<?>> loadFromModuleDirectory(Path dir) {
        log.printInfo("Scanning module: " + dir + " ...");
        log.printlnVerbose(" "); // Break line

        FileSystemFinder finder = new FileSystemFinder(dir, pathname -> entryIsClassFile(pathname.toString()));
        Set<Class<?>> cls = loadWithClassLoader(() -> ClassLoader.getSystemClassLoader(), dir, finder);
        log.printlnInfo(" " + cls.size() + " classes loaded.");
        return cls;
    }

    private void findFilesToScan(List<String> filesToScan, String module) {
        // Try to search regular directory, .jar or .class files
        Path path = Paths.get(options.modulepath, module);

        if (Files.isDirectory(path)) {
            filesToScan.add(".");
            options.classpath = path.toString();
        } else if (path.endsWith(".jar") || path.endsWith(".class")) {
            filesToScan.add(path.toString());
        } else {
            path = Paths.get(options.modulepath, module + ".jar");
            if (Files.exists(path)) {
                filesToScan.add(path.toString());
            } else {
                path = Paths.get(options.modulepath, module + ".class");
                if (Files.exists(path)) {
                    filesToScan.add(path.toString());
                } else {
                    throw new InternalError("Expecting a .class, .jar or directory: " + path);
                }
            }
        }
    }

    private boolean entryIsClassFile(String entry) {
        return entry.endsWith(".class") && !entry.endsWith("module-info.class");
    }

    private Set<Class<?>> scanFiles(List<String> filesToScan) {
        Set<Class<?>> classes = new HashSet<>();
        for (String fileName : filesToScan) {
            Set<Class<?>> loaded = scanFile(fileName);
            log.printlnInfo(" " + loaded.size() + " classes loaded.");
            classes.addAll(loaded);
        }
        return classes;
    }

    interface ClassLoaderFactory {
        ClassLoader create() throws IOException;
    }

    private Set<Class<?>> loadWithClassLoader(ClassLoaderFactory factory, Path root, FileSystemFinder finder) {
        ClassLoader loader = null;
        try {
            loader = factory.create();
            return loadClassFiles(root, finder, loader);
        } catch (IOException e) {
            throw new InternalError(e);
        } finally {
            if (loader instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) loader).close();
                } catch (Exception e) {
                    throw new InternalError(e);
                }
            }
        }
    }

    private Set<Class<?>> scanFile(String fileName) {
        log.printInfo("Scanning: " + fileName + " ...");
        log.printlnVerbose(" "); // Break line

        if (fileName.endsWith(".jar")) {
            return loadFromJarFile(fileName);
        } else if (fileName.endsWith(".class")) {
            Set<Class<?>> classes = new HashSet<>();
            loadFromClassFile(fileName, classes);
            return classes;
        } else {
            return scanClassPath(fileName);
        }
    }

    private Set<Class<?>> loadFromJarFile(String fileName) {
        FileSystem fs = makeFileSystem(fileName);
        FileSystemFinder finder = new FileSystemFinder(fs.getPath("/"), pathname -> entryIsClassFile(pathname.toString()));
        return loadWithClassLoader(() -> URLClassLoader.newInstance(buildUrls(fileName)), fs.getPath("/"), finder);
    }

    private void loadFromClassFile(String fileName, Set<Class<?>> classes) {
        Class<?> result;
        File file = new File(options.classpath);
        try (URLClassLoader loader = URLClassLoader.newInstance(buildUrls(file))) {
            result = loadClassFile(loader, fileName);
        } catch (IOException e) {
            throw new InternalError(e);
        }
        Class<?> c = result;
        addClass(classes, fileName, c);
    }

    private Set<Class<?>> scanClassPath(String fileName) {
        Path classPath = Paths.get(options.classpath);
        if (!Files.exists(classPath)) {
            throw new InternalError("Path does not exist: " + classPath);
        }
        if (!Files.isDirectory(classPath)) {
            throw new InternalError("Path must be a directory: " + classPath);
        }

        // Combine class path and file name and see what it is.
        Path combinedPath = Paths.get(options.classpath + File.separator + fileName);
        if (combinedPath.endsWith(".class")) {
            throw new InternalError("unimplemented");
        } else if (Files.isDirectory(combinedPath)) {
            return scanDirectory(classPath, combinedPath);
        } else {
            throw new InternalError("Expecting a .class, .jar or directory: " + fileName);
        }
    }

    private FileSystem makeFileSystem(String fileName) {
        try {
            return FileSystems.newFileSystem(makeJarFileURI(fileName), new HashMap<>());
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    private URI makeJarFileURI(String fileName) {
        String name = Paths.get(fileName).toAbsolutePath().toString();
        name = name.replace('\\', '/');
        try {
            return new URI("jar:file:///" + name + "!/");
        } catch (URISyntaxException e) {
            throw new InternalError(e);
        }
    }

    private PathMatcher combine(PathMatcher m1, PathMatcher m2) {
        return path -> m1.matches(path) && m2.matches(path);
    }

    private Set<Class<?>> scanDirectory(Path classPath, Path combinedPath) {
        String dir = options.classpath;

        FileSystem fileSystem = FileSystems.getDefault();
        PathMatcher matcher = fileSystem.getPathMatcher("glob:" + "*.class");
        FileSystemFinder finder = new FileSystemFinder(combinedPath,
            combine(matcher, pathname -> entryIsClassFile(pathname.toString())));

        File file = new File(dir);
        try (URLClassLoader loader = URLClassLoader.newInstance(buildUrls(file))) {
            return loadClassFiles(classPath, finder, loader);
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    private Set<Class<?>> loadClassFiles(Path root, FileSystemFinder finder, ClassLoader loader) {
        Set<Class<?>> classes = new HashSet<>();
        for (Path name : finder.done()) {
            // Now relativize to the class path so we get the actual class names.
            String entry = root.relativize(name).normalize().toString();
            Class<?> c = loadClassFile(loader, entry);
            addClass(classes, entry, c);
        }
        return classes;
    }

    private void addClass(Set<Class<?>> classes, String name, Class<?> c) {
        if (c != null) {
            classes.add(c);
            log.printlnVerbose(" loaded " + name);
        }
    }

    private URL[] buildUrls(String fileName) throws MalformedURLException {
        String name = Paths.get(fileName).toAbsolutePath().toString();
        name = name.replace('\\', '/');
        return new URL[]{ new URL("jar:file:///" + name + "!/") };
    }

    private URL[] buildUrls(File file) throws MalformedURLException {
        return new URL[] {file.toURI().toURL() };
    }

    private Path getModuleDirectory(String modulepath, String module) {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        return fs.getPath(modulepath, module);
    }

    /**
     * Loads a class with the given file name from the specified {@link URLClassLoader}.
     */
    private Class<?> loadClassFile(final ClassLoader loader, final String fileName) {
        int start = 0;
        if (fileName.startsWith("/")) {
            start = 1;
        }
        String className = fileName.substring(start, fileName.length() - ".class".length());
        className = className.replace('/', '.');
        className = className.replace('\\', '.');
        try {
            return loader.loadClass(className);
        } catch (Throwable e) {
            // If we are running in JCK mode we ignore all exceptions.
            if (options.ignoreClassLoadingErrors) {
                log.printError(className + ": " + e);
                return null;
            }
            throw new InternalError(e);
        }
    }

    /**
     * {@link FileVisitor} implementation to find class files recursively.
     */
    private static class FileSystemFinder extends SimpleFileVisitor<Path> {
        private final ArrayList<Path> fileNames = new ArrayList<>();
        private final PathMatcher filter;

        FileSystemFinder(Path combinedPath, PathMatcher filter) {
            this.filter = filter;
            try {
                Files.walkFileTree(combinedPath, this);
            } catch (IOException e) {
                throw new InternalError(e);
            }
        }

        /**
         * Compares the glob pattern against the file name.
         */
        void find(Path file) {
            Path name = file.getFileName();
            if (name != null && filter.matches(name)) {
                fileNames.add(file);
            }
        }

        List<Path> done() {
            return fileNames;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            find(file);
            return CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            find(dir);
            return CONTINUE;
        }

    }
}
