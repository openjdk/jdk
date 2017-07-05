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

/* @test %M% %I%
 * @bug 6562936 6750935
 * @run compile customtypes/package-info.java
 * @run main CustomTypeTest
 */

import java.io.InvalidObjectException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.Descriptor;
import javax.management.MBeanServerInvocationHandler;
import javax.management.NotCompliantMBeanException;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.MXBeanMapping;
import javax.management.openmbean.MXBeanMappingClass;
import javax.management.openmbean.MXBeanMappingFactory;
import javax.management.openmbean.MXBeanMappingFactoryClass;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;

import static javax.management.JMX.MBeanOptions;

import customtypes.*;

public class CustomTypeTest {
    @MXBeanMappingClass(LinkedListMapping.class)
    public static class LinkedList {
        private final String name;
        private final LinkedList next;

        public LinkedList(String name, LinkedList next) {
            this.name = name;
            this.next = next;
        }

        public String getName() {
            return name;
        }

        public LinkedList getNext() {
            return next;
        }

        public String toString() {
            if (next == null)
                return "(" + name + ")";
            else
                return "(" + name + " " + next + ")";
        }

        public boolean equals(Object x) {
            if (!(x instanceof LinkedList))
                return false;
            LinkedList other = (LinkedList) x;
            return (this.name.equals(other.name) &&
                    (this.next == null ? other.next == null :
                        this.next.equals(other.next)));
        }
    }

    public static class LinkedListMapping extends MXBeanMapping {
        public LinkedListMapping(Type type) throws OpenDataException {
            super(LinkedList.class, ArrayType.getArrayType(SimpleType.STRING));
            if (type != LinkedList.class) {
                throw new OpenDataException("Mapping only valid for " +
                        LinkedList.class);
            }
        }

        public Object fromOpenValue(Object openValue) throws InvalidObjectException {
            String[] array = (String[]) openValue;
            LinkedList list = null;
            for (int i = array.length - 1; i >= 0; i--)
                list = new LinkedList(array[i], list);
            return list;
        }

        public Object toOpenValue(Object javaValue) throws OpenDataException {
            ArrayList<String> array = new ArrayList<String>();
            for (LinkedList list = (LinkedList) javaValue; list != null;
                    list = list.getNext())
                array.add(list.getName());
            return array.toArray(new String[0]);
        }
    }

    public static interface LinkedListMXBean {
        public LinkedList getLinkedList();
    }

    public static class LinkedListImpl implements LinkedListMXBean {
        public LinkedList getLinkedList() {
            return new LinkedList("car", new LinkedList("cdr", null));
        }
    }

    public static class ObjectMXBeanMapping extends MXBeanMapping {
        private static final CompositeType wildcardType;

        static {
            try {
                wildcardType =
                    new CompositeType(Object.class.getName(),
                                      "Wildcard type for Object",
                                      new String[0],        // itemNames
                                      new String[0],        // itemDescriptions
                                      new OpenType<?>[0]);  // itemTypes
            } catch (OpenDataException e) {
                throw new RuntimeException(e);
            }
        }

        public ObjectMXBeanMapping() {
            super(Object.class, wildcardType);
        }

        public Object fromOpenValue(Object openValue) throws InvalidObjectException {
            if (!(openValue instanceof CompositeData)) {
                throw new InvalidObjectException("Not a CompositeData: " +
                        openValue.getClass());
            }
            CompositeData cd = (CompositeData) openValue;
            if (!cd.containsKey("value")) {
                throw new InvalidObjectException("CompositeData does not " +
                        "contain a \"value\" item: " + cd);
            }
            Object x = cd.get("value");
            if (!(x instanceof CompositeData || x instanceof TabularData ||
                    x instanceof Object[]))
                return x;

            String typeName = (String) cd.get("type");
            if (typeName == null) {
                throw new InvalidObjectException("CompositeData does not " +
                        "contain a \"type\" item: " + cd);
            }
            Class<?> c;
            try {
                c = Class.forName(typeName);
            } catch (ClassNotFoundException e) {
                InvalidObjectException ioe =
                        new InvalidObjectException("Could not find type");
                ioe.initCause(e);
                throw ioe;
            }
            MXBeanMapping mapping;
            try {
                mapping = objectMappingFactory.mappingForType(c, objectMappingFactory);
            } catch (OpenDataException e) {
                InvalidObjectException ioe =
                        new InvalidObjectException("Could not map object's " +
                            "type " + c.getName());
                ioe.initCause(e);
                throw ioe;
            }
            return mapping.fromOpenValue(x);
        }

