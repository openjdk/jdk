/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect.generics.scope;

import java.lang.reflect.TypeVariable;

/**
 * This class is used to provide enclosing scopes for top level classes.
 * We cannot use <tt>null</tt> to represent such a scope, since the
 * enclosing scope is computed lazily, and so the field storing it is
 * null until it has been computed. Therefore, <tt>null</tt> is reserved
 * to represent an as-yet-uncomputed scope, and cannot be used for any
 * other kind of scope.
 */
public class DummyScope implements Scope {
    // Caches the unique instance of this class; instances contain no data
    // so we can use the singleton pattern
    private static DummyScope singleton = new DummyScope();

    // constructor is private to enforce use of factory method
    private DummyScope(){}

    /**
     * Factory method. Enforces the singleton pattern - only one
     * instance of this class ever exists.
     */
    public static DummyScope make() {
        return singleton;
    }

    /**
     * Lookup a type variable in the scope, using its name. Always returns
     * <tt>null</tt>.
     * @param name - the name of the type variable being looked up
     * @return  null
     */
    public TypeVariable<?> lookup(String name) {return null;}
}
