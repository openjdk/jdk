/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6252592 4967755
 * @summary Check that default fields are correctly added to Descriptors
 * and that input Descriptors are never modified
 * @author Lars Westergren
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.management.Descriptor;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.DescriptorSupport;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;

/**
 * Junit tests for bugs:
 * 6252592 Provide for the user mandatory fields missing in Descriptor given
 *         to Model*Info constructors
 * 4967755 ModelMBeanAttributeInfo constructor modifies its Descriptor argument
 *
 * @author Lars Westergren
 */
public class DefaultDescriptorFieldTest /*extends TestCase*/ {
    public static void main(String[] args) throws Exception {
        boolean fail = false;
        Object test = new DefaultDescriptorFieldTest("Test");
        for (Method m : DefaultDescriptorFieldTest.class.getMethods()) {
            if (m.getName().startsWith("test") &&
                    m.getParameterTypes().length == 0) {
                System.out.println("Testing " + m.getName());
                try {
                    m.invoke(test);
                } catch (InvocationTargetException e) {
                    fail = true;
                    Throwable t = e.getCause();
                    System.out.println("FAILED: exception: " + t);
                    t.printStackTrace(System.out);
                }
            }
        }
        if (fail)
            throw new Exception("TEST FAILED");
    }

    //No check WHICH constructor is reflected, at least when the classes tested are constructed,
    //so I just use first one that came to mind.
    final Constructor[] constArr = String.class.getConstructors();
    final Constructor dummyConstructor = constArr[0];


    Method getMethod = null;

