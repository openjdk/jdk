/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.rmi.rmic;

import sun.tools.java.Identifier;

/**
 * Names provides static utility methods used by other rmic classes
 * for dealing with identifiers.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public class Names {

    /**
     * Return stub class name for impl class name.
     */
    static final public Identifier stubFor(Identifier name) {
        return Identifier.lookup(name + "_Stub");
    }

    /**
     * Return skeleton class name for impl class name.
     */
    static final public Identifier skeletonFor(Identifier name) {
        return Identifier.lookup(name + "_Skel");
    }

    /**
     * If necessary, convert a class name to its mangled form, i.e. the
     * non-inner class name used in the binary representation of
     * inner classes.  This is necessary to be able to name inner
     * classes in the generated source code in places where the language
     * does not permit it, such as when synthetically defining an inner
     * class outside of its outer class, and for generating file names
     * corresponding to inner classes.
     *
     * Currently this mangling involves modifying the internal names of
     * inner classes by converting occurrences of ". " into "$".
     *
     * This code is taken from the "mangleInnerType" method of
     * the "sun.tools.java.Type" class; this method cannot be accessed
     * itself because it is package protected.
     */
    static final public Identifier mangleClass(Identifier className) {
        if (!className.isInner())
            return className;

        /*
         * Get '.' qualified inner class name (with outer class
         * qualification and no package qualification) and replace
         * each '.' with '$'.
         */
        Identifier mangled = Identifier.lookup(
                                               className.getFlatName().toString()
                                               .replace('.', sun.tools.java.Constants.SIGC_INNERCLASS));
        if (mangled.isInner())
            throw new Error("failed to mangle inner class name");

        // prepend package qualifier back for returned identifier
        return Identifier.lookup(className.getQualifier(), mangled);
    }
}
