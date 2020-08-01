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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.MemberSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.MemberWriter;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.taglets.DeprecatedTaglet;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.NATIVE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STRICTFP;
import static javax.lang.model.element.Modifier.SYNCHRONIZED;

/**
 * The base class for member writers.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class AbstractMemberWriter implements MemberSummaryWriter, MemberWriter {

    protected final HtmlConfiguration configuration;
    protected final HtmlOptions options;
    protected final Utils utils;
    protected final SubWriterHolderWriter writer;
    protected final Contents contents;
    protected final Resources resources;
    protected final Links links;

    protected final TypeElement typeElement;

    public AbstractMemberWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        this.configuration = writer.configuration;
        this.options = configuration.getOptions();
        this.writer = writer;
        this.typeElement = typeElement;
        this.utils = configuration.utils;
        this.contents = configuration.contents;
        this.resources = configuration.docResources;
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
     * Add the modifier and type for the member in the member summary.
     *
     * @param member the member to add the type for
     * @param type the type to add
     * @param tdSummaryType the content tree to which the modified and type will be added
     */
    protected void addModifierAndType(Element member, TypeMirror type,
            Content tdSummaryType) {
        HtmlTree code = new HtmlTree(TagName.CODE);
        addModifier(member, code);
        if (type == null) {
            code.add(utils.isClass(member) ? "class" : "interface");
            code.add(Entity.NO_BREAK_SPACE);
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
                    code.add(new HtmlTree(TagName.BR));
                } else {
                    code.add(Entity.NO_BREAK_SPACE);
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
            Table useTable = new Table(HtmlStyle.useSummary, HtmlStyle.summaryTable)
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
                    HtmlTree name = new HtmlTree(TagName.SPAN);
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
            contentTree.add(useTable);
        }
    }

    protected void serialWarning(Element e, String key, String a1, String a2) {
        if (options.serialWarn()) {
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
        return new HtmlTree(TagName.CODE);
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
        return table;
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

    @Override
    public Content getMemberList() {
        return writer.getMemberList();
    }

    @Override
    public Content getMemberListItem(Content memberTree) {
        return writer.getMemberListItem(memberTree);
    }

    /**
     * A content builder for member signatures.
     */
    class MemberSignature {

        private Element element;
        private Content typeParameters;
        private Content returnType;
        private Content parameters;
        private Content exceptions;

        // Threshold for length of type parameters before switching from inline to block representation.
        private static final int TYPE_PARAMS_MAX_INLINE_LENGTH = 50;

        // Threshold for combined length of modifiers, type params and return type before breaking
        // it up with a line break before the return type.
        private static final int RETURN_TYPE_MAX_LINE_LENGTH = 50;

        /**
         * Create a new member signature builder.
         *
         * @param element The element for which to create a signature.
         */
        MemberSignature(Element element) {
            this.element = element;
        }

        /**
         * Add the type parameters for an executable member.
         *
         * @param typeParameters the content tree containing the type parameters to add.
         * @return this MemberSignature instance
         */
        MemberSignature addTypeParameters(Content typeParameters) {
            this.typeParameters = typeParameters;
            return this;
        }

        /**
         * Add the return type for an executable member.
         *
         * @param returnType the content tree containing the return type to add.
         * @return this MemberSignature instance
         */
        MemberSignature addReturnType(Content returnType) {
            this.returnType = returnType;
            return this;
        }

        /**
         * Add the type information for a non-executable member.
         *
         * @param type the type of the member.
         * @return this MemberSignature instance
         */
        MemberSignature addType(TypeMirror type) {
            this.returnType = writer.getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER, type));
            return this;
        }

        /**
         * Add the parameter information of an executable member.
         *
         * @param paramTree the content tree containing the parameter information.
         * @return this MemberSignature instance
         */
        MemberSignature addParameters(Content paramTree) {
            this.parameters = paramTree;
            return this;
        }

        /**
         * Add the exception information of an executable member.
         *
         * @param exceptionTree the content tree containing the exception information
         * @return this MemberSignature instance
         */
        MemberSignature addExceptions(Content exceptionTree) {
            this.exceptions = exceptionTree;
            return this;
        }

        /**
         * Return a HTML tree containing the member signature.
         *
         * @return a HTML tree containing the member signature
         */
        Content toContent() {
            Content content = new ContentBuilder();
            // Position of last line separator.
            int lastLineSeparator = 0;

            // Annotations
            Content annotationInfo = writer.getAnnotationInfo(element.getAnnotationMirrors(), true);
            if (!annotationInfo.isEmpty()) {
                content.add(HtmlTree.SPAN(HtmlStyle.annotations, annotationInfo));
                lastLineSeparator = content.charCount();
            }

            // Modifiers
            appendModifiers(content);

            // Type parameters
            if (typeParameters != null && !typeParameters.isEmpty()) {
                lastLineSeparator = appendTypeParameters(content, lastLineSeparator);
            }

            // Return type
            if (returnType != null) {
                content.add(HtmlTree.SPAN(HtmlStyle.returnType, returnType));
                content.add(Entity.NO_BREAK_SPACE);
            }

            // Name
            HtmlTree nameSpan = new HtmlTree(TagName.SPAN);
            nameSpan.setStyle(HtmlStyle.memberName);
            if (options.linkSource()) {
                Content name = new StringContent(name(element));
                writer.addSrcLink(element, name, nameSpan);
            } else {
                nameSpan.add(name(element));
            }
            content.add(nameSpan);


            // Parameters and exceptions
            if (parameters != null) {
                appendParametersAndExceptions(content, lastLineSeparator);
            }

            return HtmlTree.DIV(HtmlStyle.memberSignature, content);
        }

        /**
         * Add the modifier for the member. The modifiers are ordered as specified
         * by <em>The Java Language Specification</em>.
         *
         * @param htmltree the content tree to which the modifier information will be added.
         */
        private void appendModifiers(Content htmltree) {
            Set<Modifier> set = new TreeSet<>(element.getModifiers());

            // remove the ones we really don't need
            set.remove(NATIVE);
            set.remove(SYNCHRONIZED);
            set.remove(STRICTFP);

            // According to JLS, we should not be showing public modifier for
            // interface methods and fields.
            if ((utils.isField(element) || utils.isMethod(element))) {
               Element te = element.getEnclosingElement();
               if (utils.isInterface(te) || utils.isAnnotationType(te)) {
                   // Remove the implicit abstract and public modifiers
                   if (utils.isMethod(element)) {
                       set.remove(ABSTRACT);
                   }
                   set.remove(PUBLIC);
               }
            }
            if (!set.isEmpty()) {
                String mods = set.stream().map(Modifier::toString).collect(Collectors.joining(" "));
                htmltree.add(HtmlTree.SPAN(HtmlStyle.modifiers, new StringContent(mods)));
                htmltree.add(Entity.NO_BREAK_SPACE);
            }
        }

        /**
         * Append the type parameter information to the HTML tree.
         *
         * @param htmltree the HTML tree
         * @param lastLineSeparator index of last line separator in HTML tree
         * @return the new index of the last line separator
         */
        private int appendTypeParameters(Content htmltree, int lastLineSeparator) {
            // Apply different wrapping strategies for type parameters
            // depending of combined length of type parameters and return type.
            int typeParamLength = typeParameters.charCount();

            if (typeParamLength >= TYPE_PARAMS_MAX_INLINE_LENGTH) {
                htmltree.add(HtmlTree.SPAN(HtmlStyle.typeParametersLong, typeParameters));
            } else {
                htmltree.add(HtmlTree.SPAN(HtmlStyle.typeParameters, typeParameters));
            }

            int lineLength = htmltree.charCount() - lastLineSeparator;
            int newLastLineSeparator = lastLineSeparator;

            // sum below includes length of modifiers plus type params added above
            if (lineLength + returnType.charCount()> RETURN_TYPE_MAX_LINE_LENGTH) {
                htmltree.add(DocletConstants.NL);
                newLastLineSeparator = htmltree.charCount();
            } else {
                htmltree.add(Entity.NO_BREAK_SPACE);
            }

            return newLastLineSeparator;
        }

        /**
         * Append the parameters and exceptions information to the HTML tree.
         *
         * @param htmltree the HTML tree
         * @param lastLineSeparator the index of the last line separator in HTML tree
         */
        private void appendParametersAndExceptions(Content htmltree, int lastLineSeparator) {
            // Record current position for indentation of exceptions
            int indentSize = htmltree.charCount() - lastLineSeparator;

            if (parameters.charCount() == 2) {
                // empty parameters are added without packing
                htmltree.add(parameters);
            } else {
                htmltree.add(Entity.ZERO_WIDTH_SPACE);
                htmltree.add(HtmlTree.SPAN(HtmlStyle.parameters, parameters));
            }

            // Exceptions
            if (exceptions != null && !exceptions.isEmpty()) {
                CharSequence indent = " ".repeat(Math.max(0, indentSize + 1 - 7));
                htmltree.add(DocletConstants.NL);
                htmltree.add(indent);
                htmltree.add("throws ");
                htmltree.add(HtmlTree.SPAN(HtmlStyle.exceptions, exceptions));
            }
        }
    }
}
