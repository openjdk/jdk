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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.Entity;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;

import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.ANNOTATION_TYPE_MEMBER;
import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.ANNOTATION_TYPE_MEMBER_OPTIONAL;
import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.ANNOTATION_TYPE_MEMBER_REQUIRED;
import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.CONSTRUCTORS;
import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.ENUM_CONSTANTS;
import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.FIELDS;
import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.METHODS;
import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.NESTED_CLASSES;
import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.PROPERTIES;

/**
 * The base class for member writers.
 *
 * Two primary methods are defined: {@link #buildSummary(Content)} and {@link #buildDetails(Content)}.
 */
public abstract class AbstractMemberWriter {

    protected final HtmlConfiguration configuration;
    protected final HtmlOptions options;
    protected final Utils utils;
    protected final SubWriterHolderWriter writer;
    protected final Contents contents;
    protected final Resources resources;
    protected final HtmlIds htmlIds;

    protected final TypeElement typeElement;
    protected final VisibleMemberTable.Kind kind;
    protected final VisibleMemberTable visibleMemberTable;

    protected final Comparator<Element> summariesComparator;

    /**
     * The list of {@linkplain VisibleMemberTable.Kind kinds} of summary table
     * that appear in the page for any {@linkplain TypeElement type element}.
     *
     * Note: this is not the default ordering of {@link VisibleMemberTable.Kind}.
     * For what it is worth, that ordering is relied on by {@link Navigation}.
     *
     * Compared to {@link #detailKinds}, this list includes nested classes and distinct
     * kinds for required and optional annotation type members
     *
     * @see VisibleMemberTable.Kind#forSummariesOf(ElementKind)
     */
    static final List<VisibleMemberTable.Kind> summaryKinds = List.of(
            NESTED_CLASSES,
            ENUM_CONSTANTS, PROPERTIES, FIELDS,
            CONSTRUCTORS,
            ANNOTATION_TYPE_MEMBER_REQUIRED, ANNOTATION_TYPE_MEMBER_OPTIONAL, METHODS
    );

    /**
     * The list of {@linkplain VisibleMemberTable.Kind kinds} of detail lists
     * that appear in the page for any {@linkplain TypeElement type element}.
     *
     * Note: this is not the default ordering of {@link VisibleMemberTable.Kind}.
     * For what it is worth, that ordering is relied on by {@link Navigation}.
     *
     * Compared to {@link #summaryKinds}, this list does not include nested classes and
     * just a single kind for all annotation type members, although nested classes could
     * be included by ensuring that {@link #buildDetails} is a no-op.
     *
     * @see VisibleMemberTable.Kind#forDetailsOf(ElementKind)
     */
    static final List<VisibleMemberTable.Kind> detailKinds = List.of(
            ENUM_CONSTANTS, PROPERTIES, FIELDS,
            CONSTRUCTORS,
            ANNOTATION_TYPE_MEMBER, METHODS
    );

    /**
     * Creates a member writer for a given enclosing writer and kind of member.
     *
     * @param writer the enclosing "page" writer.
     * @param kind the kind
     */
    protected AbstractMemberWriter(ClassWriter writer, VisibleMemberTable.Kind kind) {
        this(writer, writer.typeElement, kind);
    }

    /**
     * Creates a member writer for a given enclosing writer.
     * No type element or kind is provided, limiting the set of methods that can be used.
     *
     * @param writer the writer
     */
    protected AbstractMemberWriter(SubWriterHolderWriter writer) {
        this(writer, null, null);
    }

    /**
     * Creates a member writer for a given enclosing writer, and optional type element and member kind.
     * If no specific type element or kind is provided, methods that require such information
     * may throw {@link NullPointerException}.
     *
     * @param writer the writer
     */
    protected AbstractMemberWriter(SubWriterHolderWriter writer,
                                 TypeElement typeElement,
                                 VisibleMemberTable.Kind kind) {
        this.writer = writer;
        this.typeElement = typeElement;
        this.kind = kind;

        this.configuration = writer.configuration;
        this.options = configuration.getOptions();
        this.utils = configuration.utils;
        this.contents = configuration.getContents();
        this.resources = configuration.docResources;
        this.htmlIds = configuration.htmlIds;

        visibleMemberTable = typeElement == null ? null : configuration.getVisibleMemberTable(typeElement);

        summariesComparator = utils.comparators.indexElementComparator();
    }

