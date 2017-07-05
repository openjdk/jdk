/*
 * Copyright 1998-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package build.tools.jdwpgen;

import java.io.*;

class Main {

    static String specSource;
    static boolean genDebug = true;

    static void usage() {
        System.err.println();
        System.err.println(
            "java Main <spec_input> <options>...");
        System.err.println();
        System.err.println("Options:");
        System.err.println("-doc <doc_output>");
        System.err.println("-jdi <java_output>");
        System.err.println("-include <include_file_output>");
    }

    public static void main(String args[]) throws IOException {
        Reader reader = null;
        PrintWriter doc = null;
        PrintWriter jdi = null;
        PrintWriter include = null;

        // Parse arguments
        for (int i = 0 ; i < args.length ; ++i) {
            String arg = args[i];
            if (arg.startsWith("-")) {
                String fn = args[++i];
                if (arg.equals("-doc")) {
                    doc = new PrintWriter(new FileWriter(fn));
                } else if (arg.equals("-jdi")) {
                    jdi = new PrintWriter(new FileWriter(fn));
                } else if (arg.equals("-include")) {
                    include = new PrintWriter(new FileWriter(fn));
                } else {
                    System.err.println("Invalid option: " + arg);
                    usage();
                    return;
                }
            } else {
                specSource = arg;
                reader = new FileReader(specSource);
            }
        }
        if (reader == null) {
            System.err.println("<spec_input> must be specified");
            usage();
            return;
        }

        Parse parse = new Parse(reader);
        RootNode root = parse.items();
        root.parentAndExtractComments();
        root.prune();
        root.constrain(new Context());
        if (doc != null) {
            root.document(doc);
            doc.close();
        }
        if (jdi != null) {
            root.genJava(jdi, 0);
            jdi.close();
        }
        if (include != null) {
            root.genCInclude(include);
            include.close();
        }
    }
}