        public Object toOpenValue(Object javaValue) throws OpenDataException {
            OpenType<?> openType;
            Object openValue;
            String typeName;
            if (javaValue == null) {
                openType = SimpleType.VOID;
                openValue = null;
                typeName = null;
            } else {
                Class<?> c = javaValue.getClass();
                if (c.equals(Object.class))
                    throw new OpenDataException("Cannot map Object to an open value");
                MXBeanMapping mapping =
                        objectMappingFactory.mappingForType(c, objectMappingFactory);
                openType = mapping.getOpenType();
                openValue = mapping.toOpenValue(javaValue);
                typeName = c.getName();
            }
            CompositeType ct = new CompositeType(
                    (javaValue == null) ? "null" : openType.getClassName(),
                    "Open Mapping for Object",
                    new String[] {"type", "value"},
                    new String[] {"type", "value"},
                    new OpenType<?>[] {SimpleType.STRING, openType});
            return new CompositeDataSupport(
                    ct,
                    new String[] {"type", "value"},
                    new Object[] {typeName, openValue});
        }
    }

    public static class ObjectMappingFactory extends MXBeanMappingFactory {
        private static MXBeanMapping objectMapping =
                new ObjectMXBeanMapping();

        @Override
        public MXBeanMapping mappingForType(Type t, MXBeanMappingFactory f)
        throws OpenDataException {
            if (t.equals(Object.class))
                return objectMapping;
            else
                return MXBeanMappingFactory.DEFAULT.mappingForType(t, f);
        }
    }

    private static MXBeanMappingFactory objectMappingFactory =
            new ObjectMappingFactory();

    public static interface ObjectMXBean {
        public Object getObject();
        public Object[] getObjects();
        public List<Object> getObjectList();
        public Object[][] getMoreObjects();
    }

    public static class ObjectImpl implements ObjectMXBean {
        public Object getObject() {
            return 123;
        }

        private static Object[] objects = {
            "foo", 3, 3.14f, 3.14, 3L, new Date(), ObjectName.WILDCARD,
            new byte[3], new char[3], new int[3][3],
            new LinkedListImpl().getLinkedList(),
        };

        public Object[] getObjects() {
            return objects;
        }

        public List<Object> getObjectList() {
            return Arrays.asList(getObjects());
        }

        public Object[][] getMoreObjects() {
            return new Object[][] {{getObjects()}};
        }
    }

    @MXBeanMappingFactoryClass(ObjectMappingFactory.class)
    public static interface AnnotatedObjectMXBean extends ObjectMXBean {}

    public static class AnnotatedObjectImpl extends ObjectImpl
            implements AnnotatedObjectMXBean {}

    public static class BrokenMappingFactory extends MXBeanMappingFactory {
        public MXBeanMapping mappingForType(Type t, MXBeanMappingFactory f)
        throws OpenDataException {
            throw new OpenDataException(t.toString());
        }
    }

    public static class ReallyBrokenMappingFactory extends BrokenMappingFactory {
        public ReallyBrokenMappingFactory() {
            throw new RuntimeException("Oops");
        }
    }

    @MXBeanMappingFactoryClass(BrokenMappingFactory.class)
    public static interface BrokenMXBean {
        public int getX();
    }

    public static class BrokenImpl implements BrokenMXBean {
        public int getX() {return 0;}
    }

    @MXBeanMappingFactoryClass(ReallyBrokenMappingFactory.class)
    public static interface ReallyBrokenMXBean {
        public int getX();
    }

