/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.invoke.util;

import java.lang.reflect.Modifier;
import static java.lang.reflect.Modifier.*;
import sun.reflect.Reflection;

/**
 * This class centralizes information about the JVM's linkage access control.
 * @author jrose
 */
public class VerifyAccess {

    private VerifyAccess() { }  // cannot instantiate

    private static final int PACKAGE_ONLY = 0;
    private static final int PACKAGE_ALLOWED = java.lang.invoke.MethodHandles.Lookup.PACKAGE;
    private static final int PROTECTED_OR_PACKAGE_ALLOWED = (PACKAGE_ALLOWED|PROTECTED);
    private static final int ALL_ACCESS_MODES = (PUBLIC|PRIVATE|PROTECTED|PACKAGE_ONLY);
    private static final boolean ALLOW_NESTMATE_ACCESS = false;

    /**
     * Evaluate the JVM linkage rules for access to the given method
     * on behalf of a caller class which proposes to perform the access.
     * Return true if the caller class has privileges to invoke a method
     * or access a field with the given properties.
     * This requires an accessibility check of the referencing class,
     * plus an accessibility check of the member within the class,
     * which depends on the member's modifier flags.
     * <p>
     * The relevant properties include the defining class ({@code defc})
     * of the member, and its modifier flags ({@code mods}).
     * Also relevant is the class used to make the initial symbolic reference
     * to the member ({@code refc}).  If this latter class is not distinguished,
     * the defining class should be passed for both arguments ({@code defc == refc}).
     * <h3>JVM Specification, 5.4.4 "Access Control"</h3>
     * A field or method R is accessible to a class or interface D if
     * and only if any of the following conditions is true:<ul>
     * <li>R is public.
     * <li>R is protected and is declared in a class C, and D is either
     *     a subclass of C or C itself.  Furthermore, if R is not
     *     static, then the symbolic reference to R must contain a
     *     symbolic reference to a class T, such that T is either a
     *     subclass of D, a superclass of D or D itself.
     * <li>R is either protected or has default access (that is,
     *     neither public nor protected nor private), and is declared
     *     by a class in the same runtime package as D.
     * <li>R is private and is declared in D.
     * </ul>
     * This discussion of access control omits a related restriction
     * on the target of a protected field access or method invocation
     * (the target must be of class D or a subtype of D). That
     * requirement is checked as part of the verification process
     * (5.4.1); it is not part of link-time access control.
     * @param refc the class used in the symbolic reference to the proposed member
     * @param defc the class in which the proposed member is actually defined
     * @param mods modifier flags for the proposed member
     * @param lookupClass the class for which the access check is being made
     * @return true iff the the accessing class can access such a member
     */
    public static boolean isMemberAccessible(Class<?> refc,  // symbolic ref class
                                             Class<?> defc,  // actual def class
                                             int      mods,  // actual member mods
                                             Class<?> lookupClass,
                                             int      allowedModes) {
        if (allowedModes == 0)  return false;
        assert((allowedModes & PUBLIC) != 0 &&
               (allowedModes & ~(ALL_ACCESS_MODES|PACKAGE_ALLOWED)) == 0);
        // The symbolic reference class (refc) must always be fully verified.
        if (!isClassAccessible(refc, lookupClass, allowedModes)) {
            return false;
        }
        // Usually refc and defc are the same, but verify defc also in case they differ.
        if (defc == lookupClass &&
            (allowedModes & PRIVATE) != 0)
            return true;        // easy check; all self-access is OK
        switch (mods & ALL_ACCESS_MODES) {
        case PUBLIC:
            return true;  // already checked above
        case PROTECTED:
            if ((allowedModes & PROTECTED_OR_PACKAGE_ALLOWED) != 0 &&
                isSamePackage(defc, lookupClass))
                return true;
            if ((allowedModes & PROTECTED) == 0)
                return false;
            if ((mods & STATIC) != 0 &&
                !isRelatedClass(refc, lookupClass))
                return false;
            if ((allowedModes & PROTECTED) != 0 &&
                isSuperClass(defc, lookupClass))
                return true;
            return false;
        case PACKAGE_ONLY:  // That is, zero.  Unmarked member is package-only access.
            return ((allowedModes & PACKAGE_ALLOWED) != 0 &&
                    isSamePackage(defc, lookupClass));
        case PRIVATE:
            // Loosened rules for privates follows access rules for inner classes.
            return (ALLOW_NESTMATE_ACCESS &&
                    (allowedModes & PRIVATE) != 0 &&
                    isSamePackageMember(defc, lookupClass));
        default:
            throw new IllegalArgumentException("bad modifiers: "+Modifier.toString(mods));
        }
    }

    static boolean isRelatedClass(Class<?> refc, Class<?> lookupClass) {
        return (refc == lookupClass ||
                refc.isAssignableFrom(lookupClass) ||
                lookupClass.isAssignableFrom(refc));
    }

    static boolean isSuperClass(Class<?> defc, Class<?> lookupClass) {
        return defc.isAssignableFrom(lookupClass);
    }

    static int getClassModifiers(Class<?> c) {
        // This would return the mask stored by javac for the source-level modifiers.
        //   return c.getModifiers();
        // But what we need for JVM access checks are the actual bits from the class header.
        // ...But arrays and primitives are synthesized with their own odd flags:
        if (c.isArray() || c.isPrimitive())
            return c.getModifiers();
        return Reflection.getClassAccessFlags(c);
    }

