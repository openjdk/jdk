/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.io.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.javac.util.StringUtils;

/**
 * Writes method documentation in HTML format.
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
public class MethodWriterImpl extends AbstractExecutableMemberWriter
        implements MethodWriter, MemberSummaryWriter {

    /**
     * Construct a new MethodWriterImpl.
     *
     * @param writer the writer for the class that the methods belong to.
     * @param classDoc the class being documented.
     */
    public MethodWriterImpl(SubWriterHolderWriter writer, ClassDoc classDoc) {
        super(writer, classDoc);
    }

    /**
     * Construct a new MethodWriterImpl.
     *
     * @param writer The writer for the class that the methods belong to.
     */
    public MethodWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * {@inheritDoc}
     */
    public Content getMemberSummaryHeader(ClassDoc classDoc,
            Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_METHOD_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getMethodDetailsTreeHeader(ClassDoc classDoc,
            Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_METHOD_DETAILS);
        Content methodDetailsTree = writer.getMemberTreeHeader();
        methodDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.METHOD_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.methodDetailsLabel);
        methodDetailsTree.addContent(heading);
        return methodDetailsTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getMethodDocTreeHeader(MethodDoc method,
            Content methodDetailsTree) {
        String erasureAnchor;
        if ((erasureAnchor = getErasureAnchor(method)) != null) {
            methodDetailsTree.addContent(writer.getMarkerAnchor((erasureAnchor)));
        }
        methodDetailsTree.addContent(
                writer.getMarkerAnchor(writer.getAnchor(method)));
        Content methodDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(method.name());
        methodDocTree.addContent(heading);
        return methodDocTree;
    }

    /**
     * Get the signature for the given method.
     *
     * @param method the method being documented.
     * @return a content object for the signature
     */
    public Content getSignature(MethodDoc method) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(method, pre);
        addModifiers(method, pre);
        addTypeParameters(method, pre);
        addReturnType(method, pre);
        if (configuration.linksource) {
            Content methodName = new StringContent(method.name());
            writer.addSrcLink(method, methodName, pre);
        } else {
            addName(method.name(), pre);
        }
        int indent = pre.charCount();
        addParameters(method, pre, indent);
        addExceptions(method, pre, indent);
        return pre;
    }

    /**
     * {@inheritDoc}
     */
    public void addDeprecated(MethodDoc method, Content methodDocTree) {
        addDeprecatedInfo(method, methodDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addComments(Type holder, MethodDoc method, Content methodDocTree) {
        ClassDoc holderClassDoc = holder.asClassDoc();
        if (method.inlineTags().length > 0) {
            if (holder.asClassDoc().equals(classdoc) ||
                    (! (holderClassDoc.isPublic() ||
                    Util.isLinkable(holderClassDoc, configuration)))) {
                writer.addInlineComment(method, methodDocTree);
            } else {
                Content link =
                        writer.getDocLink(LinkInfoImpl.Kind.METHOD_DOC_COPY,
                        holder.asClassDoc(), method,
                        holder.asClassDoc().isIncluded() ?
                            holder.typeName() : holder.qualifiedTypeName(),
                            false);
                Content codelLink = HtmlTree.CODE(link);
                Content descfrmLabel = HtmlTree.SPAN(HtmlStyle.descfrmTypeLabel, holder.asClassDoc().isClass()?
                    writer.descfrmClassLabel : writer.descfrmInterfaceLabel);
                descfrmLabel.addContent(writer.getSpace());
                descfrmLabel.addContent(codelLink);
                methodDocTree.addContent(HtmlTree.DIV(HtmlStyle.block, descfrmLabel));
                writer.addInlineComment(method, methodDocTree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addTags(MethodDoc method, Content methodDocTree) {
        writer.addTagsInfo(method, methodDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getMethodDetails(Content methodDetailsTree) {
        return getMemberTree(methodDetailsTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getMethodDoc(Content methodDocTree,
            boolean isLastContent) {
        return getMemberTree(methodDocTree, isLastContent);
    }

    /**
     * Close the writer.
     */
    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.METHODS;
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Method_Summary"));
        memberTree.addContent(label);
    }

    /**
     * {@inheritDoc}
     */
    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Method_Summary"),
                configuration.getText("doclet.methods"));
    }

    /**
     * {@inheritDoc}
     */
    public Content getCaption() {
        return configuration.getResource("doclet.Methods");
    }

    /**
     * {@inheritDoc}
     */
    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[] {
            writer.getModifierTypeHeader(),
            configuration.getText("doclet.0_and_1",
                    configuration.getText("doclet.Method"),
                    configuration.getText("doclet.Description"))
        };
        return header;
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.METHOD_SUMMARY));
    }

    /**
     * {@inheritDoc}
     */
    public void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.METHODS_INHERITANCE, configuration.getClassName(cd)));
    }

    /**
     * {@inheritDoc}
     */
    public void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, cd, false);
        Content label = new StringContent(cd.isClass() ?
            configuration.getText("doclet.Methods_Inherited_From_Class") :
            configuration.getText("doclet.Methods_Inherited_From_Interface"));
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(writer.getSpace());
        labelHeading.addContent(classLink);
        inheritedTree.addContent(labelHeading);
    }

    /**
     * {@inheritDoc}
     */
    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {
        MethodDoc meth = (MethodDoc)member;
        addModifierAndType(meth, meth.returnType(), tdSummaryType);
    }

    /**
     * {@inheritDoc}
     */
    protected static void addOverridden(HtmlDocletWriter writer,
            Type overriddenType, MethodDoc method, Content dl) {
        if (writer.configuration.nocomment) {
            return;
        }
        ClassDoc holderClassDoc = overriddenType.asClassDoc();
        if (! (holderClassDoc.isPublic() ||
            Util.isLinkable(holderClassDoc, writer.configuration))) {
            //This is an implementation detail that should not be documented.
            return;
        }
        if (overriddenType.asClassDoc().isIncluded() && ! method.isIncluded()) {
            //The class is included but the method is not.  That means that it
            //is not visible so don't document this.
            return;
        }
        Content label = writer.overridesLabel;
        LinkInfoImpl.Kind context = LinkInfoImpl.Kind.METHOD_OVERRIDES;

        if (method != null) {
            if (overriddenType.asClassDoc().isAbstract() && method.isAbstract()){
                //Abstract method is implemented from abstract class,
                //not overridden
                label = writer.specifiedByLabel;
                context = LinkInfoImpl.Kind.METHOD_SPECIFIED_BY;
            }
            Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.overrideSpecifyLabel, label));
            dl.addContent(dt);
            Content overriddenTypeLink =
                    writer.getLink(new LinkInfoImpl(writer.configuration, context, overriddenType));
            Content codeOverridenTypeLink = HtmlTree.CODE(overriddenTypeLink);
            String name = method.name();
            Content methlink = writer.getLink(
                    new LinkInfoImpl(writer.configuration, LinkInfoImpl.Kind.MEMBER,
                    overriddenType.asClassDoc())
                    .where(writer.getName(writer.getAnchor(method))).label(name));
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.addContent(writer.getSpace());
            dd.addContent(writer.getResource("doclet.in_class"));
            dd.addContent(writer.getSpace());
            dd.addContent(codeOverridenTypeLink);
            dl.addContent(dd);
        }
    }

    /**
     * Parse the &lt;Code&gt; tag and return the text.
     */
    protected String parseCodeTag(String tag){
        if(tag == null){
            return "";
        }

        String lc = StringUtils.toLowerCase(tag);
        int begin = lc.indexOf("<code>");
        int end = lc.indexOf("</code>");
        if(begin == -1 || end == -1 || end <= begin){
            return tag;
        } else {
            return tag.substring(begin + 6, end);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected static void addImplementsInfo(HtmlDocletWriter writer,
            MethodDoc method, Content dl) {
        if(writer.configuration.nocomment){
            return;
        }
        ImplementedMethods implementedMethodsFinder =
                new ImplementedMethods(method, writer.configuration);
        MethodDoc[] implementedMethods = implementedMethodsFinder.build();
        for (MethodDoc implementedMeth : implementedMethods) {
            Type intfac = implementedMethodsFinder.getMethodHolder(implementedMeth);
            Content intfaclink = writer.getLink(new LinkInfoImpl(
                    writer.configuration, LinkInfoImpl.Kind.METHOD_SPECIFIED_BY, intfac));
            Content codeIntfacLink = HtmlTree.CODE(intfaclink);
            Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.overrideSpecifyLabel, writer.specifiedByLabel));
            dl.addContent(dt);
            Content methlink = writer.getDocLink(
                    LinkInfoImpl.Kind.MEMBER, implementedMeth,
                    implementedMeth.name(), false);
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.addContent(writer.getSpace());
            dd.addContent(writer.getResource("doclet.in_interface"));
            dd.addContent(writer.getSpace());
            dd.addContent(codeIntfacLink);
            dl.addContent(dd);
        }
    }

    /**
     * Add the return type.
     *
     * @param method the method being documented.
     * @param htmltree the content tree to which the return type will be added
     */
    protected void addReturnType(MethodDoc method, Content htmltree) {
        Type type = method.returnType();
        if (type != null) {
            Content linkContent = writer.getLink(
                    new LinkInfoImpl(configuration, LinkInfoImpl.Kind.RETURN_TYPE, type));
            htmltree.addContent(linkContent);
            htmltree.addContent(writer.getSpace());
        }
    }

    /**
     * {@inheritDoc}
     */
    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            if (cd == null) {
                return writer.getHyperLink(
                        SectionName.METHOD_SUMMARY,
                        writer.getResource("doclet.navMethod"));
            } else {
                return writer.getHyperLink(
                        SectionName.METHODS_INHERITANCE,
                        configuration.getClassName(cd), writer.getResource("doclet.navMethod"));
            }
        } else {
            return writer.getResource("doclet.navMethod");
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.METHOD_DETAIL, writer.getResource("doclet.navMethod")));
        } else {
            liNav.addContent(writer.getResource("doclet.navMethod"));
        }
    }
}
