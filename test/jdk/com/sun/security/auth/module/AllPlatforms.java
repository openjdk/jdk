/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8039951
 * @summary com.sun.security.auth.module missing classes on some platforms
 * @modules java.base/jdk.internal.util
 * @run main/othervm AllPlatforms
 */
import jdk.internal.util.OperatingSystem;

import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.security.auth.login.FailedLoginException;

public class AllPlatforms {

    private static final String UNIX_MODULE = "UnixLoginModule";
    private static final String NT_MODULE = "NTLoginModule";

    public static void main(String[] args) throws Exception {
        login(true, "cross-platform",
                UNIX_MODULE, "optional",
                NT_MODULE, "optional");
        login(OperatingSystem.isWindows(), "windows", NT_MODULE, "required");
        login(!OperatingSystem.isWindows(), "unix", UNIX_MODULE, "required");
    }

    static void login(boolean shouldSucceed, String test, String... conf) throws Exception {
        System.out.println("Testing " + test + "...");

        StringBuilder sb = new StringBuilder();
        sb.append("hello {\n");
        for (int i = 0; i < conf.length; i += 2) {
            sb.append("    com.sun.security.auth.module.")
                    .append(conf[i]).append(" ")
                    .append(conf[i + 1]).append(";\n");
        }
        sb.append("};\n");
        Files.write(Paths.get(test), sb.toString().getBytes());

        // Must be called. Configuration has an internal static field.
        Configuration.setConfiguration(null);
        System.setProperty("java.security.auth.login.config", test);

        try {
            LoginContext lc = new LoginContext("hello");
            lc.login();
            System.out.println(lc.getSubject());
            lc.logout();
            if (!shouldSucceed) {
                throw new RuntimeException("Should not succeed");
            }
        } catch (FailedLoginException e) {
            if (shouldSucceed) {
                throw new RuntimeException("Should succeed");
            }
            // This exception can occur in other platform module than the running one.
            if (e.getMessage().startsWith("Failed in attempt to import")) {
                System.out.println("Expected Exception found.");
                e.printStackTrace(System.out);
            } else {
                throw new RuntimeException("Unexpected error", e);
            }
        }
    }
}
