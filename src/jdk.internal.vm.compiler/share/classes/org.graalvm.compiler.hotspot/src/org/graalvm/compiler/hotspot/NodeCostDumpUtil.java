/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.CSVUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.Virtualizable;

public class NodeCostDumpUtil {

    private static final String prefix1 = "com.oracle.";
    private static final String prefix2 = "org.graalvm.";
    private static final String FMT = CSVUtil.buildFormatString("%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s");

    private static String getArgumentRegex(String arg) {
        if (arg.length() == 0) {
            return null;
        }
        try {
            Pattern.compile(arg);
            return arg;
        } catch (PatternSyntaxException e) {
            // silently ignore
            System.err.println("Invalid regex given, defaulting to \".*\" regex..");
            return null;
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("NodeCostDumpUtil expects exactly one argument, the node name regex to match against.");
            System.exit(-1);
        }
        final String pattern = getArgumentRegex(args[0]);
        String version = System.getProperty("java.specification.version");
        if (version.compareTo("1.9") >= 0) {
            System.err.printf("NodeCostDumpUtil does not support JDK versions greater than 1.8, current version is %s.\n", version);
            System.exit(-1);
        }
        String[] jvmciCP = System.getProperty("jvmci.class.path.append").split(File.pathSeparator);
        String[] primarySuiteCP = System.getProperty("primary.suite.cp").split(File.pathSeparator);
        ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
        HashSet<Class<?>> classes = new HashSet<>();
        try {
            Set<String> uniquePaths = new HashSet<>(Arrays.asList(primarySuiteCP));
            uniquePaths.addAll(Arrays.asList(jvmciCP));
            for (String path : uniquePaths) {
                if (new File(path).exists()) {
                    if (path.endsWith(".jar")) {
                        try (FileSystem jarFileSystem = FileSystems.newFileSystem(URI.create("jar:file:" + path), Collections.emptyMap())) {
                            initAllClasses(jarFileSystem.getPath("/"), applicationClassLoader, classes);
                        }
                    } else {
                        initAllClasses(FileSystems.getDefault().getPath(path), applicationClassLoader, classes);
                    }
                }
            }
        } catch (IOException ex) {
            GraalError.shouldNotReachHere();
        }
        System.err.printf("Loaded %d classes...\n", classes.size());
        List<Class<?>> nodeClasses = new ArrayList<>();
        for (Class<?> loaded : classes) {
            if (Node.class.isAssignableFrom(loaded) && !loaded.isArray()) {
                nodeClasses.add(loaded);
            }
        }
        System.err.printf("Loaded %s node classes...\n", nodeClasses.size());
        List<NodeClass<?>> nc = new ArrayList<>();
        for (Class<?> c : nodeClasses) {
            try {
                nc.add(NodeClass.get(c));
            } catch (Throwable t) {
                // Silently ignore problems here
            }
        }
        System.err.printf("Read TYPE field from %s node classes...\n", nc.size());
        nc = nc.stream().filter(x -> x != null).collect(Collectors.toList());
        nc.sort((x, y) -> {
            String a = x.getJavaClass().getName();
            String b = y.getJavaClass().getName();
            return a.compareTo(b);
        });
        CSVUtil.Escape.println(System.out, FMT, "NodeName", "Size", "Overrides Size Method", "Cycles", "Overrides Cycles Method", "Canonicalizable", "MemoryCheckPoint", "Virtualizable");
        for (NodeClass<?> nodeclass : nc) {
            String packageStrippedName = null;
            try {
                packageStrippedName = nodeclass.getJavaClass().getCanonicalName().replace(prefix1, "").replace(prefix2, "");
            } catch (Throwable t) {
                // do nothing
                continue;
            }
            if (pattern != null && !packageStrippedName.matches(pattern)) {
                continue;
            }
            boolean overridesSizeMethod = false;
            boolean overridesCyclesMethod = false;
            Class<?> c = nodeclass.getJavaClass();
            try {
                c.getDeclaredMethod("estimatedNodeSize");
                overridesSizeMethod = true;
            } catch (Throwable t) {
                // do nothing
            }
            try {
                c.getDeclaredMethod("estimatedNodeCycles");
                overridesCyclesMethod = true;
            } catch (Throwable t) {
                // do nothing
            }
            CSVUtil.Escape.println(System.out, FMT, packageStrippedName, nodeclass.size(), overridesSizeMethod, nodeclass.cycles(), overridesCyclesMethod, canonicalizable(c), memoryCheckPoint(c),
                            virtualizable(c));
        }
    }

    private static boolean canonicalizable(Class<?> c) {
        return Canonicalizable.class.isAssignableFrom(c);
    }

    private static boolean virtualizable(Class<?> c) {
        return Virtualizable.class.isAssignableFrom(c);
    }

    private static boolean memoryCheckPoint(Class<?> c) {
        return MemoryCheckpoint.class.isAssignableFrom(c);
    }

    private static void initAllClasses(final Path root, ClassLoader classLoader, HashSet<Class<?>> classes) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String className = root.relativize(file).toString();
                    ClassLoader c = classLoader;
                    if (className.endsWith(".class")) {
                        String prefix = prefixed(className);
                        if (prefix != null) {
                            String stripped = stripClassName(className);
                            c = new URLClassLoader(new URL[]{new File(constructURLPart(stripped, className, prefix)).toURI().toURL()}, classLoader);
                            className = constructClazzPart(stripped, prefix);
                        } else {
                            String clazzPart = className.replace('/', '.');
                            className = clazzPart.substring(0, clazzPart.length() - ".class".length());
                        }
                        try {
                            Class<?> systemClass = Class.forName(className, false, c);
                            if (systemClass.getEnclosingClass() != null) {
                                try {
                                    classes.add(systemClass.getEnclosingClass());
                                } catch (Throwable t) {
                                    // do nothing
                                }
                            }
                            classes.add(systemClass);
                        } catch (Throwable ignored) {
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            GraalError.shouldNotReachHere();
        }
    }

    private static String prefixed(String className) {
        if (className.contains(prefix1) && className.indexOf(prefix1) > 0) {
            return prefix1;
        } else if (className.contains(prefix2) && className.indexOf(prefix2) > 0) {
            return prefix2;
        }
        return null;
    }

    private static String stripClassName(String className) {
        return className.replace('/', '.');
    }

    private static String constructClazzPart(String stripped, String prefix) {
        String clazzPart = stripped.substring(stripped.lastIndexOf(prefix), stripped.length());
        return clazzPart.substring(0, clazzPart.length() - ".class".length());
    }

    private static String constructURLPart(String stripped, String className, String prefix) {
        return className.substring(0, stripped.lastIndexOf(prefix));
    }

}
