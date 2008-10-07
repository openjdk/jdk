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
 * @summary Test MBeans defined with &#64;MBean
 * @author Eamonn McManus
 * @run main/othervm -ea AnnotatedMBeanTest
 */

import java.io.File;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.management.Attribute;
import javax.management.Descriptor;
import javax.management.DescriptorKey;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MXBean;
import javax.management.MalformedObjectNameException;
import javax.management.ManagedAttribute;
import javax.management.ManagedOperation;
import javax.management.MBean;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;

public class AnnotatedMBeanTest {
    private static MBeanServer mbs;
    private static final ObjectName objectName;
    static {
        try {
            objectName = new ObjectName("test:type=Test");
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (!AnnotatedMBeanTest.class.desiredAssertionStatus())
            throw new Exception("Test must be run with -ea");

        File policyFile = File.createTempFile("jmxperms", ".policy");
        policyFile.deleteOnExit();
        PrintWriter pw = new PrintWriter(policyFile);
        pw.println("grant {");
        pw.println("    permission javax.management.MBeanPermission \"*\", \"*\";");
        pw.println("    permission javax.management.MBeanServerPermission \"*\";");
        pw.println("    permission javax.management.MBeanTrustPermission \"*\";");
        pw.println("};");
        pw.close();

        System.setProperty("java.security.policy", policyFile.getAbsolutePath());
        System.setSecurityManager(new SecurityManager());

        String failure = null;

        for (Method m : AnnotatedMBeanTest.class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) &&
                    m.getName().startsWith("test") &&
                    m.getParameterTypes().length == 0) {
                mbs = MBeanServerFactory.newMBeanServer();
                try {
                    m.invoke(null);
                    System.out.println(m.getName() + " OK");
                } catch (InvocationTargetException ite) {
                    System.out.println(m.getName() + " got exception:");
                    Throwable t = ite.getCause();
                    t.printStackTrace(System.out);
                    failure = m.getName() + ": " + t.toString();
                }
            }
        }
        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    public static class Stats {
        private final int used;
        private final int size;
        private final boolean interesting;

        public Stats(int used, int size, boolean interesting) {
            this.used = used;
            this.size = size;
            this.interesting = interesting;
        }

        public int getUsed() {
            return used;
        }

        public int getSize() {
            return size;
        }

        public boolean isInteresting() {
            return interesting;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Units {
        @DescriptorKey("units")
        String value();
    }

    @MBean
    public static class Cache {
        private int used = 23;
        private int size = 99;

        @ManagedAttribute
        @Units("bytes")
        public int getUsed() {
            return used;
        }

        @ManagedAttribute
        public int getSize() {
            return size;
        }

        @ManagedAttribute
        public void setSize(int x) {
            this.size = x;
        }

        @ManagedAttribute
        public boolean isInteresting() {
            return false;
        }

        @ManagedAttribute
        public Stats getStats() {
            return new Stats(used, size, false);
        }

        @ManagedOperation
        public int dropOldest(int n) {
            return 55;
        }

        private void irrelevantMethod() {}
        private int getIrrelevant() {return 0;}
        public int getIrrelevant2() {return 0;}

        public int otherIrrelevantMethod() {return 5;}
    }

    public static class SubCache extends Cache {
        // SubCache does not have the @MBean annotation
        // but its parent does.  It doesn't add any @ManagedAttribute or
        // @ManagedOperation methods, so its management interface
        // should be the same.
        private void irrelevantMethod2() {}
        public int otherIrrelevantMethod3() {return 0;}

        public int getX() {return 0;}
        public void setX(int x) {}
    }

    @MXBean
    public static class CacheMX {
        private int used = 23;
        private int size = 99;

        @ManagedAttribute
        @Units("bytes")
        public int getUsed() {
            return used;
        }

        @ManagedAttribute
        public int getSize() {
            return size;
        }

        @ManagedAttribute
        public void setSize(int x) {
            this.size = x;
        }

        @ManagedAttribute
        public boolean isInteresting() {
            return false;
        }

        @ManagedAttribute
        public Stats getStats() {
            return new Stats(used, size, false);
        }

        @ManagedOperation
        public int dropOldest(int n) {
            return 55;
        }

        private void irrelevantMethod() {}
        private int getIrrelevant() {return 0;}
        public int getIrrelevant2() {return 0;}

        public int otherIrrelevantMethod() {return 5;}
    }

    public static class SubCacheMX extends CacheMX {
        private void irrelevantMethod2() {}
        public int otherIrrelevantMethod3() {return 0;}

        public int getX() {return 0;}
        public void setX(int x) {}
    }

    private static void testSimpleManagedResource() throws Exception {
        testResource(new Cache(), false);
    }

    private static void testSubclassManagedResource() throws Exception {
        testResource(new SubCache(), false);
    }

    private static void testMXBeanResource() throws Exception {
        testResource(new CacheMX(), true);
    }

    private static void testSubclassMXBeanResource() throws Exception {
        testResource(new SubCacheMX(), true);
    }

    private static void testResource(Object resource, boolean mx) throws Exception {
        mbs.registerMBean(resource, objectName);

        MBeanInfo mbi = mbs.getMBeanInfo(objectName);
        assert mbi.getDescriptor().getFieldValue("mxbean").equals(Boolean.toString(mx));

        MBeanAttributeInfo[] mbais = mbi.getAttributes();

        assert mbais.length == 4: mbais.length;

        for (MBeanAttributeInfo mbai : mbais) {
            String name = mbai.getName();
            if (name.equals("Used")) {
                assert mbai.isReadable();
                assert !mbai.isWritable();
                assert !mbai.isIs();
                assert mbai.getType().equals("int");
                assert "bytes".equals(mbai.getDescriptor().getFieldValue("units"));
            } else if (name.equals("Size")) {
                assert mbai.isReadable();
                assert mbai.isWritable();
                assert !mbai.isIs();
                assert mbai.getType().equals("int");
            } else if (name.equals("Interesting")) {
                assert mbai.isReadable();
                assert !mbai.isWritable();
                assert mbai.isIs();
                assert mbai.getType().equals("boolean");
            } else if (name.equals("Stats")) {
                assert mbai.isReadable();
                assert !mbai.isWritable();
                assert !mbai.isIs();
                Descriptor d = mbai.getDescriptor();
                if (mx) {
                    assert mbai.getType().equals(CompositeData.class.getName());
                    assert d.getFieldValue("originalType").equals(Stats.class.getName());
                    CompositeType ct = (CompositeType) d.getFieldValue("openType");
                    Set<String> names = new HashSet<String>(
                            Arrays.asList("used", "size", "interesting"));
                    assert ct.keySet().equals(names) : ct.keySet();
                } else {
                    assert mbai.getType().equals(Stats.class.getName());
                }
            } else
                assert false : name;
        }

        MBeanOperationInfo[] mbois = mbi.getOperations();

        assert mbois.length == 1: mbois.length;

        MBeanOperationInfo mboi = mbois[0];
        assert mboi.getName().equals("dropOldest");
        assert mboi.getReturnType().equals("int");
        MBeanParameterInfo[] mbpis = mboi.getSignature();
        assert mbpis.length == 1: mbpis.length;
        assert mbpis[0].getType().equals("int");

        assert mbs.getAttribute(objectName, "Used").equals(23);

        assert mbs.getAttribute(objectName, "Size").equals(99);
        mbs.setAttribute(objectName, new Attribute("Size", 55));
        assert mbs.getAttribute(objectName, "Size").equals(55);

        assert mbs.getAttribute(objectName, "Interesting").equals(false);

        Object stats = mbs.getAttribute(objectName, "Stats");
        assert (mx ? CompositeData.class : Stats.class).isInstance(stats) : stats.getClass();

        int ret = (Integer) mbs.invoke(
                objectName, "dropOldest", new Object[] {66}, new String[] {"int"});
        assert ret == 55;
    }
}
