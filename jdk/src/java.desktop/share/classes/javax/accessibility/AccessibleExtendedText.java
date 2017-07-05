/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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


import java.util.*;
import java.awt.*;
import javax.swing.text.*;


/**
 * <P>The AccessibleExtendedText interface contains additional methods
 * not provided by the AccessibleText interface
 *
 * Applications can determine if an object supports the AccessibleExtendedText
 * interface by first obtaining its AccessibleContext (see {@link Accessible})
 * and then calling the {@link AccessibleContext#getAccessibleText} method of
 * AccessibleContext.  If the return value is an instance of
 * AccessibleExtendedText, the object supports this interface.
 *
 * @see Accessible
 * @see Accessible#getAccessibleContext
 * @see AccessibleContext
 * @see AccessibleContext#getAccessibleText
 *
 * @author       Peter Korn
 * @author       Lynn Monsanto
 * @since 1.5
 */
public interface AccessibleExtendedText {

    /**
     * Constant used to indicate that the part of the text that should be
     * retrieved is a line of text.
     *
     * @see AccessibleText#getAtIndex
     * @see AccessibleText#getAfterIndex
     * @see AccessibleText#getBeforeIndex
     */
    public static final int LINE = 4; // BugID: 4849720

    /**
     * Constant used to indicate that the part of the text that should be
     * retrieved is contiguous text with the same text attributes.
     *
     * @see AccessibleText#getAtIndex
     * @see AccessibleText#getAfterIndex
     * @see AccessibleText#getBeforeIndex
     */
    public static final int ATTRIBUTE_RUN = 5; // BugID: 4849720

    /**
     * Returns the text between two indices
     *
     * @param startIndex the start index in the text
     * @param endIndex the end index in the text
     * @return the text string if the indices are valid.
     * Otherwise, null is returned.
     */
    public String getTextRange(int startIndex, int endIndex);

    /**
     * Returns the {@code AccessibleTextSequence} at a given index.
     *
     * @param part the {@code CHARACTER}, {@code WORD},
     * {@code SENTENCE}, {@code LINE} or {@code ATTRIBUTE_RUN}
     * to retrieve
     * @param index an index within the text
     * @return an {@code AccessibleTextSequence} specifying the text
     * if part and index are valid.  Otherwise, null is returned.
     *
     * @see AccessibleText#CHARACTER
     * @see AccessibleText#WORD
     * @see AccessibleText#SENTENCE
     */
    public AccessibleTextSequence getTextSequenceAt(int part, int index);

    /**
     * Returns the {@code AccessibleTextSequence} after a given index.
     *
     * @param part the {@code CHARACTER}, {@code WORD},
     * {@code SENTENCE}, {@code LINE} or {@code ATTRIBUTE_RUN}
     * to retrieve
     * @param index an index within the text
     * @return an {@code AccessibleTextSequence} specifying the text
     * if part and index are valid.  Otherwise, null is returned.
     *
     * @see AccessibleText#CHARACTER
     * @see AccessibleText#WORD
     * @see AccessibleText#SENTENCE
     */
    public AccessibleTextSequence getTextSequenceAfter(int part, int index);

    /**
     * Returns the {@code AccessibleTextSequence} before a given index.
     *
     * @param part the {@code CHARACTER}, {@code WORD},
     * {@code SENTENCE}, {@code LINE} or {@code ATTRIBUTE_RUN}
     * to retrieve
     * @param index an index within the text
     * @return an {@code AccessibleTextSequence} specifying the text
     * if part and index are valid.  Otherwise, null is returned.
     *
     * @see AccessibleText#CHARACTER
     * @see AccessibleText#WORD
     * @see AccessibleText#SENTENCE
     */
    public AccessibleTextSequence getTextSequenceBefore(int part, int index);

    /**
     * Returns the bounding rectangle of the text between two indices.
     *
     * @param startIndex the start index in the text
     * @param endIndex the end index in the text
     * @return the bounding rectangle of the text if the indices are valid.
     * Otherwise, null is returned.
     */
    public Rectangle getTextBounds(int startIndex, int endIndex);
}
