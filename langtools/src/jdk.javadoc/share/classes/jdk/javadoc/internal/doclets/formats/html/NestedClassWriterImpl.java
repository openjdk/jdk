/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.io.*;

import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.MemberSummaryWriter;

/**
 * Writes nested class documentation in HTML format.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Jamie Ho (rewrite)
 * @author Bhavesh Patel (Modified)
 */
public class NestedClassWriterImpl extends AbstractMemberWriter
    implements MemberSummaryWriter {

    public NestedClassWriterImpl(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement);
    }

    public NestedClassWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getMemberSummaryHeader(TypeElement typeElement,
            Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_NESTED_CLASS_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, typeElement, memberTree);
        return memberTree;
    }

    /**
     * {@inheritDoc}
     */
    public void addMemberTree(Content memberSummaryTree, Content memberTree) {
        writer.addMemberTree(memberSummaryTree, memberTree);
    }

    /**
     * Close the writer.
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Nested_Class_Summary"));
        memberTree.addContent(label);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Nested_Class_Summary"),
                configuration.getText("doclet.nested_classes"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getCaption() {
        return configuration.getResource("doclet.Nested_Classes");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSummaryTableHeader(Element member) {
        if (utils.isInterface(member)) {
            return Arrays.asList(writer.getModifierTypeHeader(),
                configuration.getText("doclet.0_and_1",
                    configuration.getText("doclet.Interface"),
                    configuration.getText("doclet.Description")));

        } else {
            return Arrays.asList(writer.getModifierTypeHeader(),
                configuration.getText("doclet.0_and_1",
                    configuration.getText("doclet.Class"),
                    configuration.getText("doclet.Description")));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSummaryAnchor(TypeElement typeElement, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.NESTED_CLASS_SUMMARY));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryAnchor(TypeElement typeElement, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.NESTED_CLASSES_INHERITANCE,
                utils.getFullyQualifiedName(typeElement)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, typeElement, false);
        Content label = new StringContent(utils.isInterface(typeElement)
                ? configuration.getText("doclet.Nested_Classes_Interface_Inherited_From_Interface")
                : configuration.getText("doclet.Nested_Classes_Interfaces_Inherited_From_Class"));
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(writer.getSpace());
        labelHeading.addContent(classLink);
        inheritedTree.addContent(labelHeading);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSummaryLink(LinkInfoImpl.Kind context, TypeElement typeElement, Element member,
            Content tdSummary) {
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getLink(new LinkInfoImpl(configuration, context, (TypeElement)member)));
        Content code = HtmlTree.CODE(memberLink);
        tdSummary.addContent(code);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addInheritedSummaryLink(TypeElement typeElement, Element member, Content linksTree) {
        linksTree.addContent(
                writer.getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER,
                        (TypeElement)member)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSummaryType(Element member, Content tdSummaryType) {
        addModifierAndType(member, null, tdSummaryType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getDeprecatedLink(Element member) {
        return writer.getQualifiedClassLink(LinkInfoImpl.Kind.MEMBER, member);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getNavSummaryLink(TypeElement typeElement, boolean link) {
        if (link) {
            if (typeElement == null) {
                return writer.getHyperLink(
                        SectionName.NESTED_CLASS_SUMMARY,
                        writer.getResource("doclet.navNested"));
            } else {
                return writer.getHyperLink(
                        SectionName.NESTED_CLASSES_INHERITANCE,
                        utils.getFullyQualifiedName(typeElement), writer.getResource("doclet.navNested"));
            }
        } else {
            return writer.getResource("doclet.navNested");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addNavDetailLink(boolean link, Content liNav) {
    }
}
