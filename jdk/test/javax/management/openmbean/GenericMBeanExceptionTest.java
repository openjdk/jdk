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
 * @bug 6456269
 * @summary Test GenericMBeanException
 * @author Eamonn McManus
 */

import java.beans.ConstructorProperties;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.management.GenericMBeanException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.MXBeanMapping;
import javax.management.openmbean.MXBeanMappingFactory;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class GenericMBeanExceptionTest {
    private static volatile String failure = null;

    public static interface ThrowerMBean {
        public void throwGeneric() throws GenericMBeanException;
        public void throwGeneric(Throwable cause) throws GenericMBeanException;
        public void throwGeneric(String errorCode) throws GenericMBeanException;
        public void throwGeneric(CompositeData userData) throws GenericMBeanException;
        public void throwGeneric(String errorCode, CompositeData userData)
                throws GenericMBeanException;
        public void throwGeneric(String errorCode, CompositeData userData, Throwable cause)
                throws GenericMBeanException;
    }

    public static class Thrower implements ThrowerMBean {

        public void throwGeneric() throws GenericMBeanException {
            throw new GenericMBeanException("Message");
        }

        public void throwGeneric(Throwable cause) throws GenericMBeanException {
            throw new GenericMBeanException("Message", cause);
        }

        public void throwGeneric(String errorCode) throws GenericMBeanException {
            throw new GenericMBeanException("Message", errorCode, null);
        }

        public void throwGeneric(CompositeData userData) throws GenericMBeanException {
            throw new GenericMBeanException("Message", null, userData);
        }

        public void throwGeneric(String errorCode, CompositeData userData)
                throws GenericMBeanException {
            throw new GenericMBeanException("Message", errorCode, userData);
        }

        public void throwGeneric(String errorCode, CompositeData userData,
                                 Throwable cause) throws GenericMBeanException {
            throw new GenericMBeanException("Message", errorCode, userData, cause);
        }
    }

    public static class Payload {
        private final int severity;
        private final String subsystem;

        @ConstructorProperties({"severity", "subsystem"})
        public Payload(int severity, String subsystem) {
            this.severity = severity;
            this.subsystem = subsystem;
        }

        public int getSeverity() {
            return severity;
        }

        public String getSubsystem() {
            return subsystem;
        }

        @Override
        public boolean equals(Object x) {
            if (!(x instanceof Payload))
                return false;
            Payload p = (Payload) x;
            return (severity == p.severity &&
                    (subsystem == null) ?
                        p.subsystem == null : subsystem.equals(p.subsystem));
        }

        @Override
        public int hashCode() {
            return severity + subsystem.hashCode();
        }

        @Override
        public String toString() {
            return "Payload{severity: " + severity + ", subsystem: " + subsystem + "}";
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("test:type=Thrower");
        Thrower thrower = new Thrower();
        mbs.registerMBean(thrower, name);

        if (args.length > 0) {
            System.out.println("Attach client now, hit return to exit");
            System.in.read();
            return;
        }

        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://");
        JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(
                url, null, mbs);
        cs.start();
        JMXServiceURL addr = cs.getAddress();

        JMXConnector cc = JMXConnectorFactory.connect(addr);
        MBeanServerConnection mbsc = cc.getMBeanServerConnection();

        ThrowerMBean throwerProxy = JMX.newMBeanProxy(mbsc, name, ThrowerMBean.class);

        Payload payload = new Payload(5, "modular modulizer");
        MXBeanMapping payloadMapping = MXBeanMappingFactory.DEFAULT.mappingForType(
                Payload.class, MXBeanMappingFactory.DEFAULT);
        CompositeData userData = (CompositeData)
                payloadMapping.toOpenValue(payload);
        Throwable cause = new IllegalArgumentException("Badness");

        Object[][] testCases = {
            {},
            {"code1"},
            {userData},
            {"code2", userData},
            {(String) null, userData},
            {"code99", userData, cause},
            {(String) null, userData, cause},
        };

        for (Object[] testCase : testCases) {
            System.out.println("Test case: " + testCaseString(testCase));

            // Find which ThrowerMBean method it corresponds to
            Method testMethod = null;
search:
            for (Method m : ThrowerMBean.class.getMethods()) {
                Class<?>[] paramTypes = m.getParameterTypes();
                if (paramTypes.length != testCase.length)
                    continue;
                for (int i = 0; i < paramTypes.length; i++) {
                    if (testCase[i] != null && !paramTypes[i].isInstance(testCase[i]))
                        continue search;
                }
                testMethod = m;
            }

            if (testMethod == null) {
                throw new Exception("TEST ERROR: no method corresponds: " +
                        testCaseString(testCase));
            }

            try {
                testMethod.invoke(throwerProxy, testCase);
                fail("Did not throw exception", testCase);
                continue;
            } catch (InvocationTargetException e) {
                Throwable iteCause = e.getCause();
                if (!(iteCause instanceof GenericMBeanException)) {
                    iteCause.printStackTrace(System.out);
                    fail("Threw wrong exception " + iteCause, testCase);
                    continue;
                }
                GenericMBeanException ge = (GenericMBeanException) iteCause;
                if (!ge.getMessage().equals("Message"))
                    fail("Wrong message: " + ge.getMessage(), testCase);

                Class<?>[] paramTypes = testMethod.getParameterTypes();
                for (int i = 0; i < paramTypes.length; i++) {
                    Class<?> paramType = paramTypes[i];

                    if (paramType == Throwable.class) { // cause
                        Throwable geCause = ge.getCause();
                        if (!(geCause instanceof IllegalArgumentException))
                            fail("Wrong cause: " + geCause, testCase);
                        else if (!geCause.getMessage().equals("Badness"))
                            fail("Wrong cause message: " + geCause.getMessage(), testCase);
                    } else if (paramType == String.class) { // errorCode
                        String errorCode = ge.getErrorCode();
                        String expectedErrorCode =
                                (testCase[i] == null) ? "" : (String) testCase[i];
                        if (!expectedErrorCode.equals(errorCode))
                            fail("Wrong error code: " + ge.getErrorCode(), testCase);
                    } else if (paramType == CompositeData.class) { // userData
                        CompositeData userData2 = ge.getUserData();
                        if (!userData.equals(userData2))
                            fail("Wrong userData: " + userData2, testCase);
                        Payload payload2 = (Payload) payloadMapping.fromOpenValue(userData2);
                        if (!payload.equals(payload2))
                            fail("Wrong payload: " + payload2, testCase);
                    } else
                        throw new Exception("TEST ERROR: unknown parameter type: " + paramType);
                }
            }
        }

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    private static String testCaseString(Object[] testCase) {
        StringBuilder sb = new StringBuilder("[");
        String sep = "";
        for (Object x : testCase) {
            sb.append(sep);
            String xs = (x instanceof CompositeData) ?
                compositeDataString((CompositeData) x) : String.valueOf(x);
            sb.append(xs);
            sep = ", ";
        }
        sb.append("]");
        return sb.toString();
    }

    private static String compositeDataString(CompositeData cd) {
        StringBuilder sb = new StringBuilder("CompositeData{");
        CompositeType ct = cd.getCompositeType();
        String sep = "";
        for (String key : ct.keySet()) {
            sb.append(sep).append(key).append(": ").append(cd.get(key));
            sep = ", ";
        }
        sb.append("}");
        return sb.toString();
    }

    private static void fail(String why, Object[] testCase) {
        fail(testCaseString(testCase) + ": " + why);
    }

    private static void fail(String why) {
        failure = why;
        System.out.println("FAIL: " + why);
    }
}
