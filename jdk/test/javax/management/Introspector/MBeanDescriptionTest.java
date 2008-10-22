/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test %M% %I%
 * @bug 6323980
 * @summary Test &#64;Description
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import javax.management.Description;
import javax.management.IntrospectionException;
import javax.management.MBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanFeatureInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MXBean;
import javax.management.ManagedAttribute;
import javax.management.ManagedOperation;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

public class MBeanDescriptionTest {
    private static String failure;
    private static final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    private static final ObjectName name;
    static {
        try {
            name = new ObjectName("a:b=c");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static interface Interface {
        @Description("A description")
        public String getA();

        @Description("B description")
        public int getB();
        public void setB(int x);

        public boolean isC();
        @Description("C description")
        public void setC(boolean x);

        @Description("D description")
        public void setD(float x);

        @Description("H description")
        public int getH();
        @Description("H description")
        public void setH(int x);

        public String getE();

        public int getF();
        public void setF(int x);

        public void setG(boolean x);

        @Description("opA description")
        public int opA(
                @Description("p1 description")
                int p1,
                @Description("p2 description")
                int p2);

        public void opB(float x);
    }

    @Description("MBean description")
    public static interface TestMBean extends Interface {}

    public static class Test implements TestMBean {
        @Description("0-arg constructor description")
        public Test() {}

        public Test(String why) {}

        @Description("2-arg constructor description")
        public Test(
                @Description("p1 description")
                int x,
                @Description("p2 description")
                String y) {
        }

        public String getA() {
            return null;
        }

        public int getB() {
            return 0;
        }

        public void setB(int x) {
        }

        public boolean isC() {
            return false;
        }

        public void setC(boolean x) {
        }

        public void setD(float x) {
        }

        public String getE() {
            return null;
        }

        public int getF() {
            return 0;
        }

        public void setF(int x) {
        }

        public void setG(boolean x) {
        }

        public int getH() {
            return 0;
        }

        public void setH(int x) {
        }

        public int opA(int p1, int p2) {
            return 0;
        }

        public void opB(float x) {
        }
    }

    public static class TestSub extends Test {
        @Description("0-arg constructor description")
        public TestSub() {}

        public TestSub(String why) {}

        @Description("2-arg constructor description")
        public TestSub(
                @Description("p1 description")
                int x,
                @Description("p2 description")
                String y) {
        }
    }

    public static class StandardSub extends StandardMBean implements TestMBean {
        @Description("0-arg constructor description")
        public StandardSub() {
            super(TestMBean.class, false);
        }

        public StandardSub(String why) {
            super(TestMBean.class, false);
        }

        @Description("2-arg constructor description")
        public StandardSub(
                @Description("p1 description")
                int x,
                @Description("p2 description")
                String y) {
            super(TestMBean.class, false);
        }

        public String getA() {
            return null;
        }

        public int getB() {
            return 0;
        }

        public void setB(int x) {
        }

        public boolean isC() {
            return false;
        }

        public void setC(boolean x) {
        }

        public void setD(float x) {
        }

        public String getE() {
            return null;
        }

        public int getF() {
            return 0;
        }

        public void setF(int x) {
        }

        public void setG(boolean x) {
        }

        public int opA(int p1, int p2) {
            return 0;
        }

        public void opB(float x) {
        }

        public int getH() {
            return 0;
        }

        public void setH(int x) {
        }
    }

    @Description("MBean description")
    public static interface TestMXBean extends Interface {}

    public static class TestMXBeanImpl implements TestMXBean {
        @Description("0-arg constructor description")
        public TestMXBeanImpl() {}

        public TestMXBeanImpl(String why) {}

        @Description("2-arg constructor description")
        public TestMXBeanImpl(
                @Description("p1 description")
                int x,
                @Description("p2 description")
                String y) {
        }

        public String getA() {
            return null;
        }

        public int getB() {
            return 0;
        }

        public void setB(int x) {
        }

        public boolean isC() {
            return false;
        }

        public void setC(boolean x) {
        }

        public void setD(float x) {
        }

        public String getE() {
            return null;
        }

        public int getF() {
            return 0;
        }

        public void setF(int x) {
        }

        public void setG(boolean x) {
        }

        public int opA(int p1, int p2) {
            return 0;
        }

        public void opB(float x) {
        }

        public int getH() {
            return 0;
        }

        public void setH(int x) {
        }
    }

    public static class StandardMXSub extends StandardMBean implements TestMXBean {
        @Description("0-arg constructor description")
        public StandardMXSub() {
            super(TestMXBean.class, true);
        }

        public StandardMXSub(String why) {
            super(TestMXBean.class, true);
        }

        @Description("2-arg constructor description")
        public StandardMXSub(
                @Description("p1 description")
                int x,
                @Description("p2 description")
                String y) {
            super(TestMXBean.class, true);
        }

        public String getA() {
            return null;
        }

        public int getB() {
            return 0;
        }

        public void setB(int x) {
        }

        public boolean isC() {
            return false;
        }

        public void setC(boolean x) {
        }

        public void setD(float x) {
        }

        public String getE() {
            return null;
        }

        public int getF() {
            return 0;
        }

        public void setF(int x) {
        }

        public void setG(boolean x) {
        }

        public int opA(int p1, int p2) {
            return 0;
        }

        public void opB(float x) {
        }

        public int getH() {
            return 0;
        }

        public void setH(int x) {
        }
    }

    @MBean
    @Description("MBean description")
    public static class AnnotatedMBean {
        @Description("0-arg constructor description")
        public AnnotatedMBean() {}

        public AnnotatedMBean(String why) {}

        @Description("2-arg constructor description")
        public AnnotatedMBean(
                @Description("p1 description")
                int x,
                @Description("p2 description")
                String y) {}

        @ManagedAttribute
        @Description("A description")
        public String getA() {
            return null;
        }

        @ManagedAttribute
        @Description("B description")
        public int getB() {
            return 0;
        }

        @ManagedAttribute
        public void setB(int x) {
        }

        @ManagedAttribute
        public boolean isC() {
            return false;
        }

        @ManagedAttribute
        @Description("C description")
        public void setC(boolean x) {
        }

        @ManagedAttribute
        @Description("D description")
        public void setD(float x) {
        }

        @ManagedAttribute
        public String getE() {
            return null;
        }

        @ManagedAttribute
        public int getF() {
            return 0;
        }

        @ManagedAttribute
        public void setF(int x) {
        }

        @ManagedAttribute
        public void setG(boolean x) {
        }

        @ManagedAttribute
        @Description("H description")
        public int getH() {
            return 0;
        }

        @ManagedAttribute
        @Description("H description")
        public void setH(int x) {
        }

        @ManagedOperation
        @Description("opA description")
        public int opA(
                @Description("p1 description") int p1,
                @Description("p2 description") int p2) {
            return 0;
        }

        @ManagedOperation
        public void opB(float x) {
        }
    }

    @MXBean
    @Description("MBean description")
    public static class AnnotatedMXBean {
        @Description("0-arg constructor description")
        public AnnotatedMXBean() {}

        public AnnotatedMXBean(String why) {}

        @Description("2-arg constructor description")
        public AnnotatedMXBean(
                @Description("p1 description")
                int x,
                @Description("p2 description")
                String y) {}

        @ManagedAttribute
        @Description("A description")
        public String getA() {
            return null;
        }

        @ManagedAttribute
        @Description("B description")
        public int getB() {
            return 0;
        }

        @ManagedAttribute
        public void setB(int x) {
        }

        @ManagedAttribute
        public boolean isC() {
            return false;
        }

        @ManagedAttribute
        @Description("C description")
        public void setC(boolean x) {
        }

        @ManagedAttribute
        @Description("D description")
        public void setD(float x) {
        }

        @ManagedAttribute
        public String getE() {
            return null;
        }

        @ManagedAttribute
        public int getF() {
            return 0;
        }

        @ManagedAttribute
        public void setF(int x) {
        }

        @ManagedAttribute
        public void setG(boolean x) {
        }

        @ManagedAttribute
        @Description("H description")
        public int getH() {
            return 0;
        }

        @ManagedAttribute
        @Description("H description")
        public void setH(int x) {
        }

        @ManagedOperation
        @Description("opA description")
        public int opA(
                @Description("p1 description") int p1,
                @Description("p2 description") int p2) {
            return 0;
        }

        @ManagedOperation
        public void opB(float x) {
        }
    }

    // Negative tests follow.

    // Inconsistent descriptions
    public static interface BadInterface {
        @Description("foo")
        public String getFoo();
        @Description("bar")
        public void setFoo(String x);
    }

    public static interface BadMBean extends BadInterface {}

    public static class Bad implements BadMBean {
        public String getFoo() {
            return null;
        }

        public void setFoo(String x) {
        }
    }

    public static interface BadMXBean extends BadInterface {}

    public static class BadMXBeanImpl implements BadMXBean {
        public String getFoo() {
            return null;
        }

        public void setFoo(String x) {
        }
    }

    private static interface Defaults {
        public String defaultAttributeDescription(String name);
        public String defaultOperationDescription(String name);
        public String defaultParameterDescription(int index);
    }

    private static class StandardDefaults implements Defaults {
        public String defaultAttributeDescription(String name) {
            return "Attribute exposed for management";
        }

        public String defaultOperationDescription(String name) {
            return "Operation exposed for management";
        }

        public String defaultParameterDescription(int index) {
            return "";
        }
    }
    private static final Defaults standardDefaults = new StandardDefaults();

    private static class MXBeanDefaults implements Defaults {
        public String defaultAttributeDescription(String name) {
            return name;
        }

        public String defaultOperationDescription(String name) {
            return name;
        }

        public String defaultParameterDescription(int index) {
            return "p" + index;
        }
    }
    private static final Defaults mxbeanDefaults = new MXBeanDefaults();

    private static class TestCase {
        final String name;
        final Object mbean;
        final Defaults defaults;
        TestCase(String name, Object mbean, Defaults defaults) {
            this.name = name;
            this.mbean = mbean;
            this.defaults = defaults;
        }
    }

    private static class ExceptionTest {
        final String name;
        final Object mbean;
        ExceptionTest(String name, Object mbean) {
            this.name = name;
            this.mbean = mbean;
        }
    }

    private static final TestCase[] tests = {
        new TestCase("Standard MBean", new Test(), standardDefaults),
        new TestCase("Standard MBean subclass", new TestSub(), standardDefaults),
        new TestCase("StandardMBean delegating",
                new StandardMBean(new Test(), TestMBean.class, false),
                standardDefaults),
        new TestCase("StandardMBean delegating to subclass",
                new StandardMBean(new TestSub(), TestMBean.class, false),
                standardDefaults),
        new TestCase("StandardMBean subclass", new StandardSub(), standardDefaults),

        new TestCase("MXBean", new TestMXBeanImpl(), mxbeanDefaults),
        new TestCase("StandardMBean MXBean delegating",
                new StandardMBean(new TestMXBeanImpl(), TestMXBean.class, true),
                mxbeanDefaults),
        new TestCase("StandardMBean MXBean subclass",
                new StandardMXSub(), mxbeanDefaults),

        new TestCase("@MBean", new AnnotatedMBean(), standardDefaults),
        new TestCase("@MXBean", new AnnotatedMXBean(), mxbeanDefaults),
        new TestCase("StandardMBean @MBean delegating",
                new StandardMBean(new AnnotatedMBean(), null, false),
                standardDefaults),
        new TestCase("StandardMBean @MXBean delegating",
                new StandardMBean(new AnnotatedMXBean(), null, true),
                mxbeanDefaults),
    };

    private static final ExceptionTest[] exceptionTests = {
        new ExceptionTest("Standard MBean with inconsistent get/set", new Bad()),
        new ExceptionTest("MXBean with inconsistent get/set", new BadMXBeanImpl()),
    };

    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing correct MBeans ===");
        for (TestCase test : tests) {
            System.out.println("Testing " + test.name + "...");
            mbs.registerMBean(test.mbean, name);
            boolean expectConstructors =
                    (test.mbean.getClass() != StandardMBean.class);
            check(mbs.getMBeanInfo(name), test.defaults, expectConstructors);
            mbs.unregisterMBean(name);
        }
        System.out.println();

        System.out.println("=== Testing incorrect MBeans ===");
        for (ExceptionTest test : exceptionTests) {
            System.out.println("Testing " + test.name);
            try {
                mbs.registerMBean(test.mbean, name);
                fail("Registration succeeded but should not have");
                mbs.unregisterMBean(name);
            } catch (NotCompliantMBeanException e) {
                // OK
            } catch (Exception e) {
                fail("Registration failed with wrong exception: " +
                        "expected NotCompliantMBeanException, got " +
                        e.getClass().getName());
            }
        }
        System.out.println();

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    private static void check(
            MBeanInfo mbi, Defaults defaults, boolean expectConstructors)
            throws Exception {
        assertEquals("MBean description", mbi.getDescription());

        // These attributes have descriptions
        for (String attr : new String[] {"A", "B", "C", "D", "H"}) {
            MBeanAttributeInfo mbai = getAttributeInfo(mbi, attr);
            assertEquals(attr + " description", mbai.getDescription());
        }

        // These attributes don't have descriptions
        for (String attr : new String[] {"E", "F", "G"}) {
            // If we ever change the default description, we'll need to change
            // this test accordingly.
            MBeanAttributeInfo mbai = getAttributeInfo(mbi, attr);
            assertEquals(
                    defaults.defaultAttributeDescription(attr), mbai.getDescription());
        }

        // This operation has a description, as do its parameters
        MBeanOperationInfo opA = getOperationInfo(mbi, "opA");
        assertEquals("opA description", opA.getDescription());
        checkSignature(opA.getSignature());

        // This operation has the default description, as does its parameter
        MBeanOperationInfo opB = getOperationInfo(mbi, "opB");
        assertEquals(defaults.defaultOperationDescription("opB"), opB.getDescription());
        MBeanParameterInfo opB0 = opB.getSignature()[0];
        assertEquals(defaults.defaultParameterDescription(0), opB0.getDescription());

        if (expectConstructors) {
            // The 0-arg and 2-arg constructors have descriptions
            MBeanConstructorInfo con0 = getConstructorInfo(mbi, 0);
            assertEquals("0-arg constructor description", con0.getDescription());
            MBeanConstructorInfo con2 = getConstructorInfo(mbi, 2);
            assertEquals("2-arg constructor description", con2.getDescription());
            checkSignature(con2.getSignature());

            // The 1-arg constructor does not have a description.
            // The default description for constructors and their
            // parameters is the same for all types of MBean.
            MBeanConstructorInfo con1 = getConstructorInfo(mbi, 1);
            assertEquals("Public constructor of the MBean", con1.getDescription());
            assertEquals("", con1.getSignature()[0].getDescription());
        }
    }

    private static void checkSignature(MBeanParameterInfo[] params) {
        for (int i = 0; i < params.length; i++) {
            MBeanParameterInfo mbpi = params[i];
            assertEquals("p" + (i+1) + " description", mbpi.getDescription());
        }
    }

    private static MBeanAttributeInfo getAttributeInfo(MBeanInfo mbi, String attr)
    throws Exception {
        return getFeatureInfo(mbi.getAttributes(), attr);
    }

    private static MBeanOperationInfo getOperationInfo(MBeanInfo mbi, String op)
    throws Exception {
        return getFeatureInfo(mbi.getOperations(), op);
    }

    private static MBeanConstructorInfo getConstructorInfo(MBeanInfo mbi, int nparams)
    throws Exception {
        for (MBeanConstructorInfo mbci : mbi.getConstructors()) {
            if (mbci.getSignature().length == nparams)
                return mbci;
        }
        throw new Exception("Constructor not found: " + nparams);
    }

    private static <T extends MBeanFeatureInfo> T getFeatureInfo(
            T[] features, String name) throws Exception {
        for (T feature : features) {
            if (feature.getName().equals(name))
                return feature;
        }
        throw new Exception("Feature not found: " + name);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual))
            fail("Expected " + string(expected) + ", got " + string(actual));
    }

    private static String string(Object x) {
        if (x instanceof String)
            return quote((String) x);
        else
            return String.valueOf(x);
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static void fail(String why) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        int n = 0;
        for (StackTraceElement elmt : stack) {
            String method = elmt.getMethodName();
            if (method.equals("fail") || method.equals("assertEquals") ||
                    method.equals("checkSignature"))
                continue;
            n = elmt.getLineNumber();
            break;
        }
        System.out.println("FAILED: " + why + " (line " + n + ")");
        failure = why;
    }
}
