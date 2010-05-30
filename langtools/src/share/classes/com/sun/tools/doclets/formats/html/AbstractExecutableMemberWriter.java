/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Print method and constructor info.
 *
 * @author Robert Field
 * @author Atul M Dambalkar
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
     * Write the type parameters for the executable member.
     *
     * @param member the member to write type parameters for.
     * @return the display length required to write this information.
     */
    protected int writeTypeParameters(ExecutableMemberDoc member) {
        LinkInfoImpl linkInfo = new LinkInfoImpl(
            LinkInfoImpl.CONTEXT_MEMBER_TYPE_PARAMS, member, false);
        String typeParameters = writer.getTypeParameterLinks(linkInfo);
        if (linkInfo.displayLength > 0) {
            writer.print(typeParameters + " ");
            writer.displayLength += linkInfo.displayLength + 1;
        }
        return linkInfo.displayLength;
    }

    protected void writeSignature(ExecutableMemberDoc member) {
        writer.displayLength = 0;
        writer.pre();
        writer.writeAnnotationInfo(member);
        printModifiers(member);
        writeTypeParameters(member);
        if (configuration().linksource &&
            member.position().line() != classdoc.position().line()) {
            writer.printSrcLink(member, member.name());
        } else {
            strong(member.name());
        }
        writeParameters(member);
        writeExceptions(member);
        writer.preEnd();
    }

    protected void writeDeprecatedLink(ProgramElementDoc member) {
        ExecutableMemberDoc emd = (ExecutableMemberDoc)member;
        writer.printDocLink(LinkInfoImpl.CONTEXT_MEMBER, (MemberDoc) emd,
            emd.qualifiedName() + emd.flatSignature(), false);
    }

    protected void writeSummaryLink(int context, ClassDoc cd, ProgramElementDoc member) {
        ExecutableMemberDoc emd = (ExecutableMemberDoc)member;
        String name = emd.name();
        writer.strong();
        writer.printDocLink(context, cd, (MemberDoc) emd,
            name, false);
        writer.strongEnd();
        writer.displayLength = name.length();
        writeParameters(emd, false);
    }

    protected void writeInheritedSummaryLink(ClassDoc cd,
            ProgramElementDoc member) {
        writer.printDocLink(LinkInfoImpl.CONTEXT_MEMBER, cd, (MemberDoc) member,
            member.name(), false);
    }

    protected void writeParam(ExecutableMemberDoc member, Parameter param,
        boolean isVarArg) {
        if (param.type() != null) {
            writer.printLink(new LinkInfoImpl(
                LinkInfoImpl.CONTEXT_EXECUTABLE_MEMBER_PARAM, param.type(),
                isVarArg));
        }
        if(param.name().length() > 0) {
            writer.space();
            writer.print(param.name());
        }
    }

    protected void writeParameters(ExecutableMemberDoc member) {
        writeParameters(member, true);
    }

    protected void writeParameters(ExecutableMemberDoc member,
            boolean includeAnnotations) {
        print('(');
        Parameter[] params = member.parameters();
        String indent = makeSpace(writer.displayLength);
        if (configuration().linksource) {
            //add spaces to offset indentation changes caused by link.
            indent+= makeSpace(member.name().length());
        }
        int paramstart;
        for (paramstart = 0; paramstart < params.length; paramstart++) {
            Parameter param = params[paramstart];
            if (!param.name().startsWith("this$")) {
                if (includeAnnotations) {
                        boolean foundAnnotations =
                                writer.writeAnnotationInfo(indent.length(), member, param);
                        if (foundAnnotations) {
                                writer.println();
                                writer.print(indent);
                    }
                }
                writeParam(member, param,
                    (paramstart == params.length - 1) && member.isVarArgs());
                break;
            }
        }

        for (int i = paramstart + 1; i < params.length; i++) {
            writer.print(',');
            writer.println();
            writer.print(indent);
            if (includeAnnotations) {
                boolean foundAnnotations =
                    writer.writeAnnotationInfo(indent.length(), member, params[i]);
                if (foundAnnotations) {
                    writer.println();
                    writer.print(indent);
                }
            }
            writeParam(member, params[i], (i == params.length - 1) && member.isVarArgs());
        }
        writer.print(')');
    }

    protected void writeExceptions(ExecutableMemberDoc member) {
        Type[] exceptions = member.thrownExceptionTypes();
        if(exceptions.length > 0) {
            LinkInfoImpl memberTypeParam = new LinkInfoImpl(
                LinkInfoImpl.CONTEXT_MEMBER, member, false);
            int retlen = getReturnTypeLength(member);
            writer.getTypeParameterLinks(memberTypeParam);
            retlen += memberTypeParam.displayLength == 0 ?
                0 : memberTypeParam.displayLength + 1;
            String indent = makeSpace(modifierString(member).length() +
                member.name().length() + retlen - 4);
            writer.println();
            writer.print(indent);
            writer.print("throws ");
            indent += "       ";
            writer.printLink(new LinkInfoImpl(
                LinkInfoImpl.CONTEXT_MEMBER, exceptions[0]));
            for(int i = 1; i < exceptions.length; i++) {
                writer.println(",");
                writer.print(indent);
                writer.printLink(new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_MEMBER, exceptions[i]));
            }
        }
    }

    protected int getReturnTypeLength(ExecutableMemberDoc member) {
        if (member instanceof MethodDoc) {
            MethodDoc method = (MethodDoc)member;
            Type rettype = method.returnType();
            if (rettype.isPrimitive()) {
                return rettype.typeName().length() +
                       rettype.dimension().length();
            } else {
                LinkInfoImpl linkInfo = new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_MEMBER, rettype);
                writer.getLink(linkInfo);
                return linkInfo.displayLength;
            }
        } else {   // it's a constructordoc
            return -1;
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
        StringBuffer buf = new StringBuffer(emd.name() + "(");
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
