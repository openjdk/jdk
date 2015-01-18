/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 */

import java.io.File;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.registry.LocateRegistry;

/**
 * Class of test utility/library methods related to Activatable
 * objects.
 */
public class ActivationLibrary {
    private static void mesg(Object mesg) {
        System.err.println("ACTIVATION_LIBRARY: " + mesg.toString());
    }

    /**
     * Deactivate an activated Activatable
     */
    public static void deactivate(Remote remote,
                                  ActivationID id) {
        final long POLLTIME_MS = 100L;
        final long DEACTIVATE_TIME_MS = 30_000L;

        long startTime = System.currentTimeMillis();
        long deadline = TestLibrary.computeDeadline(startTime, DEACTIVATE_TIME_MS);

        while (System.currentTimeMillis() < deadline) {
            try {
                if (Activatable.inactive(id) == true) {
                    mesg("inactive successful");
                    return;
                } else {
                    Thread.sleep(POLLTIME_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mesg("Thread interrupted while trying to deactivate activatable. Exiting deactivation");
                return;
            } catch (Exception e) {
                try {
                    // forcibly unexport the object
                    mesg("Unexpected exception. Have to forcibly unexport the object." +
                         " Exception was :");
                    e.printStackTrace();
                    Activatable.unexportObject(remote, true);
                } catch (NoSuchObjectException ex) {
                }
                return;
            }
        }

        mesg("unable to inactivate after " +
            (System.currentTimeMillis() - startTime) + "ms.");
        mesg("unexporting object forcibly instead");

        try {
            Activatable.unexportObject(remote, true);
        } catch (NoSuchObjectException e) {
        }
    }
}
