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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.ClientContext;
import javax.management.MBeanServer;
import javax.management.event.EventClientDelegate;
import javax.management.remote.JMXConnectorServer;

/*
 * @test
 * @bug 6663757
 * @summary Tests standard MBeanServerForwarders introduced by connector server
 * options.
 * @author Eamonn McManus
 */
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;

public class StandardForwardersTest {
    private static String failure;

    private static class Forwarder {
        private final String attribute;
        private final boolean defaultEnabled;
        private final Class<?> expectedClass;

        public Forwarder(String attribute, boolean defaultEnabled,
                         Class<?> expectedClass) {
            this.attribute = attribute;
            this.defaultEnabled = defaultEnabled;
            this.expectedClass = expectedClass;
        }
    }

    private static enum Status {DISABLED, ENABLED, DEFAULT}

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        MBeanServerForwarder ctxFwd = ClientContext.newContextForwarder(mbs, null);
        Forwarder ctx = new Forwarder(
                JMXConnectorServer.CONTEXT_FORWARDER, true, ctxFwd.getClass());

        MBeanServerForwarder locFwd =
                ClientContext.newLocalizeMBeanInfoForwarder(mbs);
        Forwarder loc = new Forwarder(
                JMXConnectorServer.LOCALIZE_MBEAN_INFO_FORWARDER, false,
                locFwd.getClass());

        MBeanServerForwarder ecdFwd =
                EventClientDelegate.newForwarder(mbs, null);
        Forwarder ecd = new Forwarder(
                JMXConnectorServer.EVENT_CLIENT_DELEGATE_FORWARDER, true,
                ecdFwd.getClass());

        Forwarder[] forwarders = {ctx, loc, ecd};

        // Now go through every combination of forwarders.  Each forwarder
        // may be explicitly enabled, explicitly disabled, or left to its
        // default value.
        int nStatus = Status.values().length;
        int limit = (int) Math.pow(nStatus, forwarders.length);
        for (int i = 0; i < limit; i++) {
            Status[] status = new Status[forwarders.length];
            int ii = i;
            for (int j = 0; j < status.length; j++) {
                status[j] = Status.values()[ii % nStatus];
                ii /= nStatus;
            }
            Map<String, String> env = new HashMap<String, String>();
            String test = "";
            for (int j = 0; j < status.length; j++) {
                if (!test.equals(""))
                    test += "; ";
                test += forwarders[j].attribute;
                switch (status[j]) {
                case DISABLED:
                    test += "=false";
                    env.put(forwarders[j].attribute, "false");
                    break;
                case ENABLED:
                    test += "=true";
                    env.put(forwarders[j].attribute, "true");
                    break;
                case DEFAULT:
                    test += "=default(" + forwarders[j].defaultEnabled + ")";
                    break;
                }
            }
            boolean consistent = isConsistent(env);
            test += "; (" + (consistent ? "" : "in") + "consistent)";
            System.out.println(test);
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///");
            try {
                JMXConnectorServer cs =
                    JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);
                if (!consistent) {
                    fail("Inconsistent attributes should have been rejected " +
                            "but were not");
                }
                checkForwarders(cs, forwarders, status);
            } catch (IllegalArgumentException e) {
                if (consistent) {
                    fail("Consistent attributes provoked IllegalArgumentException");
                    e.printStackTrace(System.out);
                }
            }
        }

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception(failure);
    }

    // Check that the classes of the forwarders in the system chain correspond
    // to what we expect given the options we have passed.  This check is a bit
    // superficial in the sense that a forwarder might be for example a
    // SingleMBeanForwarderHandler but that doesn't prove that it is the
    // right Single MBean.  Nevertheless the test should expose any severe
    // wrongness.
    //
    // The check here makes some assumptions that could become untrue in the
    // future. First, it assumes that the forwarders that are added have
    // exactly the classes that are in the Forwarder[] array. So for example
    // the forwarder for CONTEXT_FORWARDER must be of the same class as an
    // explicit call to ClientContext.newContextForwarder. The spec doesn't
    // require that - it only requires that the forwarder have the same
    // behaviour. The second assumption is that the connector server doesn't
    // add any forwarders of its own into the system chain, and again the spec
    // doesn't disallow that.
    private static void checkForwarders(
            JMXConnectorServer cs, Forwarder[] forwarders, Status[] status) {
        List<Class<?>> expectedClasses = new ArrayList<Class<?>>();
        for (int i = 0; i < forwarders.length; i++) {
            if (status[i] == Status.ENABLED ||
                    (status[i] == Status.DEFAULT && forwarders[i].defaultEnabled))
                expectedClasses.add(forwarders[i].expectedClass);
        }
        MBeanServer stop = cs.getMBeanServer();
        List<Class<?>> foundClasses = new ArrayList<Class<?>>();
        for (MBeanServer mbs = cs.getSystemMBeanServerForwarder().getMBeanServer();
             mbs != stop;
             mbs = ((MBeanServerForwarder) mbs).getMBeanServer()) {
            foundClasses.add(mbs.getClass());
        }
        if (!expectedClasses.equals(foundClasses)) {
            fail("Incorrect forwarder chain: expected " + expectedClasses +
                    "; found " + foundClasses);
        }
    }

    // env is consistent if either (a) localizer is not enabled or (b)
    // localizer is enabled and context is enabled.
    private static boolean isConsistent(Map<String, String> env) {
        String ctxS = env.get(JMXConnectorServer.CONTEXT_FORWARDER);
        boolean ctx = (ctxS == null) ? true : Boolean.parseBoolean(ctxS);
        String locS = env.get(JMXConnectorServer.LOCALIZE_MBEAN_INFO_FORWARDER);
        boolean loc = (locS == null) ? false : Boolean.parseBoolean(locS);
        return !loc || ctx;
    }

    private static void fail(String why) {
        System.out.println("FAILED: " + why);
        failure = why;
    }
}
