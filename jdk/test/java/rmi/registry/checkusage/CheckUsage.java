/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4151966
 * @summary rmiregistry error message obsure; internationalize rmiregistry
 * @author Laird Dornin
 *
 * @library ../../testlibrary
 * @build TestLibrary JavaVM
 * @run main/othervm CheckUsage
 */

import java.io.ByteArrayOutputStream;

/**
 * Make sure that the rmiregistry prints out a correct usage statement
 * when run with an incorrect command line; test written to conform to
 * new tighter bug fix/regression test guidelines.
 */
public class CheckUsage {
    public static void main(String[] args) {

        System.err.println("\nregression test for 4151966\n");

        JavaVM registryVM = null;

        try {
            // make sure the registry exits with a proper usage statement
            ByteArrayOutputStream berr = new ByteArrayOutputStream();

            // run a VM to start the registry
            registryVM = new JavaVM("sun.rmi.registry.RegistryImpl",
                                    "", "foo",
                                    System.out, berr);
            System.err.println("starting registry");
            registryVM.start();

            // wait for registry exit
            System.err.println(" registry exited with status: " +
                               registryVM.getVM().waitFor());
            try {
                Thread.sleep(7000);
            } catch (InterruptedException ie) {
            }

            String usage = new String(berr.toByteArray());

            System.err.println("rmiregistry usage: " + usage);

            if (usage.indexOf("-J") < 0) {
                TestLibrary.bomb("rmiregistry has incorrect usage statement");
            } else {
                System.err.println("test passed");
            }
        } catch (Exception e) {
            TestLibrary.bomb(e);
        } finally {
            registryVM.destroy();
            registryVM = null;
        }
    }
}
