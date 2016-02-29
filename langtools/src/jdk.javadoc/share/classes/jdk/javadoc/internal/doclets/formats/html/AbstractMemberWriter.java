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

import java.util.*;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor9;

import com.sun.source.doctree.DocTree;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.DeprecatedTaglet;
import jdk.javadoc.internal.doclets.toolkit.util.MethodTypes;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;

import static javax.lang.model.element.Modifier.*;

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
    protected final Utils utils;
    protected final SubWriterHolderWriter writer;
    protected final TypeElement typeElement;
    protected Map<String, Integer> typeMap = new LinkedHashMap<>();
    protected Set<MethodTypes> methodTypes = EnumSet.noneOf(MethodTypes.class);
    private int methodTypesOr = 0;
    public final boolean nodepr;

    protected boolean printedSummaryHeader = false;

    public AbstractMemberWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        this.configuration = writer.configuration;
        this.writer = writer;
        this.nodepr = configuration.nodeprecated;
        this.typeElement = typeElement;
        this.utils = writer.configuration.utils;
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
    public abstract List<String> getSummaryTableHeader(Element member);

    /**
     * Add inherited summary label for the member.
     *
     * @param typeElement the TypeElement to which to link to
     * @param inheritedTree the content tree to which the inherited summary label will be added
     */
    public abstract void addInheritedSummaryLabel(TypeElement typeElement, Content inheritedTree);

    /**
     * Add the anchor for the summary section of the member.
     *
     * @param typeElement the TypeElement to be documented
     * @param memberTree the content tree to which the summary anchor will be added
     */
    public abstract void addSummaryAnchor(TypeElement typeElement, Content memberTree);

    /**
     * Add the anchor for the inherited summary section of the member.
     *
     * @param typeElement the TypeElement to be documented
     * @param inheritedTree the content tree to which the inherited summary anchor will be added
     */
    public abstract void addInheritedSummaryAnchor(TypeElement typeElement, Content inheritedTree);

    /**
     * Add the summary type for the member.
     *
     * @param member the member to be documented
     * @param tdSummaryType the content tree to which the type will be added
     */
    protected abstract void addSummaryType(Element member, Content tdSummaryType);

    /**
     * Add the summary link for the member.
     *
     * @param typeElement the TypeElement to be documented
     * @param member the member to be documented
     * @param tdSummary the content tree to which the link will be added
     */
    protected void addSummaryLink(TypeElement typeElement, Element member, Content tdSummary) {
        addSummaryLink(LinkInfoImpl.Kind.MEMBER, typeElement, member, tdSummary);
    }

    /**
     * Add the summary link for the member.
     *
     * @param context the id of the context where the link will be printed
     * @param typeElement the TypeElement to be documented
     * @param member the member to be documented
     * @param tdSummary the content tree to which the summary link will be added
     */
    protected abstract void addSummaryLink(LinkInfoImpl.Kind context,
            TypeElement typeElement, Element member, Content tdSummary);

    /**
     * Add the inherited summary link for the member.
     *
     * @param typeElement the TypeElement to be documented
     * @param member the member to be documented
     * @param linksTree the content tree to which the inherited summary link will be added
     */
    protected abstract void addInheritedSummaryLink(TypeElement typeElement,
            Element member, Content linksTree);

    /**
     * Get the deprecated link.
     *
     * @param member the member being linked to
     * @return a content tree representing the link
     */
    protected abstract Content getDeprecatedLink(Element member);

    /**
     * Get the navigation summary link.
     *
     * @param typeElement the TypeElement to be documented
     * @param link true if its a link else the label to be printed
     * @return a content tree for the navigation summary link.
     */
    protected abstract Content getNavSummaryLink(TypeElement typeElement, boolean link);

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

    protected String typeString(Element member) {
        return new SimpleElementVisitor9<String, Void>() {

            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            public String visitExecutable(ExecutableElement e, Void p) {
               return utils.isMethod(e) ? e.getReturnType().toString() : "";
            }

            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            public String visitVariable(VariableElement e, Void p) {
                return e.toString();
            }

            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            protected String defaultAction(Element e, Void p) {
                return "";
            }
        }.visit(member);
    }

    /**
     * Add the modifier for the member. The modifiers are ordered as specified
     * by <em>The Java Language Specification</em>.
     *
     * @param member the member for which teh modifier will be added.
     * @param htmltree the content tree to which the modifier information will be added.
     */
    protected void addModifiers(Element member, Content htmltree) {
        Set<Modifier> set = new TreeSet<>(member.getModifiers());

        // remove the ones we really don't need
        set.remove(NATIVE);
        set.remove(SYNCHRONIZED);
        set.remove(STRICTFP);

        // According to JLS, we should not be showing public modifier for
        // interface methods.
        if ((utils.isField(member) || utils.isMethod(member))
            && ((writer instanceof ClassWriterImpl
                 && utils.isInterface(((ClassWriterImpl) writer).getTypeElement())  ||
                 writer instanceof AnnotationTypeWriterImpl) )) {
            // Remove the implicit abstract and public modifiers
            if (utils.isMethod(member) &&
                (utils.isInterface(member.getEnclosingElement()) ||
                 utils.isAnnotationType(member.getEnclosingElement()))) {
                set.remove(ABSTRACT);
                set.remove(PUBLIC);
            }
            if (!utils.isMethod(member)) {
                set.remove(PUBLIC);
            }
        }
        if (!set.isEmpty()) {
            String mods = set.stream().map(m -> m.toString()).collect(Collectors.joining(" "));
            htmltree.addContent(mods);
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
    protected void addModifierAndType(Element member, TypeMirror type,
            Content tdSummaryType) {
        HtmlTree code = new HtmlTree(HtmlTag.CODE);
        addModifier(member, code);
        if (type == null) {
            code.addContent(utils.isClass(member) ? "class" : "interface");
            code.addContent(writer.getSpace());
        } else {
            List<? extends TypeParameterElement> list = utils.isExecutableElement(member)
                    ? ((ExecutableElement)member).getTypeParameters()
                    : null;
            if (list != null && !list.isEmpty()) {
                Content typeParameters = ((AbstractExecutableMemberWriter) this)
                        .getTypeParameters((ExecutableElement)member);
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
    private void addModifier(Element member, Content code) {
        if (utils.isProtected(member)) {
            code.addContent("protected ");
        } else if (utils.isPrivate(member)) {
            code.addContent("private ");
        } else if (!utils.isPublic(member)) { // Package private
            code.addContent(configuration.getText("doclet.Package_private"));
            code.addContent(" ");
        }
        boolean isAnnotatedTypeElement = utils.isAnnotationType(member.getEnclosingElement());
        if (!isAnnotatedTypeElement && utils.isMethod(member)) {
            if (!utils.isInterface(member.getEnclosingElement()) && utils.isAbstract(member)) {
                code.addContent("abstract ");
            }
            if (utils.isDefault(member)) {
                code.addContent("default ");
            }
        }
        if (utils.isStatic(member)) {
            code.addContent("static ");
        }
    }

    /**
     * Add the deprecated information for the given member.
     *
     * @param member the member being documented.
     * @param contentTree the content tree to which the deprecated information will be added.
     */
    protected void addDeprecatedInfo(Element member, Content contentTree) {
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
    protected void addComment(Element member, Content htmltree) {
        if (!utils.getBody(member).isEmpty()) {
            writer.addInlineComment(member, htmltree);
        }
    }

    protected String name(Element member) {
        return utils.getSimpleName(member);
    }

    /**
     * Get the header for the section.
     *
     * @param member the member being documented.
     * @return a header content for the section.
     */
    protected Content getHead(Element member) {
        Content memberContent = new StringContent(name(member));
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
    protected boolean isInherited(Element ped){
        return (!utils.isPrivate(ped) &&
                (!utils.isPackagePrivate(ped) ||
                    ped.getEnclosingElement().equals(ped.getEnclosingElement())));
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
    protected void addDeprecatedAPI(Collection<Element> deprmembers, String headingKey,
            String tableSummary, List<String> tableHeader, Content contentTree) {
        if (deprmembers.size() > 0) {
            Content caption = writer.getTableCaption(configuration.getResource(headingKey));
            Content table = (configuration.isOutputHtml5())
                    ? HtmlTree.TABLE(HtmlStyle.deprecatedSummary, caption)
                    : HtmlTree.TABLE(HtmlStyle.deprecatedSummary, tableSummary, caption);
            table.addContent(writer.getSummaryTableHeader(tableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            boolean altColor = true;
            for (Element member : deprmembers) {
                HtmlTree td = HtmlTree.TD(HtmlStyle.colOne, getDeprecatedLink(member));
                List<? extends DocTree> deprTrees = utils.getBlockTags(member, DocTree.Kind.DEPRECATED);
                if (!deprTrees.isEmpty()) {
                    writer.addInlineDeprecatedComment(member, deprTrees.get(0), td);
                }
                HtmlTree tr = HtmlTree.TR(td);
                tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
                altColor = !altColor;
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
    protected void addUseInfo(List<? extends Element> mems,
            Content heading, String tableSummary, Content contentTree) {
        if (mems == null || mems.isEmpty()) {
            return;
        }
        List<? extends Element> members = mems;
        boolean printedUseTableHeader = false;
        if (members.size() > 0) {
            Content caption = writer.getTableCaption(heading);
            Content table = (configuration.isOutputHtml5())
                    ? HtmlTree.TABLE(HtmlStyle.useSummary, caption)
                    : HtmlTree.TABLE(HtmlStyle.useSummary, tableSummary, caption);
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            boolean altColor = true;
            for (Element element : members) {
                TypeElement te = utils.getEnclosingTypeElement(element);
                if (!printedUseTableHeader) {
                    table.addContent(writer.getSummaryTableHeader(
                            this.getSummaryTableHeader(element), "col"));
                    printedUseTableHeader = true;
                }
                HtmlTree tr = new HtmlTree(HtmlTag.TR);
                tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
                altColor = !altColor;
                HtmlTree tdFirst = new HtmlTree(HtmlTag.TD);
                tdFirst.addStyle(HtmlStyle.colFirst);
                writer.addSummaryType(this, element, tdFirst);
                tr.addContent(tdFirst);
                HtmlTree tdLast = new HtmlTree(HtmlTag.TD);
                tdLast.addStyle(HtmlStyle.colLast);
                if (te != null
                        && !utils.isConstructor(element)
                        && !utils.isClass(element)
                        && !utils.isInterface(element)
                        && !utils.isAnnotationType(element)) {
                    HtmlTree name = new HtmlTree(HtmlTag.SPAN);
                    name.addStyle(HtmlStyle.typeNameLabel);
                    name.addContent(name(te) + ".");
                    tdLast.addContent(name);
                }
                addSummaryLink(utils.isClass(element) || utils.isInterface(element)
                        ? LinkInfoImpl.Kind.CLASS_USE
                        : LinkInfoImpl.Kind.MEMBER,
                    te, element, tdLast);
                writer.addSummaryLinkComment(this, element, tdLast);
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
    protected void addNavDetailLink(SortedSet<Element> members, Content liNav) {
        addNavDetailLink(!members.isEmpty(), liNav);
    }

    /**
     * Add the navigation summary link.
     *
     * @param members members to be linked
     * @param visibleMemberMap the visible inherited members map
     * @param liNav the content tree to which the navigation summary link will be added
     */
    protected void addNavSummaryLink(SortedSet<? extends Element> members,
            VisibleMemberMap visibleMemberMap, Content liNav) {
        if (!members.isEmpty()) {
            liNav.addContent(getNavSummaryLink(null, true));
            return;
        }

        TypeElement superClass = utils.getSuperClass(typeElement);
        while (superClass != null) {
            if (visibleMemberMap.hasMembersFor(superClass)) {
                liNav.addContent(getNavSummaryLink(superClass, true));
                return;
            }
            superClass = utils.getSuperClass(superClass);
        }
        liNav.addContent(getNavSummaryLink(null, false));
    }

    protected void serialWarning(Element e, String key, String a1, String a2) {
        if (configuration.serialwarn) {
            configuration.getDocletSpecificMsg().warning(e, key, a1, a2);
        }
    }

    /**
     * Add the member summary for the given class.
     *
     * @param tElement the class that is being documented
     * @param member the member being documented
     * @param firstSentenceTags the first sentence tags to be added to the summary
     * @param tableContents the list of contents to which the documentation will be added
     * @param counter the counter for determining id and style for the table row
     */
    public void addMemberSummary(TypeElement tElement, Element member,
            List<? extends DocTree> firstSentenceTags, List<Content> tableContents, int counter) {
        HtmlTree tdSummaryType = new HtmlTree(HtmlTag.TD);
        tdSummaryType.addStyle(HtmlStyle.colFirst);
        writer.addSummaryType(this, member, tdSummaryType);
        HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
        setSummaryColumnStyle(tdSummary);
        addSummaryLink(tElement, member, tdSummary);
        writer.addSummaryLinkComment(this, member, firstSentenceTags, tdSummary);
        HtmlTree tr = HtmlTree.TR(tdSummaryType);
        tr.addContent(tdSummary);
        if (utils.isMethod(member) && !utils.isAnnotationType(member)) {
            int methodType = utils.isStatic(member) ? MethodTypes.STATIC.value() :
                    MethodTypes.INSTANCE.value();
            if (utils.isInterface(member.getEnclosingElement())) {
                methodType = utils.isAbstract(member)
                        ? methodType | MethodTypes.ABSTRACT.value()
                        : methodType | MethodTypes.DEFAULT.value();
            } else {
                methodType = utils.isAbstract(member)
                        ? methodType | MethodTypes.ABSTRACT.value()
                        : methodType | MethodTypes.CONCRETE.value();
            }
            if (utils.isDeprecated(member) || utils.isDeprecated(typeElement)) {
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
     * @param tElement the class the inherited member belongs to
     * @param nestedClass the inherited member that is summarized
     * @param isFirst true if this is the first member in the list
     * @param isLast true if this is the last member in the list
     * @param linksTree the content tree to which the summary will be added
     */
    public void addInheritedMemberSummary(TypeElement tElement,
            Element nestedClass, boolean isFirst, boolean isLast,
            Content linksTree) {
        writer.addInheritedMemberSummary(this, tElement, nestedClass, isFirst,
                linksTree);
    }

    /**
     * Get the inherited summary header for the given class.
     *
     * @param tElement the class the inherited member belongs to
     * @return a content tree for the inherited summary header
     */
    public Content getInheritedSummaryHeader(TypeElement tElement) {
        Content inheritedTree = writer.getMemberTreeHeader();
        writer.addInheritedSummaryHeader(this, tElement, inheritedTree);
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
     * @param tElement the class for which the summary table is generated
     * @param tableContents list of contents to be displayed in the summary table
     * @return a content tree for the summary table
     */
    public Content getSummaryTableTree(TypeElement tElement, List<Content> tableContents) {
        return writer.getSummaryTableTree(this, tElement, tableContents, showTabs());
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
