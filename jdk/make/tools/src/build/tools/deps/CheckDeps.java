/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.deps;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Dependencies;
import com.sun.tools.classfile.Dependency;

/**
 * A simple tool to check the JAR files in a JRE image to ensure that there
 * aren't any references to types that do not exist. The tool is intended to
 * be used in the JDK "profiles" build to help ensure that the profile
 * definitions are kept up to date.
 */

public class CheckDeps {

    // classfile API for finding dependencies
    static final Dependency.Finder finder = Dependencies.getClassDependencyFinder();

    // "known types", found in rt.jar or other JAR files
    static final Set<String> knownTypes = new HashSet<>();

    // References to unknown types. The map key is the unknown type, the
    // map value is the set of classes that reference it.
    static final Map<String,Set<String>> unknownRefs = new HashMap<>();

    // The property name is the name of an unknown type that is allowed to be
    // references. The property value is a comma separated list of the types
    // that are allowed to reference it. The list also includes the names of
    // the profiles that the reference is allowed.
    static final Properties allowedBadRefs = new Properties();

    /**
     * Returns the class name for the given class file. In the case of inner
     * classes then the enclosing class is returned in order to keep the
     * rules simple.
     */
    static String toClassName(String s) {
        int i = s.indexOf('$');
        if (i > 0)
            s = s.substring(0, i);
        return s.replace("/", ".");
    }

    /**
     * Analyze the dependencies of all classes in the given JAR file. The
     * method updates knownTypes and unknownRefs as part of the analysis.
     */
    static void analyzeDependencies(Path jarpath) throws Exception {
        System.out.format("Analyzing %s%n", jarpath);
        try (JarFile jf = new JarFile(jarpath.toFile())) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (name.endsWith(".class")) {
                    ClassFile cf = ClassFile.read(jf.getInputStream(e));
                    for (Dependency d : finder.findDependencies(cf)) {
                        String origin = toClassName(d.getOrigin().getName());
                        String target = toClassName(d.getTarget().getName());

                        // origin is now known
                        unknownRefs.remove(origin);
                        knownTypes.add(origin);

                        // if the target is not known then record the reference
                        if (!knownTypes.contains(target)) {
                            Set<String> refs = unknownRefs.get(target);
                            if (refs == null) {
                                // first time seeing this unknown type
                                refs = new HashSet<>();
                                unknownRefs.put(target, refs);
                            }
                            refs.add(origin);
                        }
                    }
                }
            }
        }
    }

    /**
     * We have closure (no references to types that do not exist) if
     * unknownRefs is empty. When unknownRefs is not empty then it should
     * only contain references that are allowed to be present (these are
     * loaded from the refs.allowed properties file).
     *
     * @param the profile that is being tested, this determines the exceptions
     *   in {@code allowedBadRefs} that apply.
     *
     * @return {@code true} if there are no missing types or the only references
     *   to missing types are described by {@code allowedBadRefs}.
     */
    static boolean checkClosure(String profile) {
        // process the references to types that do not exist.
        boolean fail = false;
        for (Map.Entry<String,Set<String>> entry: unknownRefs.entrySet()) {
            String target = entry.getKey();
            for (String origin: entry.getValue()) {
                // check if origin -> target allowed
                String value = allowedBadRefs.getProperty(target);
                if (value == null) {
                    System.err.format("%s -> %s (unknown type)%n", origin, target);
                    fail = true;
                } else {
                    // target is known, check if the origin is one that we
                    // expect and that the exception applies to the profile.
                    boolean found = false;
                    boolean applicable = false;
                    for (String s: value.split(",")) {
                        s = s.trim();
                        if (s.equals(origin))
                            found = true;
                        if (s.equals(profile))
                            applicable = true;
                    }
                    if (!found || !applicable) {
                        if (!found) {
                            System.err.format("%s -> %s (not allowed)%n", origin, target);
                        } else {
                            System.err.format("%s -> %s (reference not applicable to %s)%n",
                                origin, target, profile);
                        }
                        fail = true;
                    }
                }

            }
        }

        return !fail;
    }

    static void fail(URL url) throws Exception {
        System.err.println("One or more unexpected references encountered");
        if (url != null)
            System.err.format("Check %s is up to date%n", Paths.get(url.toURI()));
        System.exit(-1);
    }

    public static void main(String[] args) throws Exception {
        // load properties file so that we know what missing types that are
        // allowed to be referenced.
        URL url = CheckDeps.class.getResource("refs.allowed");
        if (url != null) {
            try (InputStream in = url.openStream()) {
                allowedBadRefs.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        }

        if (args.length != 2) {
            System.err.println("Usage: java CheckDeps <image> <profile>");
            System.exit(-1);
        }

        String image = args[0];
        String profile = args[1];

        // process JAR files on boot class path
        Path lib = Paths.get(image, "lib");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(lib, "*.jar")) {
            for (Path jarpath: stream) {
                analyzeDependencies(jarpath);
            }
        }

        // classes on boot class path should not reference other types
        boolean okay = checkClosure(profile);
        if (!okay)
            fail(url);

        // process JAR files in the extensions directory
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(lib.resolve("ext"), "*.jar")) {
            for (Path jarpath: stream) {
                analyzeDependencies(jarpath);
            }
        }

        // re-check to ensure that the extensions doesn't reference types that
        // do not exist.
        okay = checkClosure(profile);
        if (!okay)
            fail(url);
    }
}
