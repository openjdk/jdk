/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.addtorestrictedpkgs;

import java.io.*;

/**
 * Adds additional packages to the package.access and package.definition
 * security properties.
 */
public class AddToRestrictedPkgs {

    private static final String PKG_ACC = "package.access";
    private static final String PKG_DEF = "package.definition";
    private static final int PKG_ACC_INDENT = 15;
    private static final int PKG_DEF_INDENT = 19;

    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.err.println("Usage: java AddToRestrictedPkgs " +
                               "[input java.security file name] " +
                               "[output java.security file name] " +
                               "[packages ...]");
            System.exit(1);
        }

        try (FileReader fr = new FileReader(args[0]);
             BufferedReader br = new BufferedReader(fr);
             FileWriter fw = new FileWriter(args[1]);
             BufferedWriter bw = new BufferedWriter(fw))
        {
            // parse the file line-by-line, looking for pkg access properties
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith(PKG_ACC)) {
                    writePackages(br, bw, line, PKG_ACC_INDENT, args);
                } else if (line.startsWith(PKG_DEF)) {
                    writePackages(br, bw, line, PKG_DEF_INDENT, args);
                } else {
                    writeLine(bw, line);
                }
                line = br.readLine();
            }
            bw.flush();
        }
    }

    private static void writePackages(BufferedReader br, BufferedWriter bw,
                                      String line, int numSpaces,
                                      String[] args) throws IOException {
        // parse property until EOL, not including line breaks
        while (line.endsWith("\\")) {
            writeLine(bw, line);
            line = br.readLine();
        }
        // append comma and line-break to last package
        writeLine(bw, line + ",\\");
        // add new packages, one per line
        for (int i = 2; i < args.length - 1; i++) {
            indent(bw, numSpaces);
            writeLine(bw, args[i] + ",\\");
        }
        indent(bw, numSpaces);
        writeLine(bw, args[args.length - 1]);
    }

    private static void writeLine(BufferedWriter bw, String line)
        throws IOException
    {
        bw.write(line);
        bw.newLine();
    }

    private static void indent(BufferedWriter bw, int numSpaces)
        throws IOException
    {
        for (int i = 0; i < numSpaces; i++) {
            bw.append(' ');
        }
    }
}
