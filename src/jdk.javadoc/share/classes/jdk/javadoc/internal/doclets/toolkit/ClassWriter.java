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

import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;

/**
 * The interface for writing class output.
 */
public interface ClassWriter {

    /**
     * Get the header of the page.
     *
     * @param header the header string to write
     * @return header content that needs to be added to the documentation
     */
    Content getHeader(String header);

    /**
     * Get the class content header.
     *
     * @return class content header that needs to be added to the documentation
     */
    Content getClassContentHeader();

    /**
     * Add the class inheritance tree documentation.
     *
     * @param target the content to which the documentation will be added
     */
    void addClassTree(Content target);

    /**
     * Add the type parameter and state component information.
     *
     * @param target the content to which the documentation will be added
     */
    void addParamInfo(Content target);

    /**
     * Add all super interfaces if this is an interface.
     *
     * @param target the content to which the documentation will be added
     */
    void addSuperInterfacesInfo(Content target);

    /**
     * Add all implemented interfaces if this is a class.
     *
     * @param target the content to which the documentation will be added
     */
    void addImplementedInterfacesInfo(Content target);

    /**
     * Add all the classes that extend this one.
     *
     * @param target the content to which the documentation will be added
     */
    void addSubClassInfo(Content target);

    /**
     * Add all the interfaces that extend this one.
     *
     * @param target the content to which the documentation will be added
     */
    void addSubInterfacesInfo(Content target);

    /**
     * If this is an interface, add all classes that implement this
     * interface.
     *
     * @param target the content to which the documentation will be added
     */
    void addInterfaceUsageInfo(Content target);

    /**
     * If this is an functional interface, display appropriate message.
     *
     * @param target the content to which the documentation will be added
     */
    void addFunctionalInterfaceInfo(Content target);

    /**
     * If this is an inner class or interface, add the enclosing class or
     * interface.
     *
     * @param target the content to which the documentation will be added
     */
    void addNestedClassInfo(Content target);

    /**
     * {@return the class information}
     *
     * @param classInfo the class information
     */
    Content getClassInfo(Content classInfo);

    /**
     * If this class is deprecated, add the appropriate information.
     *
     * @param classInfo the content to which the documentation will be added
     */
    void addClassDeprecationInfo(Content classInfo);

    /**
     * Add the signature of the current class content.
     *
     * @param classInfo the class content to which the signature will be added
     */
    void addClassSignature(Content classInfo);

    /**
     * Build the class description.
     *
     * @param classInfo the content to which the documentation will be added
     */
    void addClassDescription(Content classInfo);

    /**
     * Add the tag information for the current class.
     *
     * @param classInfo the content to which the tag information will be added
     */
    void addClassTagInfo(Content classInfo);

    /**
     * Returns a list to be used for the list of summaries for members of a given kind.
     *
     * @return a list to be used for the list of summaries for members of a given kind
     */
    Content getSummariesList();

    /**
     * Returns an item for the list of summaries for members of a given kind.
     *
     * @param content content for the item
     * @return an item for the list of summaries for members of a given kind
     */
    Content getSummariesListItem(Content content);

    /**
     * Returns a list to be used for the list of details for members of a given kind.
     *
     * @return a list to be used for the list of details for members of a given kind
     */
    Content getDetailsList();

    /**
     * Returns an item for the list of details for members of a given kind.
     *
     * @param content content for the item
     * @return an item for the list of details for members of a given kind
     */
    Content getDetailsListItem(Content content);

    /**
     * Add the class content.
     *
     * @param classContent the class content which will be added to the content
     */
    void addClassContent(Content classContent);

    /**
     * Add the footer of the page.
     */
    void addFooter();

    /**
     * Print the document.
     *
     * @param content the content that will be printed as a document
     * @throws DocFileIOException if there is a problem while writing the document
     */
    void printDocument(Content content) throws DocFileIOException;

    /**
     * Return the TypeElement being documented.
     *
     * @return the TypeElement being documented.
     */
    TypeElement getTypeElement();

    /**
     * {@return the member summary}
     *
     * @param memberContent the content used to build the summary
     */
    Content getMemberSummary(Content memberContent);

    /**
     * {@return the member details}
     *
     * @param memberContent the content used to generate the member details
     */
    Content getMemberDetails(Content memberContent);
}
