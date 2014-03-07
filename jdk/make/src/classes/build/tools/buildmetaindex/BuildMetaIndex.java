/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.buildmetaindex;

import java.io.*;
import java.util.*;
import java.util.jar.*;

/** Constructs a meta-index of the specified jar files. The meta-index
    contains prefixes of packages contained in these jars, indexed by
    the jar file name. It is intended to be consumed by the JVM to
    allow the boot class loader to be made lazier. For example, when
    class data sharing is enabled, the presence of the meta-index
    allows the JVM to skip opening rt.jar if all of the dependent
    classes of the application are in the shared archive. A similar
    mechanism could be useful at the application level as well, for
    example to make the extension class loader lazier.

    <p> The contents of the meta-index file for jre/lib look something
    like this:

    <PRE>
% VERSION 2
# charsets.jar
sun/
# jce.jar
javax/
! jsse.jar
sun/
com/sun/net/
javax/
com/sun/security/
# management-agent.jar
! rt.jar
org/w3c/
com/sun/image/
com/sun/org/
com/sun/imageio/
com/sun/accessibility/
javax/
...
    </PRE>

    <p> It is a current invariant of the code in the JVM which
    consumes the meta-index that the meta-index indexes only jars in
    one directory. It is acceptable for jars in that directory to not
    be mentioned in the meta-index. The meta-index is designed more to
    be able to perform a quick rejection test of the presence of a
    particular class in a particular jar file than to be a precise
    index of the contents of the jar.  */

public class BuildMetaIndex {
    public static void main(String[] args) throws IOException {
        /* The correct usage of this class is as following:
         * java BuildMetaIndex -o <meta-index> <a list of jar files>
         * So the argument length should be at least 3 and the first argument should
         * be '-o'.
         */
        if (args.length < 3 ||
            !args[0].equals("-o")) {
            printUsage();
            System.exit(1);
        }

        try {
            PrintStream out = new PrintStream(new FileOutputStream(args[1]));
            out.println("% VERSION 2");
            out.println("% WARNING: this file is auto-generated; do not edit");
            out.println("% UNSUPPORTED: this file and its format may change and/or");
            out.println("%   may be removed in a future release");
            for (int i = 2; i < args.length; i++) {
                String filename = args[i];
                JarMetaIndex jmi = new JarMetaIndex(filename);
                HashSet<String> index = jmi.getMetaIndex();
                if (index == null) {
                    continue;
                }
                /*
                 * meta-index file plays different role in JVM and JDK side.
                 * On the JVM side, meta-index file is used to speed up locating the
                 * class files only while on the JDK side, meta-index file is used to speed
                 * up the resources file and class file.
                 * To help the JVM and JDK code to better utilize the information in meta-index
                 * file, we mark the jar file differently. Here is the current rule we use (See
                 * JarFileKind.getMarkChar() method. )
                 * For jar file containing only class file, we put '!' before the jar file name;
                 * for jar file containing only resources file, we put '@' before the jar file name;
                 * for jar file containing both resources and class file, we put '#' before the jar name.
                 * Notice the fact that every jar file contains at least the manifest file, so when
                 * we say "jar file containing only class file", we don't include that file.
                 */

                out.println(jmi.getJarFileKind().getMarkerChar() + " " + filename);
                for (String entry : index) {
                    out.println(entry);
                }

            }
            out.flush();
            out.close();
        } catch (FileNotFoundException fnfe) {
            System.err.println("FileNotFoundException occurred");
            System.exit(2);
        }
    }

    private static void printUsage() {
        String usage =
            "BuildMetaIndex is used to generate a meta index file for the jar files\n" +
            "you specified. The following is its usage:\n" +
            " java BuildMetaIndex -o <the output meta index file> <a list of jar files> \n" +
            " You can specify *.jar to refer to all the jar files in the current directory";

        System.err.println(usage);
    }
}

enum JarFileKind {

    CLASSONLY ('!'),
    RESOURCEONLY ('@'),
    MIXED ('#');

