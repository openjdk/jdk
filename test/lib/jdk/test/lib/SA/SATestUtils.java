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
package jdk.test.lib.SA;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import java.util.concurrent.TimeUnit;

public class SATestUtils {

    public static boolean canAddPrivileges()
       throws IOException, InterruptedException {
       List<String> echoList = new ArrayList<String>();
       echoList.add("sudo");
       echoList.add("-E");
       echoList.add("/bin/echo");
       echoList.add("'Checking for sudo'");
       ProcessBuilder pb = new ProcessBuilder(echoList);
       Process echoProcess = pb.start();
       if (echoProcess.waitFor(60, TimeUnit.SECONDS) == false) {
           // 'sudo' has been added but we don't have a no-password
           // entry for the user in the /etc/sudoers list. Could
           // have timed out waiting for the password. Skip the
           // test if there is a timeout here.
           System.out.println("Timed out waiting for the password to be entered.");
           echoProcess.destroyForcibly();
           return false;
       }
       if (echoProcess.exitValue() == 0) {
           return true;
       }
       java.io.InputStream is = echoProcess.getErrorStream();
       String err = new String(is.readAllBytes());
       System.out.println(err);
       // 'sudo' has been added but we don't have a no-password
       // entry for the user in the /etc/sudoers list. Check for
       // the sudo error message and skip the test.
       if (err.contains("no tty present") ||
           err.contains("a password is required")) {
           return false;
       } else {
           throw new Error("Unknown Error from 'sudo'");
       }
    }

    public static List<String> addPrivileges(List<String> cmdStringList)
        throws IOException {
        Asserts.assertTrue(Platform.isOSX());

        System.out.println("Adding 'sudo -E' to the command.");
        List<String> outStringList = new ArrayList<String>();
        outStringList.add("sudo");
        outStringList.add("-E");
        outStringList.addAll(cmdStringList);
        return outStringList;
    }
}
