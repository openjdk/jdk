/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Properties;

public class Verifier {

    public static void main(String[] args) throws Exception {
        if (args.length == 0)
            throw new RuntimeException("Test bug, nothing to verify");
        for (String hsLogFile : args) {
            verify(hsLogFile);
        }
    }

    private static void verify(String hsLogFile) throws Exception {
        System.out.println("Verifying " + hsLogFile);

        final Properties expectedProperties = new Properties();
        final FileReader reader = new FileReader(hsLogFile + ".verify.properties");
        expectedProperties.load(reader);
        reader.close();

        int fullMatchCnt = 0;
        int suspectCnt = 0;
        final String intrinsicId = expectedProperties.getProperty("intrinsic.name");
        final String prefix = "<intrinsic id='";
        final String prefixWithId = prefix + intrinsicId + "'";
        final int expectedCount = Integer.parseInt(expectedProperties.getProperty("intrinsic.expectedCount"));

        BufferedReader r = new BufferedReader(new FileReader(hsLogFile));
        String s;
        while ((s = r.readLine()) != null) {
            if (s.startsWith(prefix)) {
                if (s.startsWith(prefixWithId)) {
                    fullMatchCnt++;
                } else {
                    suspectCnt++;
                    System.out.println("WARNING: Other intrinsic detected " + s);
                }
            }
        }
        r.close();

        System.out.println("Intrinsic " + intrinsicId + " verification, expected: " + expectedCount + ", matched: " + fullMatchCnt + ", suspected: " + suspectCnt);
        if (expectedCount != fullMatchCnt)
            throw new RuntimeException("Unexpected count of intrinsic  " + prefixWithId + " expected:" + expectedCount + ", matched: " + fullMatchCnt + ", suspected: " + suspectCnt);
    }
}
