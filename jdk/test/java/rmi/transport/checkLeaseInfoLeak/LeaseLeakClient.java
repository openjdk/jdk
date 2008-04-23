/*
 * Copyright 1998 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;

public class LeaseLeakClient {
    public static void main(String args[]) {
        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

        try {
            LeaseLeak leaseLeak = null;

            // put a reference on a remote object.
            Registry registry =
                java.rmi.registry.LocateRegistry.getRegistry(
                    TestLibrary.REGISTRY_PORT);
            leaseLeak = (LeaseLeak) registry.lookup("/LeaseLeak");
            leaseLeak.ping();

        } catch(Exception e) {
            System.err.println("LeaseLeakClient Error: "+e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }
}
