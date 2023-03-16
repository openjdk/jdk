/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.access;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;

public interface JavaTemplateAccess {

    /**
     * Returns a StringTemplate composed from fragments and values.
     *
     * @implSpec The {@code fragments} list size must be one more that the
     * {@code values} list size.
     *
     * @param fragments list of string fragments
     * @param values    list of expression values
     *
     * @return StringTemplate composed from fragments and values
     *
     * @throws IllegalArgumentException if fragments list size is not one more
     *         than values list size
     * @throws NullPointerException if fragments is null or values is null or if any fragment is null.
     *
     * @implNote Contents of both lists are copied to construct immutable lists.
     */
    StringTemplate of(List<String> fragments, List<?> values);

    /**
     * Creates a string that interleaves the elements of values between the
     * elements of fragments.
     *
     * @param fragments  list of String fragments
     * @param values     list of expression values
     *
     * @return String interpolation of fragments and values
     */
    String interpolate(List<String> fragments, List<?> values);

    /**
     * Combine one or more {@link StringTemplate StringTemplates} to produce a combined {@link StringTemplate}.
     * {@snippet :
     * StringTemplate st = StringTemplate.combine("\{a}", "\{b}", "\{c}");
     * assert st.interpolate().equals("\{a}\{b}\{c}");
     * }
     *
     * @param sts  zero or more {@link StringTemplate}
     *
     * @return combined {@link StringTemplate}
     *
     * @throws NullPointerException if sts is null or if any element of sts is null
     */
    StringTemplate combine(StringTemplate... sts);

}

