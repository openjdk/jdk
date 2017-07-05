/*
 * Copyright 1999-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.misc;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * This class is used to maintain mappings from packages, classes
 * and resources to their enclosing JAR files. Mappings are kept
 * at the package level except for class or resource files that
 * are located at the root directory. URLClassLoader uses the mapping
 * information to determine where to fetch an extension class or
 * resource from.
 *
 * @author  Zhenghua Li
 * @since   1.3
 */

public class JarIndex {

    /**
     * The hash map that maintains mappings from
     * package/classe/resource to jar file list(s)
     */
    private HashMap indexMap;

    /**
     * The hash map that maintains mappings from
     * jar file to package/class/resource lists
     */
    private HashMap jarMap;

    /*
     * An ordered list of jar file names.
     */
    private String[] jarFiles;

    /**
     * The index file name.
     */
    public static final String INDEX_NAME = "META-INF/INDEX.LIST";

    /**
     * Constructs a new, empty jar index.
     */
    public JarIndex() {
        indexMap = new HashMap();
        jarMap = new HashMap();
    }

    /**
     * Constructs a new index from the specified input stream.
     *
     * @param is the input stream containing the index data
     */
    public JarIndex(InputStream is) throws IOException {
        this();
        read(is);
    }

    /**
     * Constructs a new index for the specified list of jar files.
     *
     * @param files the list of jar files to construct the index from.
     */
    public JarIndex(String[] files) throws IOException {
        this();
        this.jarFiles = files;
        parseJars(files);
    }

    /**
     * Returns the jar index, or <code>null</code> if none.
     *
     * @param jar the JAR file to get the index from.
     * @exception IOException if an I/O error has occurred.
     */
    public static JarIndex getJarIndex(JarFile jar, MetaIndex metaIndex) throws IOException {
        JarIndex index = null;
        /* If metaIndex is not null, check the meta index to see
           if META-INF/INDEX.LIST is contained in jar file or not.
        */
        if (metaIndex != null &&
            !metaIndex.mayContain(INDEX_NAME)) {
            return null;
        }
        JarEntry e = jar.getJarEntry(INDEX_NAME);
        // if found, then load the index
        if (e != null) {
            index = new JarIndex(jar.getInputStream(e));
        }
        return index;
    }

    /**
     * Returns the jar files that are defined in this index.
     */
    public String[] getJarFiles() {
        return jarFiles;
    }

    /*
     * Add the key, value pair to the hashmap, the value will
     * be put in a linked list which is created if necessary.
     */
    private void addToList(String key, String value, HashMap t) {
        LinkedList list = (LinkedList)t.get(key);
        if (list == null) {
            list = new LinkedList();
            list.add(value);
            t.put(key, list);
        } else if (!list.contains(value)) {
            list.add(value);
        }
    }

    /**
     * Returns the list of jar files that are mapped to the file.
     *
     * @param fileName the key of the mapping
     */
    public LinkedList get(String fileName) {
        LinkedList jarFiles = null;
        if ((jarFiles = (LinkedList)indexMap.get(fileName)) == null) {
            /* try the package name again */
            int pos;
            if((pos = fileName.lastIndexOf("/")) != -1) {
                jarFiles = (LinkedList)indexMap.get(fileName.substring(0, pos));
            }
        }
        return jarFiles;
    }

    /**
     * Add the mapping from the specified file to the specified
     * jar file. If there were no mapping for the package of the
     * specified file before, a new linked list will be created,
     * the jar file is added to the list and a new mapping from
     * the package to the jar file list is added to the hashmap.
     * Otherwise, the jar file will be added to the end of the
     * existing list.
     *
     * @param fileName the file name
     * @param jarName the jar file that the file is mapped to
     *
     */
    public void add(String fileName, String jarName) {
        String packageName;
        int pos;
        if((pos = fileName.lastIndexOf("/")) != -1) {
            packageName = fileName.substring(0, pos);
        } else {
            packageName = fileName;
        }

        // add the mapping to indexMap
        addToList(packageName, jarName, indexMap);

        // add the mapping to jarMap
        addToList(jarName, packageName, jarMap);
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

            Enumeration entries = zrf.entries();
            while(entries.hasMoreElements()) {
                String fileName = ((ZipEntry)(entries.nextElement())).getName();
                // Index the META-INF directory, but not the index or manifest.
                if (!fileName.startsWith("META-INF/") ||
                        !(fileName.equals("META-INF/") ||
                          fileName.equals(INDEX_NAME) ||
                          fileName.equals(JarFile.MANIFEST_NAME))) {
                    add(fileName, currentJar);
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
            (new OutputStreamWriter(out, "UTF8"));
        bw.write("JarIndex-Version: 1.0\n\n");

        if (jarFiles != null) {
            for (int i = 0; i < jarFiles.length; i++) {
                /* print out the jar file name */
                String jar = jarFiles[i];
                bw.write(jar + "\n");
                LinkedList jarlist = (LinkedList)jarMap.get(jar);
                if (jarlist != null) {
                    Iterator listitr = jarlist.iterator();
                    while(listitr.hasNext()) {
                        bw.write((String)(listitr.next()) + "\n");
                    }
                }
                bw.write("\n");
            }
            bw.flush();
        }
    }


    /**
     * Reads the index from the specified InputStream.
     *
     * @param is the input stream
     * @exception IOException if an I/O error has occurred
     */
    public void read(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader
            (new InputStreamReader(is, "UTF8"));
        String line = null;
        String currentJar = null;

        /* an ordered list of jar file names */
        Vector jars = new Vector();

        /* read until we see a .jar line */
        while((line = br.readLine()) != null && !line.endsWith(".jar"));

        for(;line != null; line = br.readLine()) {
            if (line.length() == 0)
                continue;

            if (line.endsWith(".jar")) {
                currentJar = line;
                jars.add(currentJar);
            } else {
                String name = line;
                addToList(name, currentJar, indexMap);
                addToList(currentJar, name, jarMap);
            }
        }

        jarFiles = (String[])jars.toArray(new String[jars.size()]);
    }

    /**
     * Merges the current index into another index, taking into account
     * the relative path of the current index.
     *
     * @param toIndex The destination index which the current index will
     *                merge into.
     * @param path    The relative path of the this index to the destination
     *                index.
     *
     */
    public void merge(JarIndex toIndex, String path) {
        Iterator itr = indexMap.entrySet().iterator();
        while(itr.hasNext()) {
            Map.Entry e = (Map.Entry)itr.next();
            String packageName = (String)e.getKey();
            LinkedList from_list = (LinkedList)e.getValue();
            Iterator listItr = from_list.iterator();
            while(listItr.hasNext()) {
                String jarName = (String)listItr.next();
                if (path != null) {
                    jarName = path.concat(jarName);
                }
                toIndex.add(packageName, jarName);
            }
        }
    }
}
