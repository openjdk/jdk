/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4290704
  @summary Test use of AWTEventListenerProxyTest class
*/

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.AWTEventListenerProxy;
import java.util.EventListener;

public class AWTEventListenerProxyTest {
    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            Toolkit tk = Toolkit.getDefaultToolkit();
            if ("sun.awt.X11.XToolkit".equals(tk.getClass().getName())) {
                System.out.println("Do not test for XAWT Toolkit.");
                System.out.println("Passing automatically.");
                return;
            }

            // check that if no listeners added, returns a 0-length array,
            // not null
            AWTEventListener[] array1 = tk.getAWTEventListeners();
            if (array1 == null || array1.length != 0) {
                System.out.println("[Empty array test failed!!]");
                throw new RuntimeException("Test failed -" +
                    " didn't return 0-sized array");
            }
            System.out.println("[Empty array test passed]");

            // simple add/get test
            DumbListener dl1 = new DumbListener();
            final long dl1MASK = AWTEvent.ACTION_EVENT_MASK;
            tk.addAWTEventListener(dl1, dl1MASK);

            array1 = tk.getAWTEventListeners();
            if (array1 == null || array1.length != 1) {
                System.out.println("[Simple add/get test failed!!]");
                throw new RuntimeException("Test failed - didn't " +
                    "return array of 1");
            }
            AWTEventListenerProxy dp1 = (AWTEventListenerProxy) array1[0];
            EventListener getdl1 = dp1.getListener();
            if (getdl1 != dl1) {
                System.out.println("[Simple add/get test failed - " +
                    "wrong listener!!]");
                throw new RuntimeException("Test failed - wrong " +
                    "listener in proxy");
            }

            long getmask = dp1.getEventMask();
            if (getmask != dl1MASK) {
                System.out.println("[Simple add/get test failed - " +
                    "wrong mask!!]");
                throw new RuntimeException("Test failed - wrong mask in proxy");
            }
            System.out.println("[Simple add/get test passed]");

            // add the same listener inside a proxy, with a different mask
            // should get back one listener, with the ORed mask
            final long dl2MASK = AWTEvent.CONTAINER_EVENT_MASK;
            AWTEventListenerProxy newp = new AWTEventListenerProxy(dl2MASK,
                dl1);
            tk.addAWTEventListener(newp, dl2MASK);
            array1 = tk.getAWTEventListeners();
            if (array1.length != 1) {
                System.out.println("[Proxy add/get test failed!!]");
                throw new RuntimeException("Test failed - added proxy, " +
                    "but didn't return array of 1");
            }
            dp1 = (AWTEventListenerProxy) array1[0];
            getdl1 = dp1.getListener();
            if (getdl1 != dl1) {
                System.out.println("[Proxy add/get test " +
                    "failed - wrong listener!!]");
                throw new RuntimeException("Test failed - added proxy, " +
                    "wrong listener in proxy");
            }
            getmask = dp1.getEventMask();
            if (getmask != (dl1MASK | dl2MASK)) {
                System.out.println("[Proxy add/get test failed - " +
                    "wrong mask!!]");
                throw new RuntimeException("Test failed - added proxy, " +
                    "wrong mask in proxy");
            }
            System.out.println("[Proxy add/get test passed]");

            // add some other listener
            DumbListener dl3 = new DumbListener();
            final long dl3MASK = AWTEvent.FOCUS_EVENT_MASK;
            tk.addAWTEventListener(dl3, dl3MASK);

            // test getting with a mask for a listener already added
            array1 = tk.getAWTEventListeners(dl1MASK);
            if (array1.length != 1) {
                System.out.println("[Get w/ mask test failed!! - " +
                    "not 1 listener!]");
                throw new RuntimeException("Test failed - tried to " +
                    "get w/ mask");
            }
            dp1 = (AWTEventListenerProxy) array1[0];
            getdl1 = dp1.getListener();
            if (getdl1 != dl1) {
                System.out.println("[Get w/ mask test failed!! - " +
                    "wrong listener]");
                throw new RuntimeException("Test failed - tried to get " +
                    "w/ mask, wrong listener in proxy");
            }
            System.out.println("[Get w/ mask test passed]");

            // test getting with a mask for a listener not added
            array1 = tk.getAWTEventListeners(AWTEvent.MOUSE_EVENT_MASK);
            if (array1.length != 0) {
                System.out.println("[Get w/ mask test 2 failed!! - " +
                    "not 0 listeners!]");
                throw new RuntimeException("Test failed - tried to get " +
                    "w/ mask 2");
            }
            System.out.println("[Get w/ mask test 2 passed]");


            // test getting with a compound mask for a listener already added
            array1 = tk.getAWTEventListeners(dl1MASK | dl2MASK);
            if (array1.length != 1) {
                System.out.println("[Get w/ compound mask test failed!! - " +
                    "not 1 listeners!]");
                throw new RuntimeException("Test failed - tried to get w/ 2 " +
                    "ORed masks");
            }
            dp1 = (AWTEventListenerProxy) array1[0];
            getdl1 = dp1.getListener();
            if (getdl1 != dl1) {
                System.out.println("[Get w/ compound mask test failed!! - " +
                    "wrong listener]");
                throw new RuntimeException("Test failed - tried to get w/ " +
                    "compound mask, wrong listener in proxy");
            }
            System.out.println("[Get w/ compound mask test passed]");
        });
    }

    public static class DumbListener implements AWTEventListener {
        public DumbListener() {}
        public void eventDispatched(AWTEvent e) {}
    }
}
