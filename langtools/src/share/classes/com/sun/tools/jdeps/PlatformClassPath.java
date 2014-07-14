/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jdeps;

import com.sun.tools.classfile.Annotation;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.RuntimeAnnotations_attribute;
import com.sun.tools.classfile.Dependencies.ClassFileError;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static com.sun.tools.classfile.Attribute.*;

/**
 * ClassPath for Java SE and JDK
 */
class PlatformClassPath {
    private static final List<String> NON_PLATFORM_JARFILES =
        Arrays.asList("alt-rt.jar", "jfxrt.jar", "ant-javafx.jar", "javafx-mx.jar");
    private static final List<Archive> javaHomeArchives = init();

    static List<Archive> getArchives() {
        return javaHomeArchives;
    }

    private static List<Archive> init() {
        List<Archive> result = new ArrayList<>();
        Path home = Paths.get(System.getProperty("java.home"));
        try {
            if (home.endsWith("jre")) {
                // jar files in <javahome>/jre/lib
                result.addAll(addJarFiles(home.resolve("lib")));
                if (home.getParent() != null) {
                    // add tools.jar and other JDK jar files
                    Path lib = home.getParent().resolve("lib");
                    if (Files.exists(lib)) {
                        result.addAll(addJarFiles(lib));
                    }
                }
            } else if (Files.exists(home.resolve("lib"))) {
                // either a JRE or a jdk build image
                Path classes = home.resolve("classes");
                if (Files.isDirectory(classes)) {
                    // jdk build outputdir
                    result.add(new JDKArchive(classes));
                }
                // add other JAR files
                result.addAll(addJarFiles(home.resolve("lib")));
            } else {
                throw new RuntimeException("\"" + home + "\" not a JDK home");
            }
            return result;
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private static List<Archive> addJarFiles(final Path root) throws IOException {
        final List<Archive> result = new ArrayList<>();
        final Path ext = root.resolve("ext");
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                if (dir.equals(root) || dir.equals(ext)) {
                    return FileVisitResult.CONTINUE;
                } else {
                    // skip other cobundled JAR files
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs)
                throws IOException
            {
                String fn = p.getFileName().toString();
                if (fn.endsWith(".jar")) {
                    // JDK may cobundle with JavaFX that doesn't belong to any profile
                    // Treat jfxrt.jar as regular Archive
                    result.add(NON_PLATFORM_JARFILES.contains(fn)
                                   ? Archive.getInstance(p)
                                   : new JDKArchive(p));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    /**
     * A JDK archive is part of the JDK containing the Java SE API
     * or implementation classes (i.e. JDK internal API)
     */
    static class JDKArchive extends Archive {
        private static List<String> PROFILE_JARS = Arrays.asList("rt.jar", "jce.jar");
        public static boolean isProfileArchive(Archive archive) {
            if (archive instanceof JDKArchive) {
                return PROFILE_JARS.contains(archive.getName());
            }
            return false;
        }

        private final Map<String,Boolean> exportedPackages = new HashMap<>();
        private final Map<String,Boolean> exportedTypes = new HashMap<>();
        JDKArchive(Path p) throws IOException {
            super(p, ClassFileReader.newInstance(p));
        }

        /**
         * Tests if a given fully-qualified name is an exported type.
         */
        public boolean isExported(String cn) {
            int i = cn.lastIndexOf('.');
            String pn = i > 0 ? cn.substring(0, i) : "";

            boolean isJdkExported = isExportedPackage(pn);
            if (exportedTypes.containsKey(cn)) {
                return exportedTypes.get(cn);
            }
            return isJdkExported;
        }

        /**
         * Tests if a given package name is exported.
         */
        public boolean isExportedPackage(String pn) {
            if (Profile.getProfile(pn) != null) {
                return true;
            }
            return exportedPackages.containsKey(pn) ? exportedPackages.get(pn) : false;
        }

        private static final String JDK_EXPORTED_ANNOTATION = "Ljdk/Exported;";
        private Boolean isJdkExported(ClassFile cf) throws ConstantPoolException {
            RuntimeAnnotations_attribute attr = (RuntimeAnnotations_attribute)
                    cf.attributes.get(RuntimeVisibleAnnotations);
            if (attr != null) {
                for (int i = 0; i < attr.annotations.length; i++) {
                    Annotation ann = attr.annotations[i];
                    String annType = cf.constant_pool.getUTF8Value(ann.type_index);
                    if (JDK_EXPORTED_ANNOTATION.equals(annType)) {
                        boolean isJdkExported = true;
                        for (int j = 0; j < ann.num_element_value_pairs; j++) {
                            Annotation.element_value_pair pair = ann.element_value_pairs[j];
                            Annotation.Primitive_element_value ev = (Annotation.Primitive_element_value) pair.value;
                            ConstantPool.CONSTANT_Integer_info info = (ConstantPool.CONSTANT_Integer_info)
                                    cf.constant_pool.get(ev.const_value_index);
                            isJdkExported = info.value != 0;
                        }
                        return Boolean.valueOf(isJdkExported);
                    }
                }
            }
            return null;
        }

        void processJdkExported(ClassFile cf) throws IOException {
            try {
                String cn = cf.getName();
                String pn = cn.substring(0, cn.lastIndexOf('/')).replace('/', '.');

                Boolean b = isJdkExported(cf);
                if (b != null) {
                    exportedTypes.put(cn.replace('/', '.'), b);
                }
                if (!exportedPackages.containsKey(pn)) {
                    // check if package-info.class has @jdk.Exported
                    Boolean isJdkExported = null;
                    ClassFile pcf = reader().getClassFile(cn.substring(0, cn.lastIndexOf('/')+1) + "package-info");
                    if (pcf != null) {
                        isJdkExported = isJdkExported(pcf);
                    }
                    if (isJdkExported != null) {
                        exportedPackages.put(pn, isJdkExported);
                    }
                }
            } catch (ConstantPoolException e) {
                throw new ClassFileError(e);
            }
        }
    }
}
