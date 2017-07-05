/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6221321 6295867
 * @summary Test that annotations in Standard MBean interfaces
 * correctly produce Descriptor entries
 * @author Eamonn McManus
 * @run clean AnnotationTest
 * @run build AnnotationTest
 * @run main AnnotationTest
 */

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import javax.management.*;

/*
  This test checks that annotations produce Descriptor entries as
  specified in javax.management.DescriptorKey.  It does two things:

  - An annotation consisting of an int and a String, each with an
    appropriate @DescriptorKey annotation, is placed on every program
    element where it can map to a Descriptor, namely:

    . on an MBean interface
    . on a getter for a read-only attribute
    . on a setter for a write-only attribute
    . on the getter but not the setter for a read/write attribute
    . on the setter but not the getter for a read/write attribute
    . on both the getter and the setter for a read/write attribute
    . on an operation
    . on each parameter of an operation
    . on a public constructor with no parameters
    . on a public constructor with a parameter
    . on the parameter of that public constructor
    . on all of the above for an MXBean instead of an MBean

    The test checks that in each case the corresponding Descriptor
    appears in the appropriate place inside the MBean's MBeanInfo.

  - An annotation consisting of enough other types to ensure coverage
    is placed on a getter.  The test checks that the generated
    MBeanAttributeInfo contains the corresponding Descriptor.  The tested
    types are the following:

    . Class
    . an enumeration type (java.lang.annotation.RetentionPolicy)
    . boolean
    . String[]
    . Class[]
    . int[]
    . an array of enumeration type (RetentionPolicy[])
    . boolean[]
 */
public class AnnotationTest {
    private static String failed = null;

//    @Retention(RetentionPolicy.RUNTIME) @Inherited
//    @Target(ElementType.METHOD)
//    public static @interface DescriptorKey {
//        String value();
//    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Pair {
        @DescriptorKey("x")
        int x();
        @DescriptorKey("y")
        String y();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Full {
        @DescriptorKey("class")
        Class classValue();
        @DescriptorKey("enum")
        RetentionPolicy enumValue();
        @DescriptorKey("boolean")
        boolean booleanValue();
        @DescriptorKey("stringArray")
        String[] stringArrayValue();
        @DescriptorKey("classArray")
        Class[] classArrayValue();
        @DescriptorKey("intArray")
        int[] intArrayValue();
        @DescriptorKey("enumArray")
        RetentionPolicy[] enumArrayValue();
        @DescriptorKey("booleanArray")
        boolean[] booleanArrayValue();
    }

    /* We use the annotation @Pair(x = 3, y = "foo") everywhere, and this is
       the Descriptor that it should produce: */
    private static Descriptor expectedDescriptor =
        new ImmutableDescriptor(new String[] {"x", "y"},
                                new Object[] {3, "foo"});

    private static Descriptor expectedFullDescriptor =
        new ImmutableDescriptor(new String[] {
                                    "class", "enum", "boolean", "stringArray",
                                    "classArray", "intArray", "enumArray",
                                    "booleanArray",
                                },
                                new Object[] {
                                    Full.class.getName(),
                                    RetentionPolicy.RUNTIME.name(),
                                    false,
                                    new String[] {"foo", "bar"},
                                    new String[] {Full.class.getName()},
                                    new int[] {1, 2},
                                    new String[] {RetentionPolicy.RUNTIME.name()},
                                    new boolean[] {false, true},
                                });

    @Pair(x = 3, y = "foo")
    public static interface ThingMBean {
        @Pair(x = 3, y = "foo")
        @Full(classValue=Full.class,
              enumValue=RetentionPolicy.RUNTIME,
              booleanValue=false,
              stringArrayValue={"foo", "bar"},
              classArrayValue={Full.class},
              intArrayValue={1, 2},
              enumArrayValue={RetentionPolicy.RUNTIME},
              booleanArrayValue={false, true})
        int getReadOnly();

        @Pair(x = 3, y = "foo")
        void setWriteOnly(int x);

        @Pair(x = 3, y = "foo")
        int getReadWrite1();
        void setReadWrite1(int x);

        @Pair(x = 3, y = "foo")
        int getReadWrite2();
        @Pair(x = 3, y = "foo")
        void setReadWrite2(int x);

        int getReadWrite3();
        @Pair(x = 3, y = "foo")
        void setReadWrite3(int x);

        @Pair(x = 3, y = "foo")
        int operation(@Pair(x = 3, y = "foo") int p1,
                      @Pair(x = 3, y = "foo") int p2);
    }

