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
 * @bug 6287328
 * @summary Add methods to StandardMBean to retrieve a method based on
 * MBean{Attribute|Operation}Info
 * @author Jean-Francois Denise
 * @run main FindMethodTest
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.management.MBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ManagedAttribute;
import javax.management.ManagedOperation;
import javax.management.ObjectName;
import javax.management.StandardMBean;

public class FindMethodTest {

    private static MBeanServer server =
            ManagementFactory.getPlatformMBeanServer();

    private static Map<String, Set<Method>> expectedMapping =
            new HashMap<String, Set<Method>>();
    private static Set<Method> STATE_SET = new HashSet<Method>();
    private static Set<Method> ENABLED_SET = new HashSet<Method>();
    private static Set<Method> DOIT_SET = new HashSet<Method>();
    private static Set<Method> STATUS_SET = new HashSet<Method>();
    private static Set<Method> HEAPMEMORYUSAGE_SET = new HashSet<Method>();
    private static Set<Method> THREADINFO_SET = new HashSet<Method>();
    private static Set<Method> DOIT_ANNOTATED_SET = new HashSet<Method>();
    private static Set<Method> IT_ANNOTATED_SET = new HashSet<Method>();
    private static HashSet<Set<Method>> TEST_MBEAN_SET =
            new HashSet<Set<Method>>();
    private static HashSet<Set<Method>> ANNOTATED_MBEAN_SET =
            new HashSet<Set<Method>>();
    private static HashSet<Set<Method>> MEMORY_MBEAN_SET =
            new HashSet<Set<Method>>();
    private static HashSet<Set<Method>> THREAD_MBEAN_SET =
            new HashSet<Set<Method>>();

    public interface TestMBean {

        public void doIt();

        public void setState(String str);

        public String getState();

        public boolean isEnabled();

        public void setStatus(int i);
    }

    public interface FaultyTestMBean {

        public void doIt(String doIt);

        public long getState();

        public void setEnabled(boolean b);

        public int getStatus();

        public String setWrong(int i);
    }

    @MBean
    public static class AnnotatedTest {
        @ManagedOperation
        public void doItAnnotated() {

        }

        public void dontDoIt() {

        }

        @ManagedAttribute
        public String getItAnnotated() {
            return null;
        }
        @ManagedAttribute
        public void setItAnnotated(String str) {

        }

        public String getItNot() {
            return null;
        }

    }

    static class Test implements TestMBean {

        public void doIt() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setState(String str) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public String getState() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public boolean isEnabled() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setStatus(int i) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }


    static {
        try {
            ENABLED_SET.add(TestMBean.class.getDeclaredMethod("isEnabled"));

            STATE_SET.add(TestMBean.class.getDeclaredMethod("getState"));
            STATE_SET.add(TestMBean.class.getDeclaredMethod("setState",
                    String.class));
            STATUS_SET.add(TestMBean.class.getDeclaredMethod("setStatus",
                    int.class));

            DOIT_SET.add(TestMBean.class.getDeclaredMethod("doIt"));

            DOIT_ANNOTATED_SET.add(AnnotatedTest.class.getDeclaredMethod("doItAnnotated"));

            IT_ANNOTATED_SET.add(AnnotatedTest.class.getDeclaredMethod("getItAnnotated"));
            IT_ANNOTATED_SET.add(AnnotatedTest.class.getDeclaredMethod("setItAnnotated", String.class));

            THREADINFO_SET.add(ThreadMXBean.class.getDeclaredMethod("dumpAllThreads", boolean.class,
                    boolean.class));

            HEAPMEMORYUSAGE_SET.add(MemoryMXBean.class.getDeclaredMethod("getHeapMemoryUsage"));

            TEST_MBEAN_SET.add(ENABLED_SET);
            TEST_MBEAN_SET.add(STATE_SET);
            TEST_MBEAN_SET.add(STATUS_SET);
            TEST_MBEAN_SET.add(DOIT_SET);

            ANNOTATED_MBEAN_SET.add(DOIT_ANNOTATED_SET);
            ANNOTATED_MBEAN_SET.add(IT_ANNOTATED_SET);

            MEMORY_MBEAN_SET.add(HEAPMEMORYUSAGE_SET);

            THREAD_MBEAN_SET.add(THREADINFO_SET);

            expectedMapping.put("State", STATE_SET);
            expectedMapping.put("Enabled", ENABLED_SET);
            expectedMapping.put("Status", STATUS_SET);
            expectedMapping.put("doIt", DOIT_SET);
            expectedMapping.put("HeapMemoryUsage", HEAPMEMORYUSAGE_SET);
            expectedMapping.put("dumpAllThreads", THREADINFO_SET);
            expectedMapping.put("doItAnnotated", DOIT_ANNOTATED_SET);
            expectedMapping.put("ItAnnotated", IT_ANNOTATED_SET);

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("Initialization failed");
        }
    }

    private static void testMBean(ObjectName name, Class<?> itf,
            HashSet<Set<Method>> expectMappings)
            throws Exception {

        Set<Set<Method>> expectedMappings =
                (Set<Set<Method>>) expectMappings.clone();

        MBeanInfo info = server.getMBeanInfo(name);
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            Set<Method> expected = expectedMapping.get(attr.getName());
            if (expected == null) {
                continue;
            }
            if (!expectedMappings.remove(expected)) {
                throw new Exception("The mapping to use is not the expected " +
                        "one for " + attr);
            }
            System.out.println("Expected : " + expected);
            Set<Method> found =
                    StandardMBean.findAttributeAccessors(itf, attr);
            System.out.println("Found : " + found);
            if (!found.equals(expected)) {
                throw new Exception("Mapping error.");
            }
        }
        for (MBeanOperationInfo op : info.getOperations()) {
            Set<Method> expected = expectedMapping.get(op.getName());
            if (expected == null) {
                continue;
            }
            if (!expectedMappings.remove(expected)) {
                throw new Exception("The mapping to use is not the expected " +
                        "one for " + op);
            }
            System.out.println("Expected : " + expected);
            Method method =
                    StandardMBean.findOperationMethod(itf, op);
            Set<Method> found = new HashSet<Method>();
            found.add(method);
            System.out.println("Found : " + found);
            if (!found.equals(expected)) {
                throw new Exception("Mapping error.");
            }
        }

