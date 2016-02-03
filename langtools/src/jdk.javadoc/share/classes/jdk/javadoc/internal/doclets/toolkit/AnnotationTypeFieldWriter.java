/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * The interface for writing annotation type field output.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 *
 * @author Bhavesh Patel
 */

public interface AnnotationTypeFieldWriter {

    /**
     * Add the annotation type member tree header.
     *
     * @return content tree for the member tree header
     */
    public Content getMemberTreeHeader();

    /**
     * Add the annotation type field details marker.
     *
     * @param memberDetails the content tree representing field details marker
     */
    public void addAnnotationFieldDetailsMarker(Content memberDetails);

    /**
     * Add the annotation type details tree header.
     *
     * @param typeElement the annotation type being documented
     * @param memberDetailsTree the content tree representing member details
     */
    public void addAnnotationDetailsTreeHeader(TypeElement typeElement,
            Content memberDetailsTree);

    /**
     * Get the annotation type documentation tree header.
     *
     * @param member the annotation type being documented
     * @param annotationDetailsTree the content tree representing annotation type details
     * @return content tree for the annotation type documentation header
     */
    public Content getAnnotationDocTreeHeader(Element member, Content annotationDetailsTree);

    /**
     * Get the annotation type details tree.
     *
     * @param annotationDetailsTree the content tree representing annotation type details
     * @return content tree for the annotation type details
     */
    public Content getAnnotationDetails(Content annotationDetailsTree);

    /**
     * Get the annotation type documentation.
     *
     * @param annotationDocTree the content tree representing annotation type documentation
     * @param isLastContent true if the content to be added is the last content
     * @return content tree for the annotation type documentation
     */
    public Content getAnnotationDoc(Content annotationDocTree, boolean isLastContent);

    /**
     * Get the signature for the given member.
     *
     * @param member the member being documented
     * @return content tree for the annotation type signature
     */
    public Content getSignature(Element member);

    /**
     * Add the deprecated output for the given member.
     *
     * @param member the member being documented
     * @param annotationDocTree content tree to which the deprecated information will be added
     */
    public void addDeprecated(Element member, Content annotationDocTree);

    /**
     * Add the comments for the given member.
     *
     * @param member the member being documented
     * @param annotationDocTree the content tree to which the comments will be added
     */
    public void addComments(Element member, Content annotationDocTree);

    /**
     * Add the tags for the given member.
     *
     * @param member the member being documented
     * @param annotationDocTree the content tree to which the tags will be added
     */
    public void addTags(Element member, Content annotationDocTree);

    /**
     * Close the writer.
     */
    public void close() throws IOException;
}