    public static class Thing implements ThingMBean {
        @Pair(x = 3, y = "foo")
        public Thing() {}

        @Pair(x = 3, y = "foo")
        public Thing(@Pair(x = 3, y = "foo") int p1) {}

        public int getReadOnly() {return 0;}

        public void setWriteOnly(int x) {}

        public int getReadWrite1() {return 0;}
        public void setReadWrite1(int x) {}

        public int getReadWrite2() {return 0;}
        public void setReadWrite2(int x) {}

        public int getReadWrite3() {return 0;}
        public void setReadWrite3(int x) {}

        public int operation(int p1, int p2) {return 0;}
    }

    @Pair(x = 3, y = "foo")
    public static interface ThingMXBean extends ThingMBean {}

    public static class ThingImpl implements ThingMXBean {
        @Pair(x = 3, y = "foo")
        public ThingImpl() {}

        @Pair(x = 3, y = "foo")
        public ThingImpl(@Pair(x = 3, y = "foo") int p1) {}

        public int getReadOnly() {return 0;}

        public void setWriteOnly(int x) {}

        public int getReadWrite1() {return 0;}
        public void setReadWrite1(int x) {}

        public int getReadWrite2() {return 0;}
        public void setReadWrite2(int x) {}

        public int getReadWrite3() {return 0;}
        public void setReadWrite3(int x) {}

        public int operation(int p1, int p2) {return 0;}
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Testing that annotations are correctly " +
                           "reflected in Descriptor entries");

        MBeanServer mbs =
            java.lang.management.ManagementFactory.getPlatformMBeanServer();
        ObjectName on = new ObjectName("a:b=c");
        Thing thing = new Thing();
        mbs.registerMBean(thing, on);
        check(mbs, on);
        mbs.unregisterMBean(on);
        ThingImpl thingImpl = new ThingImpl();
        mbs.registerMBean(thingImpl, on);
        check(mbs, on);

