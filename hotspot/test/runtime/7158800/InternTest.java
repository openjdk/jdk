/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 7158800
 * @run shell/timeout=400 Test7158800.sh
 * @summary This test performs poorly if alternate hashing isn't used for
 * string table.
 * The timeout is handled by the shell file (which kills the process)
 */
import java.util.*;
import java.io.*;

public class InternTest {
    public static void main (String args[]) throws Exception {
        final String badStringsFilename = "badstrings.txt";

        if (args.length == 0 || (!args[0].equals("bad") && !args[0].equals("normal"))) {
            System.out.println("Usage:  java InternTest [normal|bad]");
            System.exit(1);
        }

        FileInputStream fstream = new FileInputStream(badStringsFilename);
        DataInputStream in = new DataInputStream(fstream);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String toIntern, toDiscard;
        int count = 0;
        long current = 0L;
        long last = System.currentTimeMillis();

        if (args[0].equals("bad")) {
            while ((toIntern = br.readLine()) != null) {
                toDiscard = new String((new Integer((int)(Math.random() * Integer.MAX_VALUE))).toString());
                toIntern.intern();
                count++;
                if (count % 10000 == 0 && count != 0) {
                    current = System.currentTimeMillis();
                    System.out.println(new Date(current) + ": interned " + count + " 0-hash strings - last 10000 took " + ((float)(current - last))/1000 + "s (" + ((float)(current - last))/10000000 + "s per String)");
                    last = current;
                }
            }
        }
        if (args[0].equals("normal")) {
            while ((toDiscard = br.readLine()) != null) { // do the same read from the file to try and make the test fair
                toIntern = new String((new Integer((int)(Math.random() * Integer.MAX_VALUE))).toString());
                toIntern.intern();
                count++;
                if (count % 10000 == 0 && count != 0) {
                    current = System.currentTimeMillis();
                    System.out.println(new Date(current) + ": interned " + count + " normal strings - last 10000 took " + ((float)(current - last))/1000 + "s (" + ((float)(current - last))/10000000 + "s per String)");
                    last = current;
                }
            }
        }
        in.close();
    }
}