    private char markerChar;

    JarFileKind(char markerChar) {
        this.markerChar = markerChar;
    }

    public char getMarkerChar() {
        return markerChar;
    }
}

/*
 * JarMetaIndex associates the jar file with a set of what so called
 * "meta-index" of the jar file. Essentially, the meta-index is a list
 * of class prefixes and the plain files contained in META-INF directory (
 * not include the manifest file itself). This will help sun.misc.URLClassPath
 * to quickly locate the resource file and hotspot VM to locate the class file.
 *
 */
class JarMetaIndex {
    private JarFile jar;
    private volatile HashSet<String> indexSet;

    /*
     * A hashmap contains a mapping from the prefix string to
     * a hashset which contains a set of the second level of prefix string.
     */
    private HashMap<String, HashSet<String>> knownPrefixMap = new HashMap<>();

    /**
     * Special value for the HashSet to indicate that there are classes in
     * the top-level package.
     */
    private static final String TOP_LEVEL = "TOP";

    /*
     * A class for mapping package prefixes to the number of
     * levels of package elements to include.
     */
    static class ExtraLevel {
        public ExtraLevel(String prefix, int levels) {
            this.prefix = prefix;
            this.levels = levels;
        }
        String prefix;
        int levels;
    }

    /*
     * A list of the special-cased package names.
     */
    private static ArrayList<ExtraLevel> extraLevels = new ArrayList<>();

    static {
        // The order of these statements is significant,
        // since we stop looking after the first match.

        // Need more precise information to disambiguate
        // (illegal) references from applications to
        // obsolete backported collections classes in
        // com/sun/java/util
        extraLevels.add(new ExtraLevel("com/sun/java/util/", Integer.MAX_VALUE));
        extraLevels.add(new ExtraLevel("com/sun/java/", 4));
        // Need more information than just first two package
        // name elements to determine that classes in
        // deploy.jar are not in rt.jar
        extraLevels.add(new ExtraLevel("com/sun/", 3));
        // Need to make sure things in jfr.jar aren't
        // confused with other com/oracle/** packages
        extraLevels.add(new ExtraLevel("com/oracle/jrockit", 3));
    }


    /*
     * We add maximum 5 second level entries to "sun", "jdk", "java" and
     * "javax" entries. Tune this parameter to get a balance on the
     * cold start and footprint.
     */
    private static final int MAX_PKGS_WITH_KNOWN_PREFIX = 5;

    private JarFileKind jarFileKind;

    JarMetaIndex(String fileName) throws IOException {
        jar = new JarFile(fileName);
        knownPrefixMap.put("sun", new HashSet<String>());
        knownPrefixMap.put("jdk", new HashSet<String>());
        knownPrefixMap.put("java", new HashSet<String>());
        knownPrefixMap.put("javax", new HashSet<String>());
    }