    public static class ReallyBrokenImpl implements ReallyBrokenMXBean {
        public int getX() {return 0;}
    }

    public static class BrokenMapping extends MXBeanMapping {
        public BrokenMapping(Type t) {
            super(t, SimpleType.STRING);
            throw new RuntimeException("Oops");
        }

        public Object fromOpenValue(Object openValue) throws InvalidObjectException {
            throw new AssertionError();
        }

        public Object toOpenValue(Object javaValue) throws OpenDataException {
            throw new AssertionError();
        }
    }

    @MXBeanMappingClass(BrokenMapping.class)
    public static class BrokenType {}

    public static interface BrokenTypeMXBean {
        BrokenType getBroken();
    }

    public static class BrokenTypeImpl implements BrokenTypeMXBean {
        public BrokenType getBroken() {
            throw new AssertionError();
        }
    }

    public static class BadConstructorMXBeanMappingFactory1 extends
            MXBeanMappingFactory {
        private BadConstructorMXBeanMappingFactory1() {}

        @Override
        public MXBeanMapping mappingForType(Type arg0, MXBeanMappingFactory arg1)
                throws OpenDataException {
            throw new UnsupportedOperationException("Should not be called");
        }
    }

    public static class BadConstructorMXBeanMappingFactory2 extends
            MXBeanMappingFactory {
        public BadConstructorMXBeanMappingFactory2(boolean oops) {}

        @Override
        public MXBeanMapping mappingForType(Type arg0, MXBeanMappingFactory arg1)
                throws OpenDataException {
            throw new UnsupportedOperationException("Should not be called");
        }
    }

    @MXBeanMappingFactoryClass(BadConstructorMXBeanMappingFactory1.class)
    public static interface BadConstructor1MXBean {}

    public static class BadConstructor1 implements BadConstructor1MXBean {}

    @MXBeanMappingFactoryClass(BadConstructorMXBeanMappingFactory2.class)
    public static interface BadConstructor2MXBean {}

    public static class BadConstructor2 implements BadConstructor2MXBean {}

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        System.out.println("Test @MXBeanMappingClass");
        ObjectName linkedName = new ObjectName("d:type=LinkedList");
        LinkedListMXBean linkedListMXBean = new LinkedListImpl();
        LinkedList list1 = linkedListMXBean.getLinkedList();
        mbs.registerMBean(linkedListMXBean, linkedName);
        LinkedListMXBean linkedProxy =
                JMX.newMXBeanProxy(mbs, linkedName, LinkedListMXBean.class);
        MBeanServerInvocationHandler mbsih = (MBeanServerInvocationHandler)
                Proxy.getInvocationHandler(linkedProxy);
        if (!mbsih.isMXBean())
            fail("not MXBean proxy");
        LinkedList list2 = linkedProxy.getLinkedList();
        if (list1 == list2)
            fail("lists identical!");
            // They should have gone through the mapping and back,
            // and the mapping doesn't do anything that would allow it
            // to restore the identical object.
        if (!list1.equals(list2))
            fail("lists different: " + list1 + " vs " + list2);
        System.out.println("...success");

        System.out.println("Test StandardMBean with MXBeanMappingFactory");
        ObjectMXBean wildcardMBean = new ObjectImpl();
        MBeanOptions options = new MBeanOptions();
        options.setMXBeanMappingFactory(objectMappingFactory);
        if (!options.isMXBean())
            fail("Setting MXBeanMappingFactory should imply MXBean");
        StandardMBean wildcardStandardMBean =
                new StandardMBean(wildcardMBean, ObjectMXBean.class, options);
        testWildcardMBean(mbs, wildcardMBean, wildcardStandardMBean,
                          options, ObjectMXBean.class);

        System.out.println("Test @MXBeanMappingFactoryClass on interface");
        ObjectMXBean annotatedWildcardMBean = new AnnotatedObjectImpl();
        testWildcardMBean(mbs, annotatedWildcardMBean, annotatedWildcardMBean,
                          null, AnnotatedObjectMXBean.class);

