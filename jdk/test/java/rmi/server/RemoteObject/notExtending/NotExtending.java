/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4099660 4102938
 * @summary Remote classes not extending RemoteObject should be able to
 *          implement hashCode() and equals() methods so that instances
 *          can be successfully compared to RemoteObject instances
 *          (specifically: stubs) that contain the instance's RemoteRef.
 * @author Peter Jones
 *
 * @build NotExtending_Stub NotExtending_Skel
 * @run main/othervm/timeout=240 NotExtending
 */


import java.rmi.*;
import java.rmi.server.*;

public class NotExtending implements Remote {

    /** remote stub for this server instance */
    private Remote stub;
    /** value of stub's hash code */
    private int hashValue;
    /** true if the hashValue field has been initialized */
    private boolean hashValueInitialized = false;

    public NotExtending() throws RemoteException {
        stub = UnicastRemoteObject.exportObject(this);
        setHashValue(stub.hashCode());
    }

    private void setHashValue(int value) {
        hashValue = value;
        hashValueInitialized = true;
    }

    public int hashCode() {
        /*
         * Test fails with a RuntimeException if the hashCode() method is
         * called (during the export procedure) before the correct hash
         * value has been initialized.
         */
        if (!hashValueInitialized) {
            throw new RuntimeException(
                "hashCode() invoked before hashValue initialized");
        }
        return hashValue;
    }

    public boolean equals(Object obj) {
        return stub.equals(obj);
    }

    public static void main(String[] args) throws Exception {
        /*
         * The following line is required with the JDK 1.2 VM so that the
         * VM can exit gracefully when this test completes.  Otherwise, the
         * conservative garbage collector will find a handle to the server
         * object on the native stack and not clear the weak reference to
         * it in the RMI runtime's object table.
         */
        Object dummy = new Object();

        NotExtending server;
        try {
            /*
             * Verify that hashCode() is not invoked before it is
             * initialized.  Tests bugid 4102938.
             */
            server = new NotExtending();
            System.err.println("Server exported without invoking hashCode().");

            /*
             * Verify that passing stub to server's equals() method
             * returns true.
             */
            if (server.equals(server.stub)) {
                System.err.println(
                    "Passing stub to server's equals() method succeeded.");
            } else {
                throw new RuntimeException(
                    "passing stub to server's equals() method failed");
            }

            /*
             * Verify that passing server to stub's equals() method
             * returns true.  Tests bugid 4099660.
             */
            if (server.stub.equals(server)) {
                System.err.println(
                    "Passing server to stub's equals() method succeeded.");
            } else {
                throw new RuntimeException(
                    "passing server to stub's equals() method failed");
            }

        } finally {
            server = null;
            flushCachedRefs();
        }
    }

    /**
     * Force desparate garbage collection so that all sun.misc.Ref instances
     * will be cleared.
     *
     * This method is required with the JDK 1.1.x RMI runtime so that the
     * VM can exit gracefully when this test completes.  See bugid 4006356.
     */
    public static void flushCachedRefs() {
        java.util.Vector chain = new java.util.Vector();
        try {
            while (true) {
                int[] hungry = new int[65536];
                chain.addElement(hungry);
            }
        } catch (OutOfMemoryError e) {
        }
    }
}
