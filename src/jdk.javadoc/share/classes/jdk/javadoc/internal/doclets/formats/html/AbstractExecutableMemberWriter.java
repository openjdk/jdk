/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor14;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.Entity;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

import static jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo.Kind.LINK_TYPE_PARAMS;
import static jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS;
import static jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo.Kind.PLAIN;
import static jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo.Kind.SHOW_PREVIEW;
import static jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS_AND_BOUNDS;

/**
 * Abstract "member writer" for executable elements.
 */
public abstract class AbstractExecutableMemberWriter extends AbstractMemberWriter {

    /**
     * Creates a writer for executable members, for a given enclosing writer, type element, and kind of member.
     *
     * @param writer the enclosing "page" writer, with an associated type element
     * @param typeElement the type element
     * @param kind the kind of member: one of {@link VisibleMemberTable.Kind#CONSTRUCTORS} or {@link VisibleMemberTable.Kind#METHODS}
     */
    protected AbstractExecutableMemberWriter(SubWriterHolderWriter writer, TypeElement typeElement,
                                          VisibleMemberTable.Kind kind) {
        super(writer, typeElement, kind);

        // The following would be better before the preceding call to super; see JDK-8300786
        switch (kind) {
            case CONSTRUCTORS, METHODS -> { }
            default -> throw new IllegalArgumentException(kind.toString());
        }
    }

