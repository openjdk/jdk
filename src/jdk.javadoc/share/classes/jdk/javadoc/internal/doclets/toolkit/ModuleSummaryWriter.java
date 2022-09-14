/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;

/**
 * The interface for writing module summary output.
 */
public interface ModuleSummaryWriter {

    /**
     * Get the header for the summary.
     *
     * @param heading module name.
     * @return the header to be added to the content
     */
    Content getModuleHeader(String heading);

    /**
     * Get the header for the module content.
     *
     * @return the module content header
     */
    Content getContentHeader();

    /**
     * Get the header for the summary header.
     *
     * @return the summary header
     */
    Content getSummariesList();

    /**
     * Wrap the content into summary section.
     *
     * @param source the content to wrap into the summary section
     * @return the summary
     */
    Content getSummary(Content source);

    /**
     * Adds the module description.
     *
     * @param moduleContent the content to which the module description
     *                      will be added
     */
    void addModuleDescription(Content moduleContent);

    /**
     * Adds the module signature.
     *
     * @param moduleContent the content to which the module signature
     *                      will be added
     */
    void addModuleSignature(Content moduleContent);

    /**
     * Adds the summary of modules to the list of summaries.
     *
     * @param summariesList the list of summaries
     */
    void addModulesSummary(Content summariesList);

    /**
     * Adds the summary of packages to the list of summaries.
     *
     * @param summariesList the list of summaries
     */
    void addPackagesSummary(Content summariesList);

    /**
     * Adds the summary of services to the list of summaries.
     *
     * @param summariesList the list of summaries
     */
    void addServicesSummary(Content summariesList);

    /**
     * Adds the module content to the documentation.
     *
     * @param source the content that will be added
     */
    void addModuleContent(Content source);

    /**
     * Adds the footer to the documentation.
     */
    void addModuleFooter();

    /**
     * Print the module summary document.
     *
     * @param content the content that will be printed
     * @throws DocFileIOException if there is a problem while writing the document
     */
    void printDocument(Content content) throws DocFileIOException;
}
