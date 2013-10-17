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

import java.lang.reflect.Modifier;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * The base class for member writers.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Jamie Ho (Re-write)
 * @author Bhavesh Patel (Modified)
 */
public abstract class AbstractMemberWriter {

    protected final ConfigurationImpl configuration;
    protected final SubWriterHolderWriter writer;
    protected final ClassDoc classdoc;
    protected Map<String,Integer> typeMap = new LinkedHashMap<String,Integer>();
    protected Set<MethodTypes> methodTypes = EnumSet.noneOf(MethodTypes.class);
    private int methodTypesOr = 0;
    public final boolean nodepr;

    protected boolean printedSummaryHeader = false;

    public AbstractMemberWriter(SubWriterHolderWriter writer, ClassDoc classdoc) {
        this.configuration = writer.configuration;
        this.writer = writer;
        this.nodepr = configuration.nodeprecated;
        this.classdoc = classdoc;
    }

    public AbstractMemberWriter(SubWriterHolderWriter writer) {
        this(writer, null);
    }

    /*** abstracts ***/

    /**
     * Add the summary label for the member.
     *
     * @param memberTree the content tree to which the label will be added
     */
    public abstract void addSummaryLabel(Content memberTree);

    /**
     * Get the summary for the member summary table.
     *
     * @return a string for the table summary
     */
    public abstract String getTableSummary();

    /**
     * Get the caption for the member summary table.
     *
     * @return a string for the table caption
     */
    public abstract Content getCaption();

    /**
     * Get the summary table header for the member.
     *
     * @param member the member to be documented
     * @return the summary table header
     */
    public abstract String[] getSummaryTableHeader(ProgramElementDoc member);

