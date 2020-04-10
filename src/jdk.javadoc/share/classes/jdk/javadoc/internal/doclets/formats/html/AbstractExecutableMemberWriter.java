/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.SimpleTypeVisitor9;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;

import static jdk.javadoc.internal.doclets.formats.html.LinkInfoImpl.Kind.EXECUTABLE_MEMBER_PARAM;
import static jdk.javadoc.internal.doclets.formats.html.LinkInfoImpl.Kind.MEMBER;
import static jdk.javadoc.internal.doclets.formats.html.LinkInfoImpl.Kind.MEMBER_TYPE_PARAMS;
import static jdk.javadoc.internal.doclets.formats.html.LinkInfoImpl.Kind.RECEIVER_TYPE;

/**
 * Print method and constructor info.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class AbstractExecutableMemberWriter extends AbstractMemberWriter {

    public AbstractExecutableMemberWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement);
    }

    public AbstractExecutableMemberWriter(SubWriterHolderWriter writer) {
        super(writer);
    }


    /**
     * Get the type parameters for the executable member.
     *
     * @param member the member for which to get the type parameters.
     * @return the type parameters.
     */
    protected Content getTypeParameters(ExecutableElement member) {
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration, MEMBER_TYPE_PARAMS, member);
        return writer.getTypeParameterLinks(linkInfo);
    }

    @Override
    protected Content getDeprecatedLink(Element member) {
        Content deprecatedLinkContent = new ContentBuilder();
        deprecatedLinkContent.add(utils.getFullyQualifiedName(member));
        if (!utils.isConstructor(member)) {
            deprecatedLinkContent.add(".");
            deprecatedLinkContent.add(member.getSimpleName());
        }
        String signature = utils.flatSignature((ExecutableElement) member, typeElement);
        if (signature.length() > 2) {
            deprecatedLinkContent.add(Entity.ZERO_WIDTH_SPACE);
        }
        deprecatedLinkContent.add(signature);

        return writer.getDocLink(MEMBER, utils.getEnclosingTypeElement(member), member, deprecatedLinkContent);
    }

    /**
     * Add the summary link for the member.
     *
     * @param context the id of the context where the link will be printed
     * @param te the type element being linked to
     * @param member the member being linked to
     * @param tdSummary the content tree to which the link will be added
     */
    @Override
    protected void addSummaryLink(LinkInfoImpl.Kind context, TypeElement te, Element member,
            Content tdSummary) {
        ExecutableElement ee = (ExecutableElement)member;
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getDocLink(context, te, ee,
                name(ee), false));
        Content code = HtmlTree.CODE(memberLink);
        addParameters(ee, code);
        tdSummary.add(code);
    }

    /**
     * Add the inherited summary link for the member.
     *
     * @param te the type element that we should link to
     * @param member the member being linked to
     * @param linksTree the content tree to which the link will be added
     */
    @Override
    protected void addInheritedSummaryLink(TypeElement te, Element member, Content linksTree) {
        linksTree.add(writer.getDocLink(MEMBER, te, member, name(member), false));
    }

    /**
     * Add the parameter for the executable member.
     *
     * @param member the member to write parameter for.
     * @param param the parameter that needs to be written.
     * @param isVarArg true if this is a link to var arg.
     * @param tree the content tree to which the parameter information will be added.
     */
    protected void addParam(ExecutableElement member, VariableElement param, TypeMirror paramType,
            boolean isVarArg, Content tree) {
        Content link = writer.getLink(new LinkInfoImpl(configuration, EXECUTABLE_MEMBER_PARAM,
                paramType).varargs(isVarArg));
        tree.add(link);
        if(name(param).length() > 0) {
            tree.add(Entity.NO_BREAK_SPACE);
            tree.add(name(param));
        }
    }

    /**
     * Add the receiver annotations information.
     *
     * @param member the member to write receiver annotations for.
     * @param rcvrType the receiver type.
     * @param annotationMirrors list of annotation descriptions.
     * @param tree the content tree to which the information will be added.
     */
    protected void addReceiverAnnotations(ExecutableElement member, TypeMirror rcvrType,
            List<? extends AnnotationMirror> annotationMirrors, Content tree) {
        writer.addReceiverAnnotationInfo(member, rcvrType, annotationMirrors, tree);
        tree.add(Entity.NO_BREAK_SPACE);
        tree.add(utils.getTypeName(rcvrType, false));
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration, RECEIVER_TYPE, rcvrType);
        tree.add(writer.getTypeParameterLinks(linkInfo));
        tree.add(Entity.NO_BREAK_SPACE);
        tree.add("this");
    }


    /**
     * Add all the parameters for the executable member.
     *
     * @param member the member to write parameters for.
     * @param htmltree the content tree to which the parameters information will be added.
     */
    protected void addParameters(ExecutableElement member, Content htmltree) {
        Content paramTree = getParameters(member, false);
        if (paramTree.isEmpty()) {
            htmltree.add("()");
        } else {
            htmltree.add(Entity.ZERO_WIDTH_SPACE);
            htmltree.add("(");
            htmltree.add(paramTree);
            paramTree.add(")");
        }
    }

    /**
     * Add all the parameters for the executable member.
     *
     * @param member the member to write parameters for.
     * @param includeAnnotations true if annotation information needs to be added.
     * @return the content tree containing the parameter information
     */
    protected Content getParameters(ExecutableElement member, boolean includeAnnotations) {
        Content paramTree = new ContentBuilder();
        String sep = "";
        List<? extends VariableElement> parameters = member.getParameters();
        TypeMirror rcvrType = member.getReceiverType();
        if (includeAnnotations && rcvrType != null && utils.isAnnotated(rcvrType)) {
            List<? extends AnnotationMirror> annotationMirrors = rcvrType.getAnnotationMirrors();
            addReceiverAnnotations(member, rcvrType, annotationMirrors, paramTree);
            sep = "," + DocletConstants.NL;
        }
        int paramstart;
        ExecutableType instMeth = utils.asInstantiatedMethodType(typeElement, member);
        for (paramstart = 0; paramstart < parameters.size(); paramstart++) {
            paramTree.add(sep);
            VariableElement param = parameters.get(paramstart);
            TypeMirror paramType = instMeth.getParameterTypes().get(paramstart);

            if (param.getKind() != ElementKind.INSTANCE_INIT) {
                if (includeAnnotations) {
                    boolean foundAnnotations =
                            writer.addAnnotationInfo(param, paramTree);
                    if (foundAnnotations) {
                        paramTree.add(DocletConstants.NL);
                    }
                }
                addParam(member, param, paramType,
                    (paramstart == parameters.size() - 1) && member.isVarArgs(), paramTree);
                break;
            }
        }

        for (int i = paramstart + 1; i < parameters.size(); i++) {
            paramTree.add(",");
            paramTree.add(DocletConstants.NL);
            if (includeAnnotations) {
                boolean foundAnnotations =
                        writer.addAnnotationInfo(parameters.get(i),
                        paramTree);
                if (foundAnnotations) {
                    paramTree.add(DocletConstants.NL);
                }
            }
            addParam(member, parameters.get(i), instMeth.getParameterTypes().get(i),
                    (i == parameters.size() - 1) && member.isVarArgs(),
                    paramTree);
        }

        return paramTree;
    }

    /**
     * Get a content tree containing the exception information for the executable member.
     *
     * @param member the member to write exceptions for.
     * @return the content tree containing the exceptions information.
     */
    protected Content getExceptions(ExecutableElement member) {
        List<? extends TypeMirror> exceptions = utils.asInstantiatedMethodType(typeElement, member).getThrownTypes();
        Content htmltree = new ContentBuilder();
        if (!exceptions.isEmpty()) {
            Content link = writer.getLink(new LinkInfoImpl(configuration, MEMBER, exceptions.get(0)));
            htmltree.add(link);
            for (int i = 1; i < exceptions.size(); i++) {
                htmltree.add(",");
                htmltree.add(DocletConstants.NL);
                Content exceptionLink = writer.getLink(new LinkInfoImpl(configuration, MEMBER,
                        exceptions.get(i)));
                htmltree.add(exceptionLink);
            }
        }
        return htmltree;
    }

    protected TypeElement implementsMethodInIntfac(ExecutableElement method,
                                                List<TypeElement> intfacs) {
        for (TypeElement intf : intfacs) {
            List<ExecutableElement> methods = utils.getMethods(intf);
            if (!methods.isEmpty()) {
                for (ExecutableElement md : methods) {
                    if (name(md).equals(name(method)) &&
                        md.toString().equals(method.toString())) {
                        return intf;
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
     * @param executableElement the ExecutableElement to anchor to.
     * @return the 1.4.x style anchor for the executable element.
     */
    protected String getErasureAnchor(ExecutableElement executableElement) {
        final StringBuilder buf = new StringBuilder(writer.anchorName(executableElement));
        buf.append("(");
        List<? extends VariableElement> parameters = executableElement.getParameters();
        boolean foundTypeVariable = false;
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                buf.append(",");
            }
            TypeMirror t = parameters.get(i).asType();
            SimpleTypeVisitor9<Boolean, Void> stv = new SimpleTypeVisitor9<Boolean, Void>() {
                boolean foundTypeVariable = false;

                @Override
                public Boolean visitArray(ArrayType t, Void p) {
                    visit(t.getComponentType());
                    buf.append(utils.getDimension(t));
                    return foundTypeVariable;
                }

                @Override
                public Boolean visitTypeVariable(TypeVariable t, Void p) {
                    buf.append(utils.asTypeElement(t).getQualifiedName());
                    foundTypeVariable = true;
                    return foundTypeVariable;
                }

                @Override
                public Boolean visitDeclared(DeclaredType t, Void p) {
                    buf.append(utils.getQualifiedTypeName(t));
                    return foundTypeVariable;
                }

                @Override
                protected Boolean defaultAction(TypeMirror e, Void p) {
                    buf.append(e);
                    return foundTypeVariable;
                }
            };

            boolean isTypeVariable = stv.visit(t);
            if (!foundTypeVariable) {
                foundTypeVariable = isTypeVariable;
            }
        }
        buf.append(")");
        return foundTypeVariable ? writer.links.getName(buf.toString()) : null;
    }
}
