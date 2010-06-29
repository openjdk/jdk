/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.dyn.util;

import java.dyn.LinkagePermission;
import java.dyn.NoAccessException;
import java.lang.reflect.Modifier;
import sun.dyn.MemberName;
import sun.dyn.MethodHandleImpl;
import sun.dyn.empty.Empty;
import static java.lang.reflect.Modifier.*;

/**
 * This class centralizes information about the JVM's linkage access control.
 * @author jrose
 */
public class VerifyAccess {

    private VerifyAccess() { }  // cannot instantiate

    private static final int PACKAGE_ONLY = 0;
    private static final int ALL_ACCESS_MODES = (PUBLIC|PRIVATE|PROTECTED|PACKAGE_ONLY);

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
                                             Class<?> lookupClass) {
        // Usually refc and defc are the same, but if they differ, verify them both.
        if (refc != defc) {
            if (!isClassAccessible(refc, lookupClass)) {
                // Note that defc is verified in the switch below.
                return false;
            }
            if ((mods & (ALL_ACCESS_MODES|STATIC)) == (PROTECTED|STATIC)) {
                // Apply the special rules for refc here.
                if (!isRelatedClass(refc, lookupClass))
                    return isSamePackage(defc, lookupClass);
                // If refc == defc, the call to isPublicSuperClass will do
                // the whole job, since in that case refc (as defc) will be
                // a superclass of the lookup class.
            }
        }
        switch (mods & ALL_ACCESS_MODES) {
        case PUBLIC:
            if (refc != defc)  return true;  // already checked above
            return isClassAccessible(refc, lookupClass);
        case PROTECTED:
            return isSamePackage(defc, lookupClass) || isPublicSuperClass(defc, lookupClass);
        case PACKAGE_ONLY:
            return isSamePackage(defc, lookupClass);
        case PRIVATE:
            // Loosened rules for privates follows access rules for inner classes.
            return isSamePackageMember(defc, lookupClass);
        default:
            throw new IllegalArgumentException("bad modifiers: "+Modifier.toString(mods));
        }
    }

    static boolean isRelatedClass(Class<?> refc, Class<?> lookupClass) {
        return (refc == lookupClass ||
                refc.isAssignableFrom(lookupClass) ||
                lookupClass.isAssignableFrom(refc));
    }

    static boolean isPublicSuperClass(Class<?> defc, Class<?> lookupClass) {
        return isPublic(defc.getModifiers()) && defc.isAssignableFrom(lookupClass);
    }

    /**
     * Evaluate the JVM linkage rules for access to the given class on behalf of caller.
     * <h3>JVM Specification, 5.4.4 "Access Control"</h3>
     * A class or interface C is accessible to a class or interface D
     * if and only if either of the following conditions are true:<ul>
     * <li>C is public.
     * <li>C and D are members of the same runtime package.
     * </ul>
     */
    public static boolean isClassAccessible(Class<?> refc, Class<?> lookupClass) {
        int mods = refc.getModifiers();
        if (isPublic(mods))
            return true;
        if (isSamePackage(lookupClass, refc))
            return true;
        return false;
    }

    /**
     * Test if two classes have the same class loader and package qualifier.
     * @param class1
     * @param class2
     * @return whether they are in the same package
     */
    public static boolean isSamePackage(Class<?> class1, Class<?> class2) {
        if (class1 == class2)
            return true;
        if (!loadersAreRelated(class1.getClassLoader(), class2.getClassLoader()))
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

    /**
     * Test if two classes are defined as part of the same package member (top-level class).
     * If this is true, they can share private access with each other.
     * @param class1
     * @param class2
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

    private static boolean loadersAreRelated(ClassLoader loader1, ClassLoader loader2) {
        if (loader1 == loader2 || loader1 == null || loader2 == null) {
            return true;
        }
        for (ClassLoader scan1 = loader1;
                scan1 != null; scan1 = scan1.getParent()) {
            if (scan1 == loader2)  return true;
        }
        for (ClassLoader scan2 = loader2;
                scan2 != null; scan2 = scan2.getParent()) {
            if (scan2 == loader1)  return true;
        }
        return false;
    }

    /**
     * Ensure the requesting class have privileges to perform invokedynamic
     * linkage operations on subjectClass.  True if requestingClass is
     * Access.class (meaning the request originates from the JVM) or if the
     * classes are in the same package and have consistent class loaders.
     * (The subject class loader must be identical with or be a child of
     * the requesting class loader.)
     * @param requestingClass
     * @param subjectClass
     */
    public static void checkBootstrapPrivilege(Class requestingClass, Class subjectClass,
                                               String permissionName) {
        if (requestingClass == null)          return;
        if (requestingClass == subjectClass)  return;
        SecurityManager security = System.getSecurityManager();
        if (security == null)  return;  // open season
        if (isSamePackage(requestingClass, subjectClass))  return;
        security.checkPermission(new LinkagePermission(permissionName, requestingClass));
    }
}
