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

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * The interface for writing enum constant output.
 */
public interface EnumConstantWriter extends MemberWriter {

    /**
     * Get the enum constants details header.
     *
     * @param typeElement the class being documented
     * @param memberDetails the content representing member details
     * @return a content for the enum constants details header
     */
    Content getEnumConstantsDetailsHeader(TypeElement typeElement,
                                          Content memberDetails);

    /**
     * Get the enum constants documentation header.
     *
     * @param enumConstant the enum constant being documented
     * @param enumConstantsDetails the content representing enum constant details
     * @return the enum constant documentation header
     */
    Content getEnumConstantsHeader(VariableElement enumConstant,
                                   Content enumConstantsDetails);

    /**
     * Get the signature for the given enum constant.
     *
     * @param enumConstant the enum constant being documented
     * @return the enum constant signature
     */
    Content getSignature(VariableElement enumConstant);

    /**
     * Add the deprecated output for the given enum constant.
     *
     * @param enumConstant the enum constant being documented
     * @param content the content to which the deprecated information will be added
     */
    void addDeprecated(VariableElement enumConstant, Content content);

    /**
     * Add the preview output for the given member.
     *
     * @param member the member being documented
     * @param content the content to which the preview information will be added
     */
    void addPreview(VariableElement member, Content content);

    /**
     * Add the comments for the given enum constant.
     *
     * @param enumConstant the enum constant being documented
     * @param enumConstants the content to which the comments will be added
     */
    void addComments(VariableElement enumConstant, Content enumConstants);

    /**
     * Add the tags for the given enum constant.
     *
     * @param enumConstant the enum constant being documented
     * @param content the content to which the tags will be added
     */
    void addTags(VariableElement enumConstant, Content content);

    /**
     * Get the enum constants details.
     *
     * @param memberDetailsHeader member details header
     * @param content the content representing member details
     * @return the enum constant details
     */
    Content getEnumConstantsDetails(Content memberDetailsHeader, Content content);

    /**
     * Gets the member header.
     *
     * @return the member header
     */
    Content getMemberHeader();
}
