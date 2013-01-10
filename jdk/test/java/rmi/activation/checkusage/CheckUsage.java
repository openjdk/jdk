/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4259564
 * @summary RMID's usage message is incomplete and inconsistent with other tools
 *
 * @library ../../testlibrary
 * @build TestLibrary JavaVM
 * @run main/othervm CheckUsage
 */

import java.io.ByteArrayOutputStream;

/**
 * Make sure that rmid prints out a correct usage statement when run with an
 * incorrect command line.
 */
public class CheckUsage {
    public static void main(String[] args) {
        try {
            ByteArrayOutputStream berr = new ByteArrayOutputStream();

            // create rmid with incorrect command line args
            JavaVM rmidVM = new JavaVM("sun.rmi.server.Activation", "", "foo",
                                       System.out, berr);
            System.err.println("starting rmid");

            // run the subprocess and wait for it to exit
            int rmidVMExitStatus = rmidVM.execute();
            System.err.println("rmid exited with status: " +
                               rmidVMExitStatus);

            String usage = new String(berr.toByteArray());

            System.err.println("rmid usage: " + usage);

            if (usage.indexOf("-J<runtime flag>") < 0) {
                TestLibrary.bomb("rmid has incorrect usage message");
            } else {
                System.err.println("test passed");
            }
        } catch (Exception e) {
            TestLibrary.bomb(e);
        }
    }
}
