/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
package sun.swing;

import java.security.*;
import java.lang.reflect.*;

/**
 * A utility for accessing and invoking methods, via reflection,
 * that would otherwise be unaccessible.
 *
 * @author Shannon Hickey
 */
public class AccessibleMethod {

    private final Method method;

    /**
     * Construct an instance for the given params.
     *
     * @param klass the class to which the method belongs
     * @param methodName the name of the method
     * @param paramTypes the paramater type array
     * @throws NullPointerException if <code>klass</code>
     *         or <code>name</code> is <code>null</code>
     * @throws NoSuchMethodException if the method can't be found
     */
    public AccessibleMethod(Class klass,
                            String methodName,
                            Class ... paramTypes) throws NoSuchMethodException {
        try {
            method = AccessController.doPrivileged(
                new AccessMethodAction(klass, methodName, paramTypes));
        } catch (PrivilegedActionException e) {
            throw (NoSuchMethodException)e.getCause();
        }
    }

    /**
     * Invoke the method that this object represents.
     * Has the same behavior and throws the same exceptions as
     * <code>java.lang.reflect.Method.invoke</code> with one
     * exception: This method does not throw
     * <code>IllegalAccessException</code> since the target
     * method has already been made accessible.
     *
     * @param obj the object the underlying method is invoked from
     * @param args the arguments used for the method call
     * @return the result of dispatching the method represented by
     *         this object on <code>obj</code> with parameters
     *         <code>args</code>
     * @see java.lang.reflect.Method#invoke
     */
    public Object invoke(Object obj, Object ... args)
            throws IllegalArgumentException, InvocationTargetException {

        try {
            return method.invoke(obj, args);
        } catch (IllegalAccessException e) {
            // should never happen since we've made it accessible
            throw new AssertionError("accessible method inaccessible");
        }
    }

    /**
     * Invoke the method that this object represents, with the
     * expectation that the method being called throws no
     * checked exceptions.
     * <p>
     * Simply calls <code>this.invoke(obj, args)</code>
     * but catches any <code>InvocationTargetException</code>
     * and returns the cause wrapped in a runtime exception.
     *
     * @param obj the object the underlying method is invoked from
     * @param args the arguments used for the method call
     * @return the result of dispatching the method represented by
     *         this object on <code>obj</code> with parameters
     *         <code>args</code>
     * @see #invoke
     */
    public Object invokeNoChecked(Object obj, Object ... args) {
        try {
            return invoke(obj, args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException)ex.getCause();
            } else {
                throw new RuntimeException(ex.getCause());
            }
        }
    }

    /** The action used to fetch the method and make it accessible */
    private static class AccessMethodAction implements PrivilegedExceptionAction<Method> {
        private final Class<?> klass;
        private final String methodName;
        private final Class[] paramTypes;

        public AccessMethodAction(Class klass,
                                  String methodName,
                                  Class ... paramTypes) {

            this.klass = klass;
            this.methodName = methodName;
            this.paramTypes = paramTypes;
        }

        public Method run() throws NoSuchMethodException {
            Method method = klass.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method;
        }
    }
}
