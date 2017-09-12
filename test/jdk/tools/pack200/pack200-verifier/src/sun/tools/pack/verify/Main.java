/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 */
// The Main Entry point
package sun.tools.pack.verify;

import java.io.*;

/**
 * This class provides a convenient entry point to the pack200 verifier. This
 * compares two classes, either in path or in an archive.
 * @see xmlkit.XMLKit
 * @author ksrini
 */
public class Main {

    private static void syntax() {
        System.out.println("Usage: ");
        System.out.println("\tREFERENCE_CLASSPATH COMPARED_CLASSPATH [Options]");
        System.out.println("\tOptions:");
        System.out.println("\t\t-O check jar ordering");
        System.out.println("\t\t-C ignore compile attributes (Deprecated, SourceFile, Synthetic, )");
        System.out.println("\t\t-D ignore debug attributes (LocalVariable, LineNumber)");
        System.out.println("\t\t-u ignore unknown attributes");
        System.out.println("\t\t-V turn off class validation");
        System.out.println("\t\t-c CLASS, compare CLASS only");
        System.out.println("\t\t-b Compares all entries bitwise only");
        System.out.println("\t\t-l Directory or Log File Name");
    }

    /**
     * main entry point to the class file comparator, which compares semantically
     * class files in a classpath or an archive.
     * @param args String array as described below
     * @throws RuntimeException
     * <pre>
     *  Usage:
     *     ReferenceClasspath SpecimenClaspath [Options]
     *     Options:
     *      -O check jar ordering
     *      -C do not compare compile attributes (Deprecated, SourceFile, Synthetic)
     *      -D do not compare debug attribute (LocalVariableTable, LineNumberTable)
     *      -u ignore unknown attributes
     *      -V turn off class validation
     *      -c class, compare a single class
     *      -b compares all entries bitwise (fastest)
     *      -l directory or log file name
     * </pre>
     */
    public static void main(String args[]) {
        Globals.getInstance();
        if (args == null || args.length < 2) {
            syntax();
            System.exit(1);
        }
        String refJarFileName = null;
        String cmpJarFileName = null;
        String specificClass = null;
        String logDirFileName = null;

        for (int i = 0; i < args.length; i++) {
            if (i == 0) {
                refJarFileName = args[0];
                continue;
            }
            if (i == 1) {
                cmpJarFileName = args[1];
                continue;
            }

            if (args[i].startsWith("-O")) {
                Globals.setCheckJarClassOrdering(true);
            }

            if (args[i].startsWith("-b")) {
                Globals.setBitWiseClassCompare(true);
            }

            if (args[i].startsWith("-C")) {
                Globals.setIgnoreCompileAttributes(true);
            }

            if (args[i].startsWith("-D")) {
                Globals.setIgnoreDebugAttributes(true);
            }

            if (args[i].startsWith("-V")) {
                Globals.setValidateClass(false);
            }

            if (args[i].startsWith("-c")) {
                i++;
                specificClass = args[i].trim();
            }

            if (args[i].startsWith("-u")) {
                i++;
                Globals.setIgnoreUnknownAttributes(true);
            }

            if (args[i].startsWith("-l")) {
                i++;
                logDirFileName = args[i].trim();
            }
        }

        Globals.openLog(logDirFileName);

        File refJarFile = new File(refJarFileName);
        File cmpJarFile = new File(cmpJarFileName);

        String f1 = refJarFile.getAbsoluteFile().toString();
        String f2 = cmpJarFile.getAbsoluteFile().toString();

        System.out.println("LogFile:" + Globals.getLogFileName());
        System.out.println("Reference JAR:" + f1);
        System.out.println("Compared  JAR:" + f2);

        Globals.println("LogFile:" + Globals.getLogFileName());
        Globals.println("Reference JAR:" + f1);
        Globals.println("Compared  JAR:" + f2);

        Globals.println("Ignore Compile Attributes:" + Globals.ignoreCompileAttributes());
        Globals.println("Ignore Debug   Attributes:" + Globals.ignoreDebugAttributes());
        Globals.println("Ignore Unknown Attributes:" + Globals.ignoreUnknownAttributes());
        Globals.println("Class ordering check:" + Globals.checkJarClassOrdering());
        Globals.println("Class validation check:" + Globals.validateClass());
        Globals.println("Bit-wise compare:" + Globals.bitWiseClassCompare());
        Globals.println("ClassName:" + ((specificClass == null) ? "ALL" : specificClass));

        if (specificClass == null && Globals.bitWiseClassCompare() == true) {
            JarFileCompare.jarCompare(refJarFileName, cmpJarFileName);
        } else {
            try {
                ClassCompare.compareClass(refJarFileName, cmpJarFileName, specificClass);
            } catch (Exception e) {
                Globals.log("Exception " + e);
                throw new RuntimeException(e);
            }
        }

        if (Globals.getErrors() > 0) {
            System.out.println("FAIL");
            Globals.println("FAIL");
            System.exit(Globals.getErrors());
        }

        System.out.println("PASS");
        Globals.println("PASS");
        System.exit(Globals.getErrors());
    }
}
