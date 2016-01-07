/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * The interface for writing profile summary output.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */

public interface ProfileSummaryWriter {

    /**
     * Get the header for the summary.
     *
     * @param heading profile name.
     * @return the header to be added to the content tree
     */
    public abstract Content getProfileHeader(String heading);

    /**
     * Get the header for the profile content.
     *
     * @return a content tree for the profile content header
     */
    public abstract Content getContentHeader();

    /**
     * Get the header for the summary header.
     *
     * @return a content tree with the summary header
     */
    public abstract Content getSummaryHeader();

    /**
     * Get the header for the summary tree.
     *
     * @param summaryContentTree the content tree.
     * @return a content tree with the summary tree
     */
    public abstract Content getSummaryTree(Content summaryContentTree);

    /**
     * Get the header for the package summary header.
     *
     * @return a content tree with the package summary header
     */
    public abstract Content getPackageSummaryHeader(PackageDoc pkg);

    /**
     * Get the header for the package summary tree.
     *
     * @return a content tree with the package summary
     */
    public abstract Content getPackageSummaryTree(Content packageSummaryContentTree);

    /**
     * Adds the table of classes to the documentation tree.
     *
     * @param classes the array of classes to document.
     * @param label the label for this table.
     * @param tableSummary the summary string for the table
     * @param tableHeader array of table headers
     * @param packageSummaryContentTree the content tree to which the summaries will be added
     */
    public abstract void addClassesSummary(ClassDoc[] classes, String label,
            String tableSummary, String[] tableHeader, Content packageSummaryContentTree);

    /**
     * Adds the profile content tree to the documentation tree.
     *
     * @param contentTree the tree to which the profile content tree will be added
     * @param profileContentTree the content tree that will be added
     */
    public abstract void addProfileContent(Content contentTree, Content profileContentTree);

    /**
     * Adds the footer to the documentation tree.
     *
     * @param contentTree the tree to which the footer will be added
     */
    public abstract void addProfileFooter(Content contentTree);

    /**
     * Print the profile summary document.
     *
     * @param contentTree the content tree that will be printed
     */
    public abstract void printDocument(Content contentTree) throws IOException;

    /**
     * Close the writer.
     */
    public abstract void close() throws IOException;

}
