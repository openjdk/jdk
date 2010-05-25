/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package pkg;

import java.util.*;

/**
 * Just a sample class with type parameters.  This is a link to myself:
 * {@link TypeParameters}
 *
 * @param <E> the type parameter for this class.
 * @param <BadClassTypeParam> this should cause a warning.
 * @see TypeParameters
 */

public class TypeParameters<E> implements SubInterface<E> {

    /**
     * This method uses the type parameter of this class.
     * @param param an object that is of type E.
     * @return the parameter itself.
     */
    public E methodThatUsesTypeParameter(E param) {
        return param;
    }

    /**
     * This method has type parameters.  The list of type parameters is long
     * so there should be a line break in the member summary table.
     *
     * @param <T> This is the first type parameter.
     * @param <V> This is the second type parameter.
     * @param <BadMethodTypeParam> this should cause a warning.
     * @param param1 just a parameter.
     * @param param2 just another parameter.
     *
     */
    public <T extends List, V> String[] methodThatHasTypeParameters(T param1,
        V param2) { return null;}

    /**
     * This method has type parameters.  The list of type parameters is short
     * so there should not be a line break in the member summary table.
     * @author Owner
     *
     * @param <A> This is the first type parameter.
     */
    public <A> void methodThatHasTypeParmaters(A... a) {}
}
