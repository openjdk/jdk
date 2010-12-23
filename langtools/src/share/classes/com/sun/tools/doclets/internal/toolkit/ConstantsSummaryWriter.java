/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.io.*;
import com.sun.javadoc.*;

/**
 * The interface for writing constants summary output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
 */

public interface ConstantsSummaryWriter {

    /**
     * Close the writer.
     */
    public abstract void close() throws IOException;

    /**
     * Get the header for the constant summary documentation.
     *
     * @return header that needs to be added to the documentation
     */
    public abstract Content getHeader();

    /**
     * Get the header for the constant content list.
     *
     * @return content header that needs to be added to the documentation
     */
    public abstract Content getContentsHeader();

    /**
     * Adds the given package name link to the constant content list tree.
     *
     * @param pkg                    the {@link PackageDoc} to index.
     * @param parsedPackageName      the parsed package name.  We only Write the
     *                               first 2 directory levels of the package
     *                               name. For example, java.lang.ref would be
     *                               indexed as java.lang.*.
     * @param WriteedPackageHeaders the set of package headers that have already
     *                              been indexed.  We don't want to index
     *                              something more than once.
     * @param contentListTree the content tree to which the link will be added
     */
    public abstract void addLinkToPackageContent(PackageDoc pkg, String parsedPackageName,
        Set<String> WriteedPackageHeaders, Content contentListTree);

    /**
     * Get the content list to be added to the documentation tree.
     *
     * @param contentListTree the content that will be added to the list
     * @return content list that will be added to the documentation tree
     */
    public abstract Content getContentsList(Content contentListTree);

    /**
     * Get the constant summaries for the document.
     *
     * @return constant summaries header to be added to the documentation tree
     */
    public abstract Content getConstantSummaries();

    /**
     * Adds the given package name.
     *
     * @param pkg the {@link PackageDoc} to index.
     * @param parsedPackageName the parsed package name.  We only Write the
     *                          first 2 directory levels of the package
     *                          name. For example, java.lang.ref would be
     *                          indexed as java.lang.*.
     * @param summariesTree the documentation tree to which the package name will
     *                    be written
     */
    public abstract void addPackageName(PackageDoc pkg,
        String parsedPackageName, Content summariesTree);

    /**
     * Get the class summary header for the constants summary.
     *
     * @return the header content for the class constants summary
     */
    public abstract Content getClassConstantHeader();

    /**
     * Adds the constant member table to the documentation tree.
     *
     * @param cd the class whose constants are being documented.
     * @param fields the constants being documented.
     * @param classConstantTree the documentation tree to which theconstant member
     *                    table content will be added
     */
    public abstract void addConstantMembers(ClassDoc cd, List<FieldDoc> fields,
            Content classConstantTree);

    /**
     * Adds the footer for the summary documentation.
     *
     * @param contentTree content tree to which the footer will be added
     */
    public abstract void addFooter(Content contentTree);

    /**
     * Print the constants summary document.
     *
     * @param contentTree content tree which should be printed
     */
    public abstract void printDocument(Content contentTree);

}
