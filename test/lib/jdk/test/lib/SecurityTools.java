/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SecurityTools {

    public static final String NO_ALIAS = null;

    // keytool

    public static OutputAnalyzer keytool(List<String> options)
            throws Throwable {

        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("keytool")
                .addVMArg("-Duser.language=en")
                .addVMArg("-Duser.country=US");
        for (String option : options) {
            if (option.startsWith("-J")) {
                launcher.addVMArg(option.substring(2));
            } else {
                launcher.addToolArg(option);
            }
        }
        return ProcessTools.executeCommand(launcher.getCommand());
    }

    public static OutputAnalyzer keytool(String options) throws Throwable {
        return keytool(options.split("\\s+"));
    }

    public static OutputAnalyzer keytool(String... options) throws Throwable {
        return keytool(List.of(options));
    }

    // jarsigner

    public static OutputAnalyzer jarsigner(String jar, String alias,
            List<String> options) throws Throwable {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jarsigner")
                .addVMArg("-Duser.language=en")
                .addVMArg("-Duser.country=US");
        for (String option : options) {
            if (option.startsWith("-J")) {
                launcher.addVMArg(option.substring(2));
            } else {
                launcher.addToolArg(option);
            }
        }
        launcher.addToolArg(jar);
        if (alias != null) {
            launcher.addToolArg(alias);
        }
        return ProcessTools.executeCommand(launcher.getCommand());
    }

    public static OutputAnalyzer jarsigner(String jar, String alias,
            String options) throws Throwable {

        return jarsigner(jar, alias, options.split("\\s+"));
    }

    public static OutputAnalyzer jarsigner(String jar, String alias,
            String... options) throws Throwable {

        return jarsigner(jar, alias, List.of(options));
    }

    public static OutputAnalyzer sign(String jar, String alias, String... options)
            throws Throwable {

        return jarsigner(jar, alias,
                mergeOptions("-J-Djava.security.egd=file:/dev/./urandom", options));
    }

    public static OutputAnalyzer verify(String jar, String... options)
            throws Throwable {

        return jarsigner(jar, NO_ALIAS, mergeOptions("-verify", options));
    }

    // helper methods

    private static List<String> mergeOptions(
            String firstOption, String... secondPart) {

        return mergeOptions(List.of(firstOption), secondPart);
    }

    private static List<String> mergeOptions(
            List<String> firstPart, String... secondPart) {

        List<String> options = new ArrayList<>(firstPart);
        Collections.addAll(options, secondPart);
        return options;
    }
}

