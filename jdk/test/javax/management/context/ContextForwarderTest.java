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

/*
 * @test
 * @bug 5072267
 * @summary Test that a context forwarder can be created and then installed.
 * @author Eamonn McManus
 */

/* The specific thing we're testing for is that the forwarder can be created
 * with a null "next", and then installed with a real "next".  An earlier
 * defect meant that in this case the simulated jmx.context// namespace had a
 * null handler that never changed.
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.management.ClientContext;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;

public class ContextForwarderTest {
    private static String failure;

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://");
        Map<String, String> env = new HashMap<String, String>();
        env.put(JMXConnectorServer.CONTEXT_FORWARDER, "false");
        JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(
                url, env, mbs);
        MBeanServerForwarder sysMBSF = cs.getSystemMBeanServerForwarder();
        MBeanServerForwarder mbsf = ClientContext.newContextForwarder(mbs, sysMBSF);
        sysMBSF.setMBeanServer(mbsf);

        int localCount = mbs.getMBeanCount();

        cs.start();
        try {
            JMXConnector cc = JMXConnectorFactory.connect(cs.getAddress());
            MBeanServerConnection mbsc = cc.getMBeanServerConnection();
            mbsc = ClientContext.withContext(mbsc, "foo", "bar");
            int contextCount = mbsc.getMBeanCount();
            if (localCount + 1 != contextCount) {
                fail("Local MBean count %d, context MBean count %d",
                        localCount, contextCount);
            }
            Set<ObjectName> localNames =
                    new TreeSet<ObjectName>(mbs.queryNames(null, null));
            ObjectName contextNamespaceObjectName =
                    new ObjectName(ClientContext.NAMESPACE + "//:type=JMXNamespace");
            if (!localNames.add(contextNamespaceObjectName))
                fail("Local names already contained context namespace handler");
            Set<ObjectName> contextNames = mbsc.queryNames(null, null);
            if (!localNames.equals(contextNames)) {
                fail("Name set differs locally and in context: " +
                        "local: %s; context: %s", localNames, contextNames);
            }
        } finally {
            cs.stop();
        }
        if (failure != null)
            throw new Exception("TEST FAILED: " + failure);
        else
            System.out.println("TEST PASSED");
    }

    private static void fail(String msg, Object... params) {
        failure = String.format(msg, params);
        System.out.println("FAIL: " + failure);
    }
}
