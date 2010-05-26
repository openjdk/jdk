/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package com.sun.classanalyzer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Mandy Chung
 */
public class ResourceFile implements Comparable<ResourceFile> {

    private final String pathname;
    private Module module;

    ResourceFile(String pathname) {
        this.pathname = pathname.replace(File.separatorChar, '/');
    }

    Module getModule() {
        return module;
    }

    void setModule(Module m) {
        if (module != null) {
            throw new RuntimeException("Module for " + this + " already set");
        }
        this.module = m;
    }

    String getName() {
        return pathname;
    }

    String getPathname() {
        return pathname;
    }

    @Override
    public String toString() {
        return pathname;
    }

    @Override
    public int compareTo(ResourceFile o) {
        return pathname.compareTo(o.pathname);
    }
    static Set<ResourceFile> resources = new TreeSet<ResourceFile>();

    static boolean isResource(String pathname) {
        String name = pathname.replace(File.separatorChar, '/');

        if (name.endsWith("META-INF/MANIFEST.MF")) {
            return false;
        }
        if (name.contains("META-INF/JCE_RSA.")) {
            return false;
        }

        return true;
    }

    static void addResource(String name, InputStream in) {
        ResourceFile res;
        name = name.replace(File.separatorChar, '/');
        if (name.startsWith("META-INF/services")) {
            res = new ServiceProviderConfigFile(name, in);
        } else {
            res = new ResourceFile(name);
        }
        resources.add(res);
    }

    static Set<ResourceFile> getAllResources() {
        return Collections.unmodifiableSet(resources);
    }

    static class ServiceProviderConfigFile extends ResourceFile {

        private final List<String> providers = new ArrayList<String>();
        private final String service;
        ServiceProviderConfigFile(String pathname, InputStream in) {
            super(pathname);
            readServiceConfiguration(in, providers);
            this.service = pathname.substring("META-INF/services".length() + 1, pathname.length());
        }

        @Override
        String getName() {
            if (providers.isEmpty()) {
                return service;
            } else {
                // just use the first one for matching
                return providers.get(0);
            }
        }

        @SuppressWarnings("empty-statement")
        void readServiceConfiguration(InputStream in, List<String> names) {
            BufferedReader br = null;
            try {
                if (in != null) {
                    // Properties doesn't perserve the order of the input file
                    br = new BufferedReader(new InputStreamReader(in, "utf-8"));
                    int lc = 1;
                    while ((lc = parseLine(br, lc, names)) >= 0);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        // Parse a single line from the given configuration file, adding the name
        // on the line to the names list.
        //
        private int parseLine(BufferedReader r, int lc, List<String> names) throws IOException {
            String ln = r.readLine();
            if (ln == null) {
                return -1;
            }
            int ci = ln.indexOf('#');
            if (ci >= 0) {
                ln = ln.substring(0, ci);
            }
            ln = ln.trim();
            int n = ln.length();
            if (n != 0) {
                if ((ln.indexOf(' ') >= 0) || (ln.indexOf('\t') >= 0)) {
                    throw new RuntimeException("Illegal configuration-file syntax");
                }
                int cp = ln.codePointAt(0);
                if (!Character.isJavaIdentifierStart(cp)) {
                    throw new RuntimeException("Illegal provider-class name: " + ln);
                }
                for (int i = Character.charCount(cp); i < n; i += Character.charCount(cp)) {
                    cp = ln.codePointAt(i);
                    if (!Character.isJavaIdentifierPart(cp) && (cp != '.')) {
                        throw new RuntimeException("Illegal provider-class name: " + ln);
                    }
                }
                if (!names.contains(ln)) {
                    names.add(ln);
                }
            }
            return lc + 1;
        }
    }
}
