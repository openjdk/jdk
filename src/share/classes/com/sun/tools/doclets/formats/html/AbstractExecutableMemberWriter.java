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

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Print method and constructor info.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public abstract class AbstractExecutableMemberWriter extends AbstractMemberWriter {

    public AbstractExecutableMemberWriter(SubWriterHolderWriter writer,
            ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public AbstractExecutableMemberWriter(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * Add the type parameters for the executable member.
     *
     * @param member the member to write type parameters for.
     * @param htmltree the content tree to which the parameters will be added.
     * @return the display length required to write this information.
     */
    protected void addTypeParameters(ExecutableMemberDoc member, Content htmltree) {
        Content typeParameters = getTypeParameters(member);
        if (!typeParameters.isEmpty()) {
            htmltree.addContent(typeParameters);
            htmltree.addContent(writer.getSpace());
        }
    }

    /**
     * Get the type parameters for the executable member.
     *
     * @param member the member for which to get the type parameters.
     * @return the type parameters.
     */
    protected Content getTypeParameters(ExecutableMemberDoc member) {
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
            LinkInfoImpl.Kind.MEMBER_TYPE_PARAMS, member);
        return writer.getTypeParameterLinks(linkInfo);
    }

    /**
     * {@inheritDoc}
     */
    protected Content getDeprecatedLink(ProgramElementDoc member) {
        ExecutableMemberDoc emd = (ExecutableMemberDoc)member;
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER, (MemberDoc) emd,
                emd.qualifiedName() + emd.flatSignature());
    }

    /**
     * Add the summary link for the member.
     *
     * @param context the id of the context where the link will be printed
     * @param cd the classDoc that we should link to
     * @param member the member being linked to
     * @param tdSummary the content tree to which the link will be added
     */
    protected void addSummaryLink(LinkInfoImpl.Kind context, ClassDoc cd, ProgramElementDoc member,
            Content tdSummary) {
        ExecutableMemberDoc emd = (ExecutableMemberDoc)member;
        String name = emd.name();
        Content strong = HtmlTree.STRONG(
                writer.getDocLink(context, cd, (MemberDoc) emd,
                name, false));
        Content code = HtmlTree.CODE(strong);
        addParameters(emd, false, code, name.length() - 1);
        tdSummary.addContent(code);
    }

    /**
     * Add the inherited summary link for the member.
     *
     * @param cd the classDoc that we should link to
     * @param member the member being linked to
     * @param linksTree the content tree to which the link will be added
     */
    protected void addInheritedSummaryLink(ClassDoc cd,
            ProgramElementDoc member, Content linksTree) {
        linksTree.addContent(
                writer.getDocLink(LinkInfoImpl.Kind.MEMBER, cd, (MemberDoc) member,
                member.name(), false));
    }

    /**
     * Add the parameter for the executable member.
     *
     * @param member the member to write parameter for.
     * @param param the parameter that needs to be written.
     * @param isVarArg true if this is a link to var arg.
     * @param tree the content tree to which the parameter information will be added.
     */
    protected void addParam(ExecutableMemberDoc member, Parameter param,
            boolean isVarArg, Content tree) {
        if (param.type() != null) {
            Content link = writer.getLink(new LinkInfoImpl(
                    configuration, LinkInfoImpl.Kind.EXECUTABLE_MEMBER_PARAM,
                    param.type()).varargs(isVarArg));
            tree.addContent(link);
        }
        if(param.name().length() > 0) {
            tree.addContent(writer.getSpace());
            tree.addContent(param.name());
        }
    }

    /**
     * Add the receiver annotations information.
     *
     * @param member the member to write receiver annotations for.
     * @param rcvrType the receiver type.
     * @param descList list of annotation description.
     * @param tree the content tree to which the information will be added.
     */
    protected void addReceiverAnnotations(ExecutableMemberDoc member, Type rcvrType,
            AnnotationDesc[] descList, Content tree) {
        writer.addReceiverAnnotationInfo(member, descList, tree);
        tree.addContent(writer.getSpace());
        tree.addContent(rcvrType.typeName());
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, rcvrType);
        tree.addContent(writer.getTypeParameterLinks(linkInfo));
        tree.addContent(writer.getSpace());
        tree.addContent("this");
    }


    /**
     * Add all the parameters for the executable member.
     *
     * @param member the member to write parameters for.
     * @param htmltree the content tree to which the parameters information will be added.
     */
    protected void addParameters(ExecutableMemberDoc member, Content htmltree, int indentSize) {
        addParameters(member, true, htmltree, indentSize);
    }

    /**
     * Add all the parameters for the executable member.
     *
     * @param member the member to write parameters for.
     * @param includeAnnotations true if annotation information needs to be added.
     * @param htmltree the content tree to which the parameters information will be added.
     */
    protected void addParameters(ExecutableMemberDoc member,
            boolean includeAnnotations, Content htmltree, int indentSize) {
        htmltree.addContent("(");
        String sep = "";
        Parameter[] params = member.parameters();
        String indent = makeSpace(indentSize + 1);
        Type rcvrType = member.receiverType();
        if (includeAnnotations && rcvrType instanceof AnnotatedType) {
            AnnotationDesc[] descList = rcvrType.asAnnotatedType().annotations();
            if (descList.length > 0) {
                addReceiverAnnotations(member, rcvrType, descList, htmltree);
                sep = "," + DocletConstants.NL + indent;
            }
        }
        int paramstart;
        for (paramstart = 0; paramstart < params.length; paramstart++) {
            htmltree.addContent(sep);
            Parameter param = params[paramstart];
            if (!param.name().startsWith("this$")) {
                if (includeAnnotations) {
                    boolean foundAnnotations =
                            writer.addAnnotationInfo(indent.length(),
                            member, param, htmltree);
                    if (foundAnnotations) {
                        htmltree.addContent(DocletConstants.NL);
                        htmltree.addContent(indent);
                    }
                }
                addParam(member, param,
                    (paramstart == params.length - 1) && member.isVarArgs(), htmltree);
                break;
            }
        }

        for (int i = paramstart + 1; i < params.length; i++) {
            htmltree.addContent(",");
            htmltree.addContent(DocletConstants.NL);
            htmltree.addContent(indent);
            if (includeAnnotations) {
                boolean foundAnnotations =
                        writer.addAnnotationInfo(indent.length(), member, params[i],
                        htmltree);
                if (foundAnnotations) {
                    htmltree.addContent(DocletConstants.NL);
                    htmltree.addContent(indent);
                }
            }
            addParam(member, params[i], (i == params.length - 1) && member.isVarArgs(),
                    htmltree);
        }
        htmltree.addContent(")");
    }

    /**
     * Add exceptions for the executable member.
     *
     * @param member the member to write exceptions for.
     * @param htmltree the content tree to which the exceptions information will be added.
     */
    protected void addExceptions(ExecutableMemberDoc member, Content htmltree, int indentSize) {
        Type[] exceptions = member.thrownExceptionTypes();
        if (exceptions.length > 0) {
            LinkInfoImpl memberTypeParam = new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.MEMBER, member);
            String indent = makeSpace(indentSize + 1 - 7);
            htmltree.addContent(DocletConstants.NL);
            htmltree.addContent(indent);
            htmltree.addContent("throws ");
            indent = makeSpace(indentSize + 1);
            Content link = writer.getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.MEMBER, exceptions[0]));
            htmltree.addContent(link);
            for(int i = 1; i < exceptions.length; i++) {
                htmltree.addContent(",");
                htmltree.addContent(DocletConstants.NL);
                htmltree.addContent(indent);
                Content exceptionLink = writer.getLink(new LinkInfoImpl(
                        configuration, LinkInfoImpl.Kind.MEMBER, exceptions[i]));
                htmltree.addContent(exceptionLink);
            }
        }
    }

    protected ClassDoc implementsMethodInIntfac(MethodDoc method,
                                                ClassDoc[] intfacs) {
        for (int i = 0; i < intfacs.length; i++) {
            MethodDoc[] methods = intfacs[i].methods();
            if (methods.length > 0) {
                for (int j = 0; j < methods.length; j++) {
                    if (methods[j].name().equals(method.name()) &&
                          methods[j].signature().equals(method.signature())) {
                        return intfacs[i];
                    }
                }
            }
        }
        return null;
    }

    /**
     * For backward compatibility, include an anchor using the erasures of the
     * parameters.  NOTE:  We won't need this method anymore after we fix
     * see tags so that they use the type instead of the erasure.
     *
     * @param emd the ExecutableMemberDoc to anchor to.
     * @return the 1.4.x style anchor for the ExecutableMemberDoc.
     */
    protected String getErasureAnchor(ExecutableMemberDoc emd) {
        StringBuilder buf = new StringBuilder(emd.name() + "(");
        Parameter[] params = emd.parameters();
        boolean foundTypeVariable = false;
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                buf.append(",");
            }
            Type t = params[i].type();
            foundTypeVariable = foundTypeVariable || t.asTypeVariable() != null;
            buf.append(t.isPrimitive() ?
                t.typeName() : t.asClassDoc().qualifiedName());
            buf.append(t.dimension());
        }
        buf.append(")");
        return foundTypeVariable ? buf.toString() : null;
    }
}
