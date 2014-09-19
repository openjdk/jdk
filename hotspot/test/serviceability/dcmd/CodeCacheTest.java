/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test CodeCacheTest
 * @bug 8054889
 * @build DcmdUtil CodeCacheTest
 * @run main CodeCacheTest
 * @summary Test of diagnostic command Compiler.codecache
 */

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeCacheTest {

    /**
     * This test calls Jcmd (diagnostic command tool) Compiler.codecache and then parses the output,
     * making sure that all number look ok
     *
     *
     * Expected output:
     *
     * CodeCache: size=245760Kb used=4680Kb max_used=4680Kb free=241079Kb
     * bounds [0x00007f5bd9000000, 0x00007f5bd94a0000, 0x00007f5be8000000]
     * total_blobs=575 nmethods=69 adapters=423
     * compilation: enabled
     */

    static Pattern line1 = Pattern.compile("CodeCache: size=(\\p{Digit}*)Kb used=(\\p{Digit}*)Kb max_used=(\\p{Digit}*)Kb free=(\\p{Digit}*)Kb");
    static Pattern line2 = Pattern.compile(" bounds \\[0x(\\p{XDigit}*), 0x(\\p{XDigit}*), 0x(\\p{XDigit}*)\\]");
    static Pattern line3 = Pattern.compile(" total_blobs=(\\p{Digit}*) nmethods=(\\p{Digit}*) adapters=(\\p{Digit}*)");
    static Pattern line4 = Pattern.compile(" compilation: (\\w*)");

    public static void main(String arg[]) throws Exception {

        // Get output from dcmd (diagnostic command)
        String result = DcmdUtil.executeDcmd("Compiler.codecache");
        BufferedReader r = new BufferedReader(new StringReader(result));

        // Validate first line
        String line;
        line = r.readLine();
        Matcher m = line1.matcher(line);
        if (m.matches()) {
            for(int i = 1; i <= 4; i++) {
                int val = Integer.parseInt(m.group(i));
                if (val < 0) {
                    throw new Exception("Failed parsing dcmd codecache output");
                }
            }
        } else {
            throw new Exception("Regexp 1 failed");
        }

        // Validate second line
        line = r.readLine();
        m = line2.matcher(line);
        if (m.matches()) {
            String start = m.group(1);
            String mark  = m.group(2);
            String top   = m.group(3);

            // Lexical compare of hex numbers to check that they look sane.
            if (start.compareTo(mark) > 1) {
                throw new Exception("Failed parsing dcmd codecache output");
            }
            if (mark.compareTo(top) > 1) {
                throw new Exception("Failed parsing dcmd codecache output");
            }
        } else {
            throw new Exception("Regexp 2 failed line: " + line);
        }

        // Validate third line
        line = r.readLine();
        m = line3.matcher(line);
        if (m.matches()) {
            int blobs = Integer.parseInt(m.group(1));
            if (blobs <= 0) {
                throw new Exception("Failed parsing dcmd codecache output");
            }
            int nmethods = Integer.parseInt(m.group(2));
            if (nmethods <= 0) {
                throw new Exception("Failed parsing dcmd codecache output");
            }
            int adapters = Integer.parseInt(m.group(3));
            if (adapters <= 0) {
                throw new Exception("Failed parsing dcmd codecache output");
            }
            if (blobs < (nmethods + adapters)) {
                throw new Exception("Failed parsing dcmd codecache output");
            }
        } else {
            throw new Exception("Regexp 3 failed");
        }

        // Validate fourth line
        line = r.readLine();
        m = line4.matcher(line);
        if (m.matches()) {
            if (!m.group(1).equals("enabled")) {
                throw new Exception("Invalid message: '" + m.group(1) + "'");
            }
        } else {
            throw new Exception("Regexp 4 failed");
        }
    }
}
