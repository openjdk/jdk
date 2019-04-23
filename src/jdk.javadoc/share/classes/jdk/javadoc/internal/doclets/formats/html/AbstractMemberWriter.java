/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;

import java.util.*;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.MemberSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.taglets.DeprecatedTaglet;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

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
public abstract class AbstractMemberWriter implements MemberSummaryWriter {

    protected final HtmlConfiguration configuration;
    protected final Utils utils;
    protected final SubWriterHolderWriter writer;
    protected final Contents contents;
    protected final Resources resources;
    protected final Links links;

    protected final TypeElement typeElement;
    public final boolean nodepr;

    protected boolean printedSummaryHeader = false;

    public AbstractMemberWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        this.configuration = writer.configuration;
        this.writer = writer;
        this.nodepr = configuration.nodeprecated;
        this.typeElement = typeElement;
        this.utils = configuration.utils;
        this.contents = configuration.contents;
        this.resources = configuration.resources;
        this.links = writer.links;
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
    private String getTableSummaryX() { return null; }

    /**
     * Get the summary table header for the member.
     *
     * @param member the member to be documented
     * @return the summary table header
     */
    public abstract TableHeader getSummaryTableHeader(Element member);

    private Table summaryTable;

    private Table getSummaryTable() {
        if (summaryTable == null) {
            summaryTable = createSummaryTable();
        }
        return summaryTable;
    }

    /**
     * Create the summary table for this element.
     * The table should be created and initialized if needed, and configured
     * so that it is ready to add content with {@link Table#addRow(Content[])}
     * and similar methods.
     *
     * @return the summary table
     */
    protected abstract Table createSummaryTable();



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
     * Add the member name to the content tree.
     *
     * @param name the member name to be added to the content tree.
     * @param htmltree the content tree to which the name will be added.
     */
    protected void addName(String name, Content htmltree) {
        htmltree.add(name);
    }

    /**
     * Add the modifier for the member. The modifiers are ordered as specified
     * by <em>The Java Language Specification</em>.
     *
     * @param member the member for which the modifier will be added.
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
            String mods = set.stream().map(Modifier::toString).collect(Collectors.joining(" "));
            htmltree.add(mods);
            htmltree.add(Contents.SPACE);
        }
    }

    protected CharSequence makeSpace(int len) {
        if (len <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(' ');
        }
        return sb;
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
            code.add(utils.isClass(member) ? "class" : "interface");
            code.add(Contents.SPACE);
        } else {
            List<? extends TypeParameterElement> list = utils.isExecutableElement(member)
                    ? ((ExecutableElement)member).getTypeParameters()
                    : null;
            if (list != null && !list.isEmpty()) {
                Content typeParameters = ((AbstractExecutableMemberWriter) this)
                        .getTypeParameters((ExecutableElement)member);
                    code.add(typeParameters);
                //Code to avoid ugly wrapping in member summary table.
                if (typeParameters.charCount() > 10) {
                    code.add(new HtmlTree(HtmlTag.BR));
                } else {
                    code.add(Contents.SPACE);
                }
                code.add(
                        writer.getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.SUMMARY_RETURN_TYPE, type)));
            } else {
                code.add(
                        writer.getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.SUMMARY_RETURN_TYPE, type)));
            }

        }
        tdSummaryType.add(code);
    }

    /**
     * Add the modifier for the member.
     *
     * @param member the member to add the type for
     * @param code the content tree to which the modified will be added
     */
    private void addModifier(Element member, Content code) {
        if (utils.isProtected(member)) {
            code.add("protected ");
        } else if (utils.isPrivate(member)) {
            code.add("private ");
        } else if (!utils.isPublic(member)) { // Package private
            code.add(resources.getText("doclet.Package_private"));
            code.add(" ");
        }
        boolean isAnnotatedTypeElement = utils.isAnnotationType(member.getEnclosingElement());
        if (!isAnnotatedTypeElement && utils.isMethod(member)) {
            if (!utils.isInterface(member.getEnclosingElement()) && utils.isAbstract(member)) {
                code.add("abstract ");
            }
            if (utils.isDefault(member)) {
                code.add("default ");
            }
        }
        if (utils.isStatic(member)) {
            code.add("static ");
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
            Content div = HtmlTree.DIV(HtmlStyle.deprecationBlock, deprecatedContent);
            contentTree.add(div);
        }
    }

    /**
     * Add the comment for the given member.
     *
     * @param member the member being documented.
     * @param htmltree the content tree to which the comment will be added.
     */
    protected void addComment(Element member, Content htmltree) {
        if (!utils.getFullBody(member).isEmpty()) {
            writer.addInlineComment(member, htmltree);
        }
    }

    protected String name(Element member) {
        return utils.getSimpleName(member);
    }

