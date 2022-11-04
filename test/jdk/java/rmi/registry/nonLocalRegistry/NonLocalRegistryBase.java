/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.rmi.AccessException;

public class NonLocalRegistryBase {
    static final String instructions =
            "This is a manual test that requires rmiregistry run on a different host"
                    + ". Login or ssh to a different host, install the JDK under test, "
                    + "build and invoke:\n\n"
                    + "$JDK_HOME/bin/rmiregistry"
                    + "\n\nRegistry service is run in the background without any "
                    + "output. Enter the hostname or IP address of the different "
                    + "host below and continue the test.";
    static final String message = "Enter the hostname or IP address here and submit:";
    static final int TIMEOUT_MS = 3600000;

    /**
     * Check the exception chain for the expected AccessException and message.
     * @param ex the exception from the remote invocation.
     */
     static void assertIsAccessException(Throwable ex) {
        Throwable t = ex;
        while (!(t instanceof AccessException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof AccessException) {
            String msg = t.getMessage();
            int asIndex = msg.indexOf("Registry");
            int rrIndex = msg.indexOf("Registry.Registry");     // Obsolete error text
            int disallowIndex = msg.indexOf("disallowed");
            int nonLocalHostIndex = msg.indexOf("non-local host");
            if (asIndex < 0 ||
                    rrIndex != -1 ||
                    disallowIndex < 0 ||
                    nonLocalHostIndex < 0 ) {
                throw new RuntimeException("exception message is malformed", t);
            }
            System.out.printf("Found expected AccessException: %s%n%n", t);
        } else {
            throw new RuntimeException("AccessException did not occur when expected", ex);
        }
    }
}
