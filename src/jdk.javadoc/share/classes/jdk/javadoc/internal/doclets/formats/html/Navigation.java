/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.SortedSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.Comment;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.builders.MemberSummaryBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.*;

/**
 * Factory for navigation bar.
 *
 * <p>
 * <b>This is NOT part of any supported API. If you write code that depends on this, you do so at
 * your own risk. This code and its internal interfaces are subject to change or deletion without
 * notice.</b>
 */
public class Navigation {

    private final HtmlConfiguration configuration;
    private final HtmlOptions options;
    private final Element element;
    private final Contents contents;
    private final DocPath path;
    private final DocPath pathToRoot;
    private final Links links;
    private final PageMode documentedPage;
    private Content navLinkModule;
    private Content navLinkPackage;
    private Content navLinkClass;
    private MemberSummaryBuilder memberSummaryBuilder;
    private boolean displaySummaryModuleDescLink;
    private boolean displaySummaryModulesLink;
    private boolean displaySummaryPackagesLink;
    private boolean displaySummaryServicesLink;
    private Content userHeader;
    private Content userFooter;
    private final String rowListTitle;
    private final Content searchLabel;

    private static final Content EMPTY_COMMENT = new Comment(" ");

    public enum PageMode {
        ALL_CLASSES,
        ALL_PACKAGES,
        CLASS,
        CONSTANT_VALUES,
        DEPRECATED,
        DOC_FILE,
        HELP,
        INDEX,
        MODULE,
        OVERVIEW,
        PACKAGE,
        SERIALIZED_FORM,
        SYSTEM_PROPERTIES,
        TREE,
        USE;
    }

    enum Position {
        BOTTOM(MarkerComments.START_OF_BOTTOM_NAVBAR, MarkerComments.END_OF_BOTTOM_NAVBAR),
        TOP(MarkerComments.START_OF_TOP_NAVBAR, MarkerComments.END_OF_TOP_NAVBAR);

        final Content startOfNav;
        final Content endOfNav;

        Position(Content startOfNav, Content endOfNav) {
            this.startOfNav = startOfNav;
            this.endOfNav = endOfNav;
        }

        Content startOfNav() {
            return startOfNav;
        }

        Content endOfNav() {
            return endOfNav;
        }
    }

    /**
     * Creates a {@code Navigation} object for a specific file, to be written in a specific HTML
     * version.
     *
     * @param element element being documented. null if its not an element documentation page
     * @param configuration the configuration object
     * @param page the kind of page being documented
     * @param path the DocPath object
     */
    public Navigation(Element element, HtmlConfiguration configuration, PageMode page, DocPath path) {
        this.configuration = configuration;
        this.options = configuration.getOptions();
        this.element = element;
        this.contents = configuration.contents;
        this.documentedPage = page;
        this.path = path;
        this.pathToRoot = path.parent().invert();
        this.links = new Links(path);
        this.rowListTitle = configuration.getDocResources().getText("doclet.Navigation");
        this.searchLabel = contents.getContent("doclet.search");
    }

    public Navigation setNavLinkModule(Content navLinkModule) {
        this.navLinkModule = navLinkModule;
        return this;
    }

    public Navigation setNavLinkPackage(Content navLinkPackage) {
        this.navLinkPackage = navLinkPackage;
        return this;
    }

    public Navigation setNavLinkClass(Content navLinkClass) {
        this.navLinkClass = navLinkClass;
        return this;
    }

    public Navigation setMemberSummaryBuilder(MemberSummaryBuilder memberSummaryBuilder) {
        this.memberSummaryBuilder = memberSummaryBuilder;
        return this;
    }

    public Navigation setDisplaySummaryModuleDescLink(boolean displaySummaryModuleDescLink) {
        this.displaySummaryModuleDescLink = displaySummaryModuleDescLink;
        return this;
    }

    public Navigation setDisplaySummaryModulesLink(boolean displaySummaryModulesLink) {
        this.displaySummaryModulesLink = displaySummaryModulesLink;
        return this;
    }

    public Navigation setDisplaySummaryPackagesLink(boolean displaySummaryPackagesLink) {
        this.displaySummaryPackagesLink = displaySummaryPackagesLink;
        return this;
    }

    public Navigation setDisplaySummaryServicesLink(boolean displaySummaryServicesLink) {
        this.displaySummaryServicesLink = displaySummaryServicesLink;
        return this;
    }