    /**
     * Add inherited summary lable for the member.
     *
     * @param cd the class doc to which to link to
     * @param inheritedTree the content tree to which the inherited summary label will be added
     */
    public abstract void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree);

    /**
     * Add the anchor for the summary section of the member.
     *
     * @param cd the class doc to be documented
     * @param memberTree the content tree to which the summary anchor will be added
     */
    public abstract void addSummaryAnchor(ClassDoc cd, Content memberTree);

    /**
     * Add the anchor for the inherited summary section of the member.
     *
     * @param cd the class doc to be documented
     * @param inheritedTree the content tree to which the inherited summary anchor will be added
     */
    public abstract void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree);

    /**
     * Add the summary type for the member.
     *
     * @param member the member to be documented
     * @param tdSummaryType the content tree to which the type will be added
     */
    protected abstract void addSummaryType(ProgramElementDoc member,
            Content tdSummaryType);

    /**
     * Add the summary link for the member.
     *
     * @param cd the class doc to be documented
     * @param member the member to be documented
     * @param tdSummary the content tree to which the link will be added
     */
    protected void addSummaryLink(ClassDoc cd, ProgramElementDoc member,
            Content tdSummary) {
        addSummaryLink(LinkInfoImpl.Kind.MEMBER, cd, member, tdSummary);
    }

    /**
     * Add the summary link for the member.
     *
     * @param context the id of the context where the link will be printed
     * @param cd the class doc to be documented
     * @param member the member to be documented
     * @param tdSummary the content tree to which the summary link will be added
     */
    protected abstract void addSummaryLink(LinkInfoImpl.Kind context,
            ClassDoc cd, ProgramElementDoc member, Content tdSummary);

    /**
     * Add the inherited summary link for the member.
     *
     * @param cd the class doc to be documented
     * @param member the member to be documented
     * @param linksTree the content tree to which the inherited summary link will be added
     */
    protected abstract void addInheritedSummaryLink(ClassDoc cd,
            ProgramElementDoc member, Content linksTree);

    /**
     * Get the deprecated link.
     *
     * @param member the member being linked to
     * @return a content tree representing the link
     */
    protected abstract Content getDeprecatedLink(ProgramElementDoc member);

    /**
     * Get the navigation summary link.
     *
     * @param cd the class doc to be documented
     * @param link true if its a link else the label to be printed
     * @return a content tree for the navigation summary link.
     */
    protected abstract Content getNavSummaryLink(ClassDoc cd, boolean link);

    /**
     * Add the navigation detail link.
     *
     * @param link true if its a link else the label to be printed
     * @param liNav the content tree to which the navigation detail link will be added
     */
    protected abstract void addNavDetailLink(boolean link, Content liNav);

    /**
     * Add the member name to the content tree.
     *
     * @param name the member name to be added to the content tree.
     * @param htmltree the content tree to which the name will be added.
     */
    protected void addName(String name, Content htmltree) {
        htmltree.addContent(name);
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

    /**
     * Add the modifier for the member.
     *
     * @param member the member for which teh modifier will be added.
     * @param htmltree the content tree to which the modifier information will be added.
     */
    protected void addModifiers(MemberDoc member, Content htmltree) {
        String mod = modifierString(member);
        // According to JLS, we should not be showing public modifier for
        // interface methods.
        if ((member.isField() || member.isMethod()) &&
            writer instanceof ClassWriterImpl &&
            ((ClassWriterImpl) writer).getClassDoc().isInterface()) {
            // This check for isDefault() and the default modifier needs to be
            // added for it to appear on the method details section. Once the
            // default modifier is added to the Modifier list on DocEnv and once
            // it is updated to use the javax.lang.model.element.Modifier, we
            // will need to remove this.
            mod = (member.isMethod() && ((MethodDoc)member).isDefault()) ?
                    Util.replaceText(mod, "public", "default").trim() :
                    Util.replaceText(mod, "public", "").trim();
        }
        if(mod.length() > 0) {
            htmltree.addContent(mod);
            htmltree.addContent(writer.getSpace());
        }
    }

    protected String makeSpace(int len) {
        if (len <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(' ');
    }
        return sb.toString();
    }

    /**
     * Add the modifier and type for the member in the member summary.
     *
     * @param member the member to add the type for
     * @param type the type to add
     * @param tdSummaryType the content tree to which the modified and type will be added
     */
    protected void addModifierAndType(ProgramElementDoc member, Type type,
            Content tdSummaryType) {
        HtmlTree code = new HtmlTree(HtmlTag.CODE);
        addModifier(member, code);
        if (type == null) {
            if (member.isClass()) {
                code.addContent("class");
            } else {
                code.addContent("interface");
            }
            code.addContent(writer.getSpace());
        } else {
            if (member instanceof ExecutableMemberDoc &&
                    ((ExecutableMemberDoc) member).typeParameters().length > 0) {
                Content typeParameters = ((AbstractExecutableMemberWriter) this).getTypeParameters(
                        (ExecutableMemberDoc) member);
                    code.addContent(typeParameters);
                //Code to avoid ugly wrapping in member summary table.
                if (typeParameters.charCount() > 10) {
                    code.addContent(new HtmlTree(HtmlTag.BR));
                } else {
                    code.addContent(writer.getSpace());
                }
                code.addContent(
                        writer.getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.SUMMARY_RETURN_TYPE, type)));
            } else {
                code.addContent(
                        writer.getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.SUMMARY_RETURN_TYPE, type)));
            }

        }
        tdSummaryType.addContent(code);
    }

    /**
     * Add the modifier for the member.
     *
     * @param member the member to add the type for
     * @param code the content tree to which the modified will be added
     */
    private void addModifier(ProgramElementDoc member, Content code) {
        if (member.isProtected()) {
            code.addContent("protected ");
        } else if (member.isPrivate()) {
            code.addContent("private ");
        } else if (!member.isPublic()) { // Package private
            code.addContent(configuration.getText("doclet.Package_private"));
            code.addContent(" ");
        }
        if (member.isMethod()) {
            if (!(member.containingClass().isInterface()) &&
                    ((MethodDoc)member).isAbstract()) {
                code.addContent("abstract ");
            }
            // This check for isDefault() and the default modifier needs to be
            // added for it to appear on the "Modifier and Type" column in the
            // method summary section. Once the default modifier is added
            // to the Modifier list on DocEnv and once it is updated to use the
            // javax.lang.model.element.Modifier, we will need to remove this.
            if (((MethodDoc)member).isDefault()) {
                code.addContent("default ");
            }
        }
        if (member.isStatic()) {
            code.addContent("static ");
        }
    }

    /**
     * Add the deprecated information for the given member.
     *
     * @param member the member being documented.
     * @param contentTree the content tree to which the deprecated information will be added.
     */
    protected void addDeprecatedInfo(ProgramElementDoc member, Content contentTree) {
        Content output = (new DeprecatedTaglet()).getTagletOutput(member,
            writer.getTagletWriterInstance(false));
        if (!output.isEmpty()) {
            Content deprecatedContent = output;
            Content div = HtmlTree.DIV(HtmlStyle.block, deprecatedContent);
            contentTree.addContent(div);
        }
    }

    /**
     * Add the comment for the given member.
     *
     * @param member the member being documented.
     * @param htmltree the content tree to which the comment will be added.
     */
    protected void addComment(ProgramElementDoc member, Content htmltree) {
        if (member.inlineTags().length > 0) {
            writer.addInlineComment(member, htmltree);
        }
    }

    protected String name(ProgramElementDoc member) {
        return member.name();
    }

    /**
     * Get the header for the section.
     *
     * @param member the member being documented.
     * @return a header content for the section.
     */
    protected Content getHead(MemberDoc member) {
        Content memberContent = new StringContent(member.name());
        Content heading = HtmlTree.HEADING(HtmlConstants.MEMBER_HEADING, memberContent);
        return heading;
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
     * Add deprecated information to the documentation tree
     *
     * @param deprmembers list of deprecated members
     * @param headingKey the caption for the deprecated members table
     * @param tableSummary the summary for the deprecated members table
     * @param tableHeader table headers for the deprecated members table
     * @param contentTree the content tree to which the deprecated members table will be added
     */
    protected void addDeprecatedAPI(List<Doc> deprmembers, String headingKey,
            String tableSummary, String[] tableHeader, Content contentTree) {
        if (deprmembers.size() > 0) {
            Content table = HtmlTree.TABLE(HtmlStyle.deprecatedSummary, 0, 3, 0, tableSummary,
                writer.getTableCaption(configuration.getResource(headingKey)));
            table.addContent(writer.getSummaryTableHeader(tableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            for (int i = 0; i < deprmembers.size(); i++) {
                ProgramElementDoc member =(ProgramElementDoc)deprmembers.get(i);
                HtmlTree td = HtmlTree.TD(HtmlStyle.colOne, getDeprecatedLink(member));
                if (member.tags("deprecated").length > 0)
                    writer.addInlineDeprecatedComment(member,
                            member.tags("deprecated")[0], td);
                HtmlTree tr = HtmlTree.TR(td);
                if (i%2 == 0)
                    tr.addStyle(HtmlStyle.altColor);
                else
                    tr.addStyle(HtmlStyle.rowColor);
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            Content li = HtmlTree.LI(HtmlStyle.blockList, table);
            Content ul = HtmlTree.UL(HtmlStyle.blockList, li);
            contentTree.addContent(ul);
        }
    }

    /**
     * Add use information to the documentation tree.
     *
     * @param mems list of program elements for which the use information will be added
     * @param heading the section heading
     * @param tableSummary the summary for the use table
     * @param contentTree the content tree to which the use information will be added
     */
    protected void addUseInfo(List<? extends ProgramElementDoc> mems,
            Content heading, String tableSummary, Content contentTree) {
        if (mems == null) {
            return;
        }
        List<? extends ProgramElementDoc> members = mems;
        boolean printedUseTableHeader = false;
        if (members.size() > 0) {
            Content table = HtmlTree.TABLE(HtmlStyle.useSummary, 0, 3, 0, tableSummary,
                    writer.getTableCaption(heading));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            Iterator<? extends ProgramElementDoc> it = members.iterator();
            for (int i = 0; it.hasNext(); i++) {
                ProgramElementDoc pgmdoc = it.next();
                ClassDoc cd = pgmdoc.containingClass();
                if (!printedUseTableHeader) {
                    table.addContent(writer.getSummaryTableHeader(
                            this.getSummaryTableHeader(pgmdoc), "col"));
                    printedUseTableHeader = true;
                }
                HtmlTree tr = new HtmlTree(HtmlTag.TR);
                if (i % 2 == 0) {
                    tr.addStyle(HtmlStyle.altColor);
                } else {
                    tr.addStyle(HtmlStyle.rowColor);
                }
                HtmlTree tdFirst = new HtmlTree(HtmlTag.TD);
                tdFirst.addStyle(HtmlStyle.colFirst);
                writer.addSummaryType(this, pgmdoc, tdFirst);
                tr.addContent(tdFirst);
                HtmlTree tdLast = new HtmlTree(HtmlTag.TD);
                tdLast.addStyle(HtmlStyle.colLast);
                if (cd != null && !(pgmdoc instanceof ConstructorDoc)
                        && !(pgmdoc instanceof ClassDoc)) {
                    HtmlTree name = new HtmlTree(HtmlTag.SPAN);
                    name.addStyle(HtmlStyle.strong);
                    name.addContent(cd.name() + ".");
                    tdLast.addContent(name);
                }
                addSummaryLink(pgmdoc instanceof ClassDoc ?
                    LinkInfoImpl.Kind.CLASS_USE : LinkInfoImpl.Kind.MEMBER,
                    cd, pgmdoc, tdLast);
                writer.addSummaryLinkComment(this, pgmdoc, tdLast);
                tr.addContent(tdLast);
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            contentTree.addContent(table);
        }
    }

    /**
     * Add the navigation detail link.
     *
     * @param members the members to be linked
     * @param liNav the content tree to which the navigation detail link will be added
     */
    protected void addNavDetailLink(List<?> members, Content liNav) {
        addNavDetailLink(members.size() > 0 ? true : false, liNav);
    }

    /**
     * Add the navigation summary link.
     *
     * @param members members to be linked
     * @param visibleMemberMap the visible inherited members map
     * @param liNav the content tree to which the navigation summary link will be added
     */
    protected void addNavSummaryLink(List<?> members,
            VisibleMemberMap visibleMemberMap, Content liNav) {
        if (members.size() > 0) {
            liNav.addContent(getNavSummaryLink(null, true));
            return;
        }
        ClassDoc icd = classdoc.superclass();
        while (icd != null) {
            List<?> inhmembers = visibleMemberMap.getMembersFor(icd);
            if (inhmembers.size() > 0) {
                liNav.addContent(getNavSummaryLink(icd, true));
                return;
            }
            icd = icd.superclass();
        }
        liNav.addContent(getNavSummaryLink(null, false));
    }

    protected void serialWarning(SourcePosition pos, String key, String a1, String a2) {
        if (configuration.serialwarn) {
            configuration.getDocletSpecificMsg().warning(pos, key, a1, a2);
        }
    }

    public ProgramElementDoc[] eligibleMembers(ProgramElementDoc[] members) {
        return nodepr? Util.excludeDeprecatedMembers(members): members;
    }

    /**
     * Add the member summary for the given class.
     *
     * @param classDoc the class that is being documented
     * @param member the member being documented
     * @param firstSentenceTags the first sentence tags to be added to the summary
     * @param tableContents the list of contents to which the documentation will be added
     * @param counter the counter for determining id and style for the table row
     */
    public void addMemberSummary(ClassDoc classDoc, ProgramElementDoc member,
            Tag[] firstSentenceTags, List<Content> tableContents, int counter) {
        HtmlTree tdSummaryType = new HtmlTree(HtmlTag.TD);
        tdSummaryType.addStyle(HtmlStyle.colFirst);
        writer.addSummaryType(this, member, tdSummaryType);
        HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
        setSummaryColumnStyle(tdSummary);
        addSummaryLink(classDoc, member, tdSummary);
        writer.addSummaryLinkComment(this, member, firstSentenceTags, tdSummary);
        HtmlTree tr = HtmlTree.TR(tdSummaryType);
        tr.addContent(tdSummary);
        if (member instanceof MethodDoc && !member.isAnnotationTypeElement()) {
            int methodType = (member.isStatic()) ? MethodTypes.STATIC.value() :
                    MethodTypes.INSTANCE.value();
            if (member.containingClass().isInterface()) {
                methodType = (((MethodDoc) member).isAbstract())
                        ? methodType | MethodTypes.ABSTRACT.value()
                        : methodType | MethodTypes.DEFAULT.value();
            } else {
                methodType = (((MethodDoc) member).isAbstract())
                        ? methodType | MethodTypes.ABSTRACT.value()
                        : methodType | MethodTypes.CONCRETE.value();
            }
            if (Util.isDeprecated(member) || Util.isDeprecated(classdoc)) {
                methodType = methodType | MethodTypes.DEPRECATED.value();
            }
            methodTypesOr = methodTypesOr | methodType;
            String tableId = "i" + counter;
            typeMap.put(tableId, methodType);
            tr.addAttr(HtmlAttr.ID, tableId);
        }
        if (counter%2 == 0)
            tr.addStyle(HtmlStyle.altColor);
        else
            tr.addStyle(HtmlStyle.rowColor);
        tableContents.add(tr);
    }

    /**
     * Generate the method types set and return true if the method summary table
     * needs to show tabs.
     *
     * @return true if the table should show tabs
     */
    public boolean showTabs() {
        int value;
        for (MethodTypes type : EnumSet.allOf(MethodTypes.class)) {
            value = type.value();
            if ((value & methodTypesOr) == value) {
                methodTypes.add(type);
            }
        }
        boolean showTabs = methodTypes.size() > 1;
        if (showTabs) {
            methodTypes.add(MethodTypes.ALL);
        }
        return showTabs;
    }

    /**
     * Set the style for the summary column.
     *
     * @param tdTree the column for which the style will be set
     */
    public void setSummaryColumnStyle(HtmlTree tdTree) {
        tdTree.addStyle(HtmlStyle.colLast);
    }

    /**
     * Add inherited member summary for the given class and member.
     *
     * @param classDoc the class the inherited member belongs to
     * @param nestedClass the inherited member that is summarized
     * @param isFirst true if this is the first member in the list
     * @param isLast true if this is the last member in the list
     * @param linksTree the content tree to which the summary will be added
     */
    public void addInheritedMemberSummary(ClassDoc classDoc,
            ProgramElementDoc nestedClass, boolean isFirst, boolean isLast,
            Content linksTree) {
        writer.addInheritedMemberSummary(this, classDoc, nestedClass, isFirst,
                linksTree);
    }

    /**
     * Get the inherited summary header for the given class.
     *
     * @param classDoc the class the inherited member belongs to
     * @return a content tree for the inherited summary header
     */
    public Content getInheritedSummaryHeader(ClassDoc classDoc) {
        Content inheritedTree = writer.getMemberTreeHeader();
        writer.addInheritedSummaryHeader(this, classDoc, inheritedTree);
        return inheritedTree;
    }

    /**
     * Get the inherited summary links tree.
     *
     * @return a content tree for the inherited summary links
     */
    public Content getInheritedSummaryLinksTree() {
        return new HtmlTree(HtmlTag.CODE);
    }

    /**
     * Get the summary table tree for the given class.
     *
     * @param classDoc the class for which the summary table is generated
     * @param tableContents list of contents to be displayed in the summary table
     * @return a content tree for the summary table
     */
    public Content getSummaryTableTree(ClassDoc classDoc, List<Content> tableContents) {
        return writer.getSummaryTableTree(this, classDoc, tableContents, showTabs());
    }

    /**
     * Get the member tree to be documented.
     *
     * @param memberTree the content tree of member to be documented
     * @return a content tree that will be added to the class documentation
     */
    public Content getMemberTree(Content memberTree) {
        return writer.getMemberTree(memberTree);
    }

    /**
     * Get the member tree to be documented.
     *
     * @param memberTree the content tree of member to be documented
     * @param isLastContent true if the content to be added is the last content
     * @return a content tree that will be added to the class documentation
     */
    public Content getMemberTree(Content memberTree, boolean isLastContent) {
        if (isLastContent)
            return HtmlTree.UL(HtmlStyle.blockListLast, memberTree);
        else
            return HtmlTree.UL(HtmlStyle.blockList, memberTree);
    }
}
