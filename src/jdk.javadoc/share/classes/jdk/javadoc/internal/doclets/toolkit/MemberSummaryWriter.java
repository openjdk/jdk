/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;

/**
 * The interface for writing member summary output.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */

public interface MemberSummaryWriter {

    /**
     * Get the member summary header for the given class.
     *
     * @param typeElement the class the summary belongs to
     * @param memberSummaryTree the content tree to which the member summary will be added
     * @return a content tree for the member summary header
     */
    public Content getMemberSummaryHeader(TypeElement typeElement,
            Content memberSummaryTree);

    /**
     * Get the summary table for the given class.
     *
     * @param typeElement the class the summary table belongs to
     * @return a content tree for the member summary table
     */
    public Content getSummaryTableTree(TypeElement typeElement);

    /**
     * Add the member summary for the given class and member.
     *
     * @param typeElement the class the summary belongs to
     * @param member the member that is documented
     * @param firstSentenceTags the tags for the sentence being documented
     */
    public void addMemberSummary(TypeElement typeElement, Element member,
            List<? extends DocTree> firstSentenceTags);

    /**
     * Get the inherited member summary header for the given class.
     *
     * @param typeElement the class the summary belongs to
     * @return a content tree containing the inherited summary header
     */
    public Content getInheritedSummaryHeader(TypeElement typeElement);

    /**
     * Add the inherited member summary for the given class and member.
     *
     * @param typeElement the class the inherited member belongs to
     * @param member the inherited member that is being documented
     * @param isFirst true if this is the first member in the list
     * @param isLast true if this is the last member in the list
     * @param linksTree the content tree to which the links will be added
     */
    public void addInheritedMemberSummary(TypeElement typeElement,
        Element member, boolean isFirst, boolean isLast,
        Content linksTree);

    /**
     * Get inherited summary links.
     *
     * @return a content tree containing the inherited summary links
     */
    public Content getInheritedSummaryLinksTree();

    /**
     * Add the member tree to the member summary tree.
     *
     * @param memberSummaryTree the content tree representing the member summary
     * @param memberTree the content tree representing the member
     */
    public void addMemberTree(Content memberSummaryTree, Content memberTree);

    /**
     * Get the member tree.
     *
     * @param memberTree the content tree representing the member
     * @return a content tree for the member
     */
    public Content getMemberTree(Content memberTree);
}
