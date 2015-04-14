/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify correctnes of the random generator from Utility.java
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @run driver RandomGeneratorTest SAME_SEED
 * @run driver RandomGeneratorTest NO_SEED
 * @run driver RandomGeneratorTest DIFFERENT_SEED
 */

import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The test verifies correctness of work {@link com.oracle.java.testlibrary.Utils#getRandomInstance()}.
 * Test works in three modes: same seed provided, no seed provided and
 * different seed provided. In the first case the test expects that all random numbers
 * will be repeated in all next iterations. For other two modes test expects that
 * randomly generated numbers differ from original.
 */
public class RandomGeneratorTest {
    private static final String SEED_VM_OPTION = "-D" + Utils.SEED_PROPERTY_NAME + "=";

    public static void main( String[] args) throws Throwable {
        if (args.length == 0) {
            throw new Error("TESTBUG: No test mode provided.");
        }
        SeedOption seedOpt = SeedOption.valueOf(args[0]);
        List<String> jvmArgs = new ArrayList<String>();
        String optStr = seedOpt.getSeedOption();
        if (optStr != null) {
            jvmArgs.add(optStr);
        }
        jvmArgs.add(RandomRunner.class.getName());
        String[] cmdLineArgs = jvmArgs.toArray(new String[jvmArgs.size()]);
        String etalon = ProcessTools.executeTestJvm(cmdLineArgs).getStdout().trim();
        seedOpt.verify(etalon, cmdLineArgs);
    }

    /**
     * The utility enum helps to generate an appropriate string that should be passed
     * to the command line depends on the testing mode. It is also responsible for the result
     * validation.
     */
    private enum SeedOption {
        SAME_SEED {
            @Override
            public String getSeedOption() {
                return SEED_VM_OPTION + Utils.SEED;
            }

            @Override
            protected boolean isOutputExpected(String orig, String output) {
                return output.equals(orig);
            }
        },
        DIFFERENT_SEED {
            @Override
            public String getSeedOption() {
                return SEED_VM_OPTION + Utils.getRandomInstance().nextLong();
            }

            @Override
            public void verify(String orig, String[] cmdLine) {
                cmdLine[0] = getSeedOption();
                super.verify(orig, cmdLine);
            }
        },
        NO_SEED {
            @Override
            public String getSeedOption() {
                return null;
            }
        };

        /**
         * Generates a string to be added as a command line argument.
         * It contains "-D" prefix, system property name, '=' sign
         * and seed value.
         * @return command line argument
         */
        public abstract String getSeedOption();

        protected boolean isOutputExpected(String orig, String output) {
            return !output.equals(orig);
        }

        /**
         * Verifies that the original output meets expectations
         * depending on the test mode. It compares the output of second execution
         * to original one.
         * @param orig original output
         * @param cmdLine command line arguments
         * @throws Throwable - Throws an exception in case test failure.
         */
        public void verify(String orig, String[] cmdLine) {
            String lastLineOrig = getLastLine(orig);
            String lastLine;
            try {
                lastLine = getLastLine(ProcessTools.executeTestJvm(cmdLine).getStdout().trim());
            } catch (Throwable t) {
                throw new Error("TESTBUG: Unexpedted exception during jvm execution.", t);
            }
            if (!isOutputExpected(lastLineOrig, lastLine)) {
                    throw new AssertionError("Unexpected random number sequence for mode: " + this.name());
            }
        }

        private static String getLastLine(String output) {
            return output.substring(output.lastIndexOf(Utils.NEW_LINE)).trim();
        }
    }

    /**
     * The helper class generates several random numbers
     * and prints them out.
     */
    public static class RandomRunner {
        private static final int COUNT = 10;
        public static void main(String[] args) {
            StringBuilder sb = new StringBuilder();
            Random rng = Utils.getRandomInstance();
            for (int i = 0; i < COUNT; i++) {
                sb.append(rng.nextLong()).append(' ');
            }
            System.out.println(sb.toString());
        }
    }
}