    /* Returns a HashSet contains the meta index string. */
    HashSet<String> getMetaIndex() {
        if (indexSet == null) {
            synchronized(this) {
                if (indexSet == null) {
                    indexSet = new HashSet<>();
                    Enumeration<JarEntry> entries = jar.entries();
                    boolean containsOnlyClass = true;
                    boolean containsOnlyResource = true;
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        /* We only look at the non-directory entry.
                           MANIFEST file is also skipped. */
                        if (entry.isDirectory()
                            || name.equals("META-INF/MANIFEST.MF")) {
                            continue;
                        }

                        /* Once containsOnlyResource or containsOnlyClass
                           turns to false, no need to check the entry type.
                        */
                        if (containsOnlyResource || containsOnlyClass) {
                            if (name.endsWith(".class")) {
                                containsOnlyResource = false;
                            } else {
                                containsOnlyClass = false;
                            }
                        }

                        /* Add the full-qualified name of plain files under
                           META-INF directory to the indexSet.
                         */
                        if (name.startsWith("META-INF")) {
                            indexSet.add(name);
                            continue;
                        }

                        /* Add the prefix name to the knownPrefixMap if the
                           name starts with any string in the knownPrefix list.
                        */
                        if (isPrefixKnown(name)) {
                            continue;
                        }

                        String[] pkgElements = name.split("/");
                        // Last one is the class name; definitely ignoring that
                        if (pkgElements.length > 2) {
                            String meta = "";

                            // Default is 2 levels of package elements
                            int levels = 2;

                            // But for some packages we add more elements
                            for(ExtraLevel el : extraLevels) {
                                if (name.startsWith(el.prefix)) {
                                    levels = el.levels;
                                    break;
                                }
                            }
                            for (int i = 0; i < levels && i < pkgElements.length - 1; i++) {
                                meta += pkgElements[i] + "/";
                            }

                            if (!meta.equals("")) {
                                indexSet.add(meta);
                            }
                        }

                    } // end of "while" loop;

                    // Add the second level package names to the indexSet for
                    // the predefined names such as "sun", "java" and "javax".
                    addKnownPrefix();

                    /* Set "jarFileKind" attribute. */
                    if (containsOnlyClass) {
                        jarFileKind = JarFileKind.CLASSONLY;
                    } else if (containsOnlyResource) {
                        jarFileKind = JarFileKind.RESOURCEONLY;
                    } else {
                        jarFileKind = JarFileKind.MIXED;
                    }
                }
            }
        }
        return indexSet;
    }

    /*
     * Checks to see whether the name starts with a string which is in the predefined
     * list. If it is among one of the predefined prefixes, add it to the knowPrefixMap
     * and returns true, otherwise, returns false.
     * Returns true if the name is in a predefined prefix list. Otherwise, returns false.
     */
    boolean isPrefixKnown(String name) {
        int firstSlashIndex = name.indexOf("/");
        if (firstSlashIndex == -1) {
            return false;
        }

        String firstPkgElement = name.substring(0, firstSlashIndex);
        HashSet<String> pkgSet = knownPrefixMap.get(firstPkgElement);

        /* The name does not starts with "sun", "java" or "javax". */
        if (pkgSet == null) {
            return false;
        }

        /* Add the second level package name to the corresponding hashset. */
        int secondSlashIndex = name.indexOf("/", firstSlashIndex+1);
        if (secondSlashIndex == -1) {
            pkgSet.add(TOP_LEVEL);
        } else {
            String secondPkgElement = name.substring(firstSlashIndex+1, secondSlashIndex);
            pkgSet.add(secondPkgElement);
        }

        return true;
    }

    /*
     * Adds all the second level package elements for "sun", "java" and "javax"
     * if the corresponding jar file does not contain more than
     * MAX_PKGS_WITH_KNOWN_PREFIX such entries.
     */
    void addKnownPrefix() {
        if (indexSet == null) {
            return;
        }

        /* Iterate through the hash map, add the second level package names
         * to the indexSet if has any.
         */
        for (String key : knownPrefixMap.keySet()) {
            HashSet<String> pkgSetStartsWithKey = knownPrefixMap.get(key);
            int setSize = pkgSetStartsWithKey.size();

            if (setSize == 0) {
                continue;
            }
            if (setSize > JarMetaIndex.MAX_PKGS_WITH_KNOWN_PREFIX ||
                pkgSetStartsWithKey.contains(TOP_LEVEL)) {
                 indexSet.add(key + "/");
            } else {
                /* If the set contains less than MAX_PKGS_WITH_KNOWN_PREFIX, add
                 * them to the indexSet of the MetaIndex object.
                 */
                for (String secondPkgElement : pkgSetStartsWithKey) {
                    indexSet.add(key + "/" + secondPkgElement);
                }
            }
        } // end the outer "for"
    }

    JarFileKind getJarFileKind() {
        // Build meta index if it hasn't.
        if (indexSet == null) {
            indexSet = getMetaIndex();
        }
        return jarFileKind;
    }
}
