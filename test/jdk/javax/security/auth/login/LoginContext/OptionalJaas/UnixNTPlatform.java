/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8215916
 * @summary This Sample application attempts to authenticate a user
 * and reports whether or not the authentication was successful.
 * @run main/othervm -Djava.security.debug=logincontext UnixNTPlatform
 */
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class UnixNTPlatform {

    private static final String UNIX_MODULE = "UnixLoginModule";
    private static final String NT_MODULE = "NTLoginModule";

    public static void main(String[] args) throws Exception {
        login("cross-platform",
                UNIX_MODULE, "optional", "debug=true",
                NT_MODULE, "optional", "debug=true");
    }

    static void login(String test, String... conf) throws Exception {
        System.out.println("Testing " + test + "...");

        StringBuilder sb = new StringBuilder();
        sb.append("hello {\n");
        for (int i = 0; i < conf.length; i += 3) {
            sb.append("    com.sun.security.auth.module.")
                    .append(conf[i]).append(" ")
                    .append(conf[i + 1]).append(" ")
                    .append(conf[i + 2]).append(";\n");
        }
        sb.append("};\n");
        Files.write(Paths.get(test), sb.toString().getBytes());

        // Must be called. Configuration has an internal static field.
        Configuration.setConfiguration(null);
        System.setProperty("java.security.auth.login.config", test);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(stream);
        System.setErr(ps);

        try {
            LoginContext lc = new LoginContext("hello");
            lc.login();
            System.out.println(lc.getSubject());
            lc.logout();
        } catch (LoginException e) {
            System.out.println("Retrieving exception information");
        }

        byte[] byes = stream.toByteArray();
        String s = new String(byes);
        if (!s.contains("Failed in attempt to import the underlying"))
           throw new RuntimeException();
        else
           System.out.printf("-- call stack is -- %n%s%n", s);
        }
    }
}