        if (expectedMappings.size() != 0) {
            throw new Exception("Some mapping have not been found " +
                    expectedMappings);
        } else {
            System.out.println("All mappings have been found");
        }
    }

    public static void main(String[] args) throws Exception {
        // Positive tests
        Test t = new Test();
        ObjectName name = ObjectName.valueOf(":type=Test");
        server.registerMBean(t, name);
        AnnotatedTest at = new AnnotatedTest();
        ObjectName annotatedName = ObjectName.valueOf(":type=AnnotatedTest");
        server.registerMBean(at, annotatedName);

        testMBean(name, TestMBean.class, TEST_MBEAN_SET);

        testMBean(annotatedName, AnnotatedTest.class, ANNOTATED_MBEAN_SET);

        ObjectName memoryName =
                ObjectName.valueOf(ManagementFactory.MEMORY_MXBEAN_NAME);
        testMBean(memoryName, MemoryMXBean.class, MEMORY_MBEAN_SET);

        ObjectName threadName =
                ObjectName.valueOf(ManagementFactory.THREAD_MXBEAN_NAME);
        testMBean(threadName, ThreadMXBean.class, THREAD_MBEAN_SET);

        // Negative tests
        try {
            StandardMBean.findOperationMethod(null,
                    new MBeanOperationInfo("Test",
                    TestMBean.class.getDeclaredMethod("doIt")));
            throw new Exception("Expected exception not found");
        } catch (IllegalArgumentException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        try {
            StandardMBean.findOperationMethod(TestMBean.class, null);
            throw new Exception("Expected exception not found");
        } catch (IllegalArgumentException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        try {
            StandardMBean.findAttributeAccessors(null,
                    new MBeanAttributeInfo("Test", "Test",
                    TestMBean.class.getDeclaredMethod("getState"),
                    TestMBean.class.getDeclaredMethod("setState",
                    String.class)));
            throw new Exception("Expected exception not found");
        } catch (IllegalArgumentException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        try {
            StandardMBean.findAttributeAccessors(TestMBean.class, null);
            throw new Exception("Expected exception not found");
        } catch (IllegalArgumentException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        //Wrong operation signature
        try {
            StandardMBean.findOperationMethod(TestMBean.class,
                    new MBeanOperationInfo("FaultyTest",
                    FaultyTestMBean.class.getDeclaredMethod("doIt",
                    String.class)));
            throw new Exception("Expected exception not found");
        } catch (NoSuchMethodException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        //Wrong attribute accessor
        try {
            StandardMBean.findAttributeAccessors(TestMBean.class,
                    new MBeanAttributeInfo("FaultyTest", "FaultyTest", null,
                    FaultyTestMBean.class.getDeclaredMethod("setEnabled",
                    String.class)));
            throw new Exception("Expected exception not found");
        } catch (NoSuchMethodException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        //Wrong attribute type
        try {
            StandardMBean.findAttributeAccessors(TestMBean.class,
                    new MBeanAttributeInfo("State", "toto.FaultType",
                    "FaultyTest", true, true, false));
            throw new Exception("Expected exception not found");
        } catch (ClassNotFoundException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        //Wrong operation parameter type
        try {
            MBeanParameterInfo[] p = {new MBeanParameterInfo("p1",
                "toto.FaultType2", "FaultyParameter")
            };
            StandardMBean.findOperationMethod(TestMBean.class,
                    new MBeanOperationInfo("doIt", "FaultyMethod", p, "void",
                    0));
            throw new Exception("Expected exception not found");
        } catch (ClassNotFoundException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        // Check that not annotated attributes are not found
        try {
            StandardMBean.findAttributeAccessors(AnnotatedTest.class,
                    new MBeanAttributeInfo("ItNot", String.class.getName(),
                    "FaultyTest", true, false, false));
            throw new Exception("Expected exception not found");
        } catch (NoSuchMethodException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        // Check that not annotated operations are not found
        try {
            StandardMBean.findOperationMethod(AnnotatedTest.class,
                    new MBeanOperationInfo("dontDoIt","dontDoIt",null,
                    Void.TYPE.getName(),0));
            throw new Exception("Expected exception not found");
        } catch (NoSuchMethodException ex) {
            System.out.println("OK received expected exception " + ex);
        }
        // Check that wrong getter return type throws Exception
        try {
            StandardMBean.findAttributeAccessors(AnnotatedTest.class,
                    new MBeanAttributeInfo("ItAnnotated", Long.class.getName(),
                    "FaultyTest", true, false, false));
            throw new Exception("Expected exception not found");
        } catch (NoSuchMethodException ex) {
            System.out.println("OK received expected exception " + ex);
        }
         // Check that wrong setter return type throws Exception
        try {
            StandardMBean.findAttributeAccessors(FaultyTestMBean.class,
                    new MBeanAttributeInfo("Wrong", String.class.getName(),
                    "FaultyTest", true, true, false));
            throw new Exception("Expected exception not found");
        } catch (NoSuchMethodException ex) {
            System.out.println("OK received expected exception " + ex);
        }
    }
}
