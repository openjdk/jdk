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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.MemberSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.PropertyWriter;


/**
 * Writes property documentation in HTML format.
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
public class PropertyWriterImpl extends AbstractMemberWriter
    implements PropertyWriter, MemberSummaryWriter {

    public PropertyWriterImpl(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getMemberSummaryHeader(TypeElement typeElement,
            Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_PROPERTY_SUMMARY);
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
     * {@inheritDoc}
     */
    @Override
    public Content getPropertyDetailsTreeHeader(TypeElement typeElement,
            Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_PROPERTY_DETAILS);
        Content propertyDetailsTree = writer.getMemberTreeHeader();
        propertyDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.PROPERTY_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.propertyDetailsLabel);
        propertyDetailsTree.addContent(heading);
        return propertyDetailsTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getPropertyDocTreeHeader(ExecutableElement property,
            Content propertyDetailsTree) {
        propertyDetailsTree.addContent(
                writer.getMarkerAnchor(name(property)));
        Content propertyDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(utils.getPropertyLabel(name(property)));
        propertyDocTree.addContent(heading);
        return propertyDocTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getSignature(ExecutableElement property) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(property, pre);
        addModifiers(property, pre);
        Content propertylink = writer.getLink(new LinkInfoImpl(
                configuration, LinkInfoImpl.Kind.MEMBER,
                utils.getReturnType(property)));
        pre.addContent(propertylink);
        pre.addContent(" ");
        if (configuration.linksource) {
            Content propertyName = new StringContent(name(property));
            writer.addSrcLink(property, propertyName, pre);
        } else {
            addName(name(property), pre);
        }
        return pre;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeprecated(ExecutableElement property, Content propertyDocTree) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addComments(ExecutableElement property, Content propertyDocTree) {
        TypeElement holder = (TypeElement)property.getEnclosingElement();
        if (!utils.getBody(property).isEmpty()) {
            if (holder.equals(typeElement) ||
                    (!utils.isPublic(holder) || utils.isLinkable(holder))) {
                writer.addInlineComment(property, propertyDocTree);
            } else {
                Content link =
                        writer.getDocLink(LinkInfoImpl.Kind.PROPERTY_COPY,
                        holder, property,
                        utils.isIncluded(holder)
                                ? holder.getSimpleName() : holder.getQualifiedName(),
                            false);
                Content codeLink = HtmlTree.CODE(link);
                Content descfrmLabel = HtmlTree.SPAN(HtmlStyle.descfrmTypeLabel,
                        utils.isClass(holder)
                                ? writer.descfrmClassLabel
                                : writer.descfrmInterfaceLabel);
                descfrmLabel.addContent(writer.getSpace());
                descfrmLabel.addContent(codeLink);
                propertyDocTree.addContent(HtmlTree.DIV(HtmlStyle.block, descfrmLabel));
                writer.addInlineComment(property, propertyDocTree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTags(ExecutableElement property, Content propertyDocTree) {
        writer.addTagsInfo(property, propertyDocTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getPropertyDetails(Content propertyDetailsTree) {
        if (configuration.allowTag(HtmlTag.SECTION)) {
            HtmlTree htmlTree = HtmlTree.SECTION(getMemberTree(propertyDetailsTree));
            return htmlTree;
        }
        return getMemberTree(propertyDetailsTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getPropertyDoc(Content propertyDocTree,
            boolean isLastContent) {
        return getMemberTree(propertyDocTree, isLastContent);
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
                writer.getResource("doclet.Property_Summary"));
        memberTree.addContent(label);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Property_Summary"),
                configuration.getText("doclet.properties"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getCaption() {
        return configuration.getResource("doclet.Properties");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSummaryTableHeader(Element member) {
        List<String> header = Arrays.asList(configuration.getText("doclet.Type"),
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Property"),
                        configuration.getText("doclet.Description")));
        return header;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSummaryAnchor(TypeElement typeElement, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.PROPERTY_SUMMARY));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryAnchor(TypeElement typeElement, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.PROPERTIES_INHERITANCE,
                configuration.getClassName(typeElement)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, typeElement, false);
        Content label = new StringContent(
                utils.isClass(typeElement)
                       ? configuration.getText("doclet.Properties_Inherited_From_Class")
                       : configuration.getText("doclet.Properties_Inherited_From_Interface"));
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
                writer.getDocLink(context, typeElement,
                member,
                utils.getPropertyLabel(name(member)),
                false,
                true));

        Content code = HtmlTree.CODE(memberLink);
        tdSummary.addContent(code);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addInheritedSummaryLink(TypeElement typeElement, Element member, Content linksTree) {
        String mname = name(member);
        Content content = writer.getDocLink(LinkInfoImpl.Kind.MEMBER, typeElement, member,
                utils.isProperty(mname) ? utils.getPropertyName(mname) : mname,
                false, true);
        linksTree.addContent(content);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSummaryType(Element member, Content tdSummaryType) {
        addModifierAndType(member, utils.getReturnType((ExecutableElement)member), tdSummaryType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getDeprecatedLink(Element member) {
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER, member,
                utils.getFullyQualifiedName(member));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getNavSummaryLink(TypeElement typeElement, boolean link) {
        if (link) {
            if (typeElement == null) {
                return writer.getHyperLink(
                SectionName.PROPERTY_SUMMARY,
                writer.getResource("doclet.navProperty"));
            } else {
                return writer.getHyperLink(
                SectionName.PROPERTIES_INHERITANCE,
                configuration.getClassName(typeElement), writer.getResource("doclet.navProperty"));
            }
        } else {
            return writer.getResource("doclet.navProperty");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.PROPERTY_DETAIL,
                    writer.getResource("doclet.navProperty")));
        } else {
            liNav.addContent(writer.getResource("doclet.navProperty"));
        }
    }
}