    public Navigation setUserHeader(Content userHeader) {
        this.userHeader = userHeader;
        return this;
    }

    public Navigation setUserFooter(Content userFooter) {
        this.userFooter = userFooter;
        return this;
    }

    /**
     * Add the links for the main navigation.
     *
     * @param tree the content tree to which the main navigation will added
     */
    private void addMainNavLinks(Content tree) {
        switch (documentedPage) {
            case OVERVIEW:
                addActivePageLink(tree, contents.overviewLabel, options.createOverview());
                addModuleLink(tree);
                addPackageLink(tree);
                addPageLabel(tree, contents.classLabel, true);
                addPageLabel(tree, contents.useLabel, options.classUse());
                addTreeLink(tree);
                addDeprecatedLink(tree);
                addIndexLink(tree);
                addHelpLink(tree);
                break;
            case MODULE:
                addOverviewLink(tree);
                addActivePageLink(tree, contents.moduleLabel, configuration.showModules);
                addPackageLink(tree);
                addPageLabel(tree, contents.classLabel, true);
                addPageLabel(tree, contents.useLabel, options.classUse());
                addTreeLink(tree);
                addDeprecatedLink(tree);
                addIndexLink(tree);
                addHelpLink(tree);
                break;
            case PACKAGE:
                addOverviewLink(tree);
                addModuleOfElementLink(tree);
                addActivePageLink(tree, contents.packageLabel, true);
                addPageLabel(tree, contents.classLabel, true);
                if (options.classUse()) {
                    addContentToTree(tree, links.createLink(DocPaths.PACKAGE_USE,
                            contents.useLabel, "", ""));
                }
                if (options.createTree()) {
                    addContentToTree(tree, links.createLink(DocPaths.PACKAGE_TREE,
                            contents.treeLabel, "", ""));
                }
                addDeprecatedLink(tree);
                addIndexLink(tree);
                addHelpLink(tree);
                break;
            case CLASS:
                addOverviewLink(tree);
                addModuleOfElementLink(tree);
                addPackageSummaryLink(tree);
                addActivePageLink(tree, contents.classLabel, true);
                if (options.classUse()) {
                    addContentToTree(tree, links.createLink(DocPaths.CLASS_USE.resolve(path.basename()),
                            contents.useLabel));
                }
                if (options.createTree()) {
                    addContentToTree(tree, links.createLink(DocPaths.PACKAGE_TREE,
                            contents.treeLabel, "", ""));
                }
                addDeprecatedLink(tree);
                addIndexLink(tree);
                addHelpLink(tree);
                break;
            case USE:
                addOverviewLink(tree);
                addModuleOfElementLink(tree);
                if (element instanceof PackageElement) {
                    addPackageSummaryLink(tree);
                    addPageLabel(tree, contents.classLabel, true);
                } else {
                    addPackageOfElementLink(tree);
                    addContentToTree(tree, navLinkClass);
                }
                addActivePageLink(tree, contents.useLabel, options.classUse());
                if (element instanceof PackageElement) {
                    addContentToTree(tree, links.createLink(DocPaths.PACKAGE_TREE, contents.treeLabel));
                } else {
                    addContentToTree(tree, configuration.utils.isEnclosingPackageIncluded((TypeElement) element)
                            ? links.createLink(DocPath.parent.resolve(DocPaths.PACKAGE_TREE), contents.treeLabel)
                            : links.createLink(pathToRoot.resolve(DocPaths.OVERVIEW_TREE), contents.treeLabel));
                }
                addDeprecatedLink(tree);
                addIndexLink(tree);
                addHelpLink(tree);
                break;
            case TREE:
                addOverviewLink(tree);
                if (element == null) {
                    addPageLabel(tree, contents.moduleLabel, configuration.showModules);
                    addPageLabel(tree, contents.packageLabel, true);
                } else {
                    addModuleOfElementLink(tree);
                    addPackageSummaryLink(tree);
                }
                addPageLabel(tree, contents.classLabel, true);
                addPageLabel(tree, contents.useLabel, options.classUse());
                addActivePageLink(tree, contents.treeLabel, options.createTree());
                addDeprecatedLink(tree);
                addIndexLink(tree);
                addHelpLink(tree);
                break;
            case DEPRECATED:
            case INDEX:
            case HELP:
                addOverviewLink(tree);
                addModuleLink(tree);
                addPackageLink(tree);
                addPageLabel(tree, contents.classLabel, true);
                addPageLabel(tree, contents.useLabel, options.classUse());
                addTreeLink(tree);
                if (documentedPage == PageMode.DEPRECATED) {
                    addActivePageLink(tree, contents.deprecatedLabel, !(options.noDeprecated()
                            || options.noDeprecatedList()));
                } else {
                    addDeprecatedLink(tree);
                }
                if (documentedPage == PageMode.INDEX) {
                    addActivePageLink(tree, contents.indexLabel, options.createIndex());
                } else {
                    addIndexLink(tree);
                }
                if (documentedPage == PageMode.HELP) {
                    addActivePageLink(tree, contents.helpLabel, !options.noHelp());
                } else {
                    addHelpLink(tree);
                }
                break;
            case ALL_CLASSES:
            case ALL_PACKAGES:
            case CONSTANT_VALUES:
            case SERIALIZED_FORM:
            case SYSTEM_PROPERTIES:
                addOverviewLink(tree);
                addModuleLink(tree);
                addPackageLink(tree);
                addPageLabel(tree, contents.classLabel, true);
                addPageLabel(tree, contents.useLabel, options.classUse());
                addTreeLink(tree);
                addDeprecatedLink(tree);
                addIndexLink(tree);
                addHelpLink(tree);
                break;
            case DOC_FILE:
                addOverviewLink(tree);
                addModuleOfElementLink(tree);
                addContentToTree(tree, navLinkPackage);
                addPageLabel(tree, contents.classLabel, true);
                addPageLabel(tree, contents.useLabel, options.classUse());
                addTreeLink(tree);
                addDeprecatedLink(tree);
                addIndexLink(tree);
                addHelpLink(tree);
                break;
            default:
                break;
        }
    }

