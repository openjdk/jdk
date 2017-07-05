/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6245733
 * @summary synopsis: rmid's registry's list operation doesn't include
 * activation system
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @run main/othervm/timeout=240 LookupActivationSystem
 */

import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.io.Serializable;

public class LookupActivationSystem implements Remote, Serializable {

    private static final String NAME = ActivationSystem.class.getName();

    public static void main(String[] args) throws Exception {

        System.out.println("\nRegression test for bug 6245733\n");

        RMID rmid = null;

        try {
            RMID.removeLog();
            rmid = RMID.createRMID();
            rmid.start();

            System.err.println("look up activation system");
            Registry rmidRegistry =
                LocateRegistry.getRegistry(rmid.getPort());
            ActivationSystem system = (ActivationSystem)
                rmidRegistry.lookup(NAME);

            if (system instanceof ActivationSystem) {
                System.err.println("test1 passed: lookup succeeded");
            }

            System.err.println("get list of rmid internal registry");
            String[] list = rmidRegistry.list();
            if (list.length == 1 && list[0].equals(NAME)) {
                System.err.println(
                    "test2 passed: activation system found in list");
            } else {
                throw new RuntimeException("test2 FAILED");
            }

            try {
                rmidRegistry.bind(NAME, new LookupActivationSystem());
                throw new RuntimeException("test3 FAILED: bind succeeded!");
            } catch (ServerException e) {
                if (e.getCause() instanceof AccessException) {
                    System.err.println(
                        "test3 passed: binding ActivationSystem " +
                        "failed as expected");
                } else {
                    throw new RuntimeException(
                        "test3 FAILED: incorrect cause: " + e.getCause());
                }

            }

            try {
                rmidRegistry.rebind(NAME, new LookupActivationSystem());
                throw new RuntimeException("test4 FAILED: rebind succeeded!");
            } catch (ServerException e) {
                if (e.getCause() instanceof AccessException) {
                    System.err.println(
                        "test4 passed: rebinding ActivationSystem " +
                        "failed as expected");
                } else {
                    throw new RuntimeException(
                        "test4 FAILED: incorrect cause: " + e.getCause());
                }
            }

            try {
                rmidRegistry.unbind(NAME);
                throw new RuntimeException("test4 FAILED: unbind succeeded!");
            } catch (ServerException e) {
                if (e.getCause() instanceof AccessException) {
                    System.err.println(
                        "test5 passed: unbinding ActivationSystem " +
                        "failed as expected");
                } else {
                    throw new RuntimeException(
                        "test5 FAILED: incorrect cause: " + e.getCause());
                }
            }


        } finally {
            rmid.cleanup();
        }
    }
}
