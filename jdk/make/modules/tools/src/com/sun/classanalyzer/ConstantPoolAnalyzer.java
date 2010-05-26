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
 */

package com.sun.classanalyzer;

/**
 *
 * @author Mandy Chung
 */
public class ConstantPoolAnalyzer {
    public static void main(String[] args) throws Exception {
        String jdkhome = null;

        // process arguments
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.equals("-jdkhome")) {
                if (i < args.length) {
                    jdkhome = args[i++];
                } else {
                    usage();
                }
            }
        }
        if (jdkhome == null) {
            usage();
        }
        ClassPath.setJDKHome(jdkhome);
        ClassPath.parseAllClassFiles();
    }

    private static void usage() {
        System.out.println("Usage: ConstantPoolAnalyzer <options>");
        System.out.println("Options: ");
        System.out.println("\t-jdkhome <JDK home> where all jars will be parsed");
        System.out.println("\t-cpath <classpath> where classes and jars will be parsed");
        System.exit(-1);
    }
}
