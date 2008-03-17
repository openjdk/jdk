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
 * @test QueryDottedAttrTest
 * @bug 6602310
 * @summary Test that Query.attr can understand a.b etc.
 * @author Eamonn McManus
 */

import java.beans.ConstructorProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Set;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.StandardMBean;

public class QueryDottedAttrTest {
    public static class Complex {
        private final double re, im;

        @ConstructorProperties({"real", "imaginary"})
        public Complex(double re, double im) {
            this.re = re;
            this.im = im;
        }

        public double getRe() {
            return re;
        }

        public double getIm() {
            return im;
        }
    }

    public static interface Intf {
        Complex getComplex();
        int[] getIntArray();
        String[] getStringArray();
    }

    public static class Impl implements Intf {
        public Complex getComplex() {
            return new Complex(1.0, 1.0);
        }

        public int[] getIntArray() {
            return new int[] {1, 2, 3};
        }

        public String[] getStringArray() {
            return new String[] {"one", "two", "three"};
        }
    }

    public static interface TestMBean extends Intf {}

    public static class Test extends Impl implements TestMBean {}

    public static interface TestMXBean extends Intf {}

    public static class TestMX extends Impl implements TestMXBean {}

    public static class AttrWithDot extends StandardMBean {
        public <T> AttrWithDot(Object impl, Class<T> intf) {
            super(intf.cast(impl), intf, (intf == TestMXBean.class));
        }

        public Object getAttribute(String attribute)
        throws AttributeNotFoundException, MBeanException, ReflectionException {
            if (attribute.equals("Complex.re"))
                return 2.0;
            else
                return super.getAttribute(attribute);
        }
    }

    private static final boolean[] booleans = {false, true};

    private static final QueryExp[] alwaysTrueQueries = {
        Query.eq(Query.attr("IntArray.length"), Query.value(3)),
        Query.eq(Query.attr("StringArray.length"), Query.value(3)),
        Query.eq(Query.attr("Complex.im"), Query.value(1.0)),
    };

    private static final QueryExp[] alwaysFalseQueries = {
        Query.eq(Query.attr("IntArray.length"), Query.value("3")),
        Query.eq(Query.attr("IntArray.length"), Query.value(2)),
        Query.eq(Query.attr("Complex.im"), Query.value(-1.0)),
        Query.eq(Query.attr("Complex.xxx"), Query.value(0)),
    };

    private static final QueryExp[] attrWithDotTrueQueries = {
        Query.eq(Query.attr("Complex.re"), Query.value(2.0)),
    };

    private static final QueryExp[] attrWithDotFalseQueries = {
        Query.eq(Query.attr("Complex.re"), Query.value(1.0)),
    };

    private static String failure;

    public static void main(String[] args) throws Exception {
        ObjectName name = new ObjectName("a:b=c");
        for (boolean attrWithDot : booleans) {
            for (boolean mx : booleans) {
                String what =
                        (mx ? "MXBean" : "Standard MBean") +
                        (attrWithDot ? " having attribute with dot in its name" : "");
                System.out.println("Testing " + what);
                Class<?> intf = mx ? TestMXBean.class : TestMBean.class;
                Object impl = mx ? new TestMX() : new Test();
                if (attrWithDot)
                    impl = new AttrWithDot(impl, intf);
                MBeanServer mbs = MBeanServerFactory.newMBeanServer();
                mbs.registerMBean(impl, name);
                boolean ismx = "true".equals(
                        mbs.getMBeanInfo(name).getDescriptor().getFieldValue("mxbean"));
                if (mx != ismx)
                    fail("MBean should " + (mx ? "" : "not ") + "be MXBean");
                test(mbs, name, alwaysTrueQueries, true);
                test(mbs, name, alwaysFalseQueries, false);
                test(mbs, name, attrWithDotTrueQueries, attrWithDot);
                test(mbs, name, attrWithDotFalseQueries, !attrWithDot);
            }
        }
        if (failure != null)
            throw new Exception("TEST FAILED: " + failure);
    }

    private static void test(
            MBeanServer mbs, ObjectName name, QueryExp[] queries, boolean expect)
            throws Exception {
        for (QueryExp query : queries) {
            // Serialize and deserialize the query to ensure that its
            // serialization is correct
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(bout);
            oout.writeObject(query);
            oout.close();
            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream oin = new ObjectInputStream(bin);
            query = (QueryExp) oin.readObject();
            Set<ObjectName> names = mbs.queryNames(null, query);
            if (names.isEmpty()) {
                if (expect)
                    fail("Query is false but should be true: " + query);
            } else if (names.equals(Collections.singleton(name))) {
                if (!expect)
                    fail("Query is true but should be false: " + query);
            } else {
                fail("Query returned unexpected set: " + names);
            }
        }
    }

    private static void fail(String msg) {
        failure = msg;
        System.out.println("..." + msg);
    }
}