    /**
     * Creates a writer for executable members, for a given enclosing writer.
     * No type element or kind is provided, limiting the set of methods that can be used.
     *
     * @param writer the enclosing "page" writer.
     */
    protected AbstractExecutableMemberWriter(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * Get the type parameters for the executable member.
     *
     * @param member the member for which to get the type parameters.
     * @return the type parameters.
     */
    protected Content getTypeParameters(ExecutableElement member) {
        HtmlLinkInfo linkInfo = new HtmlLinkInfo(configuration, LINK_TYPE_PARAMS_AND_BOUNDS, member)
                .addLineBreaksInTypeParameters(true)
                .showTypeParameterAnnotations(true);
        return writer.getTypeParameterLinks(linkInfo);
    }

    @Override
    protected Content getSummaryLink(Element member) {
        Content content = new ContentBuilder();
        content.add(utils.getFullyQualifiedName(member));
        if (!utils.isConstructor(member)) {
            content.add(".");
            content.add(member.getSimpleName());
        }
        String signature = utils.flatSignature((ExecutableElement) member, typeElement);
        if (signature.length() > 2) {
            content.add(new HtmlTree(HtmlTag.WBR));
        }
        content.add(signature);

        return writer.getDocLink(SHOW_PREVIEW, utils.getEnclosingTypeElement(member),
                member, content, null, false);
    }

    @Override
    protected void addSummaryLink(HtmlLinkInfo.Kind context, TypeElement te, Element member,
                                  Content target) {
        ExecutableElement ee = (ExecutableElement)member;
        Content memberLink = writer.getDocLink(context, te, ee, name(ee), HtmlStyles.memberNameLink);
        var code = HtmlTree.CODE(memberLink);
        addParameters(ee, code);
        target.add(code);
    }

    @Override
    protected void addInheritedSummaryLink(TypeElement te, Element member, Content target) {
        target.add(writer.getDocLink(PLAIN, te, member, name(member)));
    }

    /**
     * Adds the generic type parameters.
     *
     * @param member the member to add the generic type parameters for
     * @param target the content to which the generic type parameters will be added
     */
    protected void addTypeParameters(ExecutableElement member, Content target) {
        Content typeParameters = getTypeParameters(member);
        target.add(typeParameters);
        // Add explicit line break between method type parameters and
        // return type in member summary table to avoid random wrapping.
        if (typeParameters.charCount() > 10) {
            target.add(new HtmlTree(HtmlTag.BR));
        } else {
            target.add(Entity.NO_BREAK_SPACE);
        }
    }

    /**
     * Add the parameter for the executable member.
     *
     * @param param the parameter that needs to be added.
     * @param paramType the type of the parameter.
     * @param isVarArg true if this is a link to var arg.
     * @param target the content to which the parameter information will be added.
     */
    protected void addParam(VariableElement param, TypeMirror paramType, boolean isVarArg,
                            Content target) {
        HtmlLinkInfo linkInfo = new HtmlLinkInfo(configuration, LINK_TYPE_PARAMS, paramType)
                .varargs(isVarArg)
                .showTypeParameterAnnotations(true);
        Content link = writer.getLink(linkInfo);
        target.add(link);
        if(name(param).length() > 0) {
            target.add(Entity.NO_BREAK_SPACE);
            target.add(name(param));
        }
    }

    /**
     * Add the receiver information.
     *
     * <p>Note: receivers can only have type-use annotations.</p>
     *
     * @param member the member to write receiver annotations for.
     * @param rcvrType the receiver type.
     * @param target the content to which the information will be added.
     */
    protected void addReceiver(ExecutableElement member, TypeMirror rcvrType, Content target) {
        var info = new HtmlLinkInfo(configuration, SHOW_TYPE_PARAMS_AND_BOUNDS, rcvrType)
                .linkToSelf(false);
        target.add(writer.getLink(info));
        target.add(Entity.NO_BREAK_SPACE);
        if (member.getKind() == ElementKind.CONSTRUCTOR) {
            target.add(utils.getTypeName(rcvrType, false));
            target.add(".");
        }
        target.add("this");
    }

    /**
     * Returns {@code true} if a receiver type is annotated anywhere in its type for
     * inclusion in member details.
     *
     * @param receiverType the receiver type.
     * @return {@code true} if the receiver is annotated
     */
    protected boolean isAnnotatedReceiver(TypeMirror receiverType) {
        return new SimpleTypeVisitor14<Boolean, Void>() {
            @Override
            protected Boolean defaultAction(TypeMirror e, Void unused) {
                return utils.isAnnotated(e);
            }

            @Override
            public Boolean visitDeclared(DeclaredType t, Void unused) {
                if (super.visitDeclared(t, unused) || visit(t.getEnclosingType())) {
                    return true;
                }

                for (var e : t.getTypeArguments()) {
                    if (visit(e)) {
                        return true;
                    }
                }

                return false;
            }
        }.visit(receiverType);
    }

    /**
     * Add all the parameters for the executable member.
     *
     * @param member the member to write parameters for.
     * @param target the content to which the parameters information will be added.
     */
    protected void addParameters(ExecutableElement member, Content target) {
        Content params = getParameters(member, false);
        if (params.charCount() > 2) {
            // only add <wbr> for non-empty parameters
            target.add(new HtmlTree(HtmlTag.WBR));
        }
        target.add(params);
    }

    /**
     * Add all the parameters for the executable member.
     *
     * @param member the member to write parameters for.
     * @param includeAnnotations true if annotation information needs to be added.
     * @return the parameter information
     */
    protected Content getParameters(ExecutableElement member, boolean includeAnnotations) {
        Content result = new ContentBuilder();
        result.add("(");
        String sep = "";
        List<? extends VariableElement> parameters = member.getParameters();
        TypeMirror rcvrType = member.getReceiverType();
        if (includeAnnotations && rcvrType != null && isAnnotatedReceiver(rcvrType)) {
            addReceiver(member, rcvrType, result);
            sep = "," + Text.NL + " ";
        }
        int paramstart;
        ExecutableType instMeth = utils.asInstantiatedMethodType(typeElement, member);
        for (paramstart = 0; paramstart < parameters.size(); paramstart++) {
            result.add(sep);
            VariableElement param = parameters.get(paramstart);
            TypeMirror paramType = instMeth.getParameterTypes().get(paramstart);

            if (param.getKind() != ElementKind.INSTANCE_INIT) {
                if (includeAnnotations) {
                    Content annotationInfo = writer.getAnnotationInfo(param, false);
                    if (!annotationInfo.isEmpty()) {
                        result.add(annotationInfo)
                                .add(Text.NL)
                                .add(" ");
                    }
                }
                addParam(param, paramType,
                    (paramstart == parameters.size() - 1) && member.isVarArgs(), result);
                break;
            }
        }

        for (int i = paramstart + 1; i < parameters.size(); i++) {
            result.add(",");
            result.add(Text.NL);
            result.add(" ");

            if (includeAnnotations) {
                Content annotationInfo = writer.getAnnotationInfo(parameters.get(i), false);
                if (!annotationInfo.isEmpty()) {
                    result.add(annotationInfo)
                            .add(Text.NL)
                            .add(" ");
                }
            }
            addParam(parameters.get(i), instMeth.getParameterTypes().get(i),
                    (i == parameters.size() - 1) && member.isVarArgs(),
                    result);
        }
        result.add(")");
        return result;
    }

    /**
     * Get the exception information for the executable member.
     *
     * @param member the member to get the exception information for
     * @return the exception information
     */
    protected Content getExceptions(ExecutableElement member) {
        List<? extends TypeMirror> exceptions = utils.asInstantiatedMethodType(typeElement, member).getThrownTypes();
        Content result = new ContentBuilder();
        for (TypeMirror t : exceptions) {
            if (!result.isEmpty()) {
                result.add(",");
                result.add(Text.NL);
            }
            Content link = writer.getLink(new HtmlLinkInfo(configuration, PLAIN, t));
            result.add(link);
        }
        return result;
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
}