    /** Creates a new instance of MBeanTest */
    public DefaultDescriptorFieldTest(final String name) {
        try {
            getMethod = String.class.getMethod("toString");
        } catch (SecurityException ex) {
            ex.printStackTrace();
        } catch (NoSuchMethodException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Test instantiating the 5 different classes with a null
     * Descriptor, and also setting a null descriptor after.
     * Expected: Default Descriptors created.
     */
    public void testNullDescriptors()
    throws IntrospectionException, MBeanException {
        final ModelMBeanConstructorInfo constInfo =
                new ModelMBeanConstructorInfo("Dummy", dummyConstructor, null);
        constInfo.setDescriptor(null);
        ModelMBeanAttributeInfo attInfo =
                new ModelMBeanAttributeInfo("dummyAttInfoName", "dummyAttInfoDesc", getMethod, null, null);
        attInfo.setDescriptor(null);
        ModelMBeanNotificationInfo notInfo =
                new ModelMBeanNotificationInfo(null, "notificationClassName", "daName", null);
        notInfo.setDescriptor(null);
        ModelMBeanOperationInfo opInfo =
                new ModelMBeanOperationInfo("test", getMethod, null);
        opInfo.setDescriptor(null);
        ModelMBeanInfoSupport infoSupport =
                new ModelMBeanInfoSupport(new DummyModelMBeanInfo());
        infoSupport.setDescriptor(null,null);
        infoSupport.setDescriptor(null, "mbean");
    }

    /**
     * Test instantiating and setting a Descriptor without the "name",
     * "descriptorType" or "role" fields. This also tests whether the
     * Descriptor is cloned before default values are set, since
     * default values for one class will be incorrect for the next.
     * Expected: Descriptor should be cloned, missing default values should be
     * set
     */
    public void testFieldlessDescriptor()
    throws IntrospectionException, MBeanException {
        Descriptor theNamelessOne = new DescriptorSupport();
        final ModelMBeanConstructorInfo constInfo =
                new ModelMBeanConstructorInfo("Dummy", dummyConstructor, theNamelessOne);
        constInfo.setDescriptor(theNamelessOne);
        ModelMBeanAttributeInfo attInfo =
                new ModelMBeanAttributeInfo("dummyAttInfoName", "dummyAttInfoDesc", getMethod, null, theNamelessOne);
        attInfo.setDescriptor(theNamelessOne);
        ModelMBeanNotificationInfo notInfo =
                new ModelMBeanNotificationInfo(null, "notificationClassName", "daName", theNamelessOne);
        notInfo.setDescriptor(theNamelessOne);
        ModelMBeanOperationInfo opInfo =
                new ModelMBeanOperationInfo("test", getMethod, theNamelessOne);
        opInfo.setDescriptor(theNamelessOne);
        ModelMBeanInfoSupport infoSupport = new ModelMBeanInfoSupport(new DummyModelMBeanInfo());
        infoSupport.setDescriptor(theNamelessOne, null);
        infoSupport.setDescriptor(theNamelessOne, "mbean");
    }


     /**
     * Creates an empty DescriptorSupport and then test that ModelMBeanConstructorInfo accepts
      * the correct fields in validation one by one.
     */
    public void testCorrectConstructorDescriptors()
    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Descriptor cDesc = new DescriptorSupport(new String[0],new String[0]);
        final ModelMBeanConstructorInfo constInfo =
                new ModelMBeanConstructorInfo("Dummy", dummyConstructor, cDesc);
        assertFalse(fieldRefuted(constInfo, cDesc, "name" , "java.lang.String"));
        assertFalse(fieldRefuted(constInfo, cDesc, "role" , "constructor"));
        assertFalse(fieldRefuted(constInfo, cDesc, "descriptorType" , "operation"));
    }

    /**
     * Test that ModelMBeanConstructorInfo refutes incorrect fields in validation one by one.
     */
    public void testIncorrectConstructorDescriptors()
    throws NoSuchMethodException, IllegalAccessException {
        Descriptor cDesc = new DescriptorSupport(
           new String[] {"getMethod", "setMethod", "role", "Class", "name", "descriptorType"},
           new String[] {"dummyGetMethod", "dummySetMethod", "constructor", "dummyClass", "java.lang.String", "operation"});
        final ModelMBeanConstructorInfo constInfo = new ModelMBeanConstructorInfo("Dummy", dummyConstructor, cDesc);
        assertTrue(fieldRefuted(constInfo, cDesc, "name" , "blah"));
        assertTrue(fieldRefuted(constInfo, cDesc, "descriptorType", "mbean"));
        assertTrue(fieldRefuted(constInfo, cDesc, "descriptorType", "notification"));
        assertTrue(fieldRefuted(constInfo, cDesc, "descriptorType", "constructor"));
        assertTrue(fieldRefuted(constInfo, cDesc, "role", "operation"));
    }

    public void testCorrectAttributeDescriptors()
    throws IntrospectionException, NoSuchMethodException, IllegalAccessException {
        Descriptor aDesc = new DescriptorSupport(new String[0],new String[0]);
        final ModelMBeanAttributeInfo attInfo = new ModelMBeanAttributeInfo("dummyAttInfoName", "dummyAttInfoDesc", getMethod, null, null);
        assertFalse(fieldRefuted(attInfo, aDesc, "name" , "dummyAttInfoName"));
        assertFalse(fieldRefuted(attInfo, aDesc, "descriptorType" , "attribute"));
    }

    public void testIncorrectAttributeDescriptors()
    throws IntrospectionException, NoSuchMethodException, IllegalAccessException {
        Descriptor aDesc = new DescriptorSupport();
        final ModelMBeanAttributeInfo attInfo = new ModelMBeanAttributeInfo("dummyAttInfoName", "dummyAttInfoDesc", getMethod, null, null);
        assertTrue(fieldRefuted(attInfo, aDesc, "name" , "blah"));
        assertTrue(fieldRefuted(attInfo, aDesc, "descriptorType" , "constructor"));
        assertTrue(fieldRefuted(attInfo, aDesc, "descriptorType" , "notification"));
    }

    public void testCorrectNotificationDescriptors()
    throws NoSuchMethodException, IllegalAccessException  {
        Descriptor nDesc = new DescriptorSupport();
        final ModelMBeanNotificationInfo nInfo = new ModelMBeanNotificationInfo(null, "notificationClassName", "daName", nDesc);
        assertFalse(fieldRefuted(nInfo, nDesc, "name" , "notificationClassName"));
        assertFalse(fieldRefuted(nInfo, nDesc, "descriptorType" , "notification"));
    }

    public void testIncorrectNotificationDescriptors()
    throws NoSuchMethodException, IllegalAccessException {
        Descriptor nDesc = new DescriptorSupport();
        final ModelMBeanNotificationInfo nInfo = new ModelMBeanNotificationInfo(null, "notificationClassName", "daName", nDesc);
        assertTrue(fieldRefuted(nInfo, nDesc, "name" , "blah"));
        assertTrue(fieldRefuted(nInfo, nDesc, "descriptorType" , "constructor"));
        assertTrue(fieldRefuted(nInfo, nDesc, "descriptorType" , "operation"));
    }

    public void testCorrectOperationDescriptors()
    throws NoSuchMethodException, IllegalAccessException {
        Descriptor opDesc = new DescriptorSupport();
        final ModelMBeanOperationInfo opInfo = new ModelMBeanOperationInfo("test", "readable description", null, "type", 1, opDesc);
        assertFalse(fieldRefuted(opInfo, opDesc, "name" , "test"));
        assertFalse(fieldRefuted(opInfo, opDesc, "descriptorType" , "operation"));
        assertFalse(fieldRefuted(opInfo, opDesc, "role" , "operation"));
        assertFalse(fieldRefuted(opInfo, opDesc, "role" , "getter"));
        assertFalse(fieldRefuted(opInfo, opDesc, "role" , "setter"));
    }

    public void testIncorrectOperationDescriptors()
    throws NoSuchMethodException, IllegalAccessException {
        Descriptor opDesc = new DescriptorSupport();
        final ModelMBeanOperationInfo opInfo = new ModelMBeanOperationInfo("test", "readable description", null, "type", 1, opDesc);
        assertTrue(fieldRefuted(opInfo, opDesc, "name" , "DENIED!!"));
        assertTrue(fieldRefuted(opInfo, opDesc, "descriptorType" , "constructor"));
        assertTrue(fieldRefuted(opInfo, opDesc, "descriptorType" , "attribute"));
        assertTrue(fieldRefuted(opInfo, opDesc, "descriptorType" , "notification"));
        assertTrue(fieldRefuted(opInfo, opDesc, "descriptorType" , "x"));
    }

    //TODO also test ModelMBeanInfoSupport perhaps. Slightly more difficult to set up and test.

    /**
     * Clones descriptor, sets a new value in the clone and tries to apply clone to
     * reflected Model*Info object method. The new field should be refuted if it
     * is a bad value.
     */
    //I like java, but this is one case where duck typing would have been slightly easier I think. :)
    private boolean fieldRefuted(Object mbeanInfo, Descriptor desc, String fieldName, String newValue)
    throws NoSuchMethodException, IllegalAccessException {
        Method setDescMethod = mbeanInfo.getClass().getMethod("setDescriptor", Descriptor.class);
        Descriptor newDesc = (Descriptor)desc.clone();
        boolean refused = false;
        newDesc.setField(fieldName, newValue);
        try {
            setDescMethod.invoke(mbeanInfo, newDesc);
        } catch (InvocationTargetException ex) {
            //If we get classcast exception, someone passed in a bad object to reflection.
            //Perhaps an unnecessary check, this cast?
            RuntimeOperationsException rex = (RuntimeOperationsException)ex.getTargetException();
            refused = true;
        }
        return refused;
    }

    /**
     * Dummy class used to create test objects. May not be needed.
     */
   private class DummyModelMBeanInfo implements ModelMBeanInfo {
            public Descriptor[] getDescriptors(String inDescriptorType)
            throws MBeanException, RuntimeOperationsException {
                return null;
            }

            public void setDescriptors(Descriptor[] inDescriptors)
            throws MBeanException, RuntimeOperationsException {

            }

            public Descriptor getDescriptor(String inDescriptorName, String inDescriptorType)
            throws MBeanException, RuntimeOperationsException {
                return null;
            }

            public void setDescriptor(Descriptor inDescriptor, String inDescriptorType)
            throws MBeanException, RuntimeOperationsException {

            }

            public Descriptor getMBeanDescriptor()
            throws MBeanException, RuntimeOperationsException {
                return null;
            }

            public void setMBeanDescriptor(Descriptor inDescriptor)
            throws MBeanException, RuntimeOperationsException {

            }

            public ModelMBeanAttributeInfo getAttribute(String inName)
            throws MBeanException, RuntimeOperationsException {
                return null;
            }

            public ModelMBeanOperationInfo getOperation(String inName)
            throws MBeanException, RuntimeOperationsException {
                return null;
            }

            public ModelMBeanNotificationInfo getNotification(String inName)
            throws MBeanException, RuntimeOperationsException {
                return null;
            }

            public MBeanAttributeInfo[] getAttributes() {
                return null;
            }

            public String getClassName() {
                return "AnonMBeanInfoImpl";
            }

            public MBeanConstructorInfo[] getConstructors() {
                return null;
            }

            public String getDescription() {
                return null;
            }

            public MBeanNotificationInfo[] getNotifications() {
                return null;
            }

            public MBeanOperationInfo[] getOperations() {
                return null;
            }

            public Object clone() {
                return null;

            }

    }

    private static void assertFalse(boolean x) {
        assertTrue(!x);
    }

    private static void assertTrue(boolean x) {
        if (!x)
            throw new AssertionError("Assertion failed");
    }

}
