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
 * @summary Test resource injection via &#64;Resource
 * @author Eamonn McManus
 * @run main/othervm -ea ResourceInjectionTest
 */

import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import javax.annotation.Resource;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.MBean;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MXBean;
import javax.management.MalformedObjectNameException;
import javax.management.ManagedAttribute;
import javax.management.ManagedOperation;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.SendNotification;
import javax.management.StandardEmitterMBean;
import javax.management.StandardMBean;
import javax.management.openmbean.MXBeanMappingFactory;

public class ResourceInjectionTest {
    private static MBeanServer mbs;
    private static final ObjectName objectName;
    static {
        try {
            objectName = new ObjectName("test:type=Test");
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    /* This is somewhat nasty.  In the current state of affairs, a
     * StandardEmitterMBean can only get the
     * MBeanServer to rewrite the source of a Notification from
     * the originating object's reference to its ObjectName IF
     * StandardEmitterMBean.getResource() returns a reference to the
     * wrapped object.  By default it doesn't, and you need to specify
     * the option below to make it do so.  We may hope that this is
     * obscure enough for users to run into it rarely if ever.
     */
    private static final StandardMBean.Options withWrappedVisible;
    private static final StandardMBean.Options withWrappedVisibleMX;
    static {
        withWrappedVisible = new StandardMBean.Options();
        withWrappedVisible.setWrappedObjectVisible(true);
        withWrappedVisibleMX = withWrappedVisible.clone();
        withWrappedVisibleMX.setMXBeanMappingFactory(MXBeanMappingFactory.DEFAULT);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private static @interface ExpectException {
        Class<? extends Exception> value();
    }

    public static void main(String[] args) throws Exception {
        if (!ResourceInjectionTest.class.desiredAssertionStatus())
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

        for (Method m : ResourceInjectionTest.class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) &&
                    m.getName().startsWith("test") &&
                    m.getParameterTypes().length == 0) {
                ExpectException expexc = m.getAnnotation(ExpectException.class);
                mbs = MBeanServerFactory.newMBeanServer();
                try {
                    m.invoke(null);
                    if (expexc != null) {
                        failure =
                                m.getName() + " did not got expected exception " +
                                expexc.value().getName();
                        System.out.println(failure);
                    } else
                        System.out.println(m.getName() + " OK");
                } catch (InvocationTargetException ite) {
                    Throwable t = ite.getCause();
                    String prob = null;
                    if (expexc != null) {
                        if (expexc.value().isInstance(t)) {
                            System.out.println(m.getName() + " OK (got expected " +
                                    expexc.value().getName() + ")");
                        } else
                            prob = "got wrong exception";
                    } else
                        prob = "got exception";
                    if (prob != null) {
                        failure = m.getName() + ": " + prob + " " +
                                t.getClass().getName();
                        System.out.println(failure);
                        t.printStackTrace(System.out);
                    }
                }
            }
        }
        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    private static interface Send {
        public void send();
    }

    // Test @Resource in MBean defined by annotations

    @MBean
    public static class Annotated {
        @Resource
        private volatile MBeanServer mbeanServer;
        @Resource
        private volatile ObjectName myName;

        @ManagedAttribute
        public ObjectName getMyName() {
            return myName;
        }

        @ManagedOperation
        public void unregisterSelf()
        throws InstanceNotFoundException, MBeanRegistrationException {
            mbeanServer.unregisterMBean(myName);
        }
    }

    private static void testAnnotated() throws Exception {
        testMBean(new Annotated());
    }

    private static void testAnnotatedWrapped() throws Exception {
        testMBean(new StandardMBean(new Annotated(), null));
    }

    @MBean
    public static class AnnotatedSend extends Annotated implements Send {
        @Resource
        private volatile SendNotification sender;

        @ManagedOperation
        public void send() {
            sender.sendNotification(new Notification("type", this, 0L));
        }
    }

    private static void testAnnotatedSend() throws Exception {
        testMBean(new AnnotatedSend());
    }

    private static void testAnnotatedSendWrapped() throws Exception {
        testMBean(new StandardEmitterMBean(
                new AnnotatedSend(), null, withWrappedVisible, null));
    }

    // Test @Resource in MXBean defined by annotations

    @MXBean
    public static class AnnotatedMX {
        @Resource
        private volatile MBeanServer mbeanServer;
        @Resource
        private volatile ObjectName myName;

        @ManagedAttribute
        public ObjectName getMyName() {
            return myName;
        }

        @ManagedOperation
        public void unregisterSelf()
        throws InstanceNotFoundException, MBeanRegistrationException {
            mbeanServer.unregisterMBean(myName);
        }
    }

    private static void testAnnotatedMX() throws Exception {
        testMBean(new AnnotatedMX());
    }

    private static void testAnnotatedMXWrapped() throws Exception {
        testMBean(new StandardMBean(new AnnotatedMX(), null, true));
    }

    public static class AnnotatedMXSend extends AnnotatedMX implements Send {
        @Resource
        private volatile SendNotification sender;

        @ManagedOperation
        public void send() {
            sender.sendNotification(new Notification("type", this, 0L));
        }
    }

    private static void testAnnotatedMXSend() throws Exception {
        testMBean(new AnnotatedMXSend());
    }

    private static void testAnnotatedMXSendWrapped() throws Exception {
        testMBean(new StandardEmitterMBean(
                new AnnotatedMXSend(), null, withWrappedVisibleMX, null));
    }

    // Test @Resource in Standard MBean

    public static interface SimpleStandardMBean {
        public ObjectName getMyName();
        public void unregisterSelf() throws Exception;
    }

    public static class SimpleStandard implements SimpleStandardMBean {
        @Resource(type = MBeanServer.class)
        private volatile Object mbeanServer;
        @Resource(type = ObjectName.class)
        private volatile Object myName;

        public ObjectName getMyName() {
            return (ObjectName) myName;
        }

        public void unregisterSelf() throws Exception {
            ((MBeanServer) mbeanServer).unregisterMBean(getMyName());
        }
    }

    private static void testStandard() throws Exception {
        testMBean(new SimpleStandard());
    }

    private static void testStandardWrapped() throws Exception {
        testMBean(new StandardMBean(new SimpleStandard(), SimpleStandardMBean.class));
    }

    public static interface SimpleStandardSendMBean extends SimpleStandardMBean {
        public void send();
    }

    public static class SimpleStandardSend
            extends SimpleStandard implements SimpleStandardSendMBean {
        @Resource(type = SendNotification.class)
        private volatile Object sender;

        public void send() {
            ((SendNotification) sender).sendNotification(
                    new Notification("type", this, 0L));
        }
    }

    private static void testStandardSend() throws Exception {
        testMBean(new SimpleStandardSend());
    }

    private static void testStandardSendWrapped() throws Exception {
        testMBean(new StandardEmitterMBean(
                new SimpleStandardSend(), SimpleStandardSendMBean.class,
                withWrappedVisible, null));
    }

    // Test @Resource in MXBean

    public static interface SimpleMXBean {
        public ObjectName getMyName();
        public void unregisterSelf() throws Exception;
    }

    public static class SimpleMX implements SimpleMXBean {
        @Resource(type = MBeanServer.class)
        private volatile Object mbeanServer;
        @Resource(type = ObjectName.class)
        private volatile Object myName;

        public ObjectName getMyName() {
            return (ObjectName) myName;
        }

        public void unregisterSelf() throws Exception {
            ((MBeanServer) mbeanServer).unregisterMBean(getMyName());
        }
    }

    private static void testMX() throws Exception {
        testMBean(new SimpleMX());
    }

    private static void testMXWrapped() throws Exception {
        testMBean(new StandardMBean(new SimpleMX(), SimpleMXBean.class, true));
    }

    public static interface SimpleMXBeanSend extends SimpleMXBean {
        public void send();
    }

    public MBeanServer getMbs() {
        return mbs;
    }

    public static class SimpleMXSend extends SimpleMX implements SimpleMXBeanSend {
        @Resource(type = SendNotification.class)
        private volatile Object sender;

        public void send() {
            ((SendNotification) sender).sendNotification(
                    new Notification("type", this, 0L));
        }
    }

    private static void testMXSend() throws Exception {
        testMBean(new SimpleMXSend());
    }

    private static void testMXSendWrapped() throws Exception {
        testMBean(new StandardEmitterMBean(
                new SimpleMXSend(), SimpleMXBeanSend.class,
                withWrappedVisibleMX, null));
    }

    // Test @Resource in Dynamic MBean

    private static class SimpleDynamic implements DynamicMBean {
        private MBeanServer mbeanServer;
        private ObjectName myName;

        @Resource
        private synchronized void setMBeanServer(MBeanServer mbs) {
            mbeanServer = mbs;
        }

        @Resource(type = ObjectName.class)
        private synchronized void setObjectName(Serializable name) {
            myName = (ObjectName) name;
        }

        public synchronized Object getAttribute(String attribute)
        throws AttributeNotFoundException {
            if (attribute.equals("MyName"))
                return myName;
            throw new AttributeNotFoundException(attribute);
        }

        public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException {
            throw new AttributeNotFoundException(attribute.getName());
        }

        public synchronized AttributeList getAttributes(String[] attributes) {
            AttributeList list = new AttributeList();
            for (String name : attributes) {
                if (name.equals("MyName"))
                    list.add(new Attribute("MyName", myName));
            }
            return list;
        }

        public AttributeList setAttributes(AttributeList attributes) {
            return new AttributeList();
        }

        public synchronized Object invoke(
                String actionName, Object[] params, String[] signature)
        throws MBeanException, ReflectionException {
            if (actionName.equals("unregisterSelf") &&
                    (params == null || params.length == 0) &&
                    (signature == null || signature.length == 0)) {
                try {
                    mbeanServer.unregisterMBean(myName);
                    return null;
                } catch (Exception x) {
                    throw new MBeanException(x);
                }
            } else {
                Exception x = new NoSuchMethodException(
                        actionName + Arrays.toString(signature));
                throw new MBeanException(x);
            }
        }

        public MBeanInfo getMBeanInfo() {
            DynamicMBean mbean = new StandardMBean(
                    new SimpleStandard(), SimpleStandardMBean.class, false);
            return mbean.getMBeanInfo();
        }
    }

    private static void testDynamic() throws Exception {
        testMBean(new SimpleDynamic());
    }

    private static class SimpleDynamicSend extends SimpleDynamic {
        private SendNotification sender;

        @Resource
        private synchronized void setSender(SendNotification sender) {
            this.sender = sender;
        }

        @Override
        public synchronized Object invoke(
                String actionName, Object[] params, String[] signature)
        throws MBeanException, ReflectionException {
            if (actionName.equals("send")) {
                sender.sendNotification(new Notification("type", this, 0L));
                return null;
            } else
                return super.invoke(actionName, params, signature);
        }
    }

    private static void testDynamicSend() throws Exception {
        testMBean(new SimpleDynamicSend());
    }

    // Test that @Resource classes don't have to be public
    // They can even be defined within methods!
    // But you can't have any @ManagedAttributes or @ManagedOperations
    // in such MBeans so their utility is limited.

    private static void testNonPublic() throws Exception {
        @MBean
        class NonPublic {
            @Resource
            ObjectName myName;
        }
        assert !Modifier.isPublic(NonPublic.class.getModifiers());
        NonPublic mbean = new NonPublic();
        mbs.registerMBean(mbean, objectName);
        assert objectName.equals(mbean.myName);
    }

    // Test inheritance and multiple injections of the same value

    private static class ManyResources extends AnnotatedSend {
        @Resource
        private volatile ObjectName myName;  // same name as in parent!
        @Resource(type=ObjectName.class)
        private volatile Object myOtherName;
        private volatile ObjectName myThirdName;
        private volatile ObjectName myFourthName;
        private volatile int methodCalls;
        @Resource
        private volatile SendNotification send1;
        @Resource(type = SendNotification.class)
        private volatile Object send2;

        @Resource
        void setMyName(ObjectName name) {
            myThirdName = name;
            methodCalls++;
        }

        @Resource(type=ObjectName.class)
        private void setMyNameAgain(ObjectName name) {
            myFourthName = name;
            methodCalls++;
        }

        void check() {
            assert objectName.equals(myName) : myName;
            for (ObjectName name : new ObjectName[] {
                (ObjectName)myOtherName, myThirdName, myFourthName
            }) {
                assert myName == name : name;
            }
            assert methodCalls == 2 : methodCalls;
            assert send1 != null && send2 == send1;
        }
    }

    private static void testManyResources() throws Exception {
        ManyResources mr = new ManyResources();
        testMBean(mr);
        mr.check();
    }

    // Test that method override doesn't lead to multiple calls of the same method

    private static class ManyResourcesSub extends ManyResources {
        private boolean called;

        @Override
        @Resource
        void setMyName(ObjectName name) {
            super.setMyName(name);
            called = true;
        }

        void check2() {
            assert called;
        }
    }

    private static void testOverride() throws Exception {
        ManyResourcesSub mrs = new ManyResourcesSub();
        testMBean(mrs);
        mrs.check();
        mrs.check2();
    }

    // Test that @Resource is illegal on static fields

    @MBean
    public static class StaticResource {
        @Resource
        private static ObjectName name;
    }

    @ExpectException(NotCompliantMBeanException.class)
    private static void testStaticResource() throws Exception {
        testMBean(new StaticResource());
    }

    // Test that @Resource is illegal on static methods

    @MBean
    public static class StaticResourceMethod {
        @Resource
        private static void setObjectName(ObjectName name) {}
    }

    @ExpectException(NotCompliantMBeanException.class)
    private static void testStaticResourceMethod() throws Exception {
        testMBean(new StaticResourceMethod());
    }

    // Test that @Resource is illegal on methods that don't return void

    @MBean
    public static class NonVoidMethod {
        @Resource
        private String setObjectName(ObjectName name) {
            return "oops";
        }
    }

    @ExpectException(NotCompliantMBeanException.class)
    private static void testNonVoidMethod() throws Exception {
        testMBean(new NonVoidMethod());
    }

    // Test that @Resource is illegal on methods with no arguments

    @MBean
    public static class NoArgMethod {
        @Resource(type=ObjectName.class)
        private void setObjectName() {}
    }

    @ExpectException(NotCompliantMBeanException.class)
    private static void testNoArgMethod() throws Exception {
        testMBean(new NoArgMethod());
    }

    // Test that @Resource is illegal on methods with more than one argument

    @MBean
    public static class MultiArgMethod {
        @Resource
        private void setObjectName(ObjectName name, String what) {}
    }

    @ExpectException(NotCompliantMBeanException.class)
    private static void testMultiArgMethod() throws Exception {
        testMBean(new MultiArgMethod());
    }

    private static class CountListener implements NotificationListener {
        volatile int count;
        public void handleNotification(Notification notification, Object handback) {
            count++;
        }
    }

    private static void testMBean(Object mbean) throws Exception {
        mbs.registerMBean(mbean, objectName);

        final ObjectName name = (ObjectName) mbs.getAttribute(objectName, "MyName");
        assert objectName.equals(name) : name;

        if (mbean instanceof Send || mbean instanceof NotificationEmitter) {
            assert mbs.isInstanceOf(name, NotificationEmitter.class.getName());
            CountListener countL = new CountListener();
            mbs.addNotificationListener(name, countL, null, null);
            NotificationListener checkSource = new NotificationListener() {
                public void handleNotification(Notification n, Object h) {
                    assert n.getSource().equals(name) : n.getSource();
                }
            };
            mbs.addNotificationListener(name, checkSource, null, null);
            mbs.invoke(objectName, "send", null, null);
            assert countL.count == 1;
            mbs.removeNotificationListener(name, checkSource);
            mbs.removeNotificationListener(name, countL, null, null);
        }

        mbs.invoke(objectName, "unregisterSelf", null, null);
        assert !mbs.isRegistered(objectName);
    }
}
