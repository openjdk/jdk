/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.makeclasslist;

import java.io.*;
import java.util.*;
import java.util.jar.*;

/** Reads a set of files containing the output of java -verbose:class
    runs. Finds all classes that were loaded from the bootstrap class
    path by comparing the prefix of the load path to the current JRE's
    java.home system property. Prints the names of these classes to
    stdout.
*/

public class MakeClasslist {
  public static void main(String[] args) throws IOException {
    List/*<String>*/ classes = new ArrayList();
    String origJavaHome = System.getProperty("java.home");
    String javaHome     = origJavaHome.toLowerCase();
    if (javaHome.endsWith("jre")) {
      origJavaHome = origJavaHome.substring(0, origJavaHome.length() - 4);
      javaHome     = javaHome.substring(0, javaHome.length() - 4);
    }
    for (int i = 0; i < args.length; i++) {
      try {
        File file = new File(args[i]);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = null;
        while ((line = reader.readLine()) != null) {
          StringTokenizer tok = new StringTokenizer(line, "[ \t\n\r\f");
          if (tok.hasMoreTokens()) {
            String t = tok.nextToken();
            // Understand only "Loading" from -XX:+TraceClassLoadingPreorder.
            // This ignores old "Loaded" from -verbose:class to force correct
            // classlist generation on Mustang.
            if (t.equals("Loading")) {
              t = tok.nextToken();
              t = t.replace('.', '/');

              // Check to make sure it came from the boot class path
              if (tok.hasMoreTokens()) {
                String tmp = tok.nextToken();
                if (tmp.equals("from")) {
                  if (tok.hasMoreTokens()) {
                    tmp = tok.nextToken().toLowerCase();
                    // System.err.println("Loaded " + t + " from " + tmp);
                    if (tmp.startsWith(javaHome)) {
                      // OK, remember this class for later
                      classes.add(t);
                    }
                  }
                }
              }
            }
          }
        }
      } catch (IOException e) {
        System.err.println("Error reading file " + args[i]);
        throw(e);
      }
    }

    Set/*<String>*/  seenClasses = new HashSet();

    for (Iterator iter = classes.iterator(); iter.hasNext(); ) {
      String str = (String) iter.next();
      if (seenClasses.add(str)) {
        System.out.println(str);
      }
    }

    // Try to complete certain packages
    // Note: not using this new code yet; need to consider whether the
    // footprint increase is worth any startup gains
    // Note also that the packages considered below for completion are
    // (obviously) platform-specific
    // JarFile rtJar = new JarFile(origJavaHome + File.separator +
    //                             "jre" + File.separator +
    //                             "lib" + File.separator +
    //                             "rt.jar");
    // completePackage(seenClasses, rtJar, "java/awt");
    // completePackage(seenClasses, rtJar, "sun/awt");
    // completePackage(seenClasses, rtJar, "sun/awt/X11");
    // completePackage(seenClasses, rtJar, "java/awt/im/spi");
    // completePackage(seenClasses, rtJar, "java/lang");
  }

  private static void completePackage(Set seenClasses,
                                      JarFile jar,
                                      String packageName) {
    int len = packageName.length();
    Enumeration entries = jar.entries();
    while (entries.hasMoreElements()) {
      JarEntry entry = (JarEntry) entries.nextElement();
      String name = entry.getName();
      if (name.startsWith(packageName) &&
          name.endsWith(".class") &&
          name.lastIndexOf('/') == len) {
        // Trim ".class" from end
        name = name.substring(0, name.length() - 6);
        if (seenClasses.add(name)) {
          System.out.println(name);
        }
      }
    }
  }
}
