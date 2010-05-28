/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;

/**
 * The base class for member writers.
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Jamie Ho (Re-write)
 * @author Bhavesh Patel (Modified)
 */
public abstract class AbstractMemberWriter {

    protected boolean printedSummaryHeader = false;
    protected final SubWriterHolderWriter writer;
    protected final ClassDoc classdoc;
    public final boolean nodepr;

    public AbstractMemberWriter(SubWriterHolderWriter writer,
                             ClassDoc classdoc) {
        this.writer = writer;
        this.nodepr = configuration().nodeprecated;
        this.classdoc = classdoc;
    }

    public AbstractMemberWriter(SubWriterHolderWriter writer) {
        this(writer, null);
    }

    /*** abstracts ***/

    public abstract void printSummaryLabel();

    public abstract void printTableSummary();

    public abstract void printSummaryTableHeader(ProgramElementDoc member);

    public abstract void printInheritedSummaryLabel(ClassDoc cd);

    public abstract void printSummaryAnchor(ClassDoc cd);

    public abstract void printInheritedSummaryAnchor(ClassDoc cd);

    protected abstract void printSummaryType(ProgramElementDoc member);

    protected void writeSummaryLink(ClassDoc cd, ProgramElementDoc member) {
        writeSummaryLink(LinkInfoImpl.CONTEXT_MEMBER, cd, member);
    }

    protected abstract void writeSummaryLink(int context,
                                             ClassDoc cd,
                                             ProgramElementDoc member);

    protected abstract void writeInheritedSummaryLink(ClassDoc cd,
                                                     ProgramElementDoc member);

    protected abstract void writeDeprecatedLink(ProgramElementDoc member);

    protected abstract void printNavSummaryLink(ClassDoc cd, boolean link);

    protected abstract void printNavDetailLink(boolean link);

    /***  ***/

    protected void print(String str) {
        writer.print(str);
        writer.displayLength += str.length();
    }

    protected void print(char ch) {
        writer.print(ch);
        writer.displayLength++;
    }

    protected void strong(String str) {
        writer.strong(str);
        writer.displayLength += str.length();
    }

    /**
     * Return a string describing the access modifier flags.
     * Don't include native or synchronized.
     *
     * The modifier names are returned in canonical order, as
     * specified by <em>The Java Language Specification</em>.
     */
    protected String modifierString(MemberDoc member) {
        int ms = member.modifierSpecifier();
        int no = Modifier.NATIVE | Modifier.SYNCHRONIZED;
    return Modifier.toString(ms & ~no);
    }

    protected String typeString(MemberDoc member) {
        String type = "";
        if (member instanceof MethodDoc) {
            type = ((MethodDoc)member).returnType().toString();
        } else if (member instanceof FieldDoc) {
            type = ((FieldDoc)member).type().toString();
        }
        return type;
    }

    protected void printModifiers(MemberDoc member) {
        String mod = modifierString(member);
        // According to JLS, we should not be showing public modifier for
        // interface methods.
        if ((member.isField() || member.isMethod()) &&
            writer instanceof ClassWriterImpl &&
             ((ClassWriterImpl) writer).getClassDoc().isInterface()) {
            mod = Util.replaceText(mod, "public", "").trim();
        }
        if(mod.length() > 0) {
            print(mod);
            print(' ');
        }
    }

    protected String makeSpace(int len) {
        if (len <= 0) {
            return "";
        }
        StringBuffer sb = new StringBuffer(len);
        for(int i = 0; i < len; i++) {
            sb.append(' ');
    }
        return sb.toString();
    }

