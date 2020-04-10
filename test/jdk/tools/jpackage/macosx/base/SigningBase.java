/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;

import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Executor;

public class SigningBase {

    public static String DEV_NAME = "jpackage.openjdk.java.net";
    public static String APP_CERT
            = "Developer ID Application: " + DEV_NAME;
    public static String INSTALLER_CERT
            = "Developer ID Installer: " + DEV_NAME;
    public static String KEYCHAIN = "jpackagerTest.keychain";

    private static void checkString(List<String> result, String lookupString) {
        TKit.assertTextStream(lookupString).predicate(
                (line, what) -> line.trim().equals(what)).apply(result.stream());
    }

    private static List<String> codesignResult(Path target, boolean signed) {
        int exitCode = signed ? 0 : 1;
        List<String> result = new Executor()
                .setExecutable("codesign")
                .addArguments("--verify", "--deep", "--strict", "--verbose=2",
                        target.toString())
                .saveOutput()
                .execute(exitCode).getOutput();

        return result;
    }

    private static void verifyCodesignResult(List<String> result, Path target,
            boolean signed) {
        result.stream().forEachOrdered(TKit::trace);
        if (signed) {
            String lookupString = target.toString() + ": valid on disk";
            checkString(result, lookupString);
            lookupString = target.toString() + ": satisfies its Designated Requirement";
            checkString(result, lookupString);
        } else {
            String lookupString = target.toString()
                    + ": code object is not signed at all";
            checkString(result, lookupString);
        }
    }

    private static List<String> spctlResult(Path target, String type) {
        List<String> result = new Executor()
                .setExecutable("/usr/sbin/spctl")
                .addArguments("-vvv", "--assess", "--type", type,
                        target.toString())
                // on Catalina, the exit code can be 3, meaning not notarized
                .saveOutput()
                .executeWithoutExitCodeCheck()
                .getOutput();

        return result;
    }

    private static void verifySpctlResult(List<String> result, Path target, String type) {
        result.stream().forEachOrdered(TKit::trace);
        String lookupString;
/* on Catalina, spctl may return 3 and say:
 *   target: rejected
 *   source=Unnotarized DEV_NAME
 * so we must skip these two checks
        lookupString = target.toString() + ": accepted";
        checkString(result, lookupString);
        lookupString = "source=" + DEV_NAME;
        checkString(result, lookupString);
 */
        if (type.equals("install")) {
            lookupString = "origin=" + INSTALLER_CERT;
        } else {
            lookupString = "origin=" + APP_CERT;
        }
        checkString(result, lookupString);
    }

    private static List<String> pkgutilResult(Path target) {
        List<String> result = new Executor()
                .setExecutable("/usr/sbin/pkgutil")
                .addArguments("--check-signature",
                        target.toString())
                .executeAndGetOutput();

        return result;
    }

    private static void verifyPkgutilResult(List<String> result) {
        result.stream().forEachOrdered(TKit::trace);
        String lookupString = "Status: signed by a certificate trusted for current user";
        checkString(result, lookupString);
        lookupString = "1. " + INSTALLER_CERT;
        checkString(result, lookupString);
    }

    public static void verifyCodesign(Path target, boolean signed) {
        List<String> result = codesignResult(target, signed);
        verifyCodesignResult(result, target, signed);
    }

    public static void verifySpctl(Path target, String type) {
        List<String> result = spctlResult(target, type);
        verifySpctlResult(result, target, type);
    }

    public static void verifyPkgutil(Path target) {
        List<String> result = pkgutilResult(target);
        verifyPkgutilResult(result);
    }

}
