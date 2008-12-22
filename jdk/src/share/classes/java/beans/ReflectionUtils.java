/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.beans;

import com.sun.beans.finder.PrimitiveWrapperMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import java.util.*;

import sun.reflect.misc.MethodUtil;
import sun.reflect.misc.ConstructorUtil;
import sun.reflect.misc.ReflectUtil;

/**
 * A utility class for reflectively finding methods, constuctors and fields
 * using reflection.
 */
class ReflectionUtils {

    private static Reference methodCacheRef;

    public static boolean isPrimitive(Class type) {
        return primitiveTypeFor(type) != null;
    }

    public static Class primitiveTypeFor(Class wrapper) {
        if (wrapper == Boolean.class) return Boolean.TYPE;
        if (wrapper == Byte.class) return Byte.TYPE;
        if (wrapper == Character.class) return Character.TYPE;
        if (wrapper == Short.class) return Short.TYPE;
        if (wrapper == Integer.class) return Integer.TYPE;
        if (wrapper == Long.class) return Long.TYPE;
        if (wrapper == Float.class) return Float.TYPE;
        if (wrapper == Double.class) return Double.TYPE;
        if (wrapper == Void.class) return Void.TYPE;
        return null;
    }

    /**
     * Tests each element on the class arrays for assignability.
     *
     * @param argClasses arguments to be tested
     * @param argTypes arguments from Method
     * @return true if each class in argTypes is assignable from the
     *         corresponding class in argClasses.
     */
    private static boolean matchArguments(Class[] argClasses, Class[] argTypes) {
        return matchArguments(argClasses, argTypes, false);
    }

    /**
     * Tests each element on the class arrays for equality.
     *
     * @param argClasses arguments to be tested
     * @param argTypes arguments from Method
     * @return true if each class in argTypes is equal to the
     *         corresponding class in argClasses.
     */
    private static boolean matchExplicitArguments(Class[] argClasses, Class[] argTypes) {
        return matchArguments(argClasses, argTypes, true);
    }

    private static boolean matchArguments(Class[] argClasses,
                                          Class[] argTypes, boolean explicit) {

        boolean match = (argClasses.length == argTypes.length);
        for(int j = 0; j < argClasses.length && match; j++) {
            Class argType = argTypes[j];
            if (argType.isPrimitive()) {
                argType = PrimitiveWrapperMap.getType(argType.getName());
            }
            if (explicit) {
                // Test each element for equality
                if (argClasses[j] != argType) {
                    match = false;
                }
            } else {
                // Consider null an instance of all classes.
                if (argClasses[j] != null &&
                    !(argType.isAssignableFrom(argClasses[j]))) {
                    match = false;
                }
            }
        }
        return match;
    }

    /**
     * @return the method which best matches the signature or throw an exception
     *         if it can't be found or the method is ambiguous.
     */
    static Method getPublicMethod(Class declaringClass, String methodName,
                                           Class[] argClasses) throws NoSuchMethodException {
        Method m;

        m = findPublicMethod(declaringClass, methodName, argClasses);
        if (m == null)
            throw new NoSuchMethodException(declaringClass.getName() + "." + methodName);
        return m;
    }

    /**
     * @return the method which best matches the signature or null if it cant be found or
     *         the method is ambiguous.
     */
    public static Method findPublicMethod(Class declaringClass, String methodName,
                                           Class[] argClasses) {
        // Many methods are "getters" which take no arguments.
        // This permits the following optimisation which
        // avoids the expensive call to getMethods().
        if (argClasses.length == 0) {
            try {
                return MethodUtil.getMethod(declaringClass, methodName, argClasses);
            }
            catch (NoSuchMethodException e) {
                  return null;
            } catch (SecurityException se) {
                // fall through
            }
        }
        Method[] methods = MethodUtil.getPublicMethods(declaringClass);
        List list = new ArrayList();
        for(int i = 0; i < methods.length; i++) {
            // Collect all the methods which match the signature.
            Method method = methods[i];
            if (method.getName().equals(methodName)) {
                if (matchArguments(argClasses, method.getParameterTypes())) {
                    list.add(method);
                }
            }
        }
        if (list.size() > 0) {
            if (list.size() == 1) {
                return (Method)list.get(0);
            }
            else {
                ListIterator iterator = list.listIterator();
                Method method;
                while (iterator.hasNext()) {
                    method = (Method)iterator.next();
                    if (matchExplicitArguments(argClasses, method.getParameterTypes())) {
                        return method;
                    }
                }
                // There are more than one method which matches this signature.
                // try to return the most specific method.
                return getMostSpecificMethod(list, argClasses);
            }
        }
        return null;
    }

    /**
     * Return the most specific method from the list of methods which
     * matches the args. The most specific method will have the most
     * number of equal parameters or will be closest in the inheritance
     * heirarchy to the runtime execution arguments.
     * <p>
     * See the JLS section 15.12
     * http://java.sun.com/docs/books/jls/second_edition/html/expressions.doc.html#20448
     *
     * @param methods List of methods which already have the same param length
     *                and arg types are assignable to param types
     * @param args an array of param types to match
     * @return method or null if a specific method cannot be determined
     */
    private static Method getMostSpecificMethod(List methods, Class[] args) {
        Method method = null;

        int matches = 0;
        int lastMatch = matches;

        ListIterator iterator = methods.listIterator();
        while (iterator.hasNext()) {
            Method m = (Method)iterator.next();
            Class[] mArgs = m.getParameterTypes();
            matches = 0;
            for (int i = 0; i < args.length; i++) {
                Class mArg = mArgs[i];
                if (mArg.isPrimitive()) {
                    mArg = PrimitiveWrapperMap.getType(mArg.getName());
                }
                if (args[i] == mArg) {
                    matches++;
                }
            }
            if (matches == 0 && lastMatch == 0) {
                if (method == null) {
                    method = m;
                } else {
                    // Test existing method. We already know that the args can
                    // be assigned to all the method params. However, if the
                    // current method parameters is higher in the inheritance
                    // hierarchy then replace it.
                    if (!matchArguments(method.getParameterTypes(),
                                        m.getParameterTypes())) {
                        method = m;
                    }
                }
            } else if (matches > lastMatch) {
                lastMatch = matches;
                method = m;
            } else if (matches == lastMatch) {
                // ambiguous method selection.
                method = null;
            }
        }
        return method;
    }

