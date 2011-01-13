/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import com.sun.javadoc.*;

/**
 * The interface for writing member summary output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
 */

public interface MemberSummaryWriter {

    /**
     * Get the member summary header for the given class.
     *
     * @param classDoc the class the summary belongs to
     * @param memberSummaryTree the content tree to which the member summary will be added
     * @return a content tree for the member summary header
     */
    public Content getMemberSummaryHeader(ClassDoc classDoc,
            Content memberSummaryTree);

    /**
     * Get the summary table for the given class.
     *
     * @param classDoc the class the summary table belongs to
     * @return a content tree for the member summary table
     */
    public Content getSummaryTableTree(ClassDoc classDoc);

    /**
     * Add the member summary for the given class and member.
     *
     * @param classDoc the class the summary belongs to
     * @param member the member that is documented
     * @param firstSentenceTags the tags for the sentence being documented
     * @param tableTree the content treeto which the information will be added
     * @param counter the counter for determing style for the table row
     */
    public void addMemberSummary(ClassDoc classDoc, ProgramElementDoc member,
        Tag[] firstSentenceTags, Content tableTree, int counter);

    /**
     * Get the inherited member summary header for the given class.
     *
     * @param classDoc the class the summary belongs to
     * @return a content tree containing the inherited summary header
     */
    public Content getInheritedSummaryHeader(ClassDoc classDoc);

    /**
     * Add the inherited member summary for the given class and member.
     *
     * @param classDoc the class the inherited member belongs to
     * @param member the inherited member that is being documented
     * @param isFirst true if this is the first member in the list
     * @param isLast true if this is the last member in the list
     * @param linksTree the content tree to which the links will be added
     */
    public void addInheritedMemberSummary(ClassDoc classDoc,
        ProgramElementDoc member, boolean isFirst, boolean isLast,
        Content linksTree);

    /**
     * Get inherited summary links.
     *
     * @return a content tree conatining the inherited summary links
     */
    public Content getInheritedSummaryLinksTree();

    /**
     * Get the member tree.
     *
     * @param memberTree the content tree representating the member
     * @return a content tree for the member
     */
    public Content getMemberTree(Content memberTree);

    /**
     * Close the writer.
     */
    public void close() throws IOException;
}
