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
 * @bug 6734813
 * @summary Test the ObjectName.valueOf methods
 * @author Eamonn McManus
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Hashtable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public class ValueOfTest {
    public static void main(String[] args) throws Exception {
        // Calls that should work
        testPositive("d:foo=bar,baz=buh");
        testPositive("foo", "bar", "baz");
        Hashtable<String, String> h = new Hashtable<String, String>();
        h.put("foo", "bar");
        h.put("baz", "buh");
        testPositive("domain", h);

        // Calls that should not work
        testNegative("d");
        testNegative("d:");
        testNegative("d::foo=bar");
        testNegative("d:", "foo", "bar");
        testNegative("d", "foo=", "bar");
        testNegative("d:", h);
        testNegative("d", new Hashtable<String, String>());
    }

    private static void testPositive(Object... args) throws Exception {
        Method valueOf = valueOfMethod(args);
        Method getInstance = getInstanceMethod(args);
        Constructor<?> constructor = constructor(args);

        Object valueOfValue = valueOf.invoke(null, args);
        Object getInstanceValue = getInstance.invoke(null, args);
        Object constructorValue = constructor.newInstance(args);

        String argString =
                Arrays.toString(args).replace('[', '(').replace(']', ')');

        if (!valueOfValue.equals(getInstanceValue)) {
            throw new Exception(
                    "valueOf" + argString + " differs from getInstance" +
                    argString);
        }

        if (!valueOfValue.equals(constructorValue)) {
            throw new Exception(
                    "valueOf" + argString + " differs from new ObjectName " +
                    argString);
        }

        System.out.println("OK: valueOf" + argString);
    }

    private static void testNegative(Object... args) throws Exception {
        Method valueOf = valueOfMethod(args);
        Method getInstance = getInstanceMethod(args);

        String argString =
                Arrays.toString(args).replace('[', '(').replace(']', ')');

        final Throwable valueOfException;
        try {
            valueOf.invoke(null, args);
            throw new Exception("valueOf" + argString + " did not fail but should");
        } catch (InvocationTargetException e) {
            valueOfException = e.getCause();
        }
        if (!(valueOfException instanceof IllegalArgumentException)) {
            throw new Exception(
                    "valueOf" + argString + " threw " +
                    valueOfException.getClass().getName() + " instead of " +
                    "IllegalArgumentException", valueOfException);
        }

        final Throwable valueOfCause = valueOfException.getCause();
        if (!(valueOfCause instanceof MalformedObjectNameException)) {
            throw new Exception(
                    "valueOf" + argString + " threw exception with wrong " +
                    "type of cause", valueOfCause);
        }

        if (!valueOfException.getMessage().equals(valueOfCause.getMessage())) {
            // The IllegalArgumentException should have the same message as
            // the MalformedObjectNameException it wraps.
            // This isn't specified but is desirable.
            throw new Exception(
                    "valueOf" + argString + ": message in wrapping " +
                    "IllegalArgumentException (" + valueOfException.getMessage() +
                    ") differs from message in wrapped " +
                    "MalformedObjectNameException (" + valueOfCause.getMessage() +
                    ")");
        }

        final Throwable getInstanceException;
        try {
            getInstance.invoke(null, args);
            throw new Exception("getInstance" + argString + " did not fail but should");
        } catch (InvocationTargetException e) {
            getInstanceException = e.getCause();
        }
        if (!(getInstanceException instanceof MalformedObjectNameException)) {
            throw new Exception(
                    "getInstance" + argString + " threw wrong exception",
                    getInstanceException);
        }

        if (!valueOfException.getMessage().equals(getInstanceException.getMessage())) {
            // Again this is not specified.
            throw new Exception(
                    "Exception message from valueOf" + argString + " (" +
                    valueOfException.getMessage() + ") differs from message " +
                    "from getInstance" + argString + " (" +
                    getInstanceException.getMessage() + ")");
        }

        System.out.println("OK (correct exception): valueOf" + argString);
    }

    private static Method valueOfMethod(Object[] args) throws Exception {
        return method("valueOf", args);
    }

    private static Method getInstanceMethod(Object[] args) throws Exception {
        return method("getInstance", args);
    }

    private static Method method(String name, Object[] args) throws Exception {
        Class<?>[] argTypes = argTypes(args);
        return ObjectName.class.getMethod(name, argTypes);
    }

    private static Constructor<?> constructor(Object[] args) throws Exception {
        Class<?>[] argTypes = argTypes(args);
        return ObjectName.class.getConstructor(argTypes);
    }

    private static Class<?>[] argTypes(Object[] args) {
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++)
            argTypes[i] = args[i].getClass();
        return argTypes;
    }
}
