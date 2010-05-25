/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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
package javax.accessibility;


/**
 * <P>The AccessibleTextSequence provides information about
 * a contiguous sequence of text.
 *
 * @see Accessible
 * @see Accessible#getAccessibleContext
 * @see AccessibleContext
 * @see AccessibleContext#getAccessibleText
 * @see AccessibleAttributeSequence
 *
 * @author       Lynn Monsanto
 */

/**
 * This class collects together key details of a span of text.  It
 * is used by implementors of the class <code>AccessibleExtendedText</code> in
 * order to return the requested triplet of a <code>String</code>, and the
 * start and end indicies/offsets into a larger body of text that the
 * <code>String</code> comes from.
 *
 * @see javax.accessibility.AccessibleExtendedText
 */
public class AccessibleTextSequence {

    /* The start index of the text sequence */
    public int startIndex;

    /** The end index of the text sequence */
    public int endIndex;

    /** The text */
    public String text;

    /**
     * Constructs an <code>AccessibleTextSequence</code> with the given
     * parameters.
     *
     * @param start the beginning index of the span of text
     * @param end the ending index of the span of text
     * @param txt the <code>String</code> shared by this text span
     *
     * @since 1.6
     */
    public AccessibleTextSequence(int start, int end, String txt) {
        startIndex = start;
        endIndex = end;
        text = txt;
    }
};
