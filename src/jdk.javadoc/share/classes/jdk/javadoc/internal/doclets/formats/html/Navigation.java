/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

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
    private final HtmlIds htmlIds;
    private final DocPath path;
    private final DocPath pathToRoot;
    private final Links links;
    private final PageMode documentedPage;
    private Content userHeader;
    private final String rowListTitle;
    private List<Content> subNavLinks = List.of();

    public enum PageMode {
        ALL_CLASSES,
        ALL_PACKAGES,
        CLASS,
        CONSTANT_VALUES,
        DEPRECATED,
        DOC_FILE,
        EXTERNAL_SPECS,
        HELP,
        INDEX,
        MODULE,
        NEW,
        OVERVIEW,
        PACKAGE,
        PREVIEW,
        RESTRICTED,
        SERIALIZED_FORM,
        SEARCH,
        SYSTEM_PROPERTIES,
        TREE,
        USE
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
        this.contents = configuration.getContents();
        this.htmlIds = configuration.htmlIds;
        this.documentedPage = page;
        this.path = path;
        this.pathToRoot = path.parent().invert();
        this.links = new Links(path);
        this.rowListTitle = configuration.getDocResources().getText("doclet.Navigation");
    }

    public Navigation setUserHeader(Content userHeader) {
        this.userHeader = userHeader;
        return this;
    }

    public Navigation setSubNavLinks(List<Content> subNavLinks) {
        this.subNavLinks = subNavLinks;
        return this;
    }

    /**
     * Adds the links for the main navigation.
     *
     * @param target the content to which the main navigation will added
     */
    private void addMainNavLinks(Content target) {
        switch (documentedPage) {
            case OVERVIEW:
                addActivePageLink(target, contents.overviewLabel, options.createOverview());
                addTreeLink(target);
                addPreviewLink(target);
                addNewLink(target);
                addDeprecatedLink(target);
                addIndexLink(target);
                addSearchLink(target);
                addHelpLink(target);
                break;
            case MODULE:
                addOverviewLink(target);
                addActivePageLink(target, contents.moduleLabel, configuration.showModules);
                addTreeLink(target);
                addPreviewLink(target);
                addNewLink(target);
                addDeprecatedLink(target);
                addIndexLink(target);
                addSearchLink(target);
                addHelpLink(target);
                break;
            case PACKAGE:
                addOverviewLink(target);
                addActivePageLink(target, contents.packageLabel, true);
                if (options.classUse()) {
                    addItemToList(target, links.createLink(DocPaths.PACKAGE_USE,
                            contents.useLabel, ""));
                }
                if (options.createTree()) {
                    addItemToList(target, links.createLink(DocPaths.PACKAGE_TREE,
                            contents.treeLabel, ""));
                }
                addPreviewLink(target);
                addNewLink(target);
                addDeprecatedLink(target);
                addIndexLink(target);
                addSearchLink(target);
                addHelpLink(target);
                break;
            case CLASS:
                addOverviewLink(target);
                addActivePageLink(target, contents.classLabel, true);
                if (options.classUse()) {
                    addItemToList(target, links.createLink(DocPaths.CLASS_USE.resolve(path.basename()),
                            contents.useLabel));
                }
                if (options.createTree()) {
                    addItemToList(target, links.createLink(DocPaths.PACKAGE_TREE,
                            contents.treeLabel, ""));
                }
                addPreviewLink(target);
                addNewLink(target);
                addDeprecatedLink(target);
                addIndexLink(target);
                addSearchLink(target);
                addHelpLink(target);
                break;
            case USE:
                addOverviewLink(target);
                // Class-use page is still generated for deprecated classes with
                // -nodeprecated option, make sure not to link to non-existent page.
                if (!options.noDeprecated() || !configuration.utils.isDeprecated(element)) {
                    addPageElementLink(target);
                }
                addActivePageLink(target, contents.useLabel, options.classUse());
                if (options.createTree()) {
                    if (configuration.utils.isPackage(element)) {
                        addItemToList(target, links.createLink(DocPaths.PACKAGE_TREE, contents.treeLabel));
                    } else {
                        addItemToList(target, configuration.utils.isEnclosingPackageIncluded((TypeElement) element)
                                ? links.createLink(DocPath.parent.resolve(DocPaths.PACKAGE_TREE), contents.treeLabel)
                                : links.createLink(pathToRoot.resolve(DocPaths.OVERVIEW_TREE), contents.treeLabel));
                    }
                }
                addPreviewLink(target);
                addNewLink(target);
                addDeprecatedLink(target);
                addIndexLink(target);
                addSearchLink(target);
                addHelpLink(target);
                break;
            case TREE:
                addOverviewLink(target);
                if (element != null && !configuration.utils.isModule(element)) {
                    addPageElementLink(target);
                    if (options.classUse()) {
                        if (configuration.utils.isPackage(element) || configuration.utils.isTypeElement(element)) {
                            addItemToList(target, links.createLink(DocPaths.PACKAGE_USE, contents.useLabel));
                        }
                    }
                }
                addActivePageLink(target, contents.treeLabel, options.createTree());
                addPreviewLink(target);
                addNewLink(target);
                addDeprecatedLink(target);
                addIndexLink(target);
                addSearchLink(target);
                addHelpLink(target);
                break;
            case DEPRECATED:
            case INDEX:
            case HELP:
            case PREVIEW:
            case NEW:
            case SEARCH:
                addOverviewLink(target);
                addTreeLink(target);
                if (documentedPage == PageMode.PREVIEW) {
                    addActivePageLink(target, contents.previewLabel,
                            configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.PREVIEW));
                } else {
                    addPreviewLink(target);
                }
                if (documentedPage == PageMode.NEW) {
                    addActivePageLink(target, contents.newLabel,
                            configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.NEW));
                } else {
                    addNewLink(target);
                }
                if (documentedPage == PageMode.DEPRECATED) {
                    addActivePageLink(target, contents.deprecatedLabel,
                            configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.DEPRECATED));
                } else {
                    addDeprecatedLink(target);
                }
                if (documentedPage == PageMode.INDEX) {
                    addActivePageLink(target, contents.indexLabel, options.createIndex());
                } else {
                    addIndexLink(target);
                }
                if (documentedPage == PageMode.SEARCH) {
                    addActivePageLink(target, contents.searchLabel, options.createIndex());
                } else {
                    addSearchLink(target);
                }
                if (documentedPage == PageMode.HELP) {
                    addActivePageLink(target, contents.helpLabel, !options.noHelp());
                } else {
                    addHelpLink(target);
                }
                break;
            case ALL_CLASSES:
            case ALL_PACKAGES:
            case CONSTANT_VALUES:
            case EXTERNAL_SPECS:
            case RESTRICTED:
            case SERIALIZED_FORM:
            case SYSTEM_PROPERTIES:
                addOverviewLink(target);
                addTreeLink(target);
                addPreviewLink(target);
                addNewLink(target);
                addDeprecatedLink(target);
                addIndexLink(target);
                addSearchLink(target);
                addHelpLink(target);
                break;
            case DOC_FILE:
                addOverviewLink(target);
                if (element != null) {
                    addPageElementLink(target);
                    if (options.classUse()) {
                        if (configuration.utils.isPackage(element)) {
                            addItemToList(target, links.createLink(pathToRoot.resolve(
                                    configuration.docPaths.forPackage((PackageElement) element)
                                            .resolve(DocPaths.PACKAGE_USE)), contents.useLabel));
                        }
                    }
                    if (options.createTree() && configuration.utils.isPackage(element)) {
                        addItemToList(target, links.createLink(pathToRoot.resolve(
                                configuration.docPaths.forPackage((PackageElement) element)
                                        .resolve(DocPaths.PACKAGE_TREE)), contents.treeLabel));
                    } else {
                        addTreeLink(target);
                    }
                }
                addPreviewLink(target);
                addNewLink(target);
                addDeprecatedLink(target);
                addIndexLink(target);
                addSearchLink(target);
                addHelpLink(target);
                break;
            default:
                break;
        }
    }

    /**
     * Adds the navigation Type detail link.
     *
     * @param kind the kind of member being documented
     * @param link true if the members are listed and need to be linked
     * @param listContents the list of contents to which the detail will be added.
     */
    protected void addTypeDetailLink(VisibleMemberTable.Kind kind, boolean link, List<Content> listContents) {
        addContentToList(listContents, switch (kind) {
            case CONSTRUCTORS -> links.createLinkOrLabel(HtmlIds.CONSTRUCTOR_DETAIL, contents.navConstructor, link);
            case ENUM_CONSTANTS -> links.createLinkOrLabel(HtmlIds.ENUM_CONSTANT_DETAIL, contents.navEnum, link);
            case FIELDS -> links.createLinkOrLabel(HtmlIds.FIELD_DETAIL, contents.navField, link);
            case METHODS -> links.createLinkOrLabel(HtmlIds.METHOD_DETAIL, contents.navMethod, link);
            case PROPERTIES -> links.createLinkOrLabel(HtmlIds.PROPERTY_DETAIL, contents.navProperty, link);
            case ANNOTATION_TYPE_MEMBER -> links.createLinkOrLabel(HtmlIds.ANNOTATION_TYPE_ELEMENT_DETAIL,
                    contents.navAnnotationTypeMember, link);
            default -> Text.EMPTY;
        });
    }

    private void addContentToList(List<Content> listContents, Content source) {
        listContents.add(HtmlTree.LI(source));
    }

    private void addItemToList(Content list, Content item) {
        list.add(HtmlTree.LI(item));
    }

    private void addActivePageLink(Content target, Content label, boolean display) {
        if (display) {
            target.add(HtmlTree.LI(HtmlStyle.navBarCell1Rev, label));
        }
    }

    /**
     * Adds a link to the overview page if indicated by the configuration.
     * Otherwise a link to the first module or package is added.
     *
     * @param target content to add the link to
     */
    private void addOverviewLink(Content target) {
        if (options.createOverview()) {
            target.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(DocPaths.INDEX),
                    contents.overviewLabel, "")));
        } else if (configuration.showModules && configuration.modules.size() > 0) {
            ModuleElement mdle = configuration.modules.first();
            if (!mdle.equals(element)) {
                boolean included = configuration.utils.isIncluded(mdle);
                target.add(HtmlTree.LI((included)
                        ? links.createLink(pathToRoot.resolve(configuration.docPaths.moduleSummary(mdle)), contents.moduleLabel, "")
                        : contents.moduleLabel));
            }
        } else if (configuration.packages.size() > 0 && !(element instanceof PackageElement)) {
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
                target.add(HtmlTree.LI(links.createLink(
                        pathToRoot.resolve(configuration.docPaths.forPackage(packageElement).resolve(DocPaths.PACKAGE_SUMMARY)),
                        contents.packageLabel)));
            } else {
                DocLink crossPkgLink = configuration.extern.getExternalLink(
                        packageElement, pathToRoot, DocPaths.PACKAGE_SUMMARY.getPath());
                if (crossPkgLink != null) {
                    target.add(HtmlTree.LI(links.createLink(crossPkgLink, contents.packageLabel)));
                }
            }
        }
    }


    private void addPageElementLink(Content list) {
        Content link = switch (element) {
            case ModuleElement mdle -> links.createLink(pathToRoot.resolve(
                    configuration.docPaths.moduleSummary(mdle)), contents.moduleLabel);
            case PackageElement pkg -> links.createLink(pathToRoot.resolve(
                    configuration.docPaths.forPackage(pkg).resolve(DocPaths.PACKAGE_SUMMARY)), contents.packageLabel);
            case TypeElement type -> links.createLink(pathToRoot.resolve(
                    configuration.docPaths.forClass(type)), contents.classLabel);
            default -> throw new RuntimeException();
        };
        list.add(HtmlTree.LI(link));
    }

    private void addTreeLink(Content target) {
        if (options.createTree()) {
            List<PackageElement> packages = new ArrayList<>(configuration.getSpecifiedPackageElements());
            DocPath docPath = packages.size() == 1 && configuration.getSpecifiedTypeElements().isEmpty()
                    ? pathToRoot.resolve(configuration.docPaths.forPackage(packages.get(0)).resolve(DocPaths.PACKAGE_TREE))
                    : pathToRoot.resolve(DocPaths.OVERVIEW_TREE);
            target.add(HtmlTree.LI(links.createLink(docPath, contents.treeLabel, "")));
        }
    }

    private void addDeprecatedLink(Content target) {
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.DEPRECATED)) {
            target.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(DocPaths.DEPRECATED_LIST),
                    contents.deprecatedLabel, "")));
        }
    }

    private void addPreviewLink(Content target) {
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.PREVIEW)) {
            target.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(DocPaths.PREVIEW_LIST),
                    contents.previewLabel, "")));
        }
    }

    private void addNewLink(Content target) {
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.NEW)) {
            target.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(DocPaths.NEW_LIST),
                    contents.newLabel, "")));
        }
    }

    private void addIndexLink(Content target) {
        if (options.createIndex()) {
            target.add(HtmlTree.LI(links.createLink(pathToRoot.resolve(
                    (options.splitIndex()
                            ? DocPaths.INDEX_FILES.resolve(DocPaths.indexN(1))
                            : DocPaths.INDEX_ALL)),
                    contents.indexLabel, "")));
        }
    }

    private void addSearchLink(Content target) {
        if (options.createIndex()) {
            target.add(HtmlTree.LI(links.createLink(
                    pathToRoot.resolve(DocPaths.SEARCH_PAGE), contents.searchLabel, "")));
        }
    }

    private void addHelpLink(Content target) {
        if (!options.noHelp()) {
            String helpfile = options.helpFile();
            DocPath helpfilenm;
            if (helpfile.isEmpty()) {
                helpfilenm = DocPaths.HELP_DOC;
            } else {
                DocFile file = DocFile.createFileForInput(configuration, helpfile);
                helpfilenm = DocPath.create(file.getName());
            }
            target.add(HtmlTree.LI(links.createLink(
                    new DocLink(pathToRoot.resolve(helpfilenm), htmlIds.forPage(documentedPage).name()),
                    contents.helpLabel, "")));
        }
    }

    private void addSearch(Content target) {
        var resources = configuration.getDocResources();
        var inputText = HtmlTree.INPUT(HtmlAttr.InputType.TEXT, HtmlIds.SEARCH_INPUT)
                .put(HtmlAttr.PLACEHOLDER, resources.getText("doclet.search_placeholder"))
                .put(HtmlAttr.ARIA_LABEL, resources.getText("doclet.search_in_documentation"))
                .put(HtmlAttr.AUTOCOMPLETE, "off");
        var inputReset = HtmlTree.INPUT(HtmlAttr.InputType.RESET, HtmlIds.RESET_SEARCH)
                .put(HtmlAttr.VALUE, resources.getText("doclet.search_reset"));
        var searchDiv = HtmlTree.DIV(HtmlStyle.navListSearch)
                .add(inputText)
                .add(inputReset);
        target.add(searchDiv);
    }

    /**
     * Returns the navigation content.
     *
     * @return the navigation content
     */
    public Content getContent() {
        if (options.noNavbar()) {
            return new ContentBuilder();
        }
        var navigationBar = HtmlTree.NAV();

        var navContent = new HtmlTree(TagName.DIV);
        Content skipNavLinks = contents.getContent("doclet.Skip_navigation_links");
        String toggleNavLinks = configuration.getDocResources().getText("doclet.Toggle_navigation_links");
        navigationBar.add(MarkerComments.START_OF_TOP_NAVBAR);
        // The mobile menu button uses three empty spans to produce its animated icon
        HtmlTree iconSpan = HtmlTree.SPAN(HtmlStyle.navBarToggleIcon).add(Entity.NO_BREAK_SPACE);
        navContent.setStyle(HtmlStyle.navContent).add(HtmlTree.DIV(HtmlStyle.navMenuButton,
                        new HtmlTree(TagName.BUTTON).setId(HtmlIds.NAVBAR_TOGGLE_BUTTON)
                                .put(HtmlAttr.ARIA_CONTROLS, HtmlIds.NAVBAR_TOP.name())
                                .put(HtmlAttr.ARIA_EXPANDED, String.valueOf(false))
                                .put(HtmlAttr.ARIA_LABEL, toggleNavLinks)
                                .add(iconSpan)
                                .add(iconSpan)
                                .add(iconSpan)))
                .add(HtmlTree.DIV(HtmlStyle.skipNav,
                        links.createLink(HtmlIds.SKIP_NAVBAR_TOP, skipNavLinks,
                                skipNavLinks.toString())));
        Content aboutContent = userHeader;

        var navList = new HtmlTree(TagName.UL)
                .setId(HtmlIds.NAVBAR_TOP_FIRSTROW)
                .setStyle(HtmlStyle.navList)
                .put(HtmlAttr.TITLE, rowListTitle);
        addMainNavLinks(navList);
        navContent.add(navList);
        var aboutDiv = HtmlTree.DIV(HtmlStyle.aboutLanguage, aboutContent);
        navContent.add(aboutDiv);
        navigationBar.add(HtmlTree.DIV(HtmlStyle.topNav, navContent).setId(HtmlIds.NAVBAR_TOP));

        var subNavContent = HtmlTree.DIV(HtmlStyle.navContent);

        // Add the breadcrumb navigation links if present.
        var breadcrumbNav = HtmlTree.OL(HtmlStyle.subNavList);
        breadcrumbNav.addAll(subNavLinks, HtmlTree::LI);
        subNavContent.addUnchecked(breadcrumbNav);

        if (options.createIndex() && documentedPage != PageMode.SEARCH) {
            addSearch(subNavContent);
        }
        navigationBar.add(HtmlTree.DIV(HtmlStyle.subNav, subNavContent));

        navigationBar.add(MarkerComments.END_OF_TOP_NAVBAR);
        navigationBar.add(HtmlTree.SPAN(HtmlStyle.skipNav)
                .addUnchecked(Text.EMPTY)
                .setId(HtmlIds.SKIP_NAVBAR_TOP));

        return navigationBar;
    }
}
