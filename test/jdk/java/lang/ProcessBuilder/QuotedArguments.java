/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Azul Systems, Inc. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/*
 * @test
 * @bug 8282008
 * @requires (os.family == "windows")
 * @summary Incorrect handling of quoted arguments in ProcessBuilder
 * @run main QuotedArguments
 */

public class QuotedArguments {

    public static void main(String[] args) {

        class CheckCase {
            public String data, expected, allowAmbiguousCommands;
            CheckCase(String s0, String s1, String s2) {
                data = s0;
                expected = s1;
                allowAmbiguousCommands =s2;
            }
        };

        CheckCase[] cases = {
                new CheckCase("\"C:\\Program Files\\Git\\\"", "C:\\Program Files\\Git\\", "true"),
                new CheckCase("\"C:\\Program Files\\Git\\\"", "C:\\Program Files\\Git\\", "false")
        };

        ProcessBuilder pb = new ProcessBuilder();

        try {
            for (CheckCase c : cases) {
                System.setProperty("jdk.lang.Process.allowAmbiguousCommands", c.allowAmbiguousCommands);

                pb.command("echo", c.data);
                Process p = pb.start();

                String out = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();
                boolean isEqual = c.expected.equals(out);

                try {
                    System.out.println("Process exited with code: " + p.waitFor());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println("\nInput    : " + c.data +
                                   "\nOutput   : " + out +
                                   "\nExpected : " + c.expected);

                if (!isEqual) {
                    System.out.println("Test failed.");
                    throw new AssertionError("Test failed.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Test succeeded.");
    }
}