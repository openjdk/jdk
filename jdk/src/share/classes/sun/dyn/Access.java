/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.dyn;

import sun.reflect.Reflection;

/**
 * Access control to this package.
 * Classes in other packages can attempt to acquire the access token,
 * but will fail if they are not recognized as friends.
 * Certain methods in this package, although public, require a non-null
 * access token in order to proceed; they act like package-private methods.
 * @author jrose
 */

public class Access {

    private Access() { }

    /**
     * The heart of this pattern:  The list of classes which are
     * permitted to acquire the access token, and become honorary
     * members of this package.
     */
    private static final String[] FRIENDS = {
        "java.dyn.", "sun.dyn."
    };

    /**
     * The following object is NOT public.  That's the point of the pattern.
     * It is package-private, so that any member of this package
     * can acquire the access token, and give it away to trusted friends.
     */
    static final Access TOKEN = new Access();

    /**
     * @return Access.TOKEN, if the caller is a friend of this package
     */
    public static Access getToken() {
        Class<?> callc = Reflection.getCallerClass(2);
        if (isFriend(callc))
            return TOKEN;
        else
            throw new IllegalAccessError("bad caller: " + callc);
    }

    /** Is the given name the name of a class which could be our friend? */
    public static boolean isFriendName(String name) {
        for (String friend : FRIENDS) {
            if (name.startsWith(friend))
                return true;
        }
        return false;
    }

    /** Is the given class a friend?  True if {@link #isFriendName},
     *  and the given class also shares a class loader with us.
     */
    public static boolean isFriend(Class<?> c) {
        return isFriendName(c.getName()) && c.getClassLoader() == CLASS_LOADER;
    }

    private static final ClassLoader CLASS_LOADER = Access.class.getClassLoader();

    /**
     * Throw an IllegalAccessError if the caller does not possess
     * the Access.TOKEN.
     * @param must be Access.TOKEN
     */
    public static void check(Access token) {
        if (token == null)
            fail();
        // else it must be the unique Access.TOKEN
        assert(token == Access.TOKEN);
    }
    private static void fail() {
        final int CALLER_DEPTH = 3;
        // 0: Reflection.getCC, 1: this.fail, 2: Access.*, 3: caller
        Class<?> callc = Reflection.getCallerClass(CALLER_DEPTH);
        throw new IllegalAccessError("bad caller: " + callc);
    }

    static {
        //sun.reflect.Reflection.registerMethodsToFilter(MH.class, "getToken");
    }
}
