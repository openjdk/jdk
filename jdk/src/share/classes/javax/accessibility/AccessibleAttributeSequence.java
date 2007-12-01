/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
package javax.accessibility;

import javax.swing.text.AttributeSet;


/**
 * <P>The AccessibleAttributeSequence provides information about
 * a contiguous sequence of text attributes
 *
 * @see Accessible
 * @see Accessible#getAccessibleContext
 * @see AccessibleContext
 * @see AccessibleContext#getAccessibleText
 * @see AccessibleTextSequence
 *
 * @author       Lynn Monsanto
 */

/**
 * This class collects together the span of text that share the same
 * contiguous set of attributes, along with that set of attributes.  It
 * is used by implementors of the class <code>AccessibleContext</code> in
 * order to generate <code>ACCESSIBLE_TEXT_ATTRIBUTES_CHANGED</code> events.
 *
 * @see javax.accessibility.AccessibleContext
 * @see javax.accessibility.AccessibleContext#ACCESSIBLE_TEXT_ATTRIBUTES_CHANGED
 */
public class AccessibleAttributeSequence {
    /** The start index of the text sequence */
    public int startIndex;

    /** The end index of the text sequence */
    public int endIndex;

    /** The text attributes */
    public AttributeSet attributes;

    /**
     * Constructs an <code>AccessibleAttributeSequence</code> with the given
     * parameters.
     *
     * @param start the beginning index of the span of text
     * @param end the ending index of the span of text
     * @param attr the <code>AttributeSet</code> shared by this text span
     *
     * @since 1.6
     */
    public AccessibleAttributeSequence(int start, int end, AttributeSet attr) {
        startIndex = start;
        endIndex = end;
        attributes = attr;
    }

};
