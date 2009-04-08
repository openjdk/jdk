/*
 * Copyright 1997-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;

/**
 * Writes method documentation in HTML format.
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Jamie Ho (rewrite)
 * @author Bhavesh Patel (Modified)
 */
public class MethodWriterImpl extends AbstractExecutableMemberWriter
        implements MethodWriter, MemberSummaryWriter {

    private boolean printedSummaryHeader = false;

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
     * Write the methods summary header for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeMemberSummaryHeader(ClassDoc classDoc) {
        printedSummaryHeader = true;
        writer.println();
        writer.println("<!-- ========== METHOD SUMMARY =========== -->");
        writer.println();
        writer.printSummaryHeader(this, classDoc);
    }

    /**
     * Write the methods summary footer for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeMemberSummaryFooter(ClassDoc classDoc) {
        writer.printSummaryFooter(this, classDoc);
    }

    /**
     * Write the inherited methods summary header for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeInheritedMemberSummaryHeader(ClassDoc classDoc) {
        if(! printedSummaryHeader){
            //We don't want inherited summary to not be under heading.
            writeMemberSummaryHeader(classDoc);
            writeMemberSummaryFooter(classDoc);
            printedSummaryHeader = true;
        }
        writer.printInheritedSummaryHeader(this, classDoc);
    }

    /**
     * {@inheritDoc}
     */
    public void writeInheritedMemberSummary(ClassDoc classDoc,
        ProgramElementDoc method, boolean isFirst, boolean isLast) {
        writer.printInheritedSummaryMember(this, classDoc, method, isFirst);
    }

    /**
     * Write the inherited methods summary footer for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeInheritedMemberSummaryFooter(ClassDoc classDoc) {
        writer.printInheritedSummaryFooter(this, classDoc);        ;
    }

    /**
     * Write the header for the method documentation.
     *
     * @param classDoc the class that the methods belong to.
     */
    public void writeHeader(ClassDoc classDoc, String header) {
        writer.println();
        writer.println("<!-- ============ METHOD DETAIL ========== -->");
        writer.println();
        writer.anchor("method_detail");
        writer.printTableHeadingBackground(header);
    }

    /**
     * Write the method header for the given method.
     *
     * @param method the method being documented.
     * @param isFirst the flag to indicate whether or not the method is the
     *        first to be documented.
     */
    public void writeMethodHeader(MethodDoc method, boolean isFirst) {
        if (! isFirst) {
            writer.printMemberHeader();
        }
        writer.println();
        String erasureAnchor;
        if ((erasureAnchor = getErasureAnchor(method)) != null) {
            writer.anchor(erasureAnchor);
        }
        writer.anchor(method);
        writer.h3();
        writer.print(method.name());
        writer.h3End();
    }

    /**
     * Write the signature for the given method.
     *
     * @param method the method being documented.
     */
    public void writeSignature(MethodDoc method) {
        writer.displayLength = 0;
        writer.pre();
        writer.writeAnnotationInfo(method);
        printModifiers(method);
        writeTypeParameters(method);
        printReturnType(method);
        if (configuration().linksource) {
            writer.printSrcLink(method, method.name());
        } else {
            strong(method.name());
        }
        writeParameters(method);
        writeExceptions(method);
        writer.preEnd();
        assert !writer.getMemberDetailsListPrinted();
    }

    /**
     * Write the deprecated output for the given method.
     *
     * @param method the method being documented.
     */
    public void writeDeprecated(MethodDoc method) {
        printDeprecated(method);
    }

    /**
     * Write the comments for the given method.
     *
     * @param method the method being documented.
     */
    public void writeComments(Type holder, MethodDoc method) {
        ClassDoc holderClassDoc = holder.asClassDoc();
        if (method.inlineTags().length > 0) {
            writer.printMemberDetailsListStartTag();
            if (holder.asClassDoc().equals(classdoc) ||
                (! (holderClassDoc.isPublic() ||
                    Util.isLinkable(holderClassDoc, configuration())))) {
                writer.dd();
                writer.printInlineComment(method);
                writer.ddEnd();
            } else {
                String classlink = writer.codeText(
                    writer.getDocLink(LinkInfoImpl.CONTEXT_METHOD_DOC_COPY,
                        holder.asClassDoc(), method,
                        holder.asClassDoc().isIncluded() ?
                            holder.typeName() : holder.qualifiedTypeName(),
                        false));
                writer.dd();
                writer.strongText(holder.asClassDoc().isClass()?
                        "doclet.Description_From_Class":
                        "doclet.Description_From_Interface",
                    classlink);
                writer.ddEnd();
                writer.dd();
                writer.printInlineComment(method);
                writer.ddEnd();
            }
        }
    }

    /**
     * Write the tag output for the given method.
     *
     * @param method the method being documented.
     */
    public void writeTags(MethodDoc method) {
        writer.printTags(method);
    }

    /**
     * Write the method footer.
     */
    public void writeMethodFooter() {
        printMemberFooter();
    }

    /**
     * Write the footer for the method documentation.
     *
     * @param classDoc the class that the methods belong to.
     */
    public void writeFooter(ClassDoc classDoc) {
        //No footer to write for method documentation
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

    public void printSummaryLabel() {
        writer.printText("doclet.Method_Summary");
    }

    public void printTableSummary() {
        writer.tableIndexSummary(configuration().getText("doclet.Member_Table_Summary",
                configuration().getText("doclet.Method_Summary"),
                configuration().getText("doclet.methods")));
    }

    public void printSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[] {
            writer.getModifierTypeHeader(),
            configuration().getText("doclet.0_and_1",
                    configuration().getText("doclet.Method"),
                    configuration().getText("doclet.Description"))
        };
        writer.summaryTableHeader(header, "col");
    }

    public void printSummaryAnchor(ClassDoc cd) {
        writer.anchor("method_summary");
    }

    public void printInheritedSummaryAnchor(ClassDoc cd) {
        writer.anchor("methods_inherited_from_class_" +
            ConfigurationImpl.getInstance().getClassName(cd));
    }

    public void printInheritedSummaryLabel(ClassDoc cd) {
        String classlink = writer.getPreQualifiedClassLink(
            LinkInfoImpl.CONTEXT_MEMBER, cd, false);
        writer.strong();
        String key = cd.isClass()?
            "doclet.Methods_Inherited_From_Class" :
            "doclet.Methods_Inherited_From_Interface";
        writer.printText(key, classlink);
        writer.strongEnd();
    }

    protected void printSummaryType(ProgramElementDoc member) {
        MethodDoc meth = (MethodDoc)member;
        printModifierAndType(meth, meth.returnType());
    }

    protected static void printOverridden(HtmlDocletWriter writer,
            Type overriddenType, MethodDoc method) {
        if(writer.configuration.nocomment){
            return;
        }
        ClassDoc holderClassDoc = overriddenType.asClassDoc();
        if (! (holderClassDoc.isPublic() ||
            Util.isLinkable(holderClassDoc, writer.configuration()))) {
            //This is an implementation detail that should not be documented.
            return;
        }
        if (overriddenType.asClassDoc().isIncluded() && ! method.isIncluded()) {
            //The class is included but the method is not.  That means that it
            //is not visible so don't document this.
            return;
        }
        String label = "doclet.Overrides";
        int context = LinkInfoImpl.CONTEXT_METHOD_OVERRIDES;

        if (method != null) {
            if(overriddenType.asClassDoc().isAbstract() && method.isAbstract()){
                //Abstract method is implemented from abstract class,
                //not overridden
                label = "doclet.Specified_By";
                context = LinkInfoImpl.CONTEXT_METHOD_SPECIFIED_BY;
            }
            String overriddenTypeLink = writer.codeText(
                writer.getLink(new LinkInfoImpl(context, overriddenType)));
            String name = method.name();
            writer.dt();
            writer.strongText(label);
            writer.dtEnd();
            writer.dd();
            String methLink = writer.codeText(
                writer.getLink(
                    new LinkInfoImpl(LinkInfoImpl.CONTEXT_MEMBER,
                        overriddenType.asClassDoc(),
                        writer.getAnchor(method), name, false)
                ));
            writer.printText("doclet.in_class", methLink, overriddenTypeLink);
            writer.ddEnd();
        }
    }

    /**
     * Parse the &lt;Code&gt; tag and return the text.
     */
    protected String parseCodeTag(String tag){
        if(tag == null){
            return "";
        }

        String lc = tag.toLowerCase();
        int begin = lc.indexOf("<code>");
        int end = lc.indexOf("</code>");
        if(begin == -1 || end == -1 || end <= begin){
            return tag;
        } else {
            return tag.substring(begin + 6, end);
        }
    }

    protected static void printImplementsInfo(HtmlDocletWriter writer,
            MethodDoc method) {
        if(writer.configuration.nocomment){
            return;
        }
        ImplementedMethods implementedMethodsFinder =
            new ImplementedMethods(method, writer.configuration);
        MethodDoc[] implementedMethods = implementedMethodsFinder.build();
        for (int i = 0; i < implementedMethods.length; i++) {
            MethodDoc implementedMeth = implementedMethods[i];
            Type intfac = implementedMethodsFinder.getMethodHolder(implementedMeth);
            String methlink = "";
            String intfaclink = writer.codeText(
                writer.getLink(new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_METHOD_SPECIFIED_BY, intfac)));
            writer.dt();
            writer.strongText("doclet.Specified_By");
            writer.dtEnd();
            writer.dd();
            methlink = writer.codeText(writer.getDocLink(
                LinkInfoImpl.CONTEXT_MEMBER, implementedMeth,
                implementedMeth.name(), false));
            writer.printText("doclet.in_interface", methlink, intfaclink);
            writer.ddEnd();
        }

    }

    protected void printReturnType(MethodDoc method) {
        Type type = method.returnType();
        if (type != null) {
            writer.printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_RETURN_TYPE,
                type));
            print(' ');
        }
    }

    protected void printNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            writer.printHyperLink("", (cd == null)?
                "method_summary":
                "methods_inherited_from_class_" +
                ConfigurationImpl.getInstance().getClassName(cd),
                ConfigurationImpl.getInstance().getText("doclet.navMethod"));
        } else {
            writer.printText("doclet.navMethod");
        }
    }

    protected void printNavDetailLink(boolean link) {
        if (link) {
            writer.printHyperLink("", "method_detail",
                ConfigurationImpl.getInstance().getText("doclet.navMethod"));
        } else {
            writer.printText("doclet.navMethod");
        }
    }
}