    /**
     * Add the summary links to the sub-navigation.
     *
     * @param tree the content tree to which the sub-navigation will added
     */
    private void addSummaryLinks(Content tree) {
        List<Content> listContents = new ArrayList<>();
        switch (documentedPage) {
            case CLASS:
                if (element.getKind() == ElementKind.ANNOTATION_TYPE) {
                    addAnnotationTypeSummaryLink("doclet.navField",
                            ANNOTATION_TYPE_FIELDS, listContents);
                    addAnnotationTypeSummaryLink("doclet.navAnnotationTypeRequiredMember",
                            ANNOTATION_TYPE_MEMBER_REQUIRED, listContents);
                    addAnnotationTypeSummaryLink("doclet.navAnnotationTypeOptionalMember",
                            ANNOTATION_TYPE_MEMBER_OPTIONAL, listContents);
                } else {
                    TypeElement typeElement = (TypeElement) element;
                    for (VisibleMemberTable.Kind kind : summarySet) {
                        if (kind == ENUM_CONSTANTS && !configuration.utils.isEnum(typeElement)) {
                            continue;
                        }
                        if (kind == CONSTRUCTORS && configuration.utils.isEnum(typeElement)) {
                            continue;
                        }
                        AbstractMemberWriter writer
                                = ((AbstractMemberWriter) memberSummaryBuilder.getMemberSummaryWriter(kind));
                        if (writer == null) {
                            addContentToList(listContents, contents.getNavLinkLabelContent(kind));
                        } else {
                            addTypeSummaryLink(memberSummaryBuilder.members(kind),
                                    memberSummaryBuilder.getVisibleMemberTable(),
                                    kind, listContents);
                        }
                    }
                }
                if (!listContents.isEmpty()) {
                    Content li = HtmlTree.LI(contents.summaryLabel);
                    li.add(Entity.NO_BREAK_SPACE);
                    tree.add(li);
                    addListToNav(listContents, tree);
                }
                break;
            case MODULE:
                if (displaySummaryModuleDescLink) {
                    addContentToList(listContents,
                            links.createLink(SectionName.MODULE_DESCRIPTION, contents.navModuleDescription));
                } else {
                    addContentToList(listContents, contents.navModuleDescription);
                }
                if (displaySummaryModulesLink) {
                    addContentToList(listContents,
                            links.createLink(SectionName.MODULES, contents.navModules));
                } else {
                    addContentToList(listContents, contents.navModules);
                }
                if (displaySummaryPackagesLink) {
                    addContentToList(listContents,
                            links.createLink(SectionName.PACKAGES, contents.navPackages));
                } else {
                    addContentToList(listContents, contents.navPackages);
                }
                if (displaySummaryServicesLink) {
                    addContentToList(listContents,
                            links.createLink(SectionName.SERVICES, contents.navServices));
                } else {
                    addContentToList(listContents, contents.navServices);
                }
                if (!listContents.isEmpty()) {
                    Content li = HtmlTree.LI(contents.moduleSubNavLabel);
                    li.add(Entity.NO_BREAK_SPACE);
                    tree.add(li);
                    addListToNav(listContents, tree);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Add the navigation summary link.
     *
     * @param members members to be linked
     * @param vmt the visible member table
     * @param kind the visible member kind
     * @param listContents the list of contents
     */
    private void addTypeSummaryLink(SortedSet<? extends Element> members,
            VisibleMemberTable vmt,
            VisibleMemberTable.Kind kind, List<Content> listContents) {
        if (!members.isEmpty()) {
            addTypeSummaryLink(null, kind, true, listContents);
            return;
        }
        Set<TypeElement> visibleClasses = vmt.getVisibleTypeElements();
        for (TypeElement t : visibleClasses) {
            if (configuration.getVisibleMemberTable(t).hasVisibleMembers(kind)) {
                addTypeSummaryLink(null, kind, true, listContents);
                return;
            }
        }
        addTypeSummaryLink(null, kind, false, listContents);
    }

    /**
     * Add the navigation Type summary link.
     *
     * @param typeElement the Type being documented
     * @param kind the kind of member being documented
     * @param link true if the members are listed and need to be linked
     * @param listContents the list of contents to which the summary will be added
     */
    private void addTypeSummaryLink(TypeElement typeElement, VisibleMemberTable.Kind kind, boolean link,
            List<Content> listContents) {
        switch (kind) {
            case CONSTRUCTORS:
                if (link) {
                    addContentToList(listContents, links.createLink(SectionName.CONSTRUCTOR_SUMMARY,
                            contents.navConstructor));
                } else {
                    addContentToList(listContents, contents.navConstructor);
                }
                break;
            case ENUM_CONSTANTS:
                if (link) {
                    if (typeElement == null) {
                        addContentToList(listContents, links.createLink(SectionName.ENUM_CONSTANT_SUMMARY,
                                contents.navEnum));
                    } else {
                        addContentToList(listContents, links.createLink(
                                SectionName.ENUM_CONSTANTS_INHERITANCE,
                                configuration.getClassName(typeElement), contents.navEnum));
                    }
                } else {
                    addContentToList(listContents, contents.navEnum);
                }
                break;
            case FIELDS:
                if (link) {
                    if (typeElement == null) {
                        addContentToList(listContents,
                                links.createLink(SectionName.FIELD_SUMMARY, contents.navField));
                    } else {
                        addContentToList(listContents, links.createLink(SectionName.FIELDS_INHERITANCE,
                                configuration.getClassName(typeElement), contents.navField));
                    }
                } else {
                    addContentToList(listContents, contents.navField);
                }
                break;
            case METHODS:
                if (link) {
                    if (typeElement == null) {
                        addContentToList(listContents,
                                links.createLink(SectionName.METHOD_SUMMARY, contents.navMethod));
                    } else {
                        addContentToList(listContents, links.createLink(SectionName.METHODS_INHERITANCE,
                                configuration.getClassName(typeElement), contents.navMethod));
                    }
                } else {
                    addContentToList(listContents, contents.navMethod);
                }
                break;
            case INNER_CLASSES:
                if (link) {
                    if (typeElement == null) {
                        addContentToList(listContents,
                                links.createLink(SectionName.NESTED_CLASS_SUMMARY, contents.navNested));
                    } else {
                        addContentToList(listContents, links.createLink(SectionName.NESTED_CLASSES_INHERITANCE,
                                configuration.utils.getFullyQualifiedName(typeElement), contents.navNested));
                    }
                } else {
                    addContentToList(listContents, contents.navNested);
                }
                break;
            case PROPERTIES:
                if (link) {
                    if (typeElement == null) {
                        addContentToList(listContents,
                                links.createLink(SectionName.PROPERTY_SUMMARY, contents.navProperty));
                    } else {
                        addContentToList(listContents, links.createLink(SectionName.PROPERTIES_INHERITANCE,
                                configuration.getClassName(typeElement), contents.navProperty));
                    }
                } else {
                    addContentToList(listContents, contents.navProperty);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Add the navigation Type summary link.
     *
     * @param label the label to be added
     * @param kind the kind of member being documented
     * @param listContents the list of contents to which the summary will be added
     */
    private void addAnnotationTypeSummaryLink(String label, VisibleMemberTable.Kind kind, List<Content> listContents) {
        AbstractMemberWriter writer = ((AbstractMemberWriter) memberSummaryBuilder.
                getMemberSummaryWriter(kind));
        if (writer == null) {
            addContentToList(listContents, contents.getContent(label));
        } else {
            boolean link = memberSummaryBuilder.getVisibleMemberTable().hasVisibleMembers(kind);
            switch (kind) {
                case ANNOTATION_TYPE_FIELDS:
                    if (link) {
                        addContentToList(listContents, links.createLink(SectionName.ANNOTATION_TYPE_FIELD_SUMMARY,
                                contents.navField));
                    } else {
                        addContentToList(listContents, contents.navField);
                    }
                    break;
                case ANNOTATION_TYPE_MEMBER_REQUIRED:
                    if (link) {
                        addContentToList(listContents, links.createLink(
                                SectionName.ANNOTATION_TYPE_REQUIRED_ELEMENT_SUMMARY,
                                contents.navAnnotationTypeRequiredMember));
                    } else {
                        addContentToList(listContents, contents.navAnnotationTypeRequiredMember);
                    }
                    break;
                case ANNOTATION_TYPE_MEMBER_OPTIONAL:
                    if (link) {
                        addContentToList(listContents, links.createLink(
                                SectionName.ANNOTATION_TYPE_OPTIONAL_ELEMENT_SUMMARY,
                                contents.navAnnotationTypeOptionalMember));
                    } else {
                        addContentToList(listContents, contents.navAnnotationTypeOptionalMember);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Add the detail links to sub-navigation.
     *
     * @param tree the content tree to which the links will be added
     */
    private void addDetailLinks(Content tree) {
        switch (documentedPage) {
            case CLASS:
                List<Content> listContents = new ArrayList<>();
                if (element.getKind() == ElementKind.ANNOTATION_TYPE) {
                    addAnnotationTypeDetailLink(listContents);
                } else {
                    TypeElement typeElement = (TypeElement) element;
                    for (VisibleMemberTable.Kind kind : detailSet) {
                        AbstractMemberWriter writer
                                = ((AbstractMemberWriter) memberSummaryBuilder.
                                        getMemberSummaryWriter(kind));
                        if (kind == ENUM_CONSTANTS && !configuration.utils.isEnum(typeElement)) {
                            continue;
                        }
                        if (kind == CONSTRUCTORS && configuration.utils.isEnum(typeElement)) {
                            continue;
                        }
                        if (writer == null) {
                            addContentToList(listContents, contents.getNavLinkLabelContent(kind));
                        } else {
                            addTypeDetailLink(kind, memberSummaryBuilder.hasMembers(kind), listContents);
                        }
                    }
                }
                if (!listContents.isEmpty()) {
                    Content li = HtmlTree.LI(contents.detailLabel);
                    li.add(Entity.NO_BREAK_SPACE);
                    tree.add(li);
                    addListToNav(listContents, tree);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Add the navigation Type detail link.
     *
     * @param kind the kind of member being documented
     * @param link true if the members are listed and need to be linked
     * @param listContents the list of contents to which the detail will be added.
     */
    protected void addTypeDetailLink(VisibleMemberTable.Kind kind, boolean link, List<Content> listContents) {
        switch (kind) {
            case CONSTRUCTORS:
                if (link) {
                    addContentToList(listContents, links.createLink(SectionName.CONSTRUCTOR_DETAIL, contents.navConstructor));
                } else {
                    addContentToList(listContents, contents.navConstructor);
                }
                break;
            case ENUM_CONSTANTS:
                if (link) {
                    addContentToList(listContents, links.createLink(SectionName.ENUM_CONSTANT_DETAIL, contents.navEnum));
                } else {
                    addContentToList(listContents, contents.navEnum);
                }
                break;
            case FIELDS:
                if (link) {
                    addContentToList(listContents, links.createLink(SectionName.FIELD_DETAIL, contents.navField));
                } else {
                    addContentToList(listContents, contents.navField);
                }
                break;
            case METHODS:
                if (link) {
                    addContentToList(listContents, links.createLink(SectionName.METHOD_DETAIL, contents.navMethod));
                } else {
                    addContentToList(listContents, contents.navMethod);
                }
                break;
            case PROPERTIES:
                if (link) {
                    addContentToList(listContents, links.createLink(SectionName.PROPERTY_DETAIL, contents.navProperty));
                } else {
                    addContentToList(listContents, contents.navProperty);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Add the navigation Annotation Type detail link.
     *
     * @param listContents the list of contents to which the annotation detail will be added.
     */
    protected void addAnnotationTypeDetailLink(List<Content> listContents) {
        TypeElement annotationType = (TypeElement) element;
        AbstractMemberWriter writerField
                = ((AbstractMemberWriter) memberSummaryBuilder.
                        getMemberSummaryWriter(ANNOTATION_TYPE_FIELDS));
        AbstractMemberWriter writerOptional
                = ((AbstractMemberWriter) memberSummaryBuilder.
                        getMemberSummaryWriter(ANNOTATION_TYPE_MEMBER_OPTIONAL));
        AbstractMemberWriter writerRequired
                = ((AbstractMemberWriter) memberSummaryBuilder.
                        getMemberSummaryWriter(ANNOTATION_TYPE_MEMBER_REQUIRED));
        if (writerField != null) {
            addAnnotationTypeDetailLink(ANNOTATION_TYPE_FIELDS,
                    !configuration.utils.getAnnotationFields(annotationType).isEmpty(),
                    listContents);
        } else {
            addContentToList(listContents, contents.navField);
        }
        if (writerOptional != null) {
            addAnnotationTypeDetailLink(ANNOTATION_TYPE_MEMBER_OPTIONAL,
                    !annotationType.getAnnotationMirrors().isEmpty(), listContents);
        } else if (writerRequired != null) {
            addAnnotationTypeDetailLink(ANNOTATION_TYPE_MEMBER_REQUIRED,
                    !annotationType.getAnnotationMirrors().isEmpty(), listContents);
        } else {
            addContentToList(listContents, contents.navAnnotationTypeMember);
        }
    }

    /**
     * Add the navigation Annotation Type detail link.
     *
     * @param type the kind of member being documented
     * @param link true if the member details need to be linked
     * @param listContents the list of contents to which the annotation detail will be added.
     */
    protected void addAnnotationTypeDetailLink(VisibleMemberTable.Kind type, boolean link, List<Content> listContents) {
        switch (type) {
            case ANNOTATION_TYPE_FIELDS:
                if (link) {
                    addContentToList(listContents, links.createLink(SectionName.ANNOTATION_TYPE_FIELD_DETAIL,
                            contents.navField));
                } else {
                    addContentToList(listContents, contents.navField);
                }
                break;
            case ANNOTATION_TYPE_MEMBER_REQUIRED:
            case ANNOTATION_TYPE_MEMBER_OPTIONAL:
                if (link) {
                    addContentToList(listContents, links.createLink(SectionName.ANNOTATION_TYPE_ELEMENT_DETAIL,
                            contents.navAnnotationTypeMember));
                } else {
                    addContentToList(listContents, contents.navAnnotationTypeMember);
                }
                break;
            default:
                break;
        }
    }

    private void addContentToList(List<Content> listContents, Content tree) {
        listContents.add(HtmlTree.LI(tree));
    }

    private void addContentToTree(Content tree, Content content) {
        tree.add(HtmlTree.LI(content));
    }

    private void addListToNav(List<Content> listContents, Content tree) {
        int count = 0;
        for (Content liContent : listContents) {
            if (count < listContents.size() - 1) {
                liContent.add(Entity.NO_BREAK_SPACE);
                liContent.add("|");
                liContent.add(Entity.NO_BREAK_SPACE);
            }
            tree.add(liContent);
            count++;
        }
    }

    private void addActivePageLink(Content tree, Content label, boolean display) {
        if (display) {
            tree.add(HtmlTree.LI(HtmlStyle.navBarCell1Rev, label));
        }
    }

    private void addPageLabel(Content tree, Content label, boolean display) {
        if (display) {
            tree.add(HtmlTree.LI(label));
        }
    }

    private void addOverviewLink(Content tree) {
        if (options.createOverview()) {
            tree.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(DocPaths.INDEX),
                    contents.overviewLabel, "", "")));
        }
    }

    private void addModuleLink(Content tree) {
        if (configuration.showModules) {
            if (configuration.modules.size() == 1) {
                ModuleElement mdle = configuration.modules.first();
                boolean included = configuration.utils.isIncluded(mdle);
                tree.add(HtmlTree.LI((included)
                        ? links.createLink(pathToRoot.resolve(configuration.docPaths.moduleSummary(mdle)), contents.moduleLabel, "", "")
                        : contents.moduleLabel));
            } else if (!configuration.modules.isEmpty()) {
                addPageLabel(tree, contents.moduleLabel, true);
            }
        }
    }

    private void addModuleOfElementLink(Content tree) {
        if (configuration.showModules) {
            tree.add(HtmlTree.LI(navLinkModule));
        }
    }

    private void addPackageLink(Content tree) {
        if (configuration.packages.size() == 1) {
            PackageElement packageElement = configuration.packages.first();
            boolean included = packageElement != null && configuration.utils.isIncluded(packageElement);
            if (!included) {
                for (PackageElement p : configuration.packages) {
                    if (p.equals(packageElement)) {
                        included = true;
                        break;
                    }
                }
            }
            if (included || packageElement == null) {
                tree.add(HtmlTree.LI(links.createLink(
                        pathToRoot.resolve(configuration.docPaths.forPackage(packageElement).resolve(DocPaths.PACKAGE_SUMMARY)),
                        contents.packageLabel)));
            } else {
                DocLink crossPkgLink = configuration.extern.getExternalLink(
                        packageElement, pathToRoot, DocPaths.PACKAGE_SUMMARY.getPath());
                if (crossPkgLink != null) {
                    tree.add(HtmlTree.LI(links.createLink(crossPkgLink, contents.packageLabel)));
                } else {
                    tree.add(HtmlTree.LI(contents.packageLabel));
                }
            }
        } else if (!configuration.packages.isEmpty()) {
            addPageLabel(tree, contents.packageLabel, true);
        }
    }

    private void addPackageOfElementLink(Content tree) {
        tree.add(HtmlTree.LI(links.createLink(DocPath.parent.resolve(DocPaths.PACKAGE_SUMMARY),
                contents.packageLabel)));
    }

    private void addPackageSummaryLink(Content tree) {
        tree.add(HtmlTree.LI(links.createLink(DocPaths.PACKAGE_SUMMARY, contents.packageLabel)));
    }

    private void addTreeLink(Content tree) {
        if (options.createTree()) {
            List<PackageElement> packages = new ArrayList<>(configuration.getSpecifiedPackageElements());
            DocPath docPath = packages.size() == 1 && configuration.getSpecifiedTypeElements().isEmpty()
                    ? pathToRoot.resolve(configuration.docPaths.forPackage(packages.get(0)).resolve(DocPaths.PACKAGE_TREE))
                    : pathToRoot.resolve(DocPaths.OVERVIEW_TREE);
            tree.add(HtmlTree.LI(links.createLink(docPath, contents.treeLabel, "", "")));
        }
    }

    private void addDeprecatedLink(Content tree) {
        if (!(options.noDeprecated() || options.noDeprecatedList())) {
            tree.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(DocPaths.DEPRECATED_LIST),
                    contents.deprecatedLabel, "", "")));
        }
    }

    private void addIndexLink(Content tree) {
        if (options.createIndex()) {
            tree.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(
                    (options.splitIndex()
                            ? DocPaths.INDEX_FILES.resolve(DocPaths.indexN(1))
                            : DocPaths.INDEX_ALL)),
                    contents.indexLabel, "", "")));
        }
    }

    private void addHelpLink(Content tree) {
        if (!options.noHelp()) {
            String helpfile = options.helpFile();
            DocPath helpfilenm;
            if (helpfile.isEmpty()) {
                helpfilenm = DocPaths.HELP_DOC;
            } else {
                DocFile file = DocFile.createFileForInput(configuration, helpfile);
                helpfilenm = DocPath.create(file.getName());
            }
            tree.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(helpfilenm),
                    contents.helpLabel, "", "")));
        }
    }

    private void addSearch(Content tree) {
        String searchValueId = "search";
        String reset = "reset";
        HtmlTree inputText = HtmlTree.INPUT("text", searchValueId, searchValueId);
        HtmlTree inputReset = HtmlTree.INPUT(reset, reset, reset);
        HtmlTree searchDiv = HtmlTree.DIV(HtmlStyle.navListSearch, HtmlTree.LABEL(searchValueId, searchLabel));
        searchDiv.add(inputText);
        searchDiv.add(inputReset);
        tree.add(searchDiv);
    }

    /**
     * Get the navigation content.
     *
     * @param posn the position for the navigation bar
     * @return the navigation contents
     */
    public Content getContent(Position posn) {
        if (options.noNavbar()) {
            return new ContentBuilder();
        }
        Content tree = HtmlTree.NAV();

        HtmlTree navDiv = new HtmlTree(HtmlTag.DIV);
        Content skipNavLinks = contents.getContent("doclet.Skip_navigation_links");
        SectionName navListSection;
        Content aboutContent;
        boolean addSearch;
        switch (posn) {
            case TOP:
                tree.add(Position.TOP.startOfNav());
                navDiv.setStyle(HtmlStyle.topNav)
                        .setId(SectionName.NAVBAR_TOP.getName())
                        .add(HtmlTree.DIV(HtmlStyle.skipNav,
                                links.createLink(SectionName.SKIP_NAVBAR_TOP, skipNavLinks,
                                        skipNavLinks.toString(), "")));
                navListSection = SectionName.NAVBAR_TOP_FIRSTROW;
                aboutContent = userHeader;
                addSearch = options.createIndex();
                break;

            case BOTTOM:
                tree.add(Position.BOTTOM.startOfNav());
                navDiv.setStyle(HtmlStyle.bottomNav)
                        .setId(SectionName.NAVBAR_BOTTOM.getName())
                        .add(HtmlTree.DIV(HtmlStyle.skipNav,
                                links.createLink(SectionName.SKIP_NAVBAR_BOTTOM, skipNavLinks,
                                        skipNavLinks.toString(), "")));
                navListSection = SectionName.NAVBAR_BOTTOM_FIRSTROW;
                aboutContent = userFooter;
                addSearch = false;
                break;

            default:
                throw new Error();
        }

        HtmlTree navList = new HtmlTree(HtmlTag.UL)
                .setId(navListSection.getName())
                .setStyle(HtmlStyle.navList)
                .put(HtmlAttr.TITLE, rowListTitle);
        addMainNavLinks(navList);
        navDiv.add(navList);
        Content aboutDiv = HtmlTree.DIV(HtmlStyle.aboutLanguage, aboutContent);
        navDiv.add(aboutDiv);
        tree.add(navDiv);

        HtmlTree subDiv = new HtmlTree(HtmlTag.DIV).setStyle(HtmlStyle.subNav);

        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        // Add the summary links if present.
        HtmlTree ulNavSummary = new HtmlTree(HtmlTag.UL).setStyle(HtmlStyle.subNavList);
        addSummaryLinks(ulNavSummary);
        div.add(ulNavSummary);
        // Add the detail links if present.
        HtmlTree ulNavDetail = new HtmlTree(HtmlTag.UL).setStyle(HtmlStyle.subNavList);
        addDetailLinks(ulNavDetail);
        div.add(ulNavDetail);
        subDiv.add(div);

        if (addSearch) {
            addSearch(subDiv);
        }
        tree.add(subDiv);

        switch (posn) {
            case TOP:
                tree.add(Position.TOP.endOfNav());
                tree.add(HtmlTree.SPAN(HtmlStyle.skipNav, EMPTY_COMMENT)
                        .setId(SectionName.SKIP_NAVBAR_TOP.getName()));
                break;

            case BOTTOM:
                tree.add(Position.BOTTOM.endOfNav());
                tree.add(HtmlTree.SPAN(HtmlStyle.skipNav, EMPTY_COMMENT)
                        .setId(SectionName.SKIP_NAVBAR_BOTTOM.getName()));
        }
        return tree;
    }
}
