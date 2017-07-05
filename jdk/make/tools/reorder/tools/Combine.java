/*
 * Copyright 2000 Sun Microsystems, Inc.  All Rights Reserved.
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


// Append files to one another without duplicating any lines.

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;

public class Combine {

    private static HashMap map = new HashMap(10007);

    private static void appendFile(String fileName, boolean keep) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));

            // Read a line at a time.  If the line does not appear in the
            // hashmap, print it and add it to the hashmap, so that it will
            // not be repeated.

        lineLoop:
            while (true) {
                String line = br.readLine();
                if (line == null)
                    break;
                if (keep || !map.containsKey(line)) {
                    System.out.println(line);
                    map.put(line,line);
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    public static void main(String[] args) {

        if (args.length < 2) {
            System.err.println("Usage:  java Combine  file1  file2  ...");
            System.exit(2);
        }

        for (int i = 0; i < args.length; ++i)
            appendFile(args[i], i == 0);
    }
}