    /**
    * Return true if the given <code>ProgramElement</code> is inherited
    * by the class that is being documented.
    *
    * @param ped The <code>ProgramElement</code> being checked.
    * return true if the <code>ProgramElement</code> is being inherited and
    * false otherwise.
     *@return true if inherited
    */
    protected boolean isInherited(Element ped){
        return (!utils.isPrivate(ped) &&
                (!utils.isPackagePrivate(ped) ||
                    ped.getEnclosingElement().equals(ped.getEnclosingElement())));
    }

    /**
     * Add use information to the documentation tree.
     *
     * @param mems list of program elements for which the use information will be added
     * @param heading the section heading
     * @param contentTree the content tree to which the use information will be added
     */
    protected void addUseInfo(List<? extends Element> mems, Content heading, Content contentTree) {
        if (mems == null || mems.isEmpty()) {
            return;
        }
        List<? extends Element> members = mems;
        boolean printedUseTableHeader = false;
        if (members.size() > 0) {
            Table useTable = new Table(HtmlStyle.useSummary)
                    .setCaption(heading)
                    .setRowScopeColumn(1)
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast);
            for (Element element : members) {
                TypeElement te = (typeElement == null)
                        ? utils.getEnclosingTypeElement(element)
                        : typeElement;
                if (!printedUseTableHeader) {
                    useTable.setHeader(getSummaryTableHeader(element));
                    printedUseTableHeader = true;
                }
                Content summaryType = new ContentBuilder();
                addSummaryType(element, summaryType);
                Content typeContent = new ContentBuilder();
                if (te != null
                        && !utils.isConstructor(element)
                        && !utils.isClass(element)
                        && !utils.isInterface(element)
                        && !utils.isAnnotationType(element)) {
                    HtmlTree name = new HtmlTree(HtmlTag.SPAN);
                    name.setStyle(HtmlStyle.typeNameLabel);
                    name.add(name(te) + ".");
                    typeContent.add(name);
                }
                addSummaryLink(utils.isClass(element) || utils.isInterface(element)
                        ? LinkInfoImpl.Kind.CLASS_USE
                        : LinkInfoImpl.Kind.MEMBER,
                        te, element, typeContent);
                Content desc = new ContentBuilder();
                writer.addSummaryLinkComment(this, element, desc);
                useTable.addRow(summaryType, typeContent, desc);
            }
            contentTree.add(useTable.toContent());
        }
    }

    protected void serialWarning(Element e, String key, String a1, String a2) {
        if (configuration.serialwarn) {
            configuration.messages.warning(e, key, a1, a2);
        }
    }

    /**
     * Add the member summary for the given class.
     *
     * @param tElement the class that is being documented
     * @param member the member being documented
     * @param firstSentenceTags the first sentence tags to be added to the summary
     */
    @Override
    public void addMemberSummary(TypeElement tElement, Element member,
            List<? extends DocTree> firstSentenceTags) {
        if (tElement != typeElement) {
            throw new IllegalStateException();
        }
        Table table = getSummaryTable();
        List<Content> rowContents = new ArrayList<>();
        Content summaryType = new ContentBuilder();
        addSummaryType(member, summaryType);
        if (!summaryType.isEmpty())
            rowContents.add(summaryType);
        Content summaryLink = new ContentBuilder();
        addSummaryLink(tElement, member, summaryLink);
        rowContents.add(summaryLink);
        Content desc = new ContentBuilder();
        writer.addSummaryLinkComment(this, member, firstSentenceTags, desc);
        rowContents.add(desc);
        table.addRow(member, rowContents);
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
    @Override
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
    @Override
    public Content getInheritedSummaryHeader(TypeElement tElement) {
        Content inheritedTree = writer.getMemberInheritedTree();
        writer.addInheritedSummaryHeader(this, tElement, inheritedTree);
        return inheritedTree;
    }

    /**
     * Get the inherited summary links tree.
     *
     * @return a content tree for the inherited summary links
     */
    @Override
    public Content getInheritedSummaryLinksTree() {
        return new HtmlTree(HtmlTag.CODE);
    }

    /**
     * Get the summary table tree for the given class.
     *
     * @param tElement the class for which the summary table is generated
     * @return a content tree for the summary table
     */
    @Override
    public Content getSummaryTableTree(TypeElement tElement) {
        if (tElement != typeElement) {
            throw new IllegalStateException();
        }
        Table table = getSummaryTable();
        if (table.needsScript()) {
            writer.getMainBodyScript().append(table.getScript());
        }
        return table.toContent();
    }

    /**
     * Get the member tree to be documented.
     *
     * @param memberTree the content tree of member to be documented
     * @return a content tree that will be added to the class documentation
     */
    @Override
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
            return HtmlTree.LI(HtmlStyle.blockListLast, memberTree);
        else
            return HtmlTree.LI(HtmlStyle.blockList, memberTree);
    }
}
