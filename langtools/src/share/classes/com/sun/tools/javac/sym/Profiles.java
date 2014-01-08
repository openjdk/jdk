/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.javac.sym;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.tools.javac.util.Assert;

/**
 * Provide details about profile contents.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public abstract class Profiles {
    // for debugging
    public static void main(String[] args) throws IOException {
        Profiles p = Profiles.read(new File(args[0]));
        if (args.length >= 2) {
            Map<Integer,Set<String>> lists = new TreeMap<>();
            for (int i = 1; i <= 4; i++)
                lists.put(i, new TreeSet<String>());

            File rt_jar_lst = new File(args[1]);
            for (String line: Files.readAllLines(rt_jar_lst.toPath(), Charset.defaultCharset())) {
                if (line.endsWith(".class")) {
                    String type = line.substring(0, line.length() - 6);
                    int profile = p.getProfile(type);
                    for (int i = profile; i <= 4; i++)
                        lists.get(i).add(type);
                }
            }

            for (int i = 1; i <= 4; i++) {
                try (BufferedWriter out = new BufferedWriter(new FileWriter(i + ".txt"))) {
                    for (String type : lists.get(i)) {
                        out.write(type);
                        out.newLine();
                    }
                }
            }
        }
    }

    public static Profiles read(File file) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            Properties p = new Properties();
            p.load(in);
            if (p.containsKey("java/lang/Object"))
                return new SimpleProfiles(p);
            else
                return new MakefileProfiles(p);
        }
    }

    public abstract int getProfileCount();

    public abstract int getProfile(String typeName);

    public abstract Set<String> getPackages(int profile);

    private static class MakefileProfiles extends Profiles {
        static class Package {
            final Package parent;
            final String name;

            Map<String, Package> subpackages = new TreeMap<>();

            int profile;
            Map<String, Integer> includedTypes = new TreeMap<>();
            Map<String, Integer> excludedTypes = new TreeMap<>();

            Package(Package parent, String name) {
                this.parent = parent;
                this.name = name;
            }

            int getProfile() {
                return (parent == null) ? profile : Math.max(parent.getProfile(), profile);
            }

            int getProfile(String simpleTypeName) {
                Integer i;
                if ((i = includedTypes.get(simpleTypeName)) != null)
                    return i;
                if ((i = includedTypes.get("*")) != null)
                    return i;
                if ((i = excludedTypes.get(simpleTypeName)) != null)
                    return i + 1;
                if ((i = excludedTypes.get("*")) != null)
                    return i + 1;
                return getProfile();
            }

            String getName() {
                return (parent == null) ? name : (parent.getName() + "/" + name);
            }

            void getPackages(int profile, Set<String> results) {
                int prf = getProfile();
                if (prf != 0 && profile >= prf)
                    results.add(getName());
                for (Package pkg: subpackages.values())
                    pkg.getPackages(profile, results);
            }
        }

        final Map<String, Package> packages = new TreeMap<>();

        final int maxProfile = 4;  // Three compact profiles plus full JRE

        MakefileProfiles(Properties p) {
            for (int profile = 1; profile <= maxProfile; profile++) {
                String prefix = (profile < maxProfile ? "PROFILE_" + profile : "FULL_JRE");
                String inclPackages = p.getProperty(prefix + "_RTJAR_INCLUDE_PACKAGES");
                if (inclPackages == null)
                    break;
                for (String pkg: inclPackages.substring(1).trim().split("\\s+")) {
                    if (pkg.endsWith("/"))
                        pkg = pkg.substring(0, pkg.length() - 1);
                    includePackage(profile, pkg);
                }
                String inclTypes =  p.getProperty(prefix + "_RTJAR_INCLUDE_TYPES");
                if (inclTypes != null) {
                    for (String type: inclTypes.replace("$$", "$").split("\\s+")) {
                        if (type.endsWith(".class"))
                            includeType(profile, type.substring(0, type.length() - 6));
                    }
                }
                String exclTypes =  p.getProperty(prefix + "_RTJAR_EXCLUDE_TYPES");
                if (exclTypes != null) {
                    for (String type: exclTypes.replace("$$", "$").split("\\s+")) {
                        if (type.endsWith(".class"))
                            excludeType(profile, type.substring(0, type.length() - 6));
                    }
                }
            }
        }

        @Override
        public int getProfileCount() {
            return maxProfile;
        }

        @Override
        public int getProfile(String typeName) {
            int sep = typeName.lastIndexOf("/");
            String packageName = typeName.substring(0, sep);
            String simpleName = typeName.substring(sep + 1);

            Package p = getPackage(packageName);
            return p.getProfile(simpleName);
        }

        @Override
        public Set<String> getPackages(int profile) {
            Set<String> results = new TreeSet<>();
            for (Package p: packages.values())
                p.getPackages(profile, results);
            return results;
        }

        private void includePackage(int profile, String packageName) {
//            System.err.println("include package " + packageName);
            Package p = getPackage(packageName);
            Assert.check(p.profile == 0);
            p.profile = profile;
        }

        private void includeType(int profile, String typeName) {
//            System.err.println("include type " + typeName);
            int sep = typeName.lastIndexOf("/");
            String packageName = typeName.substring(0, sep);
            String simpleName = typeName.substring(sep + 1);

            Package p = getPackage(packageName);
            Assert.check(!p.includedTypes.containsKey(simpleName));
            p.includedTypes.put(simpleName, profile);
        }

        private void excludeType(int profile, String typeName) {
//            System.err.println("exclude type " + typeName);
            int sep = typeName.lastIndexOf("/");
            String packageName = typeName.substring(0, sep);
            String simpleName = typeName.substring(sep + 1);

            Package p = getPackage(packageName);
            Assert.check(!p.excludedTypes.containsKey(simpleName));
            p.excludedTypes.put(simpleName, profile);
        }

        private Package getPackage(String packageName) {
            int sep = packageName.lastIndexOf("/");
            Package parent;
            Map<String, Package> parentSubpackages;
            String simpleName;
            if (sep == -1) {
                parent = null;
                parentSubpackages = packages;
                simpleName = packageName;
            } else {
                parent = getPackage(packageName.substring(0, sep));
                parentSubpackages = parent.subpackages;
                simpleName = packageName.substring(sep + 1);
            }

            Package p = parentSubpackages.get(simpleName);
            if (p == null) {
                parentSubpackages.put(simpleName, p = new Package(parent, simpleName));
            }
            return p;
        }
    }

    private static class SimpleProfiles extends Profiles {
        private final Map<String, Integer> map;
        private final int profileCount;

        SimpleProfiles(Properties p) {
            int max = 0;
            map = new HashMap<>();
            for (Map.Entry<Object,Object> e: p.entrySet()) {
                String typeName = (String) e.getKey();
                int profile = Integer.valueOf((String) e.getValue());
                map.put(typeName, profile);
                max = Math.max(max, profile);
            }
            profileCount = max;
        }

        @Override
        public int getProfileCount() {
            return profileCount;
        }

        @Override
        public int getProfile(String typeName) {
            return map.get(typeName);
        }

        @Override
        public Set<String> getPackages(int profile) {
            Set<String> results = new TreeSet<>();
            for (Map.Entry<String,Integer> e: map.entrySet()) {
                String tn = e.getKey();
                int prf = e.getValue();
                int sep = tn.lastIndexOf("/");
                if (sep > 0 && profile >= prf)
                    results.add(tn);
            }
            return results;
        }
    }
}
