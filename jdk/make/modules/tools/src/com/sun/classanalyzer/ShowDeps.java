/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.classanalyzer;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * A simple tool to print out the static dependencies for a given set of JAR,
 * class files, or combinations of. The tools supports an -ignore option to
 * ignore references to classes listed in the file (including .classlists
 * created by the ClassAnalyzer tool).
 */

public class ShowDeps {

    static void usage() {
        System.out.println("java ShowDeps [-ignore <classlist>] file...");
        System.out.println("   where <file> is a class or JAR file, or a directory");
        System.out.println();
        System.out.println("Example usages:");
        System.out.println("  java ShowDeps Foo.jar");
        System.out.println("  java ShowDeps -ignore base.classlist Foo.jar");
        System.out.println("  java ShowDeps -ignore base.classlist -ignore " +
            "jaxp-parsers.classlist <dir>");
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        // process -ignore options
        int argi = 0;
        Set<String> ignore = new HashSet<String>();
        while (argi < args.length && args[argi].equals("-ignore")) {
            argi++;
            Scanner s = new Scanner(new File(args[argi++]));
            try {
                while (s.hasNextLine()) {
                    String line = s.nextLine();
                    if (!line.endsWith(".class"))
                        continue;
                    int len = line.length();
                    // convert to class names
                    String clazz = line.replace('\\', '.').replace('/', '.')
                        .substring(0, len-6);
                    ignore.add(clazz);
                }
            } finally {
                s.close();
            }
        }

        if (argi >= args.length)
            usage();

        // parse all classes
        while (argi < args.length)
            ClassPath.setClassPath(args[argi++]);
        ClassPath.parseAllClassFiles();

        // find the classes that don't exist
        Set<Klass> unresolved = new TreeSet<Klass>();
        for (Klass k : Klass.getAllClasses()) {
            if (k.getFileSize() == 0)
                unresolved.add(k);
        }

        // print references to classes that don't exist
        for (Klass k: Klass.getAllClasses()) {
            for (Klass other : k.getReferencedClasses()) {
                if (unresolved.contains(other)) {
                    String name = other.toString();
                    if (!ignore.contains(name)) {
                        System.out.format("%s -> %s\n", k, other);
                    }
                }
            }
        }
    }
}
