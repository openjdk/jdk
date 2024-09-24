/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model.element;


/**
 * Represents a modifier on a program element such
 * as a class, method, or field.
 *
 * <p>Not all modifiers are applicable to all kinds of elements.
 * When two or more modifiers appear in the source code of an element
 * then it is customary, though not required, that they appear in the same
 * order as the constants listed in the detail section below.
 *
 * <p>Note that it is possible additional modifiers will be added in
 * future versions of the platform.
 *
 * @jls 8.1.1 Class Modifiers
 * @jls 8.3.1 Field Modifiers
 * @jls 8.4.3 Method Modifiers
 * @jls 8.8.3 Constructor Modifiers
 * @jls 9.1.1 Interface Modifiers
 *
 * @since 1.6
 */

public enum Modifier {

    // Note java.lang.reflect.Modifier includes INTERFACE, but that's a VMism.

    /**
     * The modifier {@code public}
     *
     * @jls 6.6 Access Control
     */
    PUBLIC,

    /**
     * The modifier {@code protected}
     *
     * @jls 6.6 Access Control
     */
    PROTECTED,

    /**
     * The modifier {@code private}
     *
     * @jls 6.6 Access Control
     */
    PRIVATE,

    /**
     * The modifier {@code abstract}
     *
     * @jls 8.1.1.1 {@code abstract} Classes
     * @jls 8.4.3.1 {@code abstract} Methods
     * @jls 9.1.1.1 {@code abstract} Interfaces
     */
    ABSTRACT,

    /**
     * The modifier {@code default}
     *
     * @jls 9.4 Method Declarations
     * @since 1.8
     */
     DEFAULT,

    /**
     * The modifier {@code static}
     *
     * @jls 8.1.1.4 {@code static} Classes
     * @jls 8.3.1.1 {@code static} Fields
     * @jls 8.4.3.2 {@code static} Methods
     * @jls 9.1.1.3 {@code static} Interfaces
     */
    STATIC,

    /**
     * The modifier {@code sealed}
     *
     * @jls 8.1.1.2 {@code sealed}, {@code non-sealed}, and {@code final} Classes
     * @jls 9.1.1.4 {@code sealed} and {@code non-sealed} Interfaces
     * @since 17
     */
    SEALED,

    /**
     * The modifier {@code non-sealed}
     *
     * @jls 8.1.1.2 {@code sealed}, {@code non-sealed}, and {@code final} Classes
     * @jls 9.1.1.4 {@code sealed} and {@code non-sealed} Interfaces
     * @since 17
     */
    NON_SEALED {
        public String toString() {
            return "non-sealed";
        }
    },
    /**
     * The modifier {@code final}
     *
     * @jls 8.1.1.2 {@code sealed}, {@code non-sealed}, and {@code final} Classes
     * @jls 8.3.1.2 {@code final} Fields
     * @jls 8.4.3.3 {@code final} Methods
     */
    FINAL,

    /**
     * The modifier {@code transient}
     *
     * @jls 8.3.1.3 {@code transient} Fields
     */
    TRANSIENT,

    /**
     * The modifier {@code volatile}
     *
     * @jls 8.3.1.4 {@code volatile} Fields
     */
    VOLATILE,

    /**
     * The modifier {@code synchronized}
     *
     * @jls 8.4.3.6 {@code synchronized} Methods
     */
    SYNCHRONIZED,

    /**
     * The modifier {@code native}
     *
     * @jls 8.4.3.4 {@code native} Methods
     */
    NATIVE,

    /**
     * The modifier {@code strictfp}
     *
     * @jls 8.1.1.3 {@code strictfp} Classes
     * @jls 8.4.3.5 {@code strictfp} Methods
     * @jls 9.1.1.2 {@code strictfp} Interfaces
     */
    STRICTFP;

    /**
     * Returns this modifier's name as defined in <cite>The
     * Java Language Specification</cite>.
     * The modifier name is the {@linkplain #name() name of the enum
     * constant} in lowercase and with any underscores ("{@code _}")
     * replaced with hyphens ("{@code -}").
     * @return the modifier's name
     */
    @Override
    public String toString() {
        return name().toLowerCase(java.util.Locale.US);
    }
}
