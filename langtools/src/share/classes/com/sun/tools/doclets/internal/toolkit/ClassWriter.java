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
 * The interface for writing class output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
 */

public interface ClassWriter {

    /**
     * Get the header of the page.
     *
     * @param header the header string to write
     * @return header content that needs to be added to the documentation
     */
    public Content getHeader(String header);

    /**
     * Get the class content header.
     *
     * @return class content header that needs to be added to the documentation
     */
    public Content getClassContentHeader();

    /**
     * Add the class tree documentation.
     *
     * @param classContentTree class content tree to which the documentation will be added
     */
    public void addClassTree(Content classContentTree);

    /**
     * Get the class information tree header.
     *
     * @return class informaion tree header that needs to be added to the documentation
     */
    public Content getClassInfoTreeHeader();

    /**
     * Add the type parameter information.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addTypeParamInfo(Content classInfoTree);

    /**
     * Add all super interfaces if this is an interface.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addSuperInterfacesInfo(Content classInfoTree);

    /**
     * Add all implemented interfaces if this is a class.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addImplementedInterfacesInfo(Content classInfoTree);

    /**
     * Add all the classes that extend this one.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addSubClassInfo(Content classInfoTree);

    /**
     * Add all the interfaces that extend this one.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addSubInterfacesInfo(Content classInfoTree);

    /**
     * If this is an interface, add all classes that implement this
     * interface.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addInterfaceUsageInfo(Content classInfoTree);

    /**
     * If this is an inner class or interface, add the enclosing class or
     * interface.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addNestedClassInfo (Content classInfoTree);

    /**
     * Get the class information.
     *
     * @param classInfoTree content tree conatining the class information
     * @return a content tree for the class
     */
    public Content getClassInfo(Content classInfoTree);

    /**
     * If this class is deprecated, add the appropriate information.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addClassDeprecationInfo (Content classInfoTree);

    /**
     * Add the signature of the current class content tree.
     *
     * @param modifiers the modifiers for the signature
     * @param classInfoTree the class content tree to which the signature will be added
     */
    public void addClassSignature(String modifiers, Content classInfoTree);

    /**
     * Build the class description.
     *
     * @param classInfoTree content tree to which the documentation will be added
     */
    public void addClassDescription(Content classInfoTree);

    /**
     * Add the tag information for the current class.
     *
     * @param classInfoTree content tree to which the tag information will be added
     */
    public void addClassTagInfo(Content classInfoTree);

    /**
     * Get the member tree header for the class.
     *
     * @return a content tree for the member tree header
     */
    public Content getMemberTreeHeader();

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
     * Return the classDoc being documented.
     *
     * @return the classDoc being documented.
     */
    public ClassDoc getClassDoc();

    /**
     * Get the member summary tree.
     *
     * @param memberTree the content tree used to build the summary tree
     * @return a content tree for the member summary
     */
    public Content getMemberSummaryTree(Content memberTree);

    /**
     * Get the member details tree.
     *
     * @param memberTree the content tree used to build the details tree
     * @return a content tree for the member details
     */
    public Content getMemberDetailsTree(Content memberTree);
}
