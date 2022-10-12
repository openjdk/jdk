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

import javax.lang.model.element.Element;

/**
 * The interface for writing annotation type required member output.
 */
public interface AnnotationTypeMemberWriter extends MemberWriter {

    /**
     * Adds the annotation type member header.
     *
     * @return the content for the member header
     */
    Content getMemberHeader();

    /**
     * Adds the annotation type details marker.
     *
     * @param memberDetails the content representing details marker
     */
    void addAnnotationDetailsMarker(Content memberDetails);

    /**
     * Adds the annotation type details header.
     *
     * @return the content for the annotation details header
     */
    Content getAnnotationDetailsHeader();

    /**
     * Gets the annotation type documentation header.
     *
     * @param member the annotation type being documented
     * @return the content for the annotation type documentation header
     */
    Content getAnnotationHeaderContent(Element member);

    /**
     * Gets the annotation type details.
     *
     * @param annotationDetailsHeader the content representing annotation type details header
     * @param annotationDetails the content representing annotation type details
     * @return the annotation type details
     */
    Content getAnnotationDetails(Content annotationDetailsHeader, Content annotationDetails);

    /**
     * {@return the signature for the specified member}
     *
     * @param member the member being documented
     */
    Content getSignature(Element member);

    /**
     * Adds the deprecated output for the given member.
     *
     * @param member the member being documented
     * @param target the content to which the deprecated information will be added
     */
    void addDeprecated(Element member, Content target);

    /**
     * Adds the preview output for the given member.
     *
     * @param member the member being documented
     * @param content the content to which the preview information will be added
     */
    void addPreview(Element member, Content content);

    /**
     * Adds the comments for the given member.
     *
     * @param member the member being documented
     * @param annotationContent the content to which the comments will be added
     */
    void addComments(Element member, Content annotationContent);

    /**
     * Adds the tags for the given member.
     *
     * @param member the member being documented
     * @param annotationContent the content to which the tags will be added
     */
    void addTags(Element member, Content annotationContent);

    /**
     * Adds the default value documentation if the member has one.
     *
     * @param member the member being documented
     * @param annotationContent the content to which the default value will be added
     */
    void addDefaultValueInfo(Element member, Content annotationContent);
}
