/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import static com.sun.tools.javac.code.Flags.*;

/**
 *   A class whose instances are filters over Modifier bits.
 *   Filtering is done by returning boolean values.
 *   Classes, methods and fields can be filtered, or filtering
 *   can be done directly on modifier bits.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 *   @see com.sun.tools.javac.code.Flags
 *   @author Robert Field
 */

@Deprecated
public class ModifierFilter {

    /**
    * Package private access.
    * A "pseudo-" modifier bit that can be used in the
    * constructors of this class to specify package private
    * access. This is needed since there is no Modifier.PACKAGE.
    */
    public static final long PACKAGE = 0x8000000000000000L;

    /**
    * All access modifiers.
    * A short-hand set of modifier bits that can be used in the
    * constructors of this class to specify all access modifiers,
    * Same as PRIVATE | PROTECTED | PUBLIC | PACKAGE.
    */
    public static final long ALL_ACCESS =
                PRIVATE | PROTECTED | PUBLIC | PACKAGE;

    private long oneOf;
    private long must;
    private long cannot;

    private static final int ACCESS_BITS = PRIVATE | PROTECTED | PUBLIC;

    /**
     * Constructor - Specify a filter.
     *
     * @param   oneOf   If zero, everything passes the filter.
     *                  If non-zero, at least one of the specified
     *                  bits must be on in the modifier bits to
     *                  pass the filter.
     */
    public ModifierFilter(long oneOf) {
        this(oneOf, 0, 0);
    }

    /**
     * Constructor - Specify a filter.
     * For example, the filter below  will only pass synchronized
     * methods that are private or package private access and are
     * not native or static.
     * <pre>
     * ModifierFilter(  Modifier.PRIVATE | ModifierFilter.PACKAGE,
     *                  Modifier.SYNCHRONIZED,
     *                  Modifier.NATIVE | Modifier.STATIC)
     * </pre><p>
     * Each of the three arguments must either be
     * zero or the or'ed combination of the bits specified in the
     * class Modifier or this class. During filtering, these values
     * are compared against the modifier bits as follows:
     *
     * @param   oneOf   If zero, ignore this argument.
     *                  If non-zero, at least one of the bits must be on.
     * @param   must    All bits specified must be on.
     * @param   cannot  None of the bits specified can be on.
     */
    public ModifierFilter(long oneOf, long must, long cannot) {
        this.oneOf = oneOf;
        this.must = must;
        this.cannot = cannot;
    }

    /**
     * Filter on modifier bits.
     *
     * @param   modifierBits    Bits as specified in the Modifier class
     *
     * @return                  Whether the modifierBits pass this filter.
     */
    public boolean checkModifier(int modifierBits) {
        // Add in the "pseudo-" modifier bit PACKAGE, if needed
        long fmod = ((modifierBits & ACCESS_BITS) == 0) ?
                        modifierBits | PACKAGE :
                        modifierBits;
        return ((oneOf == 0) || ((oneOf & fmod) != 0)) &&
                ((must & fmod) == must) &&
                ((cannot & fmod) == 0);
    }

} // end ModifierFilter
