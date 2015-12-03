/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package sampleapi;

import java.io.File;

import sampleapi.SampleApi.Fault;

public class SampleApiDefaultRunner {

    public static final String MSG_DUP_OUTDIR =
        "SampleApi: duplicated outdir detected: ";
    public static final String MSG_USE_FIRST =
        "           will use first occurance: ";
    public static final String MSG_INVAL_OUTDIR =
        "SampleApi: outdir is not valid: ";
    public static final String MSG_CANNOT_GEN =
        "SampleApi: cannot generate output: ";
    public static final String MSG_WRONG_OPTION =
        "SampleApi: incorrect option: ";
    public static final String MSG_USE_HELP =
        "           use -? for help";

    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            printHelp();
            System.exit(1);
        }

        String outDirName = "";

        boolean isOutDirSet = false;
        boolean isHelpPrinted = false;
        for (String arg : args) {
            Option option = new Option(arg);
            switch (option.getOptionName()) {
                case "-?":
                case "-h":
                case "--help":
                    if (!isHelpPrinted) {
                        printHelp();
                        isHelpPrinted = true;
                    }
                    break;
                case "-o":
                case "--outdir":
                    if (!isOutDirSet) {
                        outDirName = option.getOptionValue();
                        isOutDirSet = true;
                    } else {
                        System.err.println(MSG_DUP_OUTDIR + option.getOptionValue());
                        System.err.println(MSG_USE_FIRST + outDirName);
                    }
                    break;
                default:
                    System.err.println(MSG_WRONG_OPTION + arg);
                    System.err.println(MSG_USE_HELP);
                    break;
            }

        }

        if (!isOutDirSet) {
            System.exit(1);
        }

        if (outDirName.length() == 0) {
            System.err.println(MSG_INVAL_OUTDIR + outDirName);
            System.exit(1);
        }

        File outDir = new File(outDirName);
        outDir.mkdirs();
        SampleApi apiGen = new SampleApi();

        try {
            apiGen.generate(outDir);
        } catch (Fault e) {
            System.err.println(MSG_CANNOT_GEN + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printHelp() {
        System.out.println("SampleApi:");
        System.out.println("    options: [-?|-h|--help] [-o:<dir>|--outdir:<dir>]");
        System.out.println("    -?|-h|--help             - print help");
        System.out.println("    -o:<dir>|--outdir:<dir>  - set <dir> to generate output");
    }

    private static class Option {

        private String optionName;
        private String optionValue;

        public Option(String arg) {
            int delimPos = arg.indexOf(':');

            if (delimPos == -1) {
                optionName = arg;
                optionValue = "";
            } else {
                optionName = arg.substring(0, delimPos);
                optionValue = arg.substring(delimPos + 1, arg.length());
            }
        }

        public String getOptionName() {
            return optionName;
        }

        public String getOptionValue() {
            return optionValue;
        }
    }
}