        System.out.println("Test @MXBeanMappingFactoryClass on package");
        CustomMXBean custom = zeroProxy(CustomMXBean.class);
        ObjectName customName = new ObjectName("d:type=Custom");
        mbs.registerMBean(custom, customName);
        Object x = mbs.getAttribute(customName, "X");
        if (!(x instanceof String))
            fail("Should be String: " + x + " (a " + x.getClass().getName() + ")");
        CustomMXBean customProxy =
                JMX.newMXBeanProxy(mbs, customName, CustomMXBean.class);
        x = customProxy.getX();
        if (!(x instanceof Integer) || (Integer) x != 0)
            fail("Wrong return from proxy: " + x + " (a " + x.getClass().getName() + ")");

        System.out.println("Test MXBeanMappingFactory exception");
        try {
            mbs.registerMBean(new BrokenImpl(), new ObjectName("d:type=Broken"));
            fail("Register did not throw exception");
        } catch (NotCompliantMBeanException e) {
            System.out.println("...OK: threw: " + e);
        }

        System.out.println("Test MXBeanMappingFactory constructor exception");
        try {
            mbs.registerMBean(new ReallyBrokenImpl(), new ObjectName("d:type=Broken"));
            fail("Register did not throw exception");
        } catch (NotCompliantMBeanException e) {
            System.out.println("...OK: threw: " + e);
        } catch (Exception e) {
            fail("Register threw wrong exception: " + e);
        }

        System.out.println("Test MXBeanMappingFactory exception with StandardMBean");
        MXBeanMappingFactory brokenF = new BrokenMappingFactory();
        MBeanOptions brokenO = new MBeanOptions();
        brokenO.setMXBeanMappingFactory(brokenF);
        try {
            new StandardMBean(wildcardMBean, ObjectMXBean.class, brokenO);
            fail("StandardMBean with broken factory did not throw exception");
        } catch (IllegalArgumentException e) {
            if (!(e.getCause() instanceof NotCompliantMBeanException)) {
                fail("StandardMBean with broken factory threw wrong exception: "
                        + e.getCause());
            }
        }

        System.out.println("Test MXBeanMappingClass exception");
        try {
            mbs.registerMBean(new BrokenTypeImpl(), new ObjectName("d:type=Broken"));
            fail("Broken MXBeanMappingClass did not throw exception");
        } catch (NotCompliantMBeanException e) {
            System.out.println("...OK: threw: " + e);
        }

        System.out.println("Test MXBeanMappingFactoryClass constructor exception");
        for (Object mbean : new Object[] {
            new BadConstructor1(), new BadConstructor2(),
        }) {
            String testName = mbean.getClass().getSimpleName();
            try {
                ObjectName name = new ObjectName("d:type=" + testName);
                mbs.registerMBean(mbean, name);
                fail("Broken MXBeanMappingFactoryClass did not throw exception" +
                        " (" + testName + ")");
            } catch (NotCompliantMBeanException e) {
                System.out.println("...OK: " + testName + " threw: " + e);
            } catch (Exception e) {
                fail("Broken MXBeanMappingFactoryClass " + testName + " threw " +
                        "wrong exception: " + e);
            }
        }

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    private static void testWildcardMBean(MBeanServer mbs, ObjectMXBean impl,
                                          Object mbean,
                                          MBeanOptions proxyOptions,
                                          Class<? extends ObjectMXBean> intf)
    throws Exception {
        ObjectName wildcardName = new ObjectName("d:type=Object");
        mbs.registerMBean(mbean, wildcardName);
        try {
            testWildcardMBean2(mbs, impl, wildcardName, proxyOptions, intf);
        } finally {
            mbs.unregisterMBean(wildcardName);
        }
    }

