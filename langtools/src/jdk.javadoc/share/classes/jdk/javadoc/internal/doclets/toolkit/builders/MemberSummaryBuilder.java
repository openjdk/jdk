/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.builders;

import java.text.MessageFormat;
import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeWriter;
import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.MemberSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.WriterFactory;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;
import jdk.javadoc.internal.doclets.toolkit.CommentUtils;

/**
 * Builds the member summary.
 * There are two anonymous subtype variants of this builder, created
 * in the {@link #getInstance} methods. One is for general types;
 * the other is for annotation types.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public abstract class MemberSummaryBuilder extends AbstractMemberBuilder {

    /*
     * Comparator used to sort the members in the summary.
     */
    private final Comparator<Element> comparator;

    /**
     * The member summary writers for the given class.
     */
    private final EnumMap<VisibleMemberMap.Kind, MemberSummaryWriter> memberSummaryWriters;

    /**
     * The type being documented.
     */
    protected final TypeElement typeElement;

    /**
     * Construct a new MemberSummaryBuilder.
     *
     * @param classWriter   the writer for the class whose members are being
     *                      summarized.
     * @param context       the build context.
     */
    private MemberSummaryBuilder(Context context, TypeElement typeElement) {
        super(context);
        this.typeElement = typeElement;
        memberSummaryWriters = new EnumMap<>(VisibleMemberMap.Kind.class);

        comparator = utils.makeGeneralPurposeComparator();
    }

    /**
     * Construct a new MemberSummaryBuilder for a general type.
     *
     * @param classWriter   the writer for the class whose members are being
     *                      summarized.
     * @param context       the build context.
     * @return              the instance
     */
    public static MemberSummaryBuilder getInstance(
            ClassWriter classWriter, Context context) {
        MemberSummaryBuilder builder = new MemberSummaryBuilder(context, classWriter.getTypeElement()) {
            @Override
            public void build(Content contentTree) {
                buildPropertiesSummary(contentTree);
                buildNestedClassesSummary(contentTree);
                buildEnumConstantsSummary(contentTree);
                buildFieldsSummary(contentTree);
                buildConstructorsSummary(contentTree);
                buildMethodsSummary(contentTree);
            }

            @Override
            public boolean hasMembersToDocument() {
                for (VisibleMemberMap.Kind kind : VisibleMemberMap.Kind.values()) {
                    VisibleMemberMap members = getVisibleMemberMap(kind);
                    if (!members.noVisibleMembers()) {
                        return true;
                    }
                }
                return false;
            }
        };
        WriterFactory wf = context.configuration.getWriterFactory();
        for (VisibleMemberMap.Kind kind : VisibleMemberMap.Kind.values()) {
            MemberSummaryWriter msw = builder.getVisibleMemberMap(kind).noVisibleMembers()
                    ? null
                    : wf.getMemberSummaryWriter(classWriter, kind);
            builder.memberSummaryWriters.put(kind, msw);
        }
        return builder;
    }

    /**
     * Construct a new MemberSummaryBuilder for an annotation type.
     *
     * @param annotationTypeWriter the writer for the class whose members are
     *                             being summarized.
     * @param context       the build context.
     * @return              the instance
     */
    public static MemberSummaryBuilder getInstance(
            AnnotationTypeWriter annotationTypeWriter, Context context) {
        MemberSummaryBuilder builder = new MemberSummaryBuilder(context,
                annotationTypeWriter.getAnnotationTypeElement()) {
            @Override
            public void build(Content contentTree) {
                buildAnnotationTypeFieldsSummary(contentTree);
                buildAnnotationTypeRequiredMemberSummary(contentTree);
                buildAnnotationTypeOptionalMemberSummary(contentTree);
            }

            @Override
            public boolean hasMembersToDocument() {
                return !utils.getAnnotationMembers(typeElement).isEmpty();
            }
        };
        WriterFactory wf = context.configuration.getWriterFactory();
        for (VisibleMemberMap.Kind kind : VisibleMemberMap.Kind.values()) {
            MemberSummaryWriter msw = builder.getVisibleMemberMap(kind).noVisibleMembers()
                    ? null
                    : wf.getMemberSummaryWriter(annotationTypeWriter, kind);
            builder.memberSummaryWriters.put(kind, msw);
        }
        return builder;
    }

    /**
     * Return the specified visible member map.
     *
     * @param kind the kind of visible member map to return.
     * @return the specified visible member map.
     * @throws ArrayIndexOutOfBoundsException when the type is invalid.
     * @see VisibleMemberMap
     */
    public VisibleMemberMap getVisibleMemberMap(VisibleMemberMap.Kind kind) {
        return configuration.getVisibleMemberMap(typeElement, kind);
    }

    /**.
     * Return the specified member summary writer.
     *
     * @param kind the kind of member summary writer to return.
     * @return the specified member summary writer.
     * @throws ArrayIndexOutOfBoundsException when the type is invalid.
     * @see VisibleMemberMap
     */
    public MemberSummaryWriter getMemberSummaryWriter(VisibleMemberMap.Kind kind) {
        return memberSummaryWriters.get(kind);
    }

    /**
     * Returns a list of methods that will be documented for the given class.
     * This information can be used for doclet specific documentation
     * generation.
     *
     * @param kind the kind of elements to return.
     * @return a list of methods that will be documented.
     * @see VisibleMemberMap
     */
    public SortedSet<Element> members(VisibleMemberMap.Kind kind) {
        TreeSet<Element> out = new TreeSet<>(comparator);
        out.addAll(getVisibleMemberMap(kind).getLeafMembers());
        return out;
    }

    /**
     * Build the summary for the enum constants.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildEnumConstantsSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.ENUM_CONSTANTS);
        VisibleMemberMap visibleMemberMap =
                getVisibleMemberMap(VisibleMemberMap.Kind.ENUM_CONSTANTS);
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    /**
     * Build the summary for fields.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildAnnotationTypeFieldsSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.ANNOTATION_TYPE_FIELDS);
        VisibleMemberMap visibleMemberMap =
                getVisibleMemberMap(VisibleMemberMap.Kind.ANNOTATION_TYPE_FIELDS);
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    /**
     * Build the summary for the optional members.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildAnnotationTypeOptionalMemberSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.ANNOTATION_TYPE_MEMBER_OPTIONAL);
        VisibleMemberMap visibleMemberMap =
                getVisibleMemberMap(VisibleMemberMap.Kind.ANNOTATION_TYPE_MEMBER_OPTIONAL);
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    /**
     * Build the summary for the optional members.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildAnnotationTypeRequiredMemberSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.ANNOTATION_TYPE_MEMBER_REQUIRED);
        VisibleMemberMap visibleMemberMap =
                getVisibleMemberMap(VisibleMemberMap.Kind.ANNOTATION_TYPE_MEMBER_REQUIRED);
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    /**
     * Build the summary for the fields.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildFieldsSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.FIELDS);
        VisibleMemberMap visibleMemberMap =
                getVisibleMemberMap(VisibleMemberMap.Kind.FIELDS);
        addSummary(writer, visibleMemberMap, true, memberSummaryTree);
    }

    /**
     * Build the summary for the fields.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildPropertiesSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.PROPERTIES);
        VisibleMemberMap visibleMemberMap =
                getVisibleMemberMap(VisibleMemberMap.Kind.PROPERTIES);
        addSummary(writer, visibleMemberMap, true, memberSummaryTree);
    }

    /**
     * Build the summary for the nested classes.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildNestedClassesSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.INNER_CLASSES);
        VisibleMemberMap visibleMemberMap =
                getVisibleMemberMap(VisibleMemberMap.Kind.INNER_CLASSES);
        addSummary(writer, visibleMemberMap, true, memberSummaryTree);
    }

    /**
     * Build the method summary.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildMethodsSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.METHODS);
        VisibleMemberMap visibleMemberMap =
               getVisibleMemberMap(VisibleMemberMap.Kind.METHODS);
        addSummary(writer, visibleMemberMap, true, memberSummaryTree);
    }

    /**
     * Build the constructor summary.
     *
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    protected void buildConstructorsSummary(Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters.get(VisibleMemberMap.Kind.CONSTRUCTORS);
        VisibleMemberMap visibleMemberMap =
                getVisibleMemberMap(VisibleMemberMap.Kind.CONSTRUCTORS);
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    /**
     * Build the member summary for the given members.
     *
     * @param writer the summary writer to write the output.
     * @param visibleMemberMap the given members to summarize.
     * @param summaryTreeList list of content trees to which the documentation will be added
     */
    private void buildSummary(MemberSummaryWriter writer,
            VisibleMemberMap visibleMemberMap, LinkedList<Content> summaryTreeList) {
        SortedSet<Element> members = asSortedSet(visibleMemberMap.getLeafMembers());
        if (!members.isEmpty()) {
            List<Content> tableContents = new LinkedList<>();
            int counter = 0;
            for (Element member : members) {
                final Element property = visibleMemberMap.getPropertyMemberDoc(member);
                if (property != null) {
                    processProperty(visibleMemberMap, member, property);
                }
                List<? extends DocTree> firstSentenceTags = utils.getFirstSentenceTrees(member);
                if (utils.isExecutableElement(member) && firstSentenceTags.isEmpty()) {
                    //Inherit comments from overriden or implemented method if
                    //necessary.
                    DocFinder.Output inheritedDoc =
                            DocFinder.search(configuration,
                                    new DocFinder.Input(utils, (ExecutableElement) member));
                    if (inheritedDoc.holder != null
                            && !utils.getFirstSentenceTrees(inheritedDoc.holder).isEmpty()) {
                        // let the comment helper know of the overridden element
                        CommentHelper ch = utils.getCommentHelper(member);
                        ch.setOverrideElement(inheritedDoc.holder);
                        firstSentenceTags = utils.getFirstSentenceTrees(inheritedDoc.holder);
                    }
                }
                writer.addMemberSummary(typeElement, member, firstSentenceTags,
                        tableContents, counter);
                counter++;
            }
            summaryTreeList.add(writer.getSummaryTableTree(typeElement, tableContents));
        }
    }

    /**
     * Process the property method, property setter and/or property getter
     * comment text so that it contains the documentation from
     * the property field. The method adds the leading sentence,
     * copied documentation including the defaultValue tag and
     * the see tags if the appropriate property getter and setter are
     * available.
     *
     * @param visibleMemberMap the members information.
     * @param member the member which is to be augmented.
     * @param property the original property documentation.
     */
    private void processProperty(VisibleMemberMap visibleMemberMap,
                                 Element member,
                                 Element property) {
        CommentUtils cmtutils = configuration.cmtUtils;
        final boolean isSetter = isSetter(member);
        final boolean isGetter = isGetter(member);

        List<DocTree> fullBody = new ArrayList<>();
        List<DocTree> blockTags = new ArrayList<>();
        if (isGetter || isSetter) {
            //add "[GS]ets the value of the property PROPERTY_NAME."
            if (isSetter) {
                String text = MessageFormat.format(
                        configuration.getText("doclet.PropertySetterWithName"),
                        utils.propertyName((ExecutableElement)member));
                fullBody.addAll(cmtutils.makeFirstSentenceTree(text));
            }
            if (isGetter) {
                String text = MessageFormat.format(
                        configuration.getText("doclet.PropertyGetterWithName"),
                        utils.propertyName((ExecutableElement) member));
                fullBody.addAll(cmtutils.makeFirstSentenceTree(text));
            }
            List<? extends DocTree> propertyTags = utils.getBlockTags(property, "propertyDescription");
            if (propertyTags.isEmpty()) {
                List<? extends DocTree> comment = utils.getFullBody(property);
                blockTags.addAll(cmtutils.makePropertyDescriptionTree(comment));
            }
        } else {
            fullBody.addAll(utils.getFullBody(property));
        }

        // copy certain tags
        List<? extends DocTree> tags = utils.getBlockTags(property, Kind.SINCE);
        blockTags.addAll(tags);

        List<? extends DocTree> bTags = utils.getBlockTags(property, Kind.UNKNOWN_BLOCK_TAG);
        CommentHelper ch = utils.getCommentHelper(property);
        for (DocTree dt : bTags) {
            String tagName = ch.getTagName(dt);
            if ( "defaultValue".equals(tagName)) {
                blockTags.add(dt);
            }
        }

        //add @see tags
        if (!isGetter && !isSetter) {
            ExecutableElement getter = (ExecutableElement) visibleMemberMap.getGetterForProperty(member);
            ExecutableElement setter = (ExecutableElement) visibleMemberMap.getSetterForProperty(member);

            if (null != getter) {
                StringBuilder sb = new StringBuilder("#");
                sb.append(utils.getSimpleName(getter)).append("()");
                blockTags.add(cmtutils.makeSeeTree(sb.toString(), getter));
            }

            if (null != setter) {
                VariableElement param = setter.getParameters().get(0);
                StringBuilder sb = new StringBuilder("#");
                sb.append(utils.getSimpleName(setter));
                if (!utils.isTypeVariable(param.asType())) {
                    sb.append("(").append(utils.getTypeSignature(param.asType(), false, true)).append(")");
                }
                blockTags.add(cmtutils.makeSeeTree(sb.toString(), setter));
            }
        }
        cmtutils.setDocCommentTree(member, fullBody, blockTags, utils);
    }

    /**
     * Test whether the method is a getter.
     * @param element property method documentation. Needs to be either property
     * method, property getter, or property setter.
     * @return true if the given documentation belongs to a getter.
     */
    private boolean isGetter(Element element) {
        final String pedName = element.getSimpleName().toString();
        return pedName.startsWith("get") || pedName.startsWith("is");
    }

    /**
     * Test whether the method is a setter.
     * @param element property method documentation. Needs to be either property
     * method, property getter, or property setter.
     * @return true if the given documentation belongs to a setter.
     */
    private boolean isSetter(Element element) {
        return element.getSimpleName().toString().startsWith("set");
    }

    /**
     * Build the inherited member summary for the given methods.
     *
     * @param writer the writer for this member summary.
     * @param visibleMemberMap the map for the members to document.
     * @param summaryTreeList list of content trees to which the documentation will be added
     */
    private void buildInheritedSummary(MemberSummaryWriter writer,
            VisibleMemberMap visibleMemberMap, LinkedList<Content> summaryTreeList) {
        for (TypeElement inhclass : visibleMemberMap.getVisibleClasses()) {
            if (!(utils.isPublic(inhclass) || utils.isLinkable(inhclass))) {
                continue;
            }
            if (inhclass == typeElement) {
                continue;
            }
            SortedSet<Element> inhmembers = asSortedSet(visibleMemberMap.getMembers(inhclass));
            if (!inhmembers.isEmpty()) {
                Content inheritedTree = writer.getInheritedSummaryHeader(inhclass);
                Content linksTree = writer.getInheritedSummaryLinksTree();
                for (Element member : inhmembers) {
                    TypeElement t= inhclass;
                    if (utils.isPackagePrivate(inhclass) && !utils.isLinkable(inhclass)) {
                        t = typeElement;
                    }
                    writer.addInheritedMemberSummary(t, member, inhmembers.first() == member,
                            inhmembers.last() == member, linksTree);
                }
                inheritedTree.addContent(linksTree);
                summaryTreeList.add(writer.getMemberTree(inheritedTree));
            }
        }
    }

    /**
     * Add the summary for the documentation.
     *
     * @param writer the writer for this member summary.
     * @param visibleMemberMap the map for the members to document.
     * @param showInheritedSummary true if inherited summary should be documented
     * @param memberSummaryTree the content tree to which the documentation will be added
     */
    private void addSummary(MemberSummaryWriter writer,
            VisibleMemberMap visibleMemberMap, boolean showInheritedSummary,
            Content memberSummaryTree) {
        LinkedList<Content> summaryTreeList = new LinkedList<>();
        buildSummary(writer, visibleMemberMap, summaryTreeList);
        if (showInheritedSummary)
            buildInheritedSummary(writer, visibleMemberMap, summaryTreeList);
        if (!summaryTreeList.isEmpty()) {
            Content memberTree = writer.getMemberSummaryHeader(typeElement, memberSummaryTree);
            summaryTreeList.stream().forEach(memberTree::addContent);
            writer.addMemberTree(memberSummaryTree, memberTree);
        }
    }

    private SortedSet<Element> asSortedSet(Collection<Element> members) {
        SortedSet<Element> out = new TreeSet<>(comparator);
        out.addAll(members);
        return out;
    }
}
