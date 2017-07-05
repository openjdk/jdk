/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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


package sun.reflect.misc;

import java.lang.reflect.Modifier;
import sun.reflect.Reflection;

public final class ReflectUtil {

    private ReflectUtil() {
    }

    public static Class forName(String name)
        throws ClassNotFoundException {
        checkPackageAccess(name);
        return Class.forName(name);
    }

    public static Object newInstance(Class cls)
        throws InstantiationException, IllegalAccessException {
        checkPackageAccess(cls);
        return cls.newInstance();
    }

    /*
     * Reflection.ensureMemberAccess is overly-restrictive
     * due to a bug. We awkwardly work around it for now.
     */
    public static void ensureMemberAccess(Class currentClass,
                                          Class memberClass,
                                          Object target,
                                          int modifiers)
        throws IllegalAccessException
    {
        if (target == null && Modifier.isProtected(modifiers)) {
            int mods = modifiers;
            mods = mods & (~Modifier.PROTECTED);
            mods = mods | Modifier.PUBLIC;

            /*
             * See if we fail because of class modifiers
             */
            Reflection.ensureMemberAccess(currentClass,
                                          memberClass,
                                          target,
                                          mods);
            try {
                /*
                 * We're still here so class access was ok.
                 * Now try with default field access.
                 */
                mods = mods & (~Modifier.PUBLIC);
                Reflection.ensureMemberAccess(currentClass,
                                              memberClass,
                                              target,
                                              mods);
                /*
                 * We're still here so access is ok without
                 * checking for protected.
                 */
                return;
            } catch (IllegalAccessException e) {
                /*
                 * Access failed but we're 'protected' so
                 * if the test below succeeds then we're ok.
                 */
                if (isSubclassOf(currentClass, memberClass)) {
                    return;
                } else {
                    throw e;
                }
            }
        } else {
            Reflection.ensureMemberAccess(currentClass,
                                          memberClass,
                                          target,
                                          modifiers);
        }
    }

    private static boolean isSubclassOf(Class queryClass,
                                Class ofClass)
    {
        while (queryClass != null) {
            if (queryClass == ofClass) {
                return true;
            }
            queryClass = queryClass.getSuperclass();
        }
        return false;
    }


    public static void checkPackageAccess(Class clazz) {
        checkPackageAccess(clazz.getName());
    }

    public static void checkPackageAccess(String name) {
        SecurityManager s = System.getSecurityManager();
        if (s != null) {
            String cname = name.replace('/', '.');
            if (cname.startsWith("[")) {
                int b = cname.lastIndexOf('[') + 2;
                if (b > 1 && b < cname.length()) {
                    cname = cname.substring(b);
                }
            }
            int i = cname.lastIndexOf('.');
            if (i != -1) {
                s.checkPackageAccess(cname.substring(0, i));
            }
        }
    }

    public static boolean isPackageAccessible(Class clazz) {
        try {
            checkPackageAccess(clazz);
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }
}
