/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit;

import javax.lang.model.element.VariableElement;

/**
 * The interface for writing field output.
 */
public interface FieldWriter extends MemberWriter {

    /**
     * Get the field details header.
     *
     * @param content the content representing member details
     * @return the field details header
     */
    Content getFieldDetailsHeader(Content content);

    /**
     * Get the field documentation header.
     *
     * @param field the constructor being documented
     * @return the field documentation header
     */
    Content getFieldHeaderContent(VariableElement field);

    /**
     * Get the signature for the given field.
     *
     * @param field the field being documented
     * @return the field signature
     */
    Content getSignature(VariableElement field);

    /**
     * Add the deprecated output for the given field.
     *
     * @param field the field being documented
     * @param fieldContent the content to which the deprecated information will be added
     */
    void addDeprecated(VariableElement field, Content fieldContent);

    /**
     * Adds the preview output for the given member.
     *
     * @param member the member being documented
     * @param content the content to which the preview information will be added
     */
    void addPreview(VariableElement member, Content content);

    /**
     * Add the comments for the given field.
     *
     * @param field the field being documented
     * @param fieldContent the content to which the comments will be added
     */
    void addComments(VariableElement field, Content fieldContent);

    /**
     * Add the tags for the given field.
     *
     * @param field the field being documented
     * @param fieldContent the content to which the tags will be added
     */
    void addTags(VariableElement field, Content fieldContent);

    /**
     * Get the field details.
     *
     * @param memberDetailsHeaderContent the content representing member details header
     * @param memberContent the content representing member details
     * @return the field details
     */
    Content getFieldDetails(Content memberDetailsHeaderContent, Content memberContent);

    /**
     * Gets the member header.
     *
     * @return the member header
     */
    Content getMemberHeader();
}
