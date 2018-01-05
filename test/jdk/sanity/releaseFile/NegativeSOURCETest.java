
/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8192837
 * @summary Test to verify release file does not contain closed repo info if it's open bundle
 * @run main NegativeSOURCETest
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;

public class NegativeSOURCETest {

    NegativeSOURCETest(String dataFile) {
        // Read data files
        readFile(dataFile);
    }

    private void readFile(String fileName) {
        String fishForSOURCE = null;

        File file = new File(fileName);

        // open the stream to read in for Entries
        try (BufferedReader buffRead =
            new BufferedReader(new FileReader(fileName))) {

            // this is the string read
            String readIn;

            // let's read some strings!
            while ((readIn = buffRead.readLine()) != null) {
                readIn = readIn.trim();

                // throw out blank lines
                if (readIn.length() == 0)
                    continue;

                // grab SOURCE line
                if (readIn.startsWith("SOURCE=")) {
                    fishForSOURCE = readIn;
                    break;
                }
            }
        } catch (FileNotFoundException fileExcept) {
            throw new RuntimeException("File " + fileName +
                                       " not found reading data!", fileExcept);
        } catch (IOException ioExcept) {
            throw new RuntimeException("Unexpected problem reading data!",
                                       ioExcept);
        }

        // was SOURCE even found?
        if (fishForSOURCE == null) {
            throw new RuntimeException("SOURCE line was not found!");
        } else {
            // OK it was found, did it have closed/open in it?
            if (fishForSOURCE.contains("closed") || fishForSOURCE.contains("open")) {
                System.out.println("The offending string: " + fishForSOURCE);
                throw new RuntimeException("The test failed, closed/open found!");
            }
        }

        // Everything was fine
        System.out.println("The test passed!");
    }

    public static void main(String args[]) {
        String jdkPath = System.getProperty("test.jdk");
        String runtime = System.getProperty("java.runtime.name");

        System.out.println("JDK Path : " + jdkPath);
        System.out.println("Runtime Name : " + runtime);

        if (runtime.contains("OpenJDK"))
            new NegativeSOURCETest(jdkPath + "/release");
        else
            System.out.println("Test skipped: not an OpenJDK build.");
    }
}