    /**
     * @return the method or null if it can't be found or is ambiguous.
     */
    public static Method findMethod(Class targetClass, String methodName,
                                    Class[] argClasses) {
        Method m = findPublicMethod(targetClass, methodName, argClasses);
        if (m != null && Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
            return m;
        }

        /*
        Search the interfaces for a public version of this method.

        Example: the getKeymap() method of a JTextField
        returns a package private implementation of the
        of the public Keymap interface. In the Keymap
        interface there are a number of "properties" one
        being the "resolveParent" property implied by the
        getResolveParent() method. This getResolveParent()
        cannot be called reflectively because the class
        itself is not public. Instead we search the class's
        interfaces and find the getResolveParent()
        method of the Keymap interface - on which invoke
        may be applied without error.

        So in :-

            JTextField o = new JTextField("Hello, world");
            Keymap km = o.getKeymap();
            Method m1 = km.getClass().getMethod("getResolveParent", new Class[0]);
            Method m2 = Keymap.class.getMethod("getResolveParent", new Class[0]);

        Methods m1 and m2 are different. The invocation of method
        m1 unconditionally throws an IllegalAccessException where
        the invocation of m2 will invoke the implementation of the
        method. Note that (ignoring the overloading of arguments)
        there is only one implementation of the named method which
        may be applied to this target.
        */
        for(Class type = targetClass; type != null; type = type.getSuperclass()) {
            Class[] interfaces = type.getInterfaces();
            for(int i = 0; i < interfaces.length; i++) {
                m = findPublicMethod(interfaces[i], methodName, argClasses);
                if (m != null) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * A class that represents the unique elements of a method that will be a
     * key in the method cache.
     */
    private static class Signature {
        private Class targetClass;
        private String methodName;
        private Class[] argClasses;

        private volatile int hashCode = 0;

        public Signature(Class targetClass, String methodName, Class[] argClasses) {
            this.targetClass = targetClass;
            this.methodName = methodName;
            this.argClasses = argClasses;
        }

        public boolean equals(Object o2) {
            if (this == o2) {
                return true;
            }
            Signature that = (Signature)o2;
            if (!(targetClass == that.targetClass)) {
                return false;
            }
            if (!(methodName.equals(that.methodName))) {
                return false;
            }
            if (argClasses.length != that.argClasses.length) {
                return false;
            }
            for (int i = 0; i < argClasses.length; i++) {
                if (!(argClasses[i] == that.argClasses[i])) {
                  return false;
                }
            }
            return true;
        }

        /**
         * Hash code computed using algorithm suggested in
         * Effective Java, Item 8.
         */
        public int hashCode() {
            if (hashCode == 0) {
                int result = 17;
                result = 37 * result + targetClass.hashCode();
                result = 37 * result + methodName.hashCode();
                if (argClasses != null) {
                    for (int i = 0; i < argClasses.length; i++) {
                        result = 37 * result + ((argClasses[i] == null) ? 0 :
                            argClasses[i].hashCode());
                    }
                }
                hashCode = result;
            }
            return hashCode;
        }
    }

    /**
     * A wrapper to findMethod(), which will search or populate the method
     * in a cache.
     * @throws exception if the method is ambiguios.
     */
    public static synchronized Method getMethod(Class targetClass,
                                                String methodName,
                                                Class[] argClasses) {
        Object signature = new Signature(targetClass, methodName, argClasses);

        Method method = null;
        Map methodCache = null;
        boolean cache = false;
        if (ReflectUtil.isPackageAccessible(targetClass)) {
            cache = true;
        }

        if (cache && methodCacheRef != null &&
            (methodCache = (Map)methodCacheRef.get()) != null) {
            method = (Method)methodCache.get(signature);
            if (method != null) {
                return method;
            }
        }
        method = findMethod(targetClass, methodName, argClasses);
        if (cache && method != null) {
            if (methodCache == null) {
                methodCache = new HashMap();
                methodCacheRef = new SoftReference(methodCache);
            }
            methodCache.put(signature, method);
        }
        return method;
    }

    /**
     * Return a constructor on the class with the arguments.
     *
     * @throws exception if the method is ambiguios.
     */
    public static Constructor getConstructor(Class cls, Class[] args) {
        Constructor constructor = null;

        // PENDING: Implement the resolutuion of ambiguities properly.
        Constructor[] ctors = ConstructorUtil.getConstructors(cls);
        for(int i = 0; i < ctors.length; i++) {
            if (matchArguments(args, ctors[i].getParameterTypes())) {
                constructor = ctors[i];
            }
        }
        return constructor;
    }

    public static Object getPrivateField(Object instance, Class cls, String name) {
        return getPrivateField(instance, cls, name, null);
    }

    /**
     * Returns the value of a private field.
     *
     * @param instance object instance
     * @param cls class
     * @param name name of the field
     * @param el an exception listener to handle exceptions; or null
     * @return value of the field; null if not found or an error is encountered
     */
    public static Object getPrivateField(Object instance, Class cls,
                                         String name, ExceptionListener el) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(instance);
        }
        catch (Exception e) {
            if (el != null) {
                el.exceptionThrown(e);
            }
        }
        return null;
    }
}
