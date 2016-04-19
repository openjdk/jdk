/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;


import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.VM;

/** Common utility routines used by both java.lang and
    java.lang.reflect */

public class Reflection {

    /** Used to filter out fields and methods from certain classes from public
        view, where they are sensitive or they may contain VM-internal objects.
        These Maps are updated very rarely. Rather than synchronize on
        each access, we use copy-on-write */
    private static volatile Map<Class<?>,String[]> fieldFilterMap;
    private static volatile Map<Class<?>,String[]> methodFilterMap;

    static {
        Map<Class<?>,String[]> map = new HashMap<Class<?>,String[]>();
        map.put(Reflection.class,
            new String[] {"fieldFilterMap", "methodFilterMap"});
        map.put(System.class, new String[] {"security"});
        map.put(Class.class, new String[] {"classLoader"});
        fieldFilterMap = map;

        methodFilterMap = new HashMap<>();
    }

    /** Returns the class of the caller of the method calling this method,
        ignoring frames associated with java.lang.reflect.Method.invoke()
        and its implementation. */
    @CallerSensitive
    @HotSpotIntrinsicCandidate
    public static native Class<?> getCallerClass();

    /**
     * @deprecated This method will be removed.
     * This method is a private JDK API and retained temporarily to
     * simplify the implementation of sun.misc.Reflection.getCallerClass.
     */
    @Deprecated(forRemoval=true)
    public static native Class<?> getCallerClass(int depth);

    /** Retrieves the access flags written to the class file. For
        inner classes these flags may differ from those returned by
        Class.getModifiers(), which searches the InnerClasses
        attribute to find the source-level access flags. This is used
        instead of Class.getModifiers() for run-time access checks due
        to compatibility reasons; see 4471811. Only the values of the
        low 13 bits (i.e., a mask of 0x1FFF) are guaranteed to be
        valid. */
    @HotSpotIntrinsicCandidate
    public static native int getClassAccessFlags(Class<?> c);


    public static void ensureMemberAccess(Class<?> currentClass,
                                          Class<?> memberClass,
                                          Object target,
                                          int modifiers)
        throws IllegalAccessException
    {
        if (currentClass == null || memberClass == null) {
            throw new InternalError();
        }

        if (!verifyMemberAccess(currentClass, memberClass, target, modifiers)) {
            throwIllegalAccessException(currentClass, memberClass, target, modifiers);
        }
    }

