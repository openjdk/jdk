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

package jdk.internal.util;

/**
 * Implementations of this class provide information necessary to
 * assist {@link java.lang.invoke.StringConcatFactory} perform optimal
 * insertion.
 *
 * @since 21
 *
 * Warning: This class is part of PreviewFeature.Feature.STRING_TEMPLATES.
 *          Do not rely on its availability.
 */
public interface FormatConcatItem {
    /**
     * Calculate the length of the insertion.
     *
     * @param lengthCoder current value of the length + coder
     * @return adjusted value of the length + coder
     */
    long mix(long lengthCoder);

    /**
     * Insert content into buffer prior to the current length.
     *
     * @param lengthCoder current value of the length + coder
     * @param buffer      buffer to append to
     *
     * @return adjusted value of the length + coder
     *
     * @throws Throwable if fails to prepend value (unusual).
     */
    long prepend(long lengthCoder, byte[] buffer) throws Throwable;
}
