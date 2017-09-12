/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Objects;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

import javax.rmi.CORBA.Util;
import javax.rmi.PortableRemoteObject;

import org.omg.CORBA_2_3.ORB;
import org.omg.CORBA_2_3.portable.OutputStream;
import org.omg.CORBA_2_3.portable.InputStream;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import org.testng.TestNG;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.*
 * @compile ObjectStreamTest.java  ObjectStreamTest$_Echo_Stub.java
 *          ObjectStreamTest$_Server_Tie.java
 * @modules java.base/java.io:open
 *          java.corba/com.sun.corba.se.impl.io:+open
 *          java.corba/com.sun.corba.se.impl.activation
 * @summary Tests of ReflectionFactory use in IIOP Serialization
 * @run testng/othervm ObjectStreamTest
 * @run testng/othervm/policy=security.policy ObjectStreamTest
 */

@Test
public class ObjectStreamTest {

    enum Colors {RED, GREEN, YELLOW}

    static Set<Colors> colorSet = new HashSet<>();

    static {
        colorSet.add(Colors.RED);
        colorSet.add(Colors.GREEN);
    }

    @DataProvider(name = "Objects")
    static Object[][] patterns() {
        BigInteger bigInteger = new BigInteger("8943892002309239");
        InetAddress inetAddr;
        try {
            inetAddr = java.net.InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (UnknownHostException ignored) {
            inetAddr = null;
            // ignored
        }
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("BigInteger", bigInteger);
        hashMap.put("InetAddress", inetAddr);
        hashMap.put("String", "bString");
        Object[][] patterns = new Object[][]{
                {"aString"},
                {Integer.valueOf(5)},
                {new SimpleObject(4, 4.0f)},
                {Arrays.asList("a", "b", "c")},
                {new String[]{"x", "y", "z"}},
                {new ArrayList<Object>(1)},     // uses readObject/writeObject
                {new StringBuffer("abc")},      // Has serialPersistentFields
                {new StringBuilder("abc")},
                {Colors.RED},
                {inetAddr},
                {LocalTime.MIDNIGHT},           // uses writeReplace/readResolve
                {new LongAdder()},              // uses writeReplace/readResolve
                {EnumSet.allOf(Colors.class)},  // used writeReplace/readResolve
                {bigInteger},
                {new BigDecimal(bigInteger)},
                {hashMap},
                {new PropertyPermission("abc", "read")}, // has serialPersistentFields
        };
        return patterns;
    }


    /**
     * Check ObjectStreamClass facts match between core serialization and CORBA.
     *
     * @param value
     */
    @Test(dataProvider = "Objects")
    void factCheck(Serializable value) {
        Class<?> clazz = value.getClass();
        java.io.ObjectStreamClass sOSC = java.io.ObjectStreamClass.lookup(clazz);
        java.io.ObjectStreamField[] sFields = sOSC.getFields();
        com.sun.corba.se.impl.io.ObjectStreamClass cOSC = corbaLookup(clazz);
        com.sun.corba.se.impl.io.ObjectStreamField[] cFields = cOSC.getFields();

        Assert.assertEquals(sFields.length, cFields.length, "Different number of fields");
        for (int i = 0; i < sFields.length; i++) {
            Assert.assertEquals(sFields[i].getName(), cFields[i].getName(),
                    "different field names " + cFields[i].getName());
            Assert.assertEquals(sFields[i].getType(), cFields[i].getType(),
                    "different field types " + cFields[i].getName());
            Assert.assertEquals(sFields[i].getTypeString(), cFields[i].getTypeString(),
                    "different field typestrings " + cFields[i].getName());
        }

        Assert.assertEquals(baseMethod("hasReadObjectMethod", sOSC, (Class<?>[]) null),
                corbaMethod("hasReadObject", cOSC, (Class<?>[]) null),
                "hasReadObject: " + value.getClass());

        Assert.assertEquals(baseMethod("hasWriteObjectMethod", sOSC, (Class<?>[]) null),
                corbaMethod("hasWriteObject", cOSC, (Class<?>[]) null),
                "hasWriteObject: " + value.getClass());

        Assert.assertEquals(baseMethod("hasWriteReplaceMethod", sOSC, (Class<?>[]) null),
                corbaMethod("hasWriteReplaceMethod", cOSC, (Class<?>[]) null),
                "hasWriteReplace: " + value.getClass());

        Assert.assertEquals(baseMethod("hasReadResolveMethod", sOSC, (Class<?>[]) null),
                corbaMethod("hasReadResolveMethod", cOSC, (Class<?>[]) null),
                "hasReadResolve: " + value.getClass());

        Assert.assertEquals(baseMethod("getSerialVersionUID", sOSC, (Class<?>[]) null),
                corbaMethod("getSerialVersionUID", cOSC, (Class<?>[]) null),
                "getSerialVersionUID: " + value.getClass());

    }


    /**
     * Test that objects written using Util.writeAny can be serialized
     * and deserialized using Util.readAny to equivalent objects.
     */
    @Test(dataProvider = "Objects", enabled = true, dependsOnMethods = {"factCheck"})
    void WriteValueObjectStreamTest01(Serializable value) throws Exception {
        ORB orb = (ORB) ORB.init(new String[0], null);

        OutputStream out = (OutputStream) orb.create_output_stream();
        Util.writeAny(out, value);

        InputStream in = (InputStream) out.create_input_stream();
        Object actual = Util.readAny(in);

        checkEquals(actual, value);
    }

    /**
     * Test that objects can be echoed to a server and come back equivalent.
     */
    @Test(dataProvider = "Objects", enabled = true, dependsOnMethods = {"factCheck"})
    void echoObjects(Serializable value) throws Exception {
        Echo echo = getEchoStub();
        Object actual = echo.echo(value);
        checkEquals(actual, value);
    }


    /**
     * Initialize the ORB and the singleton Echo server stub.
     * @return the stub for the Echo server.
     * @throws RemoteException if an error occurs
     */
    synchronized Echo getEchoStub() throws RemoteException {
        if (echoStub == null) {
            ORB orb = (ORB) ORB.init(new String[0], null);
            Echo server = new Server();
            echoStub = (javax.rmi.CORBA.Stub) PortableRemoteObject.toStub(server);
            echoStub.connect(orb);
        }
        return (Echo)echoStub;
    }

    /**
     * The stub for the Echo Server class. Initialized on first use.
     */
    private javax.rmi.CORBA.Stub echoStub;

    /**
     * After all the tests run shutdown the orb.
     */
    @AfterClass
    void shutdownOrb() {
        ORB orb = (ORB) ORB.init(new String[0], null);
        orb.shutdown(true);
    }

    /**
     * Check if the value and result are equals, with some tests depending on the type.
     * @param expected the expected value
     * @param actual the actual value
     */
    static void checkEquals(Object actual, Object expected) {
        Class<?> cl = expected.getClass();
        Assert.assertEquals(actual.getClass(), cl,
                "type of value not equal to class of result");
        try {
            if (cl.isArray() || !(cl.getDeclaredMethod("equals", cl) == null)) {
                Assert.assertEquals(actual, expected, "echo'd object not equal");
            } else {
                Assert.assertEquals(toString(actual), toString(expected),
                        "toString values not equal");
            }
        } catch (NoSuchMethodException ex) {
            Assert.assertEquals(toString(actual), toString(expected),
                    "toString values not equal");
        }
    }

    /**
     * Convert an object to a String, and correctly for arrays.
     * @param obj an object
     * @return the tostring for the object.
     */
    static String toString(Object obj) {
        return obj.getClass().isArray()
                ? Arrays.toString((Object[]) obj)
                : Objects.toString(obj);
    }

    /**
     * SimpleObject to test round trip.
     */
    static class SimpleObject implements Serializable {
        private static final long serialVersionUID = 5217577841494640354L;

        private int i = 0;
        private float f = 0.0f;

        SimpleObject(int i, float f) {
            this.i = i;
            this.f = f;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SimpleObject that = (SimpleObject) o;

            if (i != that.i) return false;
            return Float.compare(that.f, f) == 0;

        }

        @Override
        public int hashCode() {
            int result = i;
            result = 31 * result + (f != +0.0f ? Float.floatToIntBits(f) : 0);
            return result;
        }

        @Override
        public String toString() {
            return "SimpleObject{" +
                    "i=" + i +
                    ", f=" + f +
                    '}';
        }
    }


    /**
     * Lookup the CORBA ObjectStreamClass instance for a class.
     * @param clazz the class
     * @return the CORBA ObjectStreamClass instance for the class
     */
    static com.sun.corba.se.impl.io.ObjectStreamClass corbaLookup(Class<?> clazz) {
        Class<?> oscClass = com.sun.corba.se.impl.io.ObjectStreamClass.class;

        try {
            Method meth = oscClass.getDeclaredMethod("lookup", Class.class);
            meth.setAccessible(true);
            return (com.sun.corba.se.impl.io.ObjectStreamClass) meth.invoke(null, clazz);
        } catch (NoSuchMethodException noMeth) {
            throw new RuntimeException("missing method", noMeth);
        } catch (IllegalAccessException | InvocationTargetException rex) {
            throw new RuntimeException("invocation failed", rex);
        }
    }

    /**
     * Lookup aand invoke method on a serializable object via the CORBA ObjectStreamClass.
     * @param methodName method name
     * @param osc CORBA ObjectStreamClass
     * @param argClasses method arguments
     * @return the value returned from invoking the method
     */
    static Object corbaMethod(String methodName,
                              com.sun.corba.se.impl.io.ObjectStreamClass osc,
                              Class<?>... argClasses) {
        Class<?> oscClass = com.sun.corba.se.impl.io.ObjectStreamClass.class;

        try {
            Method meth = oscClass.getDeclaredMethod(methodName, argClasses);
            meth.setAccessible(true);
            return meth.invoke(osc);

        } catch (NoSuchMethodException noMeth) {
            throw new RuntimeException("missing method" + osc.getName()
                    + "::" + methodName, noMeth);
        } catch (IllegalAccessException | InvocationTargetException rex) {
            throw new RuntimeException("invocation failed", rex);
        }
    }


    /**
     * Lookup aand invoke method on a serializable object via java.io.ObjectStreamClass.
     * @param methodName method name
     * @param osc java.io.ObjectStreamClass
     * @param argClasses method arguments
     * @return the value returned from invoking the method
     */
    static Object baseMethod(String methodName, java.io.ObjectStreamClass osc,
                             Class<?>... argClasses) {
        Class<?> oscClass = java.io.ObjectStreamClass.class;

        try {
            Method meth = oscClass.getDeclaredMethod(methodName, argClasses);
            meth.setAccessible(true);
            return meth.invoke(osc);

        } catch (NoSuchMethodException noMeth) {
            throw new RuntimeException("missing method: " + osc.getName()
                    + "::" + methodName, noMeth);
        } catch (IllegalAccessException | InvocationTargetException rex) {
            throw new RuntimeException("invocation failed", rex);
        }
    }

    /**
     * Simple echo interface to check IIOP serialization/deserialization.
     */
    interface Echo extends Remote {
        Object echo(Object obj) throws RemoteException;
    }

    static class Server extends PortableRemoteObject implements Echo {

        public Server() throws RemoteException {
            super();
        }

        public Object echo(Object obj) {
            return obj;
        }
    }

    // Main can be used to run the tests from the command line with only testng.jar.
    @SuppressWarnings("raw_types")
    @Test(enabled = false)
    public static void main(String[] args) {
        Class<?>[] testclass = {ObjectStreamTest.class};
        TestNG testng = new TestNG();
        testng.setTestClasses(testclass);
        testng.run();
    }

}
