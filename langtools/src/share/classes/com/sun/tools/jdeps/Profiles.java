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
package com.sun.tools.jdeps;

import com.sun.tools.classfile.Annotation;
import com.sun.tools.classfile.Annotation.*;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.*;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.RuntimeAnnotations_attribute;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Build the profile information from ct.sym if exists.
 */
class Profiles {
    private static final Map<String,Profile> map = initProfiles();
    /**
     * Returns the name of the profile for the given package name.
     * It returns an empty string if the given package is not in any profile.
     */
    public static String getProfileName(String pn) {
        Profile profile = map.get(pn);
        return (profile != null && profile.packages.contains(pn))
                    ? profile.name : "";
    }

    public static int getProfileCount() {
        return new HashSet<Profile>(map.values()).size();
    }

    private static Map<String,Profile> initProfiles() {
        List<Profile> profiles = new ArrayList<Profile>();
        try {
            String profilesProps = System.getProperty("jdeps.profiles");
            if (profilesProps != null) {
                // for testing for JDK development build where ct.sym doesn't exist
                initProfilesFromProperties(profiles, profilesProps);
            } else {
                Path home = Paths.get(System.getProperty("java.home"));
                if (home.endsWith("jre")) {
                    home = home.getParent();
                }
                Path ctsym = home.resolve("lib").resolve("ct.sym");
                if (ctsym.toFile().exists()) {
                    // add a default Full JRE
                    profiles.add(0, new Profile("Full JRE", 0));
                    // parse ct.sym and load information about profiles
                    try (JarFile jf = new JarFile(ctsym.toFile())) {
                        ClassFileReader reader = ClassFileReader.newInstance(ctsym, jf);
                        for (ClassFile cf : reader.getClassFiles()) {
                            findProfile(profiles, cf);
                        }
                    }

                    // merge the last Profile with the "Full JRE"
                    if (profiles.size() > 1) {
                        Profile fullJRE = profiles.get(0);
                        Profile p = profiles.remove(profiles.size() - 1);
                        for (String pn : fullJRE.packages) {
                            // The last profile contains the packages determined from ct.sym.
                            // Move classes annotated profile==0 or no attribute that are
                            // added in the fullJRE profile to either supported or proprietary
                            // packages appropriately
                            if (p.proprietaryPkgs.contains(pn)) {
                                p.proprietaryPkgs.add(pn);
                            } else {
                                p.packages.add(pn);
                            }
                        }
                        fullJRE.packages.clear();
                        fullJRE.proprietaryPkgs.clear();
                        fullJRE.packages.addAll(p.packages);
                        fullJRE.proprietaryPkgs.addAll(p.proprietaryPkgs);
                    }
                }
            }
        } catch (IOException | ConstantPoolException e) {
            throw new Error(e);
        }
        HashMap<String,Profile> map = new HashMap<String,Profile>();
        for (Profile profile : profiles) {
            // Inner classes are not annotated with the profile annotation
            // packages may be in one profile but also appear in the Full JRE
            // Full JRE is always the first element in profiles list and
            // so the map will contain the appropriate Profile
            for (String pn : profile.packages) {
                map.put(pn, profile);
            }
            for (String pn : profile.proprietaryPkgs) {
                map.put(pn, profile);
            }
        }
        return map;
    }

    private static final String PROFILE_ANNOTATION = "Ljdk/Profile+Annotation;";
    private static final String PROPRIETARY_ANNOTATION = "Lsun/Proprietary+Annotation;";
    private static Profile findProfile(List<Profile> profiles, ClassFile cf)
            throws ConstantPoolException
    {
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
                        Primitive_element_value ev = (Primitive_element_value)pair.value;
                        CONSTANT_Integer_info info = (CONSTANT_Integer_info)
                             cf.constant_pool.get(ev.const_value_index);
                        index = info.value;
                        break;
                    }
                } else if (PROPRIETARY_ANNOTATION.equals(annType)) {
                    proprietary = true;
                }
            }
            if (index >= profiles.size()) {
                Profile p = null;
                for (int i = profiles.size(); i <= index; i++) {
                    p = new Profile(i);
                    profiles.add(p);
                }
            }
        }

        Profile p = profiles.get(index);
        String name = cf.getName();
        int i = name.lastIndexOf('/');
        name = (i > 0) ? name.substring(0, i).replace('/','.') : "";
        if (proprietary) {
            p.proprietaryPkgs.add(name);
        } else {
            p.packages.add(name);
        }
        return p;
    }

    private static void initProfilesFromProperties(List<Profile> profiles, String path)
            throws IOException
    {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(path)) {
            props.load(reader);
        }
        int i=1;
        String key;
        while (props.containsKey((key = "profile." + i + ".name"))) {
            Profile profile = new Profile(props.getProperty(key), i);
            profiles.add(profile);
            String n = props.getProperty("profile." + i + ".packages");
            String[] pkgs = n.split("\\s+");
            for (String p : pkgs) {
                if (p.isEmpty()) continue;
                profile.packages.add(p);
            }
            i++;
        }
    }

    private static class Profile {
        final String name;
        final int profile;
        final Set<String> packages;
        final Set<String> proprietaryPkgs;
        Profile(int profile) {
            this("compact" + profile, profile);
        }
        Profile(String name, int profile) {
            this.name = name;
            this.profile = profile;
            this.packages = new HashSet<String>();
            this.proprietaryPkgs = new HashSet<String>();
        }
        public String toString() {
            return name;
        }
    }

    // for debugging
    public static void main(String[] args) {
        if (args.length == 0) {
            Profile[] profiles = new Profile[getProfileCount()];
            for (Profile p : map.values()) {
                // move the zeroth profile to the last
                int index = p.profile == 0 ? profiles.length-1 : p.profile-1;
                profiles[index] = p;
            }
            for (Profile p : profiles) {
                String profileName = p.name;
                SortedSet<String> set = new TreeSet<String>(p.packages);
                for (String s : set) {
                    // filter out the inner classes that are not annotated with
                    // the profile annotation
                    if (map.get(s) == p) {
                        System.out.format("%-10s  %s%n", profileName, s);
                        profileName = "";
                    }
                }
            }
        }
        for (String pn : args) {
            System.out.format("%s in %s%n", pn, getProfileName(pn));
        }
    }
}
