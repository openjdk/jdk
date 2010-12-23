/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.tools.doclets.internal.toolkit;

import java.io.*;
import com.sun.javadoc.*;

/**
 * The interface for writing annotation type output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API.
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
 */

public interface AnnotationTypeWriter {

    /**
     * Get the header of the page.
     *
     * @param header the header string to write
     * @return a content tree for the header documentation
     */
    public Content getHeader(String header);

    /**
     * Get the annotation content header.
     *
     * @return annotation content header that needs to be added to the documentation
     */
    public Content getAnnotationContentHeader();

    /**
     * Get the annotation information tree header.
     *
     * @return annotation information tree header that needs to be added to the documentation
     */
    public Content getAnnotationInfoTreeHeader();

    /**
     * Get the annotation information.
     *
     * @param annotationInfoTree content tree containing the annotation information
     * @return a content tree for the annotation
     */
    public Content getAnnotationInfo(Content annotationInfoTree);

    /**
     * Add the signature of the current annotation type.
     *
     * @param modifiers the modifiers for the signature
     * @param annotationInfoTree the annotation content tree to which the signature will be added
     */
    public void addAnnotationTypeSignature(String modifiers, Content annotationInfoTree);

    /**
     * Build the annotation type description.
     *
     * @param annotationInfoTree content tree to which the description will be added
     */
    public void addAnnotationTypeDescription(Content annotationInfoTree);

    /**
     * Add the tag information for the current annotation type.
     *
     * @param annotationInfoTree content tree to which the tag information will be added
     */
    public void addAnnotationTypeTagInfo(Content annotationInfoTree);

    /**
     * If this annotation is deprecated, add the appropriate information.
     *
     * @param annotationInfoTree content tree to which the deprecated information will be added
     */
    public void addAnnotationTypeDeprecationInfo (Content annotationInfoTree);

    /**
     * Add the annotation type details marker.
     *
     * @param memberDetails the content tree representing member details marker
     */
    public void addAnnotationDetailsMarker(Content memberDetails);

    /**
     * Get the member tree header for the annotation type.
     *
     * @return a content tree for the member tree header
     */
    public Content getMemberTreeHeader();

    /**
     * Get the member tree.
     *
     * @param memberTree the content tree that will be modified and returned
     * @return a content tree for the member
     */
    public Content getMemberTree(Content memberTree);

    /**
     * Get the member summary tree.
     *
     * @param memberTree the content tree that will be used to build the summary tree
     * @return a content tree for the member summary
     */
    public Content getMemberSummaryTree(Content memberTree);

    /**
     * Get the member details tree.
     *
     * @param memberTree the content tree that will be used to build the details tree
     * @return a content tree for the member details
     */
    public Content getMemberDetailsTree(Content memberTree);

    /**
     * Add the footer of the page.
     *
     * @param contentTree content tree to which the footer will be added
     */
    public void addFooter(Content contentTree);

    /**
     * Print the document.
     *
     * @param contentTree content tree that will be printed as a document
     */
    public void printDocument(Content contentTree);

    /**
     * Close the writer.
     */
    public void close() throws IOException;

    /**
     * Return the {@link AnnotationTypeDoc} being documented.
     *
     * @return the AnnotationTypeDoc being documented.
     */
    public AnnotationTypeDoc getAnnotationTypeDoc();
}
