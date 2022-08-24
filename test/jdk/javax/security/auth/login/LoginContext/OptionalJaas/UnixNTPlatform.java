/*
 * Copyright (c) 2022, Red Hat, Inc.
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
 * @summary This test case attempts to verify whether call stack trace is
 * printed when JAAS optional login fails when debug is true.
 * @run main/othervm -Djava.security.debug=logincontext UnixNTPlatform
 */
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class UnixNTPlatform {

    public static void main(String[] args) throws Exception {
        System.out.println("Testing cross-platform");

        String config = """
                        hello {
                        com.sun.security.auth.module.UnixLoginModule optional debug=true;
                        com.sun.security.auth.module.NTLoginModule optional debug=true;
                        };
                        """;

        System.out.println("config is : \n"+config);
        Files.writeString(Path.of("cross-platform"), config.toString());

        System.setProperty("java.security.auth.login.config", "cross-platform");

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintStream ps = System.err;
        System.setErr(new PrintStream(new PrintStream(stream)));

        try {
            LoginContext lc = new LoginContext("hello");
            lc.login();
            System.out.println(lc.getSubject());
            lc.logout();
        } catch (LoginException e) {
            System.out.println("Retrieving exception information");
        } finally {
            System.setErr(ps);
        }

        byte[] byes = stream.toByteArray();
        String s = new String(byes);
        System.out.printf("-- call stack is -- %n%s%n", s);
        if (!s.contains("Failed in attempt to import the underlying")) {
           throw new RuntimeException();
        }
    }
}