    /**
     * Builds the list of "details" for all members of this kind.
     *
     * @param target the content to which the list will be added
     */
    public abstract void buildDetails(Content target);

    /**
     * Builds the signature.
     *
     * @param target the content to which the documentation will be added
     */
    protected abstract void buildSignature(Content target);

    /**
     * Builds the deprecation info.
     *
     * @param target the content to which the documentation will be added
     */
    protected abstract void buildDeprecationInfo(Content target);

    /**
     * Builds the preview info.
     *
     * @param target the content to which the documentation will be added
     */
    protected abstract void buildPreviewInfo(Content target);

    /**
     * Builds the "summary" for all members of this kind.
     *
     * @param target the content to which the list will be added
     */
    public void buildSummary(Content target)
    {
        var summaryTreeList = new ArrayList<Content>();

        buildMainSummary(summaryTreeList);

        var showInherited = switch (kind) {
            case FIELDS, METHODS, NESTED_CLASSES, PROPERTIES -> true;
            case ANNOTATION_TYPE_MEMBER, ANNOTATION_TYPE_MEMBER_OPTIONAL, ANNOTATION_TYPE_MEMBER_REQUIRED,
                    CONSTRUCTORS, ENUM_CONSTANTS -> false;
        };
        if (showInherited)
            buildInheritedSummary(summaryTreeList);

        if (!summaryTreeList.isEmpty()) {
            Content member = getMemberSummaryHeader(target);
            summaryTreeList.forEach(member::add);
            buildSummary(target, member);
            writer.tableOfContents.addLink(HtmlIds.forMemberSummary(kind), getSummaryLabel());
        }
    }

    /**
     * Builds the main summary table for the members of this kind.
     *
     * @param summaryTreeList the list of contents to which the documentation will be added
     */
    private void buildMainSummary(List<Content> summaryTreeList) {
        Set<? extends Element> members = asSortedSet(visibleMemberTable.getVisibleMembers(kind));
        if (!members.isEmpty()) {
            var pHelper = writer.getPropertyHelper();
            for (Element member : members) {
                final Element property = pHelper.getPropertyElement(member);
                if (property != null && member instanceof ExecutableElement ee) {
                    configuration.cmtUtils.updatePropertyMethodComment(ee, property);
                }
                if (utils.isMethod(member)) {
                    var docFinder = utils.docFinder();
                    Optional<List<? extends DocTree>> r = docFinder.search((ExecutableElement) member, (m -> {
                        var firstSentenceTrees = utils.getFirstSentenceTrees(m);
                        Optional<List<? extends DocTree>> optional = firstSentenceTrees.isEmpty() ? Optional.empty() : Optional.of(firstSentenceTrees);
                        return DocFinder.Result.fromOptional(optional);
                    })).toOptional();
                    // The fact that we use `member` for possibly unrelated tags is suspicious
                    addMemberSummary(typeElement, member, r.orElse(List.of()));
                } else {
                    addMemberSummary(typeElement, member, utils.getFirstSentenceTrees(member));
                }
            }
            summaryTreeList.add(getSummaryTable(typeElement));
        }
    }

    /**
     * Builds the inherited member summary for the members of this kind.
     *
     * @param targets the list of contents to which the documentation will be added
     */
    private void buildInheritedSummary(List<Content> targets) {
        var inheritedMembersFromMap = asSortedSet(visibleMemberTable.getAllVisibleMembers(kind));

        for (TypeElement inheritedClass : visibleMemberTable.getVisibleTypeElements()) {
            if (!(utils.isPublic(inheritedClass) || utils.isLinkable(inheritedClass))) {
                continue;
            }
            if (Objects.equals(inheritedClass, typeElement)) {
                continue;
            }
            if (utils.hasHiddenTag(inheritedClass)) {
                continue;
            }

            List<? extends Element> members = inheritedMembersFromMap.stream()
                    .filter(e -> Objects.equals(utils.getEnclosingTypeElement(e), inheritedClass))
                    .toList();

            if (!members.isEmpty()) {
                SortedSet<Element> inheritedMembers = new TreeSet<>(summariesComparator);
                inheritedMembers.addAll(members);
                Content inheritedHeader = getInheritedSummaryHeader(inheritedClass);
                Content links = getInheritedSummaryLinks();
                addSummaryFootNote(inheritedClass, inheritedMembers, links);
                inheritedHeader.add(links);
                targets.add(inheritedHeader);
            }
        }
    }