    private static void testWildcardMBean2(MBeanServer mbs, ObjectMXBean impl,
                                           ObjectName wildcardName,
                                           MBeanOptions proxyOptions,
                                           Class<? extends ObjectMXBean> intf)
    throws Exception {
        if (proxyOptions == null) {
            proxyOptions = new MBeanOptions();
            MXBeanMappingFactory f = MXBeanMappingFactory.forInterface(intf);
            proxyOptions.setMXBeanMappingFactory(f);
        }
        Descriptor d = mbs.getMBeanInfo(wildcardName).getDescriptor();
        String factoryName = (String)
                d.getFieldValue(JMX.MXBEAN_MAPPING_FACTORY_CLASS_FIELD);
        if (!ObjectMappingFactory.class.getName().equals(factoryName)) {
            fail("Descriptor has wrong MXBeanMappingFactory: " + factoryName +
                    " should be " + ObjectMappingFactory.class.getName());
        }
        ObjectMXBean wildcardProxy =
            JMX.newMBeanProxy(mbs, wildcardName, intf, proxyOptions);
        MBeanServerInvocationHandler mbsih = (MBeanServerInvocationHandler)
                Proxy.getInvocationHandler(wildcardProxy);
        MBeanOptions opts = mbsih.getMBeanOptions();
        if (!opts.equals(proxyOptions)) {
            fail("Proxy options differ from request: " + opts + " vs " +
                    proxyOptions);
        }
        Method[] wildcardMethods = ObjectMXBean.class.getMethods();
        for (Method m : wildcardMethods) {
            System.out.println("..." + m.getName());
            Object orig = m.invoke(impl);
            Object copy = m.invoke(wildcardProxy);
            if (!deepEquals(orig, copy)) {
                fail("objects differ: " + deepToString(orig) + " vs " +
                        deepToString(copy));
            }
        }
    }

    private static <T> T zeroProxy(Class<T> intf) {
        return intf.cast(Proxy.newProxyInstance(intf.getClassLoader(),
                new Class<?>[] {intf},
                new ZeroInvocationHandler()));
    }

    private static class ZeroInvocationHandler implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable {
            return 0;
        }
    }

    private static boolean deepEquals(Object x, Object y) {
        if (x == y)
            return true;
        if (x == null || y == null)
            return false;

        if (x instanceof Collection<?>) {
            if (!(y instanceof Collection<?>))
                return false;
            Collection<?> xcoll = (Collection<?>) x;
            Collection<?> ycoll = (Collection<?>) y;
            if (xcoll.size() != ycoll.size())
                return false;
            Iterator<?> xit = xcoll.iterator();
            Iterator<?> yit = ycoll.iterator();
            while (xit.hasNext()) {
                if (!deepEquals(xit.next(), yit.next()))
                    return false;
            }
            return true;
        }

        Class<?> xclass = x.getClass();
        Class<?> yclass = y.getClass();
        if (xclass.isArray()) {
            if (!yclass.isArray())
                return false;
            if (!xclass.getComponentType().equals(yclass.getComponentType()))
                return false;
            int len = Array.getLength(x);
            if (Array.getLength(y) != len)
                return false;
            for (int i = 0; i < len; i++) {
                if (!deepEquals(Array.get(x, i), Array.get(y, i)))
                    return false;
            }
            return true;
        }

//        return x.equals(y);
        if (x.equals(y))
            return true;
        System.out.println("Not equal: <" + x + "> and <" + y + ">");
        return false;
    }

    private static String deepToString(Object x) {
        if (x == null)
            return "null";

        if (x instanceof Collection<?>) {
            Collection<?> xcoll = (Collection<?>) x;
            StringBuilder sb = new StringBuilder("[");
            for (Object e : xcoll) {
                if (sb.length() > 1)
                    sb.append(", ");
                sb.append(deepToString(e));
            }
            sb.append("]");
            return sb.toString();
        }

        if (x instanceof Object[]) {
            Object[] xarr = (Object[]) x;
            return deepToString(Arrays.asList(xarr));
        }

        if (x.getClass().isArray()) { // primitive array
            String s = Arrays.deepToString(new Object[] {x});
            return s.substring(1, s.length() - 1);
        }

        return x.toString();
    }

    private static void fail(String msg) {
        System.out.println("TEST FAILED: " + msg);
        if (msg.length() > 100)
            msg = msg.substring(0, 100) + "...";
        failure = msg;
    }

    private static String failure;
}
