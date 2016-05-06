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
import java.util.Set;

import javax.lang.model.element.PackageElement;

/**
 * The interface for writing module summary output.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */

public interface ModuleSummaryWriter {

    /**
     * Get the header for the summary.
     *
     * @param heading module name.
     * @return the header to be added to the content tree
     */
    public abstract Content getModuleHeader(String heading);

    /**
     * Get the header for the module content.
     *
     * @return a content tree for the module content header
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
     * Adds the module description.
     *
     * @param moduleContentTree the content tree to which the module description
     *                           will be added
     */
    public abstract void addModuleDescription(Content moduleContentTree);

    /**
     * Adds the table of packages to the documentation tree.
     *
     * @param packages the set of packages that should be added.
     * @param label the label for this table.
     * @param tableSummary the summary string for the table
     * @param summaryContentTree the content tree to which the summary will be added
     */
    public abstract void addPackagesSummary(Set<PackageElement> packages, String label,
            String tableSummary, Content summaryContentTree);

    /**
     * Adds the module content tree to the documentation tree.
     *
     * @param contentTree the tree to which the module content tree will be added
     * @param moduleContentTree the content tree that will be added
     */
    public abstract void addModuleContent(Content contentTree, Content moduleContentTree);

    /**
     * Adds the footer to the documentation tree.
     *
     * @param contentTree the tree to which the footer will be added
     */
    public abstract void addModuleFooter(Content contentTree);

    /**
     * Print the module summary document.
     *
     * @param contentTree the content tree that will be printed
     */
    public abstract void printDocument(Content contentTree) throws IOException;

    /**
     * Close the writer.
     */
    public abstract void close() throws IOException;

}