    private void addSummaryFootNote(TypeElement inheritedClass, Iterable<Element> inheritedMembers,
                                    Content links) {
        boolean isFirst = true;
        for (Element member : inheritedMembers) {
            TypeElement t = utils.isUndocumentedEnclosure(inheritedClass)
                    ? typeElement : inheritedClass;
            addInheritedMemberSummary(t, member, isFirst, links);
            isFirst = false;
        }
    }

    private SortedSet<? extends Element> asSortedSet(Collection<? extends Element> members) {
        SortedSet<Element> out = new TreeSet<>(summariesComparator);
        out.addAll(members);
        return out;
    }

    private Content getSummaryLabel() {
        return switch (kind) {
            case FIELDS -> contents.fieldSummaryLabel;
            case METHODS -> contents.methodSummary;
            case CONSTRUCTORS -> contents.constructorSummaryLabel;
            case ENUM_CONSTANTS -> contents.enumConstantSummary;
            case NESTED_CLASSES -> contents.nestedClassSummary;
            case PROPERTIES -> contents.propertySummaryLabel;
            case ANNOTATION_TYPE_MEMBER_OPTIONAL -> contents.annotateTypeOptionalMemberSummaryLabel;
            case ANNOTATION_TYPE_MEMBER_REQUIRED -> contents.annotateTypeRequiredMemberSummaryLabel;
            default -> throw new IllegalArgumentException(kind.toString());
        };
    }

    /**
     * Returns the member summary header for the given class.
     *
     * @param content     the content to which the member summary will be added
     *
     * @return the member summary header
     */
    public abstract Content getMemberSummaryHeader(Content content);
    /**
     * Adds the given summary to the list of summaries.
     *
     * @param summariesList the list of summaries
     * @param content       the summary
     */
    public abstract void buildSummary(Content summariesList, Content content);

    /**
     * Returns a list of visible elements of the specified kind in this
     * type element.
     * @param kind of members
     * @return a list of members
     */
    protected List<Element> getVisibleMembers(VisibleMemberTable.Kind kind) {
        return configuration.getVisibleMemberTable(typeElement).getVisibleMembers(kind);
    }

    /* ----- abstracts ----- */

    /**
     * Adds the summary label for the member.
     *
     * @param content the content to which the label will be added
     */
    public abstract void addSummaryLabel(Content content);

    /**
     * Returns the summary table header for the member.
     *
     * @param member the member to be documented
     *
     * @return the summary table header
     */
    public abstract TableHeader getSummaryTableHeader(Element member);

    private Table<Element> summaryTable;

    private Table<Element> getSummaryTable() {
        if (summaryTable == null) {
            summaryTable = createSummaryTable();
        }
        return summaryTable;
    }

    /**
     * Creates the summary table for this element.
     * The table should be created and initialized if needed, and configured
     * so that it is ready to add content with {@link Table#addRow(Content[])}
     * and similar methods.
     *
     * @return the summary table
     */
    protected abstract Table<Element> createSummaryTable();

    /**
     * Adds inherited summary label for the member.
     *
     * @param typeElement the type element to which to link to
     * @param content     the content to which the inherited summary label will be added
     */
    public abstract void addInheritedSummaryLabel(TypeElement typeElement, Content content);

    /**
     * Adds the summary type for the member.
     *
     * @param member  the member to be documented
     * @param content the content to which the type will be added
     */
    protected abstract void addSummaryType(Element member, Content content);

    /**
     * Adds the summary link for the member.
     *
     * @param typeElement the type element to be documented
     * @param member      the member to be documented
     * @param content     the content to which the link will be added
     */
    protected void addSummaryLink(TypeElement typeElement, Element member, Content content) {
        addSummaryLink(HtmlLinkInfo.Kind.PLAIN, typeElement, member, content);
    }

    /**
     * Adds the summary link for the member.
     *
     * @param context     the id of the context where the link will be printed
     * @param typeElement the type element to be documented
     * @param member      the member to be documented
     * @param content     the content to which the summary link will be added
     */
    protected abstract void addSummaryLink(HtmlLinkInfo.Kind context,
                                           TypeElement typeElement, Element member, Content content);

