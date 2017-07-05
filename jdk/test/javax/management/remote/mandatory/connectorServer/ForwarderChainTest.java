/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.NoSuchElementException;
import java.util.Random;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.remote.IdentityMBeanServerForwarder;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;

/*
 * @test
 * @bug 6218920
 * @summary Tests manipulation of MBeanServerForwarder chains.
 * @author Eamonn McManus
 */
import javax.management.remote.rmi.RMIConnectorServer;

public class ForwarderChainTest {
    private static final TestMBeanServerForwarder[] forwarders =
            new TestMBeanServerForwarder[10];
    static {
        for (int i = 0; i < forwarders.length; i++)
            forwarders[i] = new TestMBeanServerForwarder(i);
    }

    private static class TestMBeanServerForwarder
            extends IdentityMBeanServerForwarder {
        private final int index;
        volatile int defaultDomainCount;

        TestMBeanServerForwarder(int index) {
            this.index = index;
        }

        @Override
        public String getDefaultDomain() {
            defaultDomainCount++;
            return super.getDefaultDomain();
        }

        @Override
        public String toString() {
            return "forwarders[" + index + "]";
        }
    }

    private static String failure;

    public static void main(String[] args) throws Exception {

        System.out.println("===Test with newly created, unattached server===");

        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///");
        JMXConnectorServer cs = new RMIConnectorServer(url, null);
        test(cs, null);

        System.out.println("===Test with server attached to MBS===");
        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        cs = new RMIConnectorServer(url, null, mbs);
        test(cs, mbs);

        System.out.println("===Remove any leftover forwarders===");
        MBeanServerForwarder systemMBSF = cs.getSystemMBeanServerForwarder();
        // Real code would just do systemMBSF.setMBeanServer(mbs).
        while (true) {
            MBeanServer xmbs = systemMBSF.getMBeanServer();
            if (!(xmbs instanceof MBeanServerForwarder))
                break;
            cs.removeMBeanServerForwarder((MBeanServerForwarder) xmbs);
        }
        expectChain(cs, "U", mbs);

        System.out.println("===Ensure forwarders are called===");
        cs.setMBeanServerForwarder(forwarders[0]);
        systemMBSF.setMBeanServer(forwarders[1]);
        forwarders[1].setMBeanServer(forwarders[0]);
        expectChain(cs, "1U0", mbs);
        cs.start();
        if (forwarders[0].defaultDomainCount != 0 ||
                forwarders[1].defaultDomainCount != 0) {
            fail("defaultDomainCount not zero");
        }
        JMXServiceURL addr = cs.getAddress();
        JMXConnector cc = JMXConnectorFactory.connect(addr);
        MBeanServerConnection mbsc = cc.getMBeanServerConnection();
        mbsc.getDefaultDomain();
        cc.close();
        cs.stop();
        for (boolean system : new boolean[] {false, true}) {
            TestMBeanServerForwarder mbsf = system ? forwarders[1] : forwarders[0];
            if (mbsf.defaultDomainCount != 1) {
                fail((system ? "System" : "User") + " forwarder called " +
                        mbsf.defaultDomainCount + " times");
            }
        }

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    private static void test(JMXConnectorServer cs, MBeanServer end) {
        // A newly-created connector server might have system forwarders,
        // so get rid of those.
        MBeanServerForwarder systemMBSF = cs.getSystemMBeanServerForwarder();
        systemMBSF.setMBeanServer(cs.getMBeanServer());

        expectChain(cs, "U", end);

        System.out.println("Add a user forwarder");
        cs.setMBeanServerForwarder(forwarders[0]);
        expectChain(cs, "U0", end);

        System.out.println("Add another user forwarder");
        cs.setMBeanServerForwarder(forwarders[1]);
        expectChain(cs, "U10", end);

        System.out.println("Add a system forwarder");
        forwarders[2].setMBeanServer(systemMBSF.getMBeanServer());
        systemMBSF.setMBeanServer(forwarders[2]);
        expectChain(cs, "2U10", end);

        System.out.println("Add another user forwarder");
        cs.setMBeanServerForwarder(forwarders[3]);
        expectChain(cs, "2U310", end);

        System.out.println("Add another system forwarder");
        forwarders[4].setMBeanServer(systemMBSF.getMBeanServer());
        systemMBSF.setMBeanServer(forwarders[4]);
        expectChain(cs, "42U310", end);

        System.out.println("Remove the first user forwarder");
        cs.removeMBeanServerForwarder(forwarders[3]);
        expectChain(cs, "42U10", end);

        System.out.println("Remove the last user forwarder");
        cs.removeMBeanServerForwarder(forwarders[0]);
        expectChain(cs, "42U1", end);

        System.out.println("Remove the first system forwarder");
        cs.removeMBeanServerForwarder(forwarders[4]);
        expectChain(cs, "2U1", end);

        System.out.println("Remove the last system forwarder");
        cs.removeMBeanServerForwarder(forwarders[2]);
        expectChain(cs, "U1", end);

        System.out.println("Remove the last forwarder");
        cs.removeMBeanServerForwarder(forwarders[1]);
        expectChain(cs, "U", end);

        System.out.println("---Doing random manipulations---");
        // In this loop we pick one of the forwarders at random each time.
        // If it is already in the chain, then we remove it.  If it is not
        // in the chain, then we do one of three things: try to remove it
        // (expecting an exception); add it to the user chain; or add it
        // to the system chain.
        // A subtle point is that if there is no MBeanServer then
        // cs.setMBeanServerForwarder(mbsf) does not change mbsf.getMBeanServer().
        // Since we're recycling a random forwarder[i], we explicitly
        // call mbsf.setMBeanServer(null) in this case.
        String chain = "U";
        Random r = new Random();
        for (int i = 0; i < 50; i++) {
            int fwdi = r.nextInt(10);
            MBeanServerForwarder mbsf = forwarders[fwdi];
            char c = (char) ('0' + fwdi);
            int ci = chain.indexOf(c);
            if (ci >= 0) {
                System.out.println("Remove " + c);
                cs.removeMBeanServerForwarder(mbsf);
                chain = chain.substring(0, ci) + chain.substring(ci + 1);
            } else {
                switch (r.nextInt(3)) {
                    case 0: { // try to remove it
                        try {
                            System.out.println("Try to remove absent " + c);
                            cs.removeMBeanServerForwarder(mbsf);
                            fail("Remove succeeded but should not have");
                            return;
                        } catch (NoSuchElementException e) {
                        }
                        break;
                    }
                    case 1: { // add it to the user chain
                        System.out.println("Add " + c + " to user chain");
                        if (cs.getMBeanServer() == null)
                            mbsf.setMBeanServer(null);
                        cs.setMBeanServerForwarder(mbsf);
                        int postu = chain.indexOf('U') + 1;
                        chain = chain.substring(0, postu) + c +
                                chain.substring(postu);
                        break;
                    }
                    case 2: { // add it to the system chain
                        System.out.println("Add " + c + " to system chain");
                        mbsf.setMBeanServer(systemMBSF.getMBeanServer());
                        systemMBSF.setMBeanServer(mbsf);
                        chain = c + chain;
                        break;
                    }
                }
            }
            expectChain(cs, chain, end);
        }
    }

    /*
     * Check that the forwarder chain has the expected contents.  The forwarders
     * are encoded as a string.  For example, "12U34" means that the system
     * chain contains forwarders[1] followed by forwarders[2], and the user
     * chain contains forwarders[3] followed by forwarders[4].  Since the
     * user chain is attached to the end of the system chain, another way to
     * look at this is that the U marks the transition from one to the other.
     *
     * After traversing the chains, we should be pointing at "end".
     */
    private static void expectChain(
            JMXConnectorServer cs, String chain, MBeanServer end) {
        System.out.println("...expected chain: " + chain);
        MBeanServer curr = cs.getSystemMBeanServerForwarder().getMBeanServer();
        int i = 0;
        while (i < chain.length()) {
            char c = chain.charAt(i);
            if (c == 'U') {
                if (cs.getMBeanServer() != curr) {
                    fail("User chain should have started here: " + curr);
                    return;
                }
            } else {
                int fwdi = c - '0';
                MBeanServerForwarder forwarder = forwarders[fwdi];
                if (curr != forwarder) {
                    fail("Expected forwarder " + c + " here: " + curr);
                    return;
                }
                curr = ((MBeanServerForwarder) curr).getMBeanServer();
            }
            i++;
        }
        if (curr != end) {
            fail("End of chain is " + curr + ", should be " + end);
            return;
        }
        System.out.println("...OK");
    }

    private static void fail(String msg) {
        System.out.println("FAILED: " + msg);
        failure = msg;
    }
}