    /**
     * Evaluate the JVM linkage rules for access to the given class on behalf of caller.
     * <h3>JVM Specification, 5.4.4 "Access Control"</h3>
     * A class or interface C is accessible to a class or interface D
     * if and only if either of the following conditions are true:<ul>
     * <li>C is public.
     * <li>C and D are members of the same runtime package.
     * </ul>
     * @param refc the symbolic reference class to which access is being checked (C)
     * @param lookupClass the class performing the lookup (D)
     */
    public static boolean isClassAccessible(Class<?> refc, Class<?> lookupClass,
                                            int allowedModes) {
        if (allowedModes == 0)  return false;
        assert((allowedModes & PUBLIC) != 0 &&
               (allowedModes & ~(ALL_ACCESS_MODES|PACKAGE_ALLOWED)) == 0);
        int mods = getClassModifiers(refc);
        if (isPublic(mods))
            return true;
        if ((allowedModes & PACKAGE_ALLOWED) != 0 &&
            isSamePackage(lookupClass, refc))
            return true;
        return false;
    }

    /**
     * Decide if the given method type, attributed to a member or symbolic
     * reference of a given reference class, is really visible to that class.
     * @param type the supposed type of a member or symbolic reference of refc
     * @param refc the class attempting to make the reference
     */
    public static boolean isTypeVisible(Class<?> type, Class<?> refc) {
        if (type == refc)  return true;  // easy check
        while (type.isArray())  type = type.getComponentType();
        if (type.isPrimitive() || type == Object.class)  return true;
        ClassLoader parent = type.getClassLoader();
        if (parent == null)  return true;
        ClassLoader child  = refc.getClassLoader();
        if (child == null)  return false;
        if (parent == child || loadersAreRelated(parent, child, true))
            return true;
        // Do it the hard way:  Look up the type name from the refc loader.
        try {
            Class<?> res = child.loadClass(type.getName());
            return (type == res);
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    /**
     * Decide if the given method type, attributed to a member or symbolic
     * reference of a given reference class, is really visible to that class.
     * @param type the supposed type of a member or symbolic reference of refc
     * @param refc the class attempting to make the reference
     */
    public static boolean isTypeVisible(java.lang.invoke.MethodType type, Class<?> refc) {
        for (int n = -1, max = type.parameterCount(); n < max; n++) {
            Class<?> ptype = (n < 0 ? type.returnType() : type.parameterType(n));
            if (!isTypeVisible(ptype, refc))
                return false;
        }
        return true;
    }

    /**
     * Test if two classes have the same class loader and package qualifier.
     * @param class1 a class
     * @param class2 another class
     * @return whether they are in the same package
     */
    public static boolean isSamePackage(Class<?> class1, Class<?> class2) {
        assert(!class1.isArray() && !class2.isArray());
        if (class1 == class2)
            return true;
        if (class1.getClassLoader() != class2.getClassLoader())
            return false;
        String name1 = class1.getName(), name2 = class2.getName();
        int dot = name1.lastIndexOf('.');
        if (dot != name2.lastIndexOf('.'))
            return false;
        for (int i = 0; i < dot; i++) {
            if (name1.charAt(i) != name2.charAt(i))
                return false;
        }
        return true;
    }

    /** Return the package name for this class.
     */
    public static String getPackageName(Class<?> cls) {
        assert(!cls.isArray());
        String name = cls.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0)  return "";
        return name.substring(0, dot);
    }

    /**
     * Test if two classes are defined as part of the same package member (top-level class).
     * If this is true, they can share private access with each other.
     * @param class1 a class
     * @param class2 another class
     * @return whether they are identical or nested together
     */
    public static boolean isSamePackageMember(Class<?> class1, Class<?> class2) {
        if (class1 == class2)
            return true;
        if (!isSamePackage(class1, class2))
            return false;
        if (getOutermostEnclosingClass(class1) != getOutermostEnclosingClass(class2))
            return false;
        return true;
    }

    private static Class<?> getOutermostEnclosingClass(Class<?> c) {
        Class<?> pkgmem = c;
        for (Class<?> enc = c; (enc = enc.getEnclosingClass()) != null; )
            pkgmem = enc;
        return pkgmem;
    }

    private static boolean loadersAreRelated(ClassLoader loader1, ClassLoader loader2,
                                             boolean loader1MustBeParent) {
        if (loader1 == loader2 || loader1 == null
                || (loader2 == null && !loader1MustBeParent)) {
            return true;
        }
        for (ClassLoader scan2 = loader2;
                scan2 != null; scan2 = scan2.getParent()) {
            if (scan2 == loader1)  return true;
        }
        if (loader1MustBeParent)  return false;
        // see if loader2 is a parent of loader1:
        for (ClassLoader scan1 = loader1;
                scan1 != null; scan1 = scan1.getParent()) {
            if (scan1 == loader2)  return true;
        }
        return false;
    }

    /**
     * Is the class loader of parentClass identical to, or an ancestor of,
     * the class loader of childClass?
     * @param parentClass a class
     * @param childClass another class, which may be a descendent of the first class
     * @return whether parentClass precedes or equals childClass in class loader order
     */
    public static boolean classLoaderIsAncestor(Class<?> parentClass, Class<?> childClass) {
        return loadersAreRelated(parentClass.getClassLoader(), childClass.getClassLoader(), true);
    }
}
