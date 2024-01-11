/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.jar;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;


/**
 * This class is used to maintain mappings from packages, classes
 * and resources to their enclosing JAR files. Mappings are kept
 * at the package level except for class or resource files that
 * are located at the root directory.
 *
 * @author Zhenghua Li
 * @since 1.3
 */

class JarIndex {

    /**
     * The hash map that maintains mappings from
     * package/class/resource to jar file list(s)
     */
    private final HashMap<String, List<String>> indexMap;

    /**
     * The hash map that maintains mappings from
     * jar file to package/class/resource lists
     */
    private final HashMap<String, List<String>> jarMap;

    /*
     * An ordered list of jar file names.
     */
    private String[] jarFiles;

    /**
     * The index file name.
     */
    static final String INDEX_NAME = "META-INF/INDEX.LIST";

    /**
     * true if, and only if, sun.misc.JarIndex.metaInfFilenames is set to true.
     * If true, the names of the files in META-INF, and its subdirectories, will
     * be added to the index. Otherwise, just the directory names are added.
     */
    private static final boolean metaInfFilenames =
            "true".equals(System.getProperty("sun.misc.JarIndex.metaInfFilenames"));

    /**
     * Constructs a new index for the specified list of jar files.
     *
     * @param files the list of jar files to construct the index from.
     */
    public JarIndex(String[] files) throws IOException {
        this.indexMap = new HashMap<>();
        this.jarMap = new HashMap<>();
        this.jarFiles = files;
        parseJars(files);
    }

    /*
     * Add the key, value pair to the hashmap, the value will
     * be put in a list which is created if necessary.
     */
    private void addToList(String key, String value,
                           HashMap<String, List<String>> t) {
        List<String> list = t.get(key);
        if (list == null) {
            list = new ArrayList<>(1);
            list.add(value);
            t.put(key, list);
        } else if (!list.contains(value)) {
            list.add(value);
        }
    }

    /**
     * Add the mapping from the specified file to the specified
     * jar file. If there were no mapping for the package of the
     * specified file before, a new list will be created,
     * the jar file is added to the list and a new mapping from
     * the package to the jar file list is added to the hashmap.
     * Otherwise, the jar file will be added to the end of the
     * existing list.
     *
     * @param fileName the file name
     * @param jarName the jar file that the file is mapped to
     *
     */
    private void add(String fileName, String jarName) {
        String packageName;
        int pos;
        if ((pos = fileName.lastIndexOf('/')) != -1) {
            packageName = fileName.substring(0, pos);
        } else {
            packageName = fileName;
        }

        addMapping(packageName, jarName);
    }

    /**
     * Same as add(String,String) except that it doesn't strip off from the
     * last index of '/'. It just adds the jarItem (filename or package)
     * as it is received.
     */
    private void addMapping(String jarItem, String jarName) {
        // add the mapping to indexMap
        addToList(jarItem, jarName, indexMap);

        // add the mapping to jarMap
        addToList(jarName, jarItem, jarMap);
     }

    /**
     * Go through all the jar files and construct the
     * index table.
     */
    private void parseJars(String[] files) throws IOException {
        if (files == null) {
            return;
        }

        String currentJar = null;

        for (int i = 0; i < files.length; i++) {
            currentJar = files[i];
            ZipFile zrf = new ZipFile(currentJar.replace
                                      ('/', File.separatorChar));

            Enumeration<? extends ZipEntry> entries = zrf.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String fileName = entry.getName();

                // Skip the META-INF directory, the index, and manifest.
                // Any files in META-INF/ will be indexed explicitly
                if (fileName.equals("META-INF/") ||
                    fileName.equals(INDEX_NAME) ||
                    fileName.equals(JarFile.MANIFEST_NAME) ||
                    fileName.startsWith("META-INF/versions/"))
                    continue;

                if (!metaInfFilenames || !fileName.startsWith("META-INF/")) {
                    add(fileName, currentJar);
                } else if (!entry.isDirectory()) {
                        // Add files under META-INF explicitly so that certain
                        // services, like ServiceLoader, etc, can be located
                        // with greater accuracy. Directories can be skipped
                        // since each file will be added explicitly.
                        addMapping(fileName, currentJar);
                }
            }

            zrf.close();
        }
    }

    /**
     * Writes the index to the specified OutputStream
     *
     * @param out the output stream
     * @exception IOException if an I/O error has occurred
     */
    public void write(OutputStream out) throws IOException {
        BufferedWriter bw = new BufferedWriter
                (new OutputStreamWriter(out, StandardCharsets.UTF_8));
        bw.write("JarIndex-Version: 1.0\n\n");

        if (jarFiles != null) {
            for (int i = 0; i < jarFiles.length; i++) {
                /* print out the jar file name */
                String jar = jarFiles[i];
                bw.write(jar + "\n");
                List<String> jarlist = jarMap.get(jar);
                if (jarlist != null) {
                    for (String s : jarlist) {
                        bw.write(s + "\n");
                    }
                }
                bw.write("\n");
            }
            bw.flush();
        }
    }
}