        if (failed == null)
            System.out.println("Test passed");
        else if (true)
            throw new Exception("TEST FAILED: " + failed);
        else
            System.out.println("Test disabled until 6221321 implemented");
    }

    private static void check(MBeanServer mbs, ObjectName on) throws Exception {
        MBeanInfo mbi = mbs.getMBeanInfo(on);

        // check the MBean itself
        check(mbi);

        // check attributes
        MBeanAttributeInfo[] attrs = mbi.getAttributes();
        for (MBeanAttributeInfo attr : attrs) {
            check(attr);
            if (attr.getName().equals("ReadOnly"))
                check("@Full", attr.getDescriptor(), expectedFullDescriptor);
        }

        // check operations
        MBeanOperationInfo[] ops = mbi.getOperations();
        for (MBeanOperationInfo op : ops) {
            check(op);
            check(op.getSignature());
        }

        MBeanConstructorInfo[] constrs = mbi.getConstructors();
        for (MBeanConstructorInfo constr : constrs) {
            check(constr);
            check(constr.getSignature());
        }
    }

    private static void check(DescriptorRead x) {
        check(x, x.getDescriptor(), expectedDescriptor);
    }

    private static void check(Object x, Descriptor d, Descriptor expect) {
        String fail = null;
        try {
            Descriptor u = ImmutableDescriptor.union(d, expect);
            if (!u.equals(d))
                fail = "should contain " + expect + "; is " + d;
        } catch (IllegalArgumentException e) {
            fail = e.getMessage();
        }
        if (fail == null) {
            System.out.println("OK: " + x);
        } else {
            failed = "NOT OK: Incorrect descriptor for: " + x;
            System.out.println(failed);
            System.out.println("..." + fail);
        }
    }

    private static void check(DescriptorRead[] xx) {
        for (DescriptorRead x : xx)
            check(x);
    }

    public static class AnnotatedMBean extends StandardMBean {
        <T> AnnotatedMBean(T resource, Class<T> interfaceClass, boolean mx) {
            super(resource, interfaceClass, mx);
        }

        private static final String[] attrPrefixes = {"get", "set", "is"};

        protected void cacheMBeanInfo(MBeanInfo info) {
            MBeanAttributeInfo[] attrs = info.getAttributes();
            MBeanOperationInfo[] ops = info.getOperations();

            for (int i = 0; i < attrs.length; i++) {
                MBeanAttributeInfo attr = attrs[i];
                String name = attr.getName();
                Descriptor d = attr.getDescriptor();
                Method m;
                if ((m = getMethod("get" + name)) != null)
                    d = ImmutableDescriptor.union(d, descriptorFor(m));
                if (attr.getType().equals("boolean") &&
                        (m = getMethod("is" + name)) != null)
                    d = ImmutableDescriptor.union(d, descriptorFor(m));
                if ((m = getMethod("set" + name, attr)) != null)
                    d = ImmutableDescriptor.union(d, descriptorFor(m));
                if (!d.equals(attr.getDescriptor())) {
                    attrs[i] =
                        new MBeanAttributeInfo(name, attr.getType(),
                            attr.getDescription(), attr.isReadable(),
                            attr.isWritable(), attr.isIs(), d);
                }
            }

            for (int i = 0; i < ops.length; i++) {
                MBeanOperationInfo op = ops[i];
                String name = op.getName();
                Descriptor d = op.getDescriptor();
                MBeanParameterInfo[] params = op.getSignature();
                Method m = getMethod(name, params);
                if (m != null) {
                    d = ImmutableDescriptor.union(d, descriptorFor(m));
                    Annotation[][] annots = m.getParameterAnnotations();
                    for (int pi = 0; pi < params.length; pi++) {
                        MBeanParameterInfo param = params[pi];
                        Descriptor pd =
                                ImmutableDescriptor.union(param.getDescriptor(),
                                    descriptorFor(annots[pi]));
                        params[pi] = new MBeanParameterInfo(param.getName(),
                                param.getType(), param.getDescription(), pd);
                    }
                    op = new MBeanOperationInfo(op.getName(),
                            op.getDescription(), params, op.getReturnType(),
                            op.getImpact(), d);
                    if (!ops[i].equals(op))
                        ops[i] = op;
                }
            }

            Descriptor id = descriptorFor(getMBeanInterface());
            info = new MBeanInfo(info.getClassName(), info.getDescription(),
                    attrs, info.getConstructors(), ops, info.getNotifications(),
                    ImmutableDescriptor.union(id, info.getDescriptor()));
            super.cacheMBeanInfo(info);
        }

        private Descriptor descriptorFor(AnnotatedElement x) {
            Annotation[] annots = x.getAnnotations();
            return descriptorFor(annots);
        }

        private Descriptor descriptorFor(Annotation[] annots) {
            if (annots.length == 0)
                return ImmutableDescriptor.EMPTY_DESCRIPTOR;
            Map<String, Object> descriptorMap = new HashMap<String, Object>();
            for (Annotation a : annots) {
                Class<? extends Annotation> c = a.annotationType();
                Method[] elements = c.getMethods();
                for (Method element : elements) {
                    DescriptorKey key =
                        element.getAnnotation(DescriptorKey.class);
                    if (key != null) {
                        String name = key.value();
                        Object value;
                        try {
                            value = element.invoke(a);
                        } catch (Exception e) {
                            // we don't expect this
                            throw new RuntimeException(e);
                        }
                        Object oldValue = descriptorMap.put(name, value);
                        if (oldValue != null && !oldValue.equals(value)) {
                            final String msg =
                                "Inconsistent values for descriptor field " +
                                name + " from annotations: " + value + " :: " +
                                oldValue;
                            throw new IllegalArgumentException(msg);
                        }
                    }
                }
            }
            if (descriptorMap.isEmpty())
                return ImmutableDescriptor.EMPTY_DESCRIPTOR;
            else
                return new ImmutableDescriptor(descriptorMap);
        }

        private Method getMethod(String name, MBeanFeatureInfo... params) {
            Class<?> intf = getMBeanInterface();
            ClassLoader loader = intf.getClassLoader();
            Class[] classes = new Class[params.length];
            for (int i = 0; i < params.length; i++) {
                MBeanFeatureInfo param = params[i];
                Descriptor d = param.getDescriptor();
                String type = (String) d.getFieldValue("originalType");
                if (type == null) {
                    if (param instanceof MBeanAttributeInfo)
                        type = ((MBeanAttributeInfo) param).getType();
                    else
                        type = ((MBeanParameterInfo) param).getType();
                }
                Class<?> c = primitives.get(type);
                if (c == null) {
                    try {
                        c = Class.forName(type, false, loader);
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                }
                classes[i] = c;
            }
            try {
                return intf.getMethod(name, classes);
            } catch (Exception e) {
                return null;
            }
        }

        private static final Map<String, Class<?>> primitives =
                new HashMap<String, Class<?>>();
        static {
            for (Class<?> c :
                    new Class[] {boolean.class, byte.class, short.class,
                                 int.class, long.class, float.class,
                                 double.class, char.class, void.class}) {
                primitives.put(c.getName(), c);
            }
        }
    }
}
