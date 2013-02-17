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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/* System properties:
 * NumberOfTests, TestMode, and TestCaseNum are mutually exclusive
 * TestSeed will be used only with NumberOfTests or TestMode, otherwise it will be ignored
 * -DNumberOfTests=[0 to 2^20+2^11+1]
 * -DTestMode=[FULL|DEFAULT]
 * -DTestSeed=[seedNumber]
 * -DTestCaseNum=[0 to 2^20+2^11+1]
 */
public class TestCaseGenerator {
    // Total number of tests to be run
    int numberOfTests = -1;
    //Single test case
    int testCaseNum = -1;
    //Seed used to generate test cases
    int testSeed;

    int maxTestNum;
    Random randNum;

    // used in getNextTestCase
    int curTestNum;
    int testCompletedCount;
    HashSet<Integer> uniqueTestSet;

    static final int DEFAULT_TEST_COUNT = 250;

    /*
     *  Get parameter values from command line to set numberOfTests, testCaseNum,
     *  and testSeed
     */
    public TestCaseGenerator(int maxTestNum) {
        this.maxTestNum = maxTestNum;

        // Set values for variables based on input from command line

        // TestMode system property
        String testModeVal = System.getProperty("TestMode");
        if(testModeVal != null && !testModeVal.isEmpty()) {
            switch (testModeVal.toUpperCase()) {
            case "FULL":
                numberOfTests = maxTestNum;
                break;
            case "DEFAULT":
                numberOfTests = DEFAULT_TEST_COUNT;
                break;
            default:
                System.out.println("Invalid property value " + testModeVal +
                        " for numberOfTests. Possible range: 0 to " +
                        maxTestNum + ". Ignoring property");
                numberOfTests = -1;
            }
        }

        // NumberOfTests system property
        String numTestsStr = System.getProperty("NumberOfTests");
        if(numTestsStr != null && !numTestsStr.isEmpty()) {
            int numTests = -1;
            try {
                numTests = Integer.parseInt(numTestsStr);
                if (numTests < 0 || numTests > maxTestNum) {
                    throw new NumberFormatException();
                }
            } catch(NumberFormatException nfe) {
                System.out.println("Invalid NumberOfTests property value " +
                        numTestsStr + ". Possible range: 0 to " + maxTestNum +
                        "Reset to default: " + DEFAULT_TEST_COUNT);
                numTests = DEFAULT_TEST_COUNT;
            }

            if (numberOfTests != -1 && numTests != -1) {
                System.out.println("TestMode and NumberOfTests cannot be set together. Ignoring TestMode.");
            }
            numberOfTests = numTests;
        }

        // TestSeed system property
        String seedVal = System.getProperty("TestSeed");
        if(seedVal != null && !seedVal.isEmpty()) {
            try {
                testSeed = Integer.parseInt(seedVal);
            } catch(NumberFormatException nfe) {
                Random srand = new Random();
                testSeed = srand.nextInt();
            }
        } else {
            Random srand = new Random();
            testSeed = srand.nextInt();
        }

        // TestCaseNum system property
        String testNumStr = System.getProperty("TestCaseNum");
        if(testNumStr != null && !testNumStr.isEmpty()) {
            try {
                testCaseNum = Integer.parseInt(testNumStr);
                if (testCaseNum < 0 || testCaseNum > maxTestNum) {
                    throw new NumberFormatException();
                }
            } catch(NumberFormatException nfe) {
                System.out.println("Invalid TestCaseNumber property value " +
                        testNumStr + ". Possible value in range: 0 to " +
                        maxTestNum + ". Defaulting to last test case.");
                testCaseNum = maxTestNum;
            }

            if ( numberOfTests != -1) {
                System.out.println("TestMode or NumberOfTests cannot be set along with TestCaseNum. Ignoring TestCaseNumber.");
                testCaseNum = -1;
            }
        }

        if (numberOfTests == -1 && testCaseNum == -1) {
            numberOfTests = DEFAULT_TEST_COUNT;
            System.out.println("Setting TestMode to default, will run " + numberOfTests + "tests.");
        }

        /*
         *  By this point in code, we will have:
         *  - testSeed: as per TestSeed or a Random one
         *  - numberOfTests to run or -1 to denote not set
         *  - testCaseNum to run or -1 to denote not set
         */

        /*
         * If numberOfTests = maxTestNum, all tests are to be run,
         * so no randNum will be required
         */
        if (numberOfTests != -1 && numberOfTests < maxTestNum) {
            System.out.println("Seed = " + testSeed);
            randNum = new Random(testSeed);
            uniqueTestSet = new HashSet<>();
        }

        testCompletedCount = 0;
        // to be used to keep sequential count when running all tests
        curTestNum = 0;
    }

    /*
     * returns next test case number to run
     * returns -1 when there are no more tests to run
     */
    public int getNextTestCase() {
        if (testCaseNum != -1) {
            int nextTC = testCaseNum;
            testCaseNum = -1;
            return nextTC;
        }
        if (++testCompletedCount <= numberOfTests) {
            if (numberOfTests == maxTestNum) {
                //all the tests need to be run, so just return
                //next test case sequentially
                return curTestNum++;
            } else {
                int nextTC = -1;
                // Ensuring unique test are run
                while(!uniqueTestSet.add(nextTC = randNum.nextInt(maxTestNum))) {
                }
                return nextTC;
            }
        }
        return -1;
    }
}