    public static boolean verifyMemberAccess(Class<?> currentClass,
                                             // Declaring class of field
                                             // or method
                                             Class<?> memberClass,
                                             // May be NULL in case of statics
                                             Object   target,
                                             int      modifiers)
    {
        // Verify that currentClass can access a field, method, or
        // constructor of memberClass, where that member's access bits are
        // "modifiers".

        boolean gotIsSameClassPackage = false;
        boolean isSameClassPackage = false;

        if (currentClass == memberClass) {
            // Always succeeds
            return true;
        }

        if (!verifyModuleAccess(currentClass, memberClass)) {
            return false;
        }

        if (!Modifier.isPublic(getClassAccessFlags(memberClass))) {
            isSameClassPackage = isSameClassPackage(currentClass, memberClass);
            gotIsSameClassPackage = true;
            if (!isSameClassPackage) {
                return false;
            }
        }

        // At this point we know that currentClass can access memberClass.

        if (Modifier.isPublic(modifiers)) {
            return true;
        }

        boolean successSoFar = false;

        if (Modifier.isProtected(modifiers)) {
            // See if currentClass is a subclass of memberClass
            if (isSubclassOf(currentClass, memberClass)) {
                successSoFar = true;
            }
        }

        if (!successSoFar && !Modifier.isPrivate(modifiers)) {
            if (!gotIsSameClassPackage) {
                isSameClassPackage = isSameClassPackage(currentClass,
                                                        memberClass);
                gotIsSameClassPackage = true;
            }

            if (isSameClassPackage) {
                successSoFar = true;
            }
        }

        if (!successSoFar) {
            return false;
        }

        if (Modifier.isProtected(modifiers)) {
            // Additional test for protected members: JLS 6.6.2
            Class<?> targetClass = (target == null ? memberClass : target.getClass());
            if (targetClass != currentClass) {
                if (!gotIsSameClassPackage) {
                    isSameClassPackage = isSameClassPackage(currentClass, memberClass);
                    gotIsSameClassPackage = true;
                }
                if (!isSameClassPackage) {
                    if (!isSubclassOf(targetClass, currentClass)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Returns {@code true} if memberClass's's module exports memberClass's
     * package to currentClass's module.
     */
    public static boolean verifyModuleAccess(Class<?> currentClass,
                                             Class<?> memberClass) {
        return verifyModuleAccess(currentClass.getModule(), memberClass);
    }

    public static boolean verifyModuleAccess(Module currentModule, Class<?> memberClass) {
        Module memberModule = memberClass.getModule();

        // module may be null during startup (initLevel 0)
        if (currentModule == memberModule)
           return true;  // same module (named or unnamed)

        // memberClass may be primitive or array class
        Class<?> c = memberClass;
        while (c.isArray()) {
            c = c.getComponentType();
        }
        if (c.isPrimitive())
            return true;

        // check that memberModule exports the package to currentModule
        return memberModule.isExported(c.getPackageName(), currentModule);
    }

    /**
     * Returns true if two classes in the same package.
     */
    private static boolean isSameClassPackage(Class<?> c1, Class<?> c2) {
        if (c1.getClassLoader() != c2.getClassLoader())
            return false;
        while (c1.isArray())
            c1 = c1.getComponentType();
        while (c2.isArray())
            c2 = c2.getComponentType();
        return Objects.equals(c1.getPackageName(), c2.getPackageName());
    }

    static boolean isSubclassOf(Class<?> queryClass,
                                Class<?> ofClass)
    {
        while (queryClass != null) {
            if (queryClass == ofClass) {
                return true;
            }
            queryClass = queryClass.getSuperclass();
        }
        return false;
    }

    // fieldNames must contain only interned Strings
    public static synchronized void registerFieldsToFilter(Class<?> containingClass,
                                              String ... fieldNames) {
        fieldFilterMap =
            registerFilter(fieldFilterMap, containingClass, fieldNames);
    }

    // methodNames must contain only interned Strings
    public static synchronized void registerMethodsToFilter(Class<?> containingClass,
                                              String ... methodNames) {
        methodFilterMap =
            registerFilter(methodFilterMap, containingClass, methodNames);
    }

    private static Map<Class<?>,String[]> registerFilter(Map<Class<?>,String[]> map,
            Class<?> containingClass, String ... names) {
        if (map.get(containingClass) != null) {
            throw new IllegalArgumentException
                            ("Filter already registered: " + containingClass);
        }
        map = new HashMap<Class<?>,String[]>(map);
        map.put(containingClass, names);
        return map;
    }

    public static Field[] filterFields(Class<?> containingClass,
                                       Field[] fields) {
        if (fieldFilterMap == null) {
            // Bootstrapping
            return fields;
        }
        return (Field[])filter(fields, fieldFilterMap.get(containingClass));
    }

    public static Method[] filterMethods(Class<?> containingClass, Method[] methods) {
        if (methodFilterMap == null) {
            // Bootstrapping
            return methods;
        }
        return (Method[])filter(methods, methodFilterMap.get(containingClass));
    }

    private static Member[] filter(Member[] members, String[] filteredNames) {
        if ((filteredNames == null) || (members.length == 0)) {
            return members;
        }
        int numNewMembers = 0;
        for (Member member : members) {
            boolean shouldSkip = false;
            for (String filteredName : filteredNames) {
                if (member.getName() == filteredName) {
                    shouldSkip = true;
                    break;
                }
            }
            if (!shouldSkip) {
                ++numNewMembers;
            }
        }
        Member[] newMembers =
            (Member[])Array.newInstance(members[0].getClass(), numNewMembers);
        int destIdx = 0;
        for (Member member : members) {
            boolean shouldSkip = false;
            for (String filteredName : filteredNames) {
                if (member.getName() == filteredName) {
                    shouldSkip = true;
                    break;
                }
            }
            if (!shouldSkip) {
                newMembers[destIdx++] = member;
            }
        }
        return newMembers;
    }

    /**
     * Tests if the given method is caller-sensitive and the declaring class
     * is defined by either the bootstrap class loader or platform class loader.
     */
    public static boolean isCallerSensitive(Method m) {
        final ClassLoader loader = m.getDeclaringClass().getClassLoader();
        if (VM.isSystemDomainLoader(loader) || isExtClassLoader(loader))  {
            return m.isAnnotationPresent(CallerSensitive.class);
        }
        return false;
    }

    private static boolean isExtClassLoader(ClassLoader loader) {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        while (cl != null) {
            if (cl.getParent() == null && cl == loader) {
                return true;
            }
            cl = cl.getParent();
        }
        return false;
    }


    // true to print a stack trace when IAE is thrown
    private static volatile boolean printStackWhenAccessFails;

    // true if printStackWhenAccessFails has been initialized
    private static volatile boolean printStackWhenAccessFailsSet;

    private static void printStackTraceIfNeeded(Throwable e) {
        if (!printStackWhenAccessFailsSet && VM.initLevel() >= 1) {
            // can't use method reference here, might be too early in startup
            PrivilegedAction<Boolean> pa = new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    String s;
                    s = System.getProperty("sun.reflect.debugModuleAccessChecks");
                    return (s != null && !s.equalsIgnoreCase("false"));
                }
            };
            printStackWhenAccessFails = AccessController.doPrivileged(pa);
            printStackWhenAccessFailsSet = true;
        }
        if (printStackWhenAccessFails) {
            e.printStackTrace();
        }
    }

    /**
     * Throws IllegalAccessException with the an exception message based on
     * the access that is denied.
     */
    private static void throwIllegalAccessException(Class<?> currentClass,
                                                    Class<?> memberClass,
                                                    Object target,
                                                    int modifiers)
        throws IllegalAccessException
    {
        String currentSuffix = "";
        String memberSuffix = "";
        Module m1 = currentClass.getModule();
        if (m1.isNamed())
            currentSuffix = " (in " + m1 + ")";
        Module m2 = memberClass.getModule();
        if (m2.isNamed())
            memberSuffix = " (in " + m2 + ")";

        Class<?> c = memberClass;
        while (c.isArray()) {
            c = c.getComponentType();
        }
        String memberPackageName = c.getPackageName();

        String msg = currentClass + currentSuffix + " cannot access ";
        if (m2.isExported(memberPackageName, m1)) {

            // module access okay so include the modifiers in the message
            msg += "a member of " + memberClass + memberSuffix +
                    " with modifiers \"" + Modifier.toString(modifiers) + "\"";

        } else {
            // module access failed
            msg += memberClass + memberSuffix+ " because "
                   + m2 + " does not export " + memberPackageName;
            if (m2.isNamed()) msg += " to " + m1;
        }

        throwIllegalAccessException(msg);
    }

    /**
     * Throws IllegalAccessException with the given exception message.
     */
    public static void throwIllegalAccessException(String msg)
        throws IllegalAccessException
    {
        IllegalAccessException e = new IllegalAccessException(msg);
        printStackTraceIfNeeded(e);
        throw e;
    }

    /**
     * Throws InaccessibleObjectException with the given exception message.
     */
    public static void throwInaccessibleObjectException(String msg) {
        InaccessibleObjectException e = new InaccessibleObjectException(msg);
        printStackTraceIfNeeded(e);
        throw e;
    }

}