    /**
     * Print 'static' if static and type link.
     */
    protected void printStaticAndType(boolean isStatic, Type type) {
        writer.printTypeSummaryHeader();
        if (isStatic) {
            print("static");
        }
        writer.space();
        if (type != null) {
            writer.printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_MEMBER,
                type));
        }
        writer.printTypeSummaryFooter();
    }

    /**
     * Print the modifier and type for the member in the member summary.
     *
     * @param member the member to print the type for.
     * @param type   the type to print.
     */
    protected void printModifierAndType(ProgramElementDoc member, Type type) {
        writer.printTypeSummaryHeader();
        printModifier(member);
        if (type == null) {
            writer.space();
            if (member.isClass()) {
                print("class");
            } else {
                print("interface");
            }
        } else {
            if (member instanceof ExecutableMemberDoc &&
                    ((ExecutableMemberDoc) member).typeParameters().length > 0) {
                //Code to avoid ugly wrapping in member summary table.
                writer.table(0,0,0);
                writer.trAlignVAlign("right", "");
                writer.tdNowrap();
                writer.font("-1");
                writer.code();
                int displayLength = ((AbstractExecutableMemberWriter) this).
                writeTypeParameters((ExecutableMemberDoc) member);
                if (displayLength > 10) {
                    writer.br();
                }
                writer.printLink(new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_SUMMARY_RETURN_TYPE, type));
                writer.codeEnd();
                writer.fontEnd();
                writer.tdEnd();
                writer.trEnd();
                writer.tableEnd();
            } else {
                writer.space();
                writer.printLink(new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_SUMMARY_RETURN_TYPE, type));
            }

        }
        writer.printTypeSummaryFooter();
    }

    private void printModifier(ProgramElementDoc member) {
        if (member.isProtected()) {
            print("protected ");
        } else if (member.isPrivate()) {
            print("private ");
        } else if (!member.isPublic()) { // Package private
            writer.printText("doclet.Package_private");
            print(" ");
        }
        if (member.isMethod() && ((MethodDoc)member).isAbstract()) {
            print("abstract ");
        }
        if (member.isStatic()) {
            print("static");
        }
    }

    /**
     * Print the deprecated output for the given member.
     *
     * @param member the member being documented.
     */
    protected void printDeprecated(ProgramElementDoc member) {
        String output = (new DeprecatedTaglet()).getTagletOutput(member,
            writer.getTagletWriterInstance(false)).toString().trim();
        if (!output.isEmpty()) {
            writer.printMemberDetailsListStartTag();
            writer.print(output);
        }
    }

    protected void printComment(ProgramElementDoc member) {
        if (member.inlineTags().length > 0) {
            writer.printMemberDetailsListStartTag();
            writer.dd();
            writer.printInlineComment(member);
            writer.ddEnd();
        }
    }

    protected String name(ProgramElementDoc member) {
        return member.name();
    }

    protected void printHead(MemberDoc member) {
        writer.h3();
        writer.print(member.name());
        writer.h3End();
    }

    protected void printFullComment(ProgramElementDoc member) {
        if(configuration().nocomment){
            return;
        }
        writer.dl();
        print(((TagletOutputImpl)
            (new DeprecatedTaglet()).getTagletOutput(member,
            writer.getTagletWriterInstance(false))).toString());
        printCommentAndTags(member);
        writer.dlEnd();
    }

    protected void printCommentAndTags(ProgramElementDoc member) {
        printComment(member);
        writer.printTags(member);
    }

    /**
     * Write the member footer.
     */
    protected void printMemberFooter() {
        writer.printMemberDetailsListEndTag();
        assert !writer.getMemberDetailsListPrinted();
    }

    /**
     * Forward to containing writer
     */
    public void printSummaryHeader(ClassDoc cd) {
        printedSummaryHeader = true;
        writer.printSummaryHeader(this, cd);
    }

    /**
     * Forward to containing writer
     */
    public void printInheritedSummaryHeader(ClassDoc cd) {
        writer.printInheritedSummaryHeader(this, cd);
    }

    /**
     * Forward to containing writer
     */
    public void printInheritedSummaryFooter(ClassDoc cd) {
        writer.printInheritedSummaryFooter(this, cd);
    }

    /**
     * Forward to containing writer
     */
    public void printSummaryFooter(ClassDoc cd) {
        writer.printSummaryFooter(this, cd);
    }

   /**
    * Return true if the given <code>ProgramElement</code> is inherited
    * by the class that is being documented.
    *
    * @param ped The <code>ProgramElement</code> being checked.
    * return true if the <code>ProgramElement</code> is being inherited and
    * false otherwise.
    */
    protected boolean isInherited(ProgramElementDoc ped){
        if(ped.isPrivate() || (ped.isPackagePrivate() &&
            ! ped.containingPackage().equals(classdoc.containingPackage()))){
            return false;
        }
        return true;
    }


    /**
     * Generate the code for listing the deprecated APIs. Create the table
     * format for listing the API. Call methods from the sub-class to complete
     * the generation.
     */
    protected void printDeprecatedAPI(List<Doc> deprmembers, String headingKey, String tableSummary, String[] tableHeader) {
        if (deprmembers.size() > 0) {
            writer.tableIndexSummary(tableSummary);
            writer.tableCaptionStart();
            writer.printText(headingKey);
            writer.tableCaptionEnd();
            writer.summaryTableHeader(tableHeader, "col");
            for (int i = 0; i < deprmembers.size(); i++) {
                ProgramElementDoc member =(ProgramElementDoc)deprmembers.get(i);
                writer.trBgcolorStyle("white", "TableRowColor");
                writer.summaryRow(0);
                writeDeprecatedLink(member);
                writer.br();
                writer.printNbsps();
                if (member.tags("deprecated").length > 0)
                    writer.printInlineDeprecatedComment(member, member.tags("deprecated")[0]);
                writer.space();
                writer.summaryRowEnd();
                writer.trEnd();
            }
            writer.tableEnd();
            writer.space();
            writer.p();
        }
    }

    /**
     * Print use info.
     */
    protected void printUseInfo(List<? extends ProgramElementDoc> mems, String heading, String tableSummary) {
        if (mems == null) {
            return;
        }
        List<? extends ProgramElementDoc> members = mems;
        boolean printedUseTableHeader = false;
        if (members.size() > 0) {
            writer.tableIndexSummary(tableSummary);
            writer.tableSubCaptionStart();
            writer.print(heading);
            writer.tableCaptionEnd();
            for (Iterator<? extends ProgramElementDoc> it = members.iterator(); it.hasNext(); ) {
                ProgramElementDoc pgmdoc = it.next();
                ClassDoc cd = pgmdoc.containingClass();
                if (!printedUseTableHeader) {
                    // Passing ProgramElementDoc helps decides printing
                    // interface or class header in case of nested classes.
                    this.printSummaryTableHeader(pgmdoc);
                    printedUseTableHeader = true;
                }

                writer.printSummaryLinkType(this, pgmdoc);
                if (cd != null && !(pgmdoc instanceof ConstructorDoc)
                               && !(pgmdoc instanceof ClassDoc)) {
                    // Add class context
                    writer.strong(cd.name() + ".");
                }
                writeSummaryLink(
                    pgmdoc instanceof ClassDoc ?
                        LinkInfoImpl.CONTEXT_CLASS_USE : LinkInfoImpl.CONTEXT_MEMBER,
                    cd, pgmdoc);
                writer.printSummaryLinkComment(this, pgmdoc);
            }
            writer.tableEnd();
            writer.space();
            writer.p();
        }
    }

    protected void navDetailLink(List<?> members) {
            printNavDetailLink(members.size() > 0? true: false);
    }


    protected void navSummaryLink(List<?> members,
            VisibleMemberMap visibleMemberMap) {
        if (members.size() > 0) {
            printNavSummaryLink(null, true);
            return;
        } else {
            ClassDoc icd = classdoc.superclass();
            while (icd != null) {
                List<?> inhmembers = visibleMemberMap.getMembersFor(icd);
                if (inhmembers.size() > 0) {
                    printNavSummaryLink(icd, true);
                    return;
                }
                icd = icd.superclass();
            }
        }
        printNavSummaryLink(null, false);
    }

    protected void serialWarning(SourcePosition pos, String key, String a1, String a2) {
        if (configuration().serialwarn) {
            ConfigurationImpl.getInstance().getDocletSpecificMsg().warning(pos, key, a1, a2);
        }
    }

    public ProgramElementDoc[] eligibleMembers(ProgramElementDoc[] members) {
        return nodepr? Util.excludeDeprecatedMembers(members): members;
    }

    public ConfigurationImpl configuration() {
        return writer.configuration;
    }

    /**
     * {@inheritDoc}
     */
    public void writeMemberSummary(ClassDoc classDoc, ProgramElementDoc member,
        Tag[] firstSentenceTags, boolean isFirst, boolean isLast) {
        writer.printSummaryLinkType(this, member);
        writeSummaryLink(classDoc, member);
        writer.printSummaryLinkComment(this, member, firstSentenceTags);
    }
}
