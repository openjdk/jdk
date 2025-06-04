/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
  * @bug  7160252 7197662
  * @key intermittent
  * @summary Checks if events are delivered to a listener
  *          when a child node is added or removed
  * @run main/othervm -Djava.util.prefs.userRoot=. AddNodeChangeListener
  */

import java.util.prefs.*;

public class AddNodeChangeListener {

    private static final int SLEEP_ITRS = 10;
    private static final String N2_STR = "N2";
    private static boolean failed = false;
    private static Preferences userRoot, N2;
    private static NodeChangeListenerAdd ncla;

    public static void main(String[] args)
             throws BackingStoreException, InterruptedException {
        try {
            userRoot = Preferences.userRoot();
            // Make sure test node is not present before test
            clearPrefs();

            ncla = new NodeChangeListenerAdd();
            userRoot.addNodeChangeListener(ncla);
            //Should initiate a node added event
            addNode();
            // Should not initiate a node added event
            addNode();
            //Should initate a child removed event
            removeNode();

            if (failed) {
                throw new RuntimeException("Failed");
            }
        } finally {
            // Make sure test node is not present after the test
            clearPrefs();
        }
    }

    private static void addNode()
            throws BackingStoreException, InterruptedException {
        N2 = userRoot.node(N2_STR);
        userRoot.flush();
        checkAndSleep(1, "addNode");
    }

    private static void removeNode()
            throws BackingStoreException, InterruptedException {
        N2.removeNode();
        userRoot.flush();
        checkAndSleep(0, "removeNode");
    }

    /* Check for the expected value in the listener (1 after addNode(), 0 after removeNode()).
     * Sleep a few extra times in a loop, if needed, for debugging purposes, to
     * see if the event gets delivered late.
     */
    private static void checkAndSleep(int expectedListenerVal, String methodName) throws InterruptedException {
        int expectedItr = -1; // iteration in which the expected value was retrieved from the listener, or -1 if never
        for (int i = 0; i < SLEEP_ITRS; i++) {
            System.out.print(methodName + " sleep iteration " + i + "...");
            Thread.sleep(3000);
            System.out.println("done.");
            if (ncla.getAddNumber() == expectedListenerVal) {
                expectedItr = i;
                break;
            }
        }

        if (expectedItr == 0) {
            System.out.println(methodName + " test passed");
        } else {
            failed = true;
            if (expectedItr == -1) {
                System.out.println("Failed in " + methodName + " - change listener never notified");
            } else {
                System.out.println("Failed in " + methodName + " - listener notified on iteration " + expectedItr);
            }
        }
    }

    /* Check if the node already exists in userRoot, and remove it if so. */
    private static void clearPrefs() throws BackingStoreException {
        if (userRoot.nodeExists(N2_STR)) {
            System.out.println("Node " + N2_STR + " already/still exists; clearing");
            Preferences clearNode = userRoot.node(N2_STR);
            userRoot.flush();
            clearNode.removeNode();
            userRoot.flush();
            if (userRoot.nodeExists(N2_STR)) {
                throw new RuntimeException("Unable to clear pre-existing node." + (failed ? " Also, the test failed" : ""));
            }
        }
    }

    private static class NodeChangeListenerAdd implements NodeChangeListener {
        private int totalNode = 0;

        @Override
        public void childAdded(NodeChangeEvent evt) {
            totalNode++;
        }

        @Override
        public void childRemoved(NodeChangeEvent evt) {
            totalNode--;
        }

        public int getAddNumber(){
            return totalNode;
        }
    }
}
