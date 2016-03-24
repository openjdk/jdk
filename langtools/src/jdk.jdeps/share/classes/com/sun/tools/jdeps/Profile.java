/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Build the profile information.
 */
enum Profile {
    COMPACT1("compact1", 1, "java.compact1"),
    COMPACT2("compact2", 2, "java.compact2"),
    COMPACT3("compact3", 3, "java.compact3", "java.smartcardio", "jdk.sctp",
                            "jdk.httpserver", "jdk.security.auth",
                            "jdk.naming.dns", "jdk.naming.rmi",
                            "jdk.management"),
    // need a way to determine JRE modules
    SE_JRE("Java SE JRE", 4, "java.se", "jdk.charsets",
                            "jdk.crypto.ec", "jdk.crypto.pkcs11",
                            "jdk.crypto.mscapi", "jdk.crypto.ucrypto", "jdk.jvmstat",
                            "jdk.localedata", "jdk.scripting.nashorn", "jdk.zipfs"),
    FULL_JRE("Full JRE", 5, "java.se.ee", "jdk.charsets",
                            "jdk.crypto.ec", "jdk.crypto.pkcs11",
                            "jdk.crypto.mscapi", "jdk.crypto.ucrypto", "jdk.jvmstat",
                            "jdk.localedata", "jdk.scripting.nashorn", "jdk.zipfs");

    final String name;
    final int profile;
    final String[] mnames;
    final Set<Module> modules = new HashSet<>();

    Profile(String name, int profile, String... mnames) {
        this.name = name;
        this.profile = profile;
        this.mnames = mnames;
    }

    public String profileName() {
        return name;
    }

    @Override
    public String toString() {
        return mnames[0];
    }

    public static int getProfileCount() {
        return JDK.isEmpty() ? 0 : Profile.values().length;
    }

    /**
     * Returns the Profile for the given package name; null if not found.
     */
    public static Profile getProfile(String pn) {
        for (Profile p : Profile.values()) {
            for (Module m : p.modules) {
                if (m.packages().contains(pn)) {
                    return p;
                }
            }
        }
        return null;
    }

    /*
     * Returns the Profile for a given Module; null if not found.
     */
    public static Profile getProfile(Module m) {
        for (Profile p : Profile.values()) {
            if (p.modules.contains(m)) {
                return p;
            }
        }
        return null;
    }

    private final static Set<Module> JDK = new HashSet<>();
    static synchronized void init(Map<String, Module> installed) {
        for (Profile p : Profile.values()) {
            for (String mn : p.mnames) {
                // this includes platform-dependent module that may not exist
                Module m = installed.get(mn);
                if (m != null) {
                    p.addModule(installed, m);
                }
            }
        }

        // JDK modules should include full JRE plus other jdk.* modules
        // Just include all installed modules.  Assume jdeps is running
        // in JDK image
        JDK.addAll(installed.values());
    }

    private void addModule(Map<String, Module> installed, Module m) {
        modules.add(m);
        for (String n : m.requires().keySet()) {
            Module d = installed.get(n);
            if (d == null) {
                throw new InternalError("module " + n + " required by " +
                        m.name() + " doesn't exist");
            }
            modules.add(d);
        }
    }
    // for debugging
    public static void main(String[] args) throws IOException {
        // find platform modules
        if (Profile.getProfileCount() == 0) {
            System.err.println("No profile is present in this JDK");
        }
        for (Profile p : Profile.values()) {
            String profileName = p.name;
            System.out.format("%2d: %-10s  %s%n", p.profile, profileName, p.modules);
            for (Module m: p.modules) {
                System.out.format("module %s%n", m.name());
                System.out.format("   requires %s%n", m.requires());
                for (Map.Entry<String,Set<String>> e: m.exports().entrySet()) {
                    System.out.format("   exports %s %s%n", e.getKey(),
                        e.getValue().isEmpty() ? "" : "to " + e.getValue());
                }
            }
        }
        System.out.println("All JDK modules:-");
        JDK.stream().sorted(Comparator.comparing(Module::name))
           .forEach(m -> System.out.println(m));
    }
}
