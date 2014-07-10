/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.classfile.Annotation.*;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool.*;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.RuntimeAnnotations_attribute;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Build the profile information from ct.sym if exists.
 */
enum Profile {
    COMPACT1("compact1", 1),
    COMPACT2("compact2", 2),
    COMPACT3("compact3", 3),
    FULL_JRE("Full JRE", 4);

    final String name;
    final int profile;
    final Set<String> packages;
    final Set<String> proprietaryPkgs;

    Profile(String name, int profile) {
        this.name = name;
        this.profile = profile;
        this.packages = new HashSet<>();
        this.proprietaryPkgs = new HashSet<>();
    }

    public String profileName() {
        return name;
    }

    public static int getProfileCount() {
        return PackageToProfile.map.values().size();
    }

    /**
     * Returns the Profile for the given package name. It returns an empty
     * string if the given package is not in any profile.
     */
    public static Profile getProfile(String pn) {
        Profile profile = PackageToProfile.map.get(pn);
        return (profile != null && profile.packages.contains(pn))
                    ? profile : null;
    }

    static class PackageToProfile {
        static String[] JAVAX_CRYPTO_PKGS = new String[] {
            "javax.crypto",
            "javax.crypto.interfaces",
            "javax.crypto.spec"
        };
        static Map<String, Profile> map = initProfiles();
        private static Map<String, Profile> initProfiles() {
            try {
                String profilesProps = System.getProperty("jdeps.profiles");
                if (profilesProps != null) {
                    // for testing for JDK development build where ct.sym doesn't exist
                    initProfilesFromProperties(profilesProps);
                } else {
                    Path home = Paths.get(System.getProperty("java.home"));
                    if (home.endsWith("jre")) {
                        home = home.getParent();
                    }
                    Path ctsym = home.resolve("lib").resolve("ct.sym");
                    if (Files.exists(ctsym)) {
                        // parse ct.sym and load information about profiles
                        try (JarFile jf = new JarFile(ctsym.toFile())) {
                            ClassFileReader reader = ClassFileReader.newInstance(ctsym, jf);
                            for (ClassFile cf : reader.getClassFiles()) {
                                findProfile(cf);
                            }
                        }
                        // special case for javax.crypto.* classes that are not
                        // included in ct.sym since they are in jce.jar
                        Collections.addAll(Profile.COMPACT1.packages, JAVAX_CRYPTO_PKGS);
                    }
                }
            } catch (IOException | ConstantPoolException e) {
                throw new Error(e);
            }
            HashMap<String,Profile> map = new HashMap<>();
            for (Profile profile : Profile.values()) {
                for (String pn : profile.packages) {
                    if (!map.containsKey(pn)) {
                        // split packages in the JRE: use the smaller compact
                        map.put(pn, profile);
                    }
                }
                for (String pn : profile.proprietaryPkgs) {
                    if (!map.containsKey(pn)) {
                        map.put(pn, profile);
                    }
                }
            }
            return map;
        }
        private static final String PROFILE_ANNOTATION = "Ljdk/Profile+Annotation;";
        private static final String PROPRIETARY_ANNOTATION = "Lsun/Proprietary+Annotation;";
        private static Profile findProfile(ClassFile cf) throws ConstantPoolException {
            RuntimeAnnotations_attribute attr = (RuntimeAnnotations_attribute)
                cf.attributes.get(Attribute.RuntimeInvisibleAnnotations);
            int index = 0;
            boolean proprietary = false;
            if (attr != null) {
                for (int i = 0; i < attr.annotations.length; i++) {
                    Annotation ann = attr.annotations[i];
                    String annType = cf.constant_pool.getUTF8Value(ann.type_index);
                    if (PROFILE_ANNOTATION.equals(annType)) {
                        for (int j = 0; j < ann.num_element_value_pairs; j++) {
                            Annotation.element_value_pair pair = ann.element_value_pairs[j];
                            Primitive_element_value ev = (Primitive_element_value) pair.value;
                            CONSTANT_Integer_info info = (CONSTANT_Integer_info)
                                cf.constant_pool.get(ev.const_value_index);
                            index = info.value;
                            break;
                        }
                    } else if (PROPRIETARY_ANNOTATION.equals(annType)) {
                        proprietary = true;
                    }
                }
            }

            Profile p = null;  // default
            switch (index) {
                case 1:
                    p = Profile.COMPACT1; break;
                case 2:
                    p = Profile.COMPACT2; break;
                case 3:
                    p = Profile.COMPACT3; break;
                case 4:
                    p = Profile.FULL_JRE; break;
                default:
                    // skip classes with profile=0
                    // Inner classes are not annotated with the profile annotation
                    return null;
            }

            String name = cf.getName();
            int i = name.lastIndexOf('/');
            name = (i > 0) ? name.substring(0, i).replace('/', '.') : "";
            if (proprietary) {
                p.proprietaryPkgs.add(name);
            } else {
                p.packages.add(name);
            }
            return p;
        }

        private static void initProfilesFromProperties(String path) throws IOException {
            Properties props = new Properties();
            try (FileReader reader = new FileReader(path)) {
                props.load(reader);
            }
            for (Profile prof : Profile.values()) {
                int i = prof.profile;
                String key = props.getProperty("profile." + i + ".name");
                if (key == null) {
                    throw new RuntimeException(key + " missing in " + path);
                }
                String n = props.getProperty("profile." + i + ".packages");
                String[] pkgs = n.split("\\s+");
                for (String p : pkgs) {
                    if (p.isEmpty()) continue;
                    prof.packages.add(p);
                }
            }
        }
    }

    // for debugging
    public static void main(String[] args) {
        if (args.length == 0) {
            if (Profile.getProfileCount() == 0) {
                System.err.println("No profile is present in this JDK");
            }
            for (Profile p : Profile.values()) {
                String profileName = p.name;
                SortedSet<String> set = new TreeSet<>(p.packages);
                for (String s : set) {
                    // filter out the inner classes that are not annotated with
                    // the profile annotation
                    if (PackageToProfile.map.get(s) == p) {
                        System.out.format("%2d: %-10s  %s%n", p.profile, profileName, s);
                        profileName = "";
                    } else {
                        System.err.format("Split package: %s in %s and %s %n",
                            s, PackageToProfile.map.get(s).name, p.name);
                    }
                }
            }
        }
        for (String pn : args) {
            System.out.format("%s in %s%n", pn, getProfile(pn));
        }
    }
}