    /**
     * Adds the inherited summary link for the member.
     *
     * @param typeElement the type element to be documented
     * @param member      the member to be documented
     * @param target      the content to which the inherited summary link will be added
     */
    protected abstract void addInheritedSummaryLink(TypeElement typeElement,
            Element member, Content target);

    /**
     * Returns a link for summary (deprecated, preview) pages.
     *
     * @param member the member being linked to
     *
     * @return the link
     */
    protected abstract Content getSummaryLink(Element member);

    /**
     * Adds the modifiers and type for the member in the member summary.
     *
     * @param member the member to add the modifiers and type for
     * @param type   the type to add
     * @param target the content to which the modifiers and type will be added
     */
    protected void addModifiersAndType(Element member, TypeMirror type,
            Content target) {
        var code = new HtmlTree(HtmlTag.CODE);
        addModifiers(member, code);
        if (type == null) {
            code.add(switch (member.getKind()) {
                case ENUM -> "enum";
                case INTERFACE -> "interface";
                case ANNOTATION_TYPE -> "@interface";
                case RECORD -> "record";
                default -> "class";
            });
            code.add(Entity.NO_BREAK_SPACE);
        } else {
            List<? extends TypeParameterElement> list = utils.isExecutableElement(member)
                    ? ((ExecutableElement)member).getTypeParameters()
                    : null;
            if (list != null && !list.isEmpty()) {
                ((AbstractExecutableMemberWriter) this)
                  .addTypeParameters((ExecutableElement)member, code);
            }
            code.add(
                    writer.getLink(new HtmlLinkInfo(configuration,
                            HtmlLinkInfo.Kind.LINK_TYPE_PARAMS, type)
                            .addLineBreakOpportunitiesInTypeParameters(true)));
        }
        target.add(code);
    }

    /**
     * Adds the modifiers for the member.
     *
     * @param member the member to add the modifiers for
     * @param target the content to which the modifiers will be added
     */
    private void addModifiers(Element member, Content target) {
        if (utils.isProtected(member)) {
            target.add("protected ");
        } else if (utils.isPrivate(member)) {
            target.add("private ");
        } else if (!utils.isPublic(member)) { // Package private
            target.add(resources.getText("doclet.Package_private"));
            target.add(" ");
        }
        if (!utils.isAnnotationInterface(member.getEnclosingElement()) && utils.isMethod(member)) {
            if (!utils.isPlainInterface(member.getEnclosingElement()) && utils.isAbstract(member)) {
                target.add("abstract ");
            }
            if (utils.isDefault(member)) {
                target.add("default ");
            }
        }
        if (utils.isStatic(member)) {
            target.add("static ");
        }
        if (!utils.isEnum(member) && utils.isFinal(member)) {
            target.add("final ");
        }
    }

    /**
     * Adds the deprecated information for the given member.
     *
     * @param member the member being documented.
     * @param target the content to which the deprecated information will be added.
     */
    protected void addDeprecatedInfo(Element member, Content target) {
        var t = configuration.tagletManager.getTaglet(DocTree.Kind.DEPRECATED);
        Content output = t.getAllBlockTagOutput(member, writer.getTagletWriterInstance(false));
        if (!output.isEmpty()) {
            target.add(HtmlTree.DIV(HtmlStyles.deprecationBlock, output));
        }
    }

    /**
     * Adds the comment for the given member.
     *
     * @param member  the member being documented.
     * @param content the content to which the comment will be added.
     */
    protected void addComment(Element member, Content content) {
        if (!utils.getFullBody(member).isEmpty()) {
            writer.addInlineComment(member, content);
        }
    }

    /**
     * Add the preview information for the given member.
     *
     * @param member the member being documented.
     * @param content the content to which the preview information will be added.
     */
    protected void addPreviewInfo(Element member, Content content) {
        writer.addPreviewInfo(member, content);
    }

    /**
     * Add the restricted information for the given method.
     *
     * @param method the method being documented.
     * @param content the content to which the preview information will be added.
     */
    protected void addRestrictedInfo(ExecutableElement method, Content content) {
        writer.addRestrictedInfo(method, content);
    }

    protected String name(Element member) {
        return utils.getSimpleName(member);
    }

