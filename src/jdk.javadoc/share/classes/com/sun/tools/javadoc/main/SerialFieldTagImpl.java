/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;

/**
 * Documents a Serializable field defined by an ObjectStreamField.
 * <pre>
 * The class parses and stores the three serialField tag parameters:
 *
 * - field name
 * - field type name
 *      (fully-qualified or visible from the current import context)
 * - description of the valid values for the field

 * </pre>
 * This tag is only allowed in the javadoc for the special member
 * serialPersistentFields.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Joe Fialli
 * @author Neal Gafter
 *
 * @see java.io.ObjectStreamField
 */
@Deprecated
class SerialFieldTagImpl
    extends TagImpl
    implements SerialFieldTag, Comparable<Object>
{
    //### These could be final, except that the constructor
    //### does not set them directly.

    private String fieldName;    // Required Argument 1 of serialField
    private String fieldType;    // Required Argument 2 of serialField
    private String description;  // Optional Remaining Arguments of serialField

    private ClassDoc containingClass;   // Class containing serialPersistentField member
    private ClassDoc fieldTypeDoc;      // ClassDocImpl of fieldType
    private FieldDocImpl matchingField; // FieldDocImpl with same name as fieldName

   /* Constructor. */
   SerialFieldTagImpl(DocImpl holder, String name, String text) {
        super(holder, name, text);
        parseSerialFieldString();
        if (holder instanceof MemberDoc) {
            containingClass = ((MemberDocImpl)holder).containingClass();
        }
    }

    /*
     * The serialField tag is composed of three entities.
     *
     *   serialField  serializableFieldName serisliableFieldType
     *                 description of field.
     *
     * The fieldName and fieldType must be legal Java Identifiers.
     */
    private void parseSerialFieldString() {
        int len = text.length();
        if (len == 0) {
            return;
        }

        // if no white space found
        /* Skip white space. */
        int inx = 0;
        int cp;
        for (; inx < len; inx += Character.charCount(cp)) {
             cp = text.codePointAt(inx);
             if (!Character.isWhitespace(cp)) {
                 break;
             }
        }

        /* find first word. */
        int first = inx;
        int last = inx;
        cp = text.codePointAt(inx);
        if (! Character.isJavaIdentifierStart(cp)) {
            docenv().warning(holder,
                             "tag.serialField.illegal_character",
                             new String(Character.toChars(cp)), text);
            return;
        }

        for (inx += Character.charCount(cp); inx < len; inx += Character.charCount(cp)) {
             cp = text.codePointAt(inx);
             if (!Character.isJavaIdentifierPart(cp)) {
                 break;
             }
        }

        if (inx < len && ! Character.isWhitespace(cp = text.codePointAt(inx))) {
            docenv().warning(holder,
                             "tag.serialField.illegal_character",
                             new String(Character.toChars(cp)), text);
            return;
        }

        last = inx;
        fieldName = text.substring(first, last);

        /* Skip white space. */
        for (; inx < len; inx += Character.charCount(cp)) {
             cp = text.codePointAt(inx);
             if (!Character.isWhitespace(cp)) {
                 break;
             }
        }

        /* find second word. */
        first = inx;
        last = inx;

        for (; inx < len; inx += Character.charCount(cp)) {
             cp = text.codePointAt(inx);
             if (Character.isWhitespace(cp)) {
                 break;
             }
        }
        if (inx < len && ! Character.isWhitespace(cp = text.codePointAt(inx))) {
            docenv().warning(holder,
                             "tag.serialField.illegal_character",
                             new String(Character.toChars(cp)), text);
            return;
        }
        last = inx;
        fieldType = text.substring(first, last);

        /* Skip leading white space. Rest of string is description for serialField.*/
        for (; inx < len; inx += Character.charCount(cp)) {
             cp = text.codePointAt(inx);
             if (!Character.isWhitespace(cp)) {
                 break;
             }
        }
        description = text.substring(inx);
    }

    /**
     * return a key for sorting.
     */
    String key() {
        return fieldName;
    }

    /*
     * Optional. Link this serialField tag to its corrsponding
     * field in the class. Note: there is no requirement that
     * there be a field in the class that matches serialField tag.
     */
    void mapToFieldDocImpl(FieldDocImpl fd) {
        matchingField = fd;
    }

    /**
     * Return the serialziable field name.
     */
    public String fieldName() {
        return fieldName;
    }

    /**
     * Return the field type string.
     */
    public String fieldType() {
        return fieldType;
    }

    /**
     * Return the ClassDocImpl for field type.
     *
     * @returns null if no ClassDocImpl for field type is visible from
     *          containingClass context.
     */
    public ClassDoc fieldTypeDoc() {
        if (fieldTypeDoc == null && containingClass != null) {
            fieldTypeDoc = containingClass.findClass(fieldType);
        }
        return fieldTypeDoc;
    }

    /**
     * Return the corresponding FieldDocImpl for this SerialFieldTagImpl.
     *
     * @returns null if no matching FieldDocImpl.
     */
    FieldDocImpl getMatchingField() {
        return matchingField;
    }

    /**
     * Return the field comment. If there is no serialField comment, return
     * javadoc comment of corresponding FieldDocImpl.
     */
    public String description() {
        if (description.length() == 0 && matchingField != null) {

            //check for javadoc comment of corresponding field.
            Comment comment = matchingField.comment();
            if (comment != null) {
                return comment.commentText();
            }
        }
        return description;
    }

    /**
     * Return the kind of this tag.
     */
    public String kind() {
        return "@serialField";
    }

    /**
     * Convert this object to a string.
     */
    public String toString() {
        return name + ":" + text;
    }

    /**
     * Compares this Object with the specified Object for order.  Returns a
     * negative integer, zero, or a positive integer as this Object is less
     * than, equal to, or greater than the given Object.
     * <p>
     * Included to make SerialFieldTagImpl items java.lang.Comparable.
     *
     * @param   obj the <code>Object</code> to be compared.
     * @return  a negative integer, zero, or a positive integer as this Object
     *          is less than, equal to, or greater than the given Object.
     * @exception ClassCastException the specified Object's type prevents it
     *            from being compared to this Object.
     * @since 1.2
     */
    public int compareTo(Object obj) {
        return key().compareTo(((SerialFieldTagImpl)obj).key());
    }
}
