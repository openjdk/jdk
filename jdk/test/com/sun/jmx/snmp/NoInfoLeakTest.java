/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8026028
 * @summary Tests no leak of internal info
 * @author Shanliang JIANG
 * @run clean NoInfoLeakTest
 * @run build NoInfoLeakTest
 * @run main NoInfoLeakTest
 */

import com.sun.jmx.snmp.SnmpString;
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.agent.SnmpMibTable;
import com.sun.jmx.snmp.daemon.CommunicatorServer;
import com.sun.jmx.snmp.daemon.SnmpAdaptorServer;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public class NoInfoLeakTest {
    public static void main(String[] args) throws Exception {
        boolean ok = true;
        ok &= snmpStringTest();
        ok &= snmpMibTest();
        ok &= communicatorServerTest();

        if (!ok) {
            throw new RuntimeException("Some tests are failed!");
        }
    }

    private static boolean snmpStringTest() {
        System.out.println("\n---NoInfoLeakTest-snmpStringTest: testing the method byteValue()...");
        boolean passed = true;

        byte[] mine = new byte[]{1,1,1,};
        SnmpString ss = new SnmpString(mine);
        byte[] got = ss.byteValue();
        got[0]=0;

        if (ss.byteValue()[0] == 0) {
            System.err.println("Failed: SnmpString.byteValue() returns an internal mutable object value");
            passed = false;
        } else {
            System.out.println("---NoInfoLeakTest-snmpStringTest done.");
        }
        return passed;
    }

    private static boolean snmpMibTest() {
        boolean passed = true;
        System.out.println("\n---NoInfoLeakTest-snmpMibTest: testing the method "
                + "SnmpMib.getRootOid()...");
        SnmpMib mib = new MySnmpMib();

        if (mib.getRootOid() == mib.getRootOid()) {
            System.err.println("Failed: SnmpMib.getRootOid() returns an internal"
                    + " mutable object value "+mib.getRootOid());
        } else {
            System.out.println("---NoInfoLeakTest-snmpMibTest done.");
        }
        return passed;
    }

    private static boolean communicatorServerTest() {
        boolean passed = true;
        System.out.println("\n---NoInfoLeakTest-communicatorServerTest: testing the method CommunicatorServer.getNotificationInfo()...");
        CommunicatorServer server = new SnmpAdaptorServer();
        MBeanNotificationInfo[] notifs = server.getNotificationInfo();

        assert notifs.length > 0 && notifs[0] != null; // the current implementation ensures this
        notifs[0] = null;
        if (server.getNotificationInfo()[0] == null) {
            System.err.println("Failed: CommunicatorServer.getNotificationInfo()"
                    + " returns an internal mutable object value");
            passed = false;
        } else {
            System.out.println("---NoInfoLeakTest-communicatorServerTest done.");
        }
        return passed;
    }

    private static class MySnmpMib extends SnmpMib {
        @Override
        public void registerTableMeta(String name, SnmpMibTable table) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public SnmpMibTable getRegisteredTableMeta(String name) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void init() throws IllegalAccessException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