    /**
     * Adds use information to the documentation.
     *
     * @param members list of program elements for which the use information will be added
     * @param heading the section heading
     * @param content the content to which the use information will be added
     */
    protected void addUseInfo(List<? extends Element> members, Content heading, Content content) {
        if (members == null || members.isEmpty()) {
            return;
        }
        boolean printedUseTableHeader = false;
        var useTable = new Table<Void>(HtmlStyles.summaryTable)
                .setCaption(heading)
                .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colSecond, HtmlStyles.colLast);
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
                    && !utils.isTypeElement(element)) {

                var name = HtmlTree.SPAN(HtmlStyles.typeNameLabel);
                name.add(name(te) + ".");
                typeContent.add(name);
            }
            addSummaryLink(utils.isClass(element) || utils.isPlainInterface(element)
                    ? HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS_AND_BOUNDS
                    : HtmlLinkInfo.Kind.PLAIN,
                    te, element, typeContent);
            Content desc = new ContentBuilder();
            writer.addSummaryLinkComment(element, desc);
            useTable.addRow(summaryType, typeContent, desc);
        }
        content.add(useTable);
    }

    protected void serialWarning(Element e, String key, String a1, String a2) {
        if (options.serialWarn()) {
            configuration.messages.warning(e, key, a1, a2);
        }
    }

    /**
     * Adds the member summary for the given class and member.
     *
     * @param tElement           the class the summary belongs to
     * @param member             the member that is documented
     * @param firstSentenceTrees the tags for the sentence being documented
     */
    public void addMemberSummary(TypeElement tElement, Element member,
            List<? extends DocTree> firstSentenceTrees) {
        if (tElement != typeElement) {
            throw new IllegalStateException(getClass() + ": " + tElement + ", " + typeElement);
        }
        var table = getSummaryTable();
        List<Content> rowContents = new ArrayList<>();
        Content summaryType = new ContentBuilder();
        addSummaryType(member, summaryType);
        if (!summaryType.isEmpty())
            rowContents.add(summaryType);
        Content summaryLink = new ContentBuilder();
        addSummaryLink(tElement, member, summaryLink);
        rowContents.add(summaryLink);
        Content desc = new ContentBuilder();
        writer.addSummaryLinkComment(member, firstSentenceTrees, desc);
        rowContents.add(desc);
        table.addRow(member, rowContents);
    }

    /**
     * Adds the inherited member summary for the given class and member.
     *
     * @param tElement the class the inherited member belongs to
     * @param member the inherited member that is being documented
     * @param isFirst true if this is the first member in the list
     * @param content the content to which the links will be added
     */
    public void addInheritedMemberSummary(TypeElement tElement,
            Element member, boolean isFirst,
            Content content) {
        writer.addInheritedMemberSummary(this, tElement, member, isFirst, content);
    }

    /**
     * Returns the inherited member summary header for the given class.
     *
     * @param tElement the class the summary belongs to
     *
     * @return the inherited member summary header
     */
    public Content getInheritedSummaryHeader(TypeElement tElement) {
        Content c = writer.getMemberInherited();
        writer.addInheritedSummaryHeader(this, tElement, c);
        return c;
    }

    /**
     * Returns the inherited summary links.
     *
     * @return the inherited summary links
     */
    public Content getInheritedSummaryLinks() {
        return new HtmlTree(HtmlTag.CODE);
    }

    /**
     * Returns the summary table for the given class.
     *
     * @param tElement the class the summary table belongs to
     *
     * @return the summary table
     */
    public Content getSummaryTable(TypeElement tElement) {
        if (tElement != typeElement) {
            throw new IllegalStateException();
        }
        return getSummaryTable();
    }

    /**
     * Returns the member content.
     *
     * @param memberContent the content representing the member
     *
     * @return the member content
     */
    public Content getMember(Content memberContent) {
        return writer.getMember(memberContent);
    }

    /**
     * {@return a list to add member items to}
     *
     * @see #getMemberListItem(Content)
     */
    protected Content getMemberList() {
        return writer.getMemberList();
    }

    /**
     * {@return a member item}
     *
     * @param memberContent the member to represent as an item
     * @see #getMemberList()
     */
    protected Content getMemberListItem(Content memberContent) {
        return writer.getMemberListItem(memberContent);
    }

}
