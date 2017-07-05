/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * MetaIndex is intended to decrease startup time (in particular cold
 * start, when files are not yet in the disk cache) by providing a
 * quick reject mechanism for probes into jar files. The on-disk
 * representation of the meta-index is a flat text file with per-jar
 * entries indicating (generally speaking) prefixes of package names
 * contained in the jar. As an example, here is an edited excerpt of
 * the meta-index generated for jre/lib in the current build:
 *
<PRE>
% VERSION 1
# charsets.jar
sun/
# jce.jar
javax/
! jsse.jar
sun/
com/sun/net/
javax/
com/sun/security/
@ resources.jar
com/sun/xml/
com/sun/rowset/
com/sun/org/
sun/
com/sun/imageio/
javax/
com/sun/java/swing/
META-INF/services/
com/sun/java/util/jar/pack/
com/sun/corba/
com/sun/jndi/
! rt.jar
org/w3c/
com/sun/imageio/
javax/
sunw/util/
java/
sun/
...
</PRE>
 * <p> A few notes about the design of the meta-index:
 *
 * <UL>
 *
 * <LI> It contains entries for multiple jar files. This is
 * intentional, to reduce the number of disk accesses that need to be
 * performed during startup.
 *
 * <LI> It is only intended to act as a fast reject mechanism to
 * prevent application and other classes from forcing all jar files on
 * the boot and extension class paths to be opened. It is not intended
 * as a precise index of the contents of the jar.
 *
 * <LI> It should be as small as possible to reduce the amount of time
 * required to parse it during startup. For example, adding on the
 * secondary package element to java/ and javax/ packages
 * ("javax/swing/", for example) causes the meta-index to grow
 * significantly. This is why substrings of the packages have been
 * chosen as the principal contents.
 *
 * <LI> It is versioned, and optional, to prevent strong dependencies
 * between the JVM and JDK. It is also potentially applicable to more
 * than just the boot and extension class paths.
 *
 * <LI> Precisely speaking, it plays different role in JVM and J2SE
 * side.  On the JVM side, meta-index file is used to speed up locating the
 * class files only while on the J2SE side, meta-index file is used to speed
 * up the resources file & class file.
 * To help the JVM and J2SE code to better utilize the information in meta-index
 * file, we mark the jar file differently. Here is the current rule we use.
 * For jar file containing only class file, we put '!' before the jar file name;
 * for jar file containing only resources file, we put '@' before the jar file name;
 * for jar file containing both resources and class file, we put '#' before the
 * jar name.
 * Notice the fact that every jar file contains at least the manifest file, so when
 * we say "jar file containing only class file", we don't include that file.
 *
 * </UL>
 *
 * <p> To avoid changing the behavior of the current application
 * loader and other loaders, the current MetaIndex implementation in
 * the JDK requires that the directory containing the meta-index be
 * registered with the MetaIndex class before construction of the
 * associated URLClassPath. This prevents the need for automatic
 * searching for the meta-index in the URLClassPath code and potential
 * changes in behavior for non-core ClassLoaders.
 *
 * This class depends on make/tools/MetaIndex/BuildMetaIndex.java and
 * is used principally by sun.misc.URLClassPath.
 */

public class MetaIndex {
    // Maps jar file names in registered directories to meta-indices
    private static volatile Map<File, MetaIndex> jarMap;

    // List of contents of this meta-index
    private String[] contents;

    // Indicate whether the coresponding jar file is a pure class jar file or not
    private boolean isClassOnlyJar;

    //----------------------------------------------------------------------
    // Registration of directories (which can cause parsing of the
    // meta-index file if it is present), and fetching of parsed
    // meta-indices
    // jarMap is not strictly thread-safe when the meta index mechanism
    // is extended for user-provided jar files in future.

    public static MetaIndex forJar(File jar) {
        return getJarMap().get(jar);
    }

    // 'synchronized' is added to protect the jarMap from being modified
    // by multiple threads.
    public static synchronized void registerDirectory(File dir) {
        // Note that this does not currently check to see whether the
        // directory has previously been registered, since the meta-index
        // in a particular directory creates multiple entries in the
        // jarMap. If this mechanism is extended beyond the boot and
        // extension class paths (for example, automatically searching for
        // meta-index files in directories containing jars which have been
        // explicitly opened) then this code should be generalized.
        //
        // This method must be called from a privileged context.
        File indexFile = new File(dir, "meta-index");
        if (indexFile.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(indexFile));
                String line = null;
                String curJarName = null;
                boolean isCurJarContainClassOnly = false;
                List<String> contents = new ArrayList<String>();
                Map<File, MetaIndex> map = getJarMap();

                /* Convert dir into canonical form. */
                dir = dir.getCanonicalFile();
                /* Note: The first line should contain the version of
                 * the meta-index file. We have to match the right version
                 * before trying to parse this file. */
                line = reader.readLine();
                if (line == null ||
                    !line.equals("% VERSION 2")) {
                    reader.close();
                    return;
                }
                while ((line = reader.readLine()) != null) {
                    switch (line.charAt(0)) {
                    case '!':
                    case '#':
                    case '@': {
                        // Store away current contents, if any
                        if ((curJarName != null) && (contents.size() > 0)) {
                            map.put(new File(dir, curJarName),
                                    new MetaIndex(contents,
                                                  isCurJarContainClassOnly));

                            contents.clear();
                        }
                        // Fetch new current jar file name
                        curJarName = line.substring(2);
                        if (line.charAt(0) == '!') {
                            isCurJarContainClassOnly = true;
                        } else if (isCurJarContainClassOnly) {
                            isCurJarContainClassOnly = false;
                        }

                        break;
                    }
                    case '%':
                        break;
                    default: {
                        contents.add(line);
                    }
                    }
                }
                // Store away current contents, if any
                if ((curJarName != null) && (contents.size() > 0)) {
                    map.put(new File(dir, curJarName),
                            new MetaIndex(contents, isCurJarContainClassOnly));
                }

                reader.close();

            } catch (IOException e) {
                // Silently fail for now (similar behavior to elsewhere in
                // extension and core loaders)
            }
        }
    }

    //----------------------------------------------------------------------
    // Public APIs
    //

    public boolean mayContain(String entry) {
        // Ask non-class file from class only jar returns false
        // This check is important to avoid some class only jar
        // files such as rt.jar are opened for resource request.
        if  (isClassOnlyJar && !entry.endsWith(".class")){
            return false;
        }

        String[] conts = contents;
        for (int i = 0; i < conts.length; i++) {
            if (entry.startsWith(conts[i])) {
                return true;
            }
        }
        return false;
    }


    //----------------------------------------------------------------------
    // Implementation only below this point
    // @IllegalArgumentException if entries is null.
    private MetaIndex(List<String> entries, boolean isClassOnlyJar)
        throws IllegalArgumentException {
        if (entries == null) {
            throw new IllegalArgumentException();
        }

        contents = entries.toArray(new String[0]);
        this.isClassOnlyJar = isClassOnlyJar;
    }

    private static Map<File, MetaIndex> getJarMap() {
        if (jarMap == null) {
            synchronized (MetaIndex.class) {
                if (jarMap == null) {
                    jarMap = new HashMap<File, MetaIndex>();
                }
            }
        }
        assert jarMap != null;
        return jarMap;
    }
}
