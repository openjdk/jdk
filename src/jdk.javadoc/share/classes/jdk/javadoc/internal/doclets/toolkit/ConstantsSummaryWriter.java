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

import java.util.*;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;

/**
 * The interface for writing constants summary output.
 */
public interface ConstantsSummaryWriter {

    /**
     * Get the header for the constant summary documentation.
     *
     * @return header that needs to be added to the documentation
     */
    Content getHeader();

    /**
     * Get the header for the constant content list.
     *
     * @return content header that needs to be added to the documentation
     */
    Content getContentsHeader();

    /**
     * Adds the given package name link to the constant content list.
     *
     * @param pkg                    the {@link PackageElement} to index.
     * @param writtenPackageHeaders  the set of package headers that have already
     *                               been indexed, we want to index utmost once.
     * @param content                the content to which the link will be added
     */
    void addLinkToPackageContent(PackageElement pkg, Set<PackageElement> writtenPackageHeaders,
                                 Content content);

    /**
     * Add the content list to the documentation.
     *
     * @param content the content that will be added to the list
     */
    void addContentsList(Content content);

    /**
     * Get the constant summaries for the document.
     *
     * @return constant summaries header to be added to the documentation
     */
    Content getConstantSummaries();

    /**
     * Adds the given package name.
     *
     * @param pkg  the parsed package name.  We only Write the
     *                          first 2 directory levels of the package
     *                          name. For example, java.lang.ref would be
     *                          indexed as java.lang.*.
     * @param toContent the summaries documentation
     * @param first true if the first package is listed
     *                    be written
     */
    void addPackageName(PackageElement pkg, Content toContent, boolean first);

    /**
     * Get the class summary header for the constants summary.
     *
     * @return the header content for the class constants summary
     */
    Content getClassConstantHeader();

    /**
     * Add the content list to the documentation summaries.
     *
     * @param fromClassConstant the class constant content that will be added to the list
     */
    void addClassConstant(Content fromClassConstant);

    /**
     * Adds the constant member table to the documentation.
     *
     * @param typeElement the class whose constants are being documented.
     * @param fields the constants being documented.
     * @param target the content to which the constant member
     *               table content will be added
     */
    void addConstantMembers(TypeElement typeElement, Collection<VariableElement> fields,
                            Content target);

    /**
     * Add the summaries list to the content.
     *
     * @param content the summaries content that will be added to the list
     */
    void addConstantSummaries(Content content);

    /**
     * Adds the footer for the summary documentation.
     */
    void addFooter();

    /**
     * Print the constants summary document.
     *
     * @param content the content which should be printed
     * @throws DocFileIOException if there is a problem while writing the document
     */
    void printDocument(Content content) throws DocFileIOException;
}
