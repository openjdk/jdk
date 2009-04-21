/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;

/**
 * Generate serialized form for Serializable/Externalizable methods.
 * Documentation denoted by the <code>serialData</code> tag is processed.
 *
 * @author Joe Fialli
 */
public class HtmlSerialMethodWriter extends MethodWriterImpl implements
        SerializedFormWriter.SerialMethodWriter{

    private boolean printedFirstMember = false;

    public HtmlSerialMethodWriter(SubWriterHolderWriter writer,
            ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public void writeHeader(String heading) {
        writer.anchor("serialized_methods");
        writer.printTableHeadingBackground(heading);
        writer.p();
    }

    public void writeNoCustomizationMsg(String msg) {
        writer.print(msg);
        writer.p();
    }

    public void writeMemberHeader(MethodDoc member) {
        if (printedFirstMember) {
            writer.printMemberHeader();
        }
        printedFirstMember = true;
        writer.anchor(member);
        printHead(member);
        writeSignature(member);
    }

    public void writeMemberFooter() {
        printMemberFooter();
    }

    public void writeDeprecatedMemberInfo(MethodDoc member) {
        printDeprecated(member);
    }

    public void writeMemberDescription(MethodDoc member) {
        printComment(member);
    }

    public void writeMemberTags(MethodDoc member) {
        TagletOutputImpl output = new TagletOutputImpl("");
        TagletManager tagletManager =
            ConfigurationImpl.getInstance().tagletManager;
        TagletWriter.genTagOuput(tagletManager, member,
            tagletManager.getSerializedFormTags(),
            writer.getTagletWriterInstance(false), output);
        String outputString = output.toString().trim();
        if (!outputString.isEmpty()) {
            writer.printMemberDetailsListStartTag();
            writer.dd();
            writer.dl();
            print(outputString);
            writer.dlEnd();
            writer.ddEnd();
        }
        MethodDoc method = member;
        if (method.name().compareTo("writeExternal") == 0
                && method.tags("serialData").length == 0) {
            serialWarning(member.position(), "doclet.MissingSerialDataTag",
                method.containingClass().qualifiedName(), method.name());
        }
    }

    protected void printTypeLinkNoDimension(Type type) {
        ClassDoc cd = type.asClassDoc();
        if (type.isPrimitive() || cd.isPackagePrivate()) {
            print(type.typeName());
        } else {
            writer.printLink(new LinkInfoImpl(
                LinkInfoImpl.CONTEXT_SERIAL_MEMBER,type));
        }
    }
}
