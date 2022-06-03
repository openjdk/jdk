/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.ListIterator;
import java.util.SortedSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DeprecatedTree;

import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;

/**
 * Generator for either a single index or split index for all
 * documented elements, terms defined in some documentation comments,
 * and summary pages.
 *
 * @see IndexBuilder
 */
public class IndexWriter extends HtmlDocletWriter {

    protected final IndexBuilder mainIndex;
    protected final boolean splitIndex;

    /**
     * Generates the main index of all documented elements, terms defined in some documentation
     * comments, and summary pages.
     *
     * If {@link HtmlOptions#splitIndex()} is true, a separate page is generated for each
     * initial letter; otherwise, a single page is generated for all items in the index.
     *
     * @param configuration the configuration
     * @throws DocFileIOException if an error occurs while writing the files
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        IndexBuilder mainIndex = configuration.mainIndex;
        List<Character> firstCharacters = mainIndex.getFirstCharacters();
        if (configuration.getOptions().splitIndex()) {
            ListIterator<Character> iter = firstCharacters.listIterator();
            while (iter.hasNext()) {
                Character ch = iter.next();
                DocPath file = DocPaths.INDEX_FILES.resolve(DocPaths.indexN(iter.nextIndex()));
                IndexWriter writer = new IndexWriter(configuration, file);
                writer.generateIndexFile(firstCharacters, List.of(ch));
            }
        } else {
            IndexWriter writer = new IndexWriter(configuration, DocPaths.INDEX_ALL);
            writer.generateIndexFile(firstCharacters, firstCharacters);
        }
    }

    /**
     * Creates a writer that can write a page containing some or all of the overall index.
     *
     * @param configuration  the current configuration
     * @param path           the file to be generated
     */
    protected IndexWriter(HtmlConfiguration configuration, DocPath path) {
        super(configuration, path);
        this.mainIndex = configuration.mainIndex;
        this.splitIndex = configuration.getOptions().splitIndex();
    }

    /**
     * Generates a page containing some or all of the overall index.
     *
     * @param allFirstCharacters     the initial characters of all index items
     * @param displayFirstCharacters the initial characters of the index items to appear on this page
     * @throws DocFileIOException if an error occurs while writing the page
     */
    protected void generateIndexFile(List<Character> allFirstCharacters,
                                     List<Character> displayFirstCharacters) throws DocFileIOException {
        String title = splitIndex
                ? resources.getText("doclet.Window_Split_Index", displayFirstCharacters.get(0))
                : resources.getText("doclet.Window_Single_Index");
        HtmlTree body = getBody(getWindowTitle(title));
        Content mainContent = new ContentBuilder();
        addLinksForIndexes(allFirstCharacters, mainContent);
        for (Character ch : displayFirstCharacters) {
            addContents(ch, mainIndex.getItems(ch), mainContent);
        }
        addLinksForIndexes(allFirstCharacters, mainContent);
        body.add(new BodyContents()
                .setHeader(getHeader(PageMode.INDEX))
                .addMainContent(HtmlTree.DIV(HtmlStyle.header,
                        HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING,
                                contents.getContent("doclet.Index"))))
                .addMainContent(mainContent)
                .setFooter(getFooter()));

        String description = splitIndex ? "index: " + displayFirstCharacters.get(0) : "index";
        printHtmlDocument(null, description, body);
    }

    /**
     * Adds a set of items to the page.
     *
     * @param ch      the first character of the names of the items
     * @param items   the items
     * @param content the content to which to add the items
     */
    protected void addContents(char ch, SortedSet<IndexItem> items, Content content) {
        addHeading(ch, content);

        var dl = HtmlTree.DL(HtmlStyle.index);
        for (IndexItem item : items) {
            addDescription(item, dl);
        }
        content.add(dl);
    }

    /**
     * Adds a heading containing the first character for a set of items.
     *
     * @param ch      the first character of the names of the items
     * @param content the content to which to add the items
     */
    protected void addHeading(char ch, Content content) {
        Content headContent = Text.of(String.valueOf(ch));
        var heading = HtmlTree.HEADING(Headings.CONTENT_HEADING, HtmlStyle.title, headContent)
                .setId(HtmlIds.forIndexChar(ch));
        content.add(heading);
    }

    /**
     * Adds the description for an index item into a list.
     *
     * @param indexItem the item
     * @param dl        the list
     */
    protected void addDescription(IndexItem indexItem, Content dl) {
        if (indexItem.isTagItem()) {
            addTagDescription(indexItem, dl);
        } else if (indexItem.isElementItem()) {
            addElementDescription(indexItem, dl);
        }
    }

    /**
     * Add one line summary comment for the item.
     *
     * @param item the item to be documented
     * @param target the content to which the description will be added
     */
    protected void addElementDescription(IndexItem item, Content target) {
        Content dt;
        Element element = item.getElement();
        String label = item.getLabel();
        switch (element.getKind()) {
            case MODULE:
                dt = HtmlTree.DT(getModuleLink((ModuleElement) element, Text.of(label)));
                dt.add(" - ").add(contents.module_).add(" " + label);
                break;

            case PACKAGE:
                dt = HtmlTree.DT(getPackageLink((PackageElement) element, Text.of(label)));
                if (configuration.showModules) {
                    item.setContainingModule(utils.getFullyQualifiedName(utils.containingModule(element)));
                }
                dt.add(" - ").add(contents.package_).add(" " + label);
                break;

            case CLASS:
            case ENUM:
            case RECORD:
            case ANNOTATION_TYPE:
            case INTERFACE:
                dt = HtmlTree.DT(getLink(new HtmlLinkInfo(configuration,
                        HtmlLinkInfo.Kind.INDEX, (TypeElement) element).style(HtmlStyle.typeNameLink)));
                dt.add(" - ");
                addClassInfo((TypeElement) element, dt);
                break;

            case CONSTRUCTOR:
            case METHOD:
            case FIELD:
            case ENUM_CONSTANT:
                TypeElement containingType = item.getContainingTypeElement();
                dt = HtmlTree.DT(getDocLink(HtmlLinkInfo.Kind.INDEX, containingType, element,
                                label, HtmlStyle.memberNameLink));
                dt.add(" - ");
                addMemberDesc(element, containingType, dt);
                break;

            default:
                throw new Error();
        }
        target.add(dt);
        var dd = new HtmlTree(TagName.DD);
        if (element.getKind() == ElementKind.MODULE || element.getKind() == ElementKind.PACKAGE) {
            addSummaryComment(element, dd);
        } else {
            addComment(element, dd);
        }
        target.add(dd);
    }

    /**
     * Adds information for the given type element.
     *
     * @param te      the element
     * @param content the content to which the class info will be added
     */
    protected void addClassInfo(TypeElement te, Content content) {
        content.add(contents.getContent("doclet.in",
                utils.getTypeElementKindName(te, false),
                getPackageLink(utils.containingPackage(te),
                    getLocalizedPackageName(utils.containingPackage(te)))
                ));
    }

    /**
     * Adds a description for an item found in a documentation comment.
     *
     * @param item   the item
     * @param target the list to which to add the description
     */
    protected void addTagDescription(IndexItem item, Content target) {
        String itemPath = pathToRoot.isEmpty() ? "" : pathToRoot.getPath() + "/";
        itemPath += item.getUrl();
        var labelLink = HtmlTree.A(itemPath, Text.of(item.getLabel()));
        var dt = HtmlTree.DT(labelLink.setStyle(HtmlStyle.searchTagLink));
        dt.add(" - ");
        dt.add(contents.getContent("doclet.Search_tag_in", item.getHolder()));
        target.add(dt);
        var dd = new HtmlTree(TagName.DD);
        if (item.getDescription().isEmpty()) {
            dd.add(Entity.NO_BREAK_SPACE);
        } else {
            dd.add(item.getDescription());
        }
        target.add(dd);
    }

    /**
     * Adds a comment for an element in the index. If the element is deprecated
     * and it has a @deprecated tag, use that comment; otherwise,  if the containing
     * class for this element is deprecated, then add the word "Deprecated." at
     * the start and then print the normal comment.
     *
     * @param element     the element
     * @param content the content to which the comment will be added
     */
    protected void addComment(Element element, Content content) {
        var span = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(element));
        var div = HtmlTree.DIV(HtmlStyle.deprecationBlock);
        if (utils.isDeprecated(element)) {
            div.add(span);
            List<? extends DeprecatedTree> tags = utils.getDeprecatedTrees(element);
            if (!tags.isEmpty())
                addInlineDeprecatedComment(element, tags.get(0), div);
            content.add(div);
        } else {
            TypeElement encl = utils.getEnclosingTypeElement(element);
            while (encl != null) {
                if (utils.isDeprecated(encl)) {
                    div.add(span);
                    content.add(div);
                    break;
                }
                encl = utils.getEnclosingTypeElement(encl);
            }
            addSummaryComment(element, content);
        }
    }

    /**
     * Adds a description for a member element.
     *
     * @param member    the element
     * @param enclosing the enclosing type element
     * @param content   the content to which the member description will be added
     */
    protected void addMemberDesc(Element member, TypeElement enclosing, Content content) {
        String kindName = utils.getTypeElementKindName(enclosing, true);
        String resource = switch (member.getKind()) {
            case ENUM_CONSTANT ->
                    "doclet.Enum_constant_in";
            case FIELD ->
                    utils.isStatic(member) ? "doclet.Static_variable_in" : "doclet.Variable_in";
            case CONSTRUCTOR ->
                    "doclet.Constructor_for";
            case METHOD ->
                    utils.isAnnotationInterface(enclosing) ? "doclet.Element_in"
                            : utils.isStatic(member) ? "doclet.Static_method_in" : "doclet.Method_in";
            case RECORD_COMPONENT ->
                    "doclet.Record_component_in";
            default -> throw new IllegalArgumentException(member.getKind().toString());
        };
        content.add(contents.getContent(resource, kindName)).add(" ");
        addPreQualifiedClassLink(HtmlLinkInfo.Kind.INDEX, enclosing,
                null, content);
    }

    /**
     * Add links for all the index files, based on the first character of the names of the items.
     *
     * @param allFirstCharacters the list of all first characters to be linked
     * @param content            the content to which the links for indexes will be added
     */
    protected void addLinksForIndexes(List<Character> allFirstCharacters, Content content) {
        ListIterator<Character> iter = allFirstCharacters.listIterator();
        while (iter.hasNext()) {
            char ch = iter.next();
            Content label = Text.of(Character.toString(ch));
            Content link = splitIndex
                    ? links.createLink(DocPaths.indexN(iter.nextIndex()), label)
                    : links.createLink(HtmlIds.forIndexChar(ch), label);
            content.add(link);
            content.add(Entity.NO_BREAK_SPACE);
        }

        content.add(new HtmlTree(TagName.BR));
        List<Content> pageLinks = Stream.of(IndexItem.Category.values())
                .flatMap(c -> mainIndex.getItems(c).stream())
                .filter(i -> !(i.isElementItem() || i.isTagItem()))
                .sorted((i1,i2)-> utils.compareStrings(i1.getLabel(), i2.getLabel()))
                .map(i -> links.createLink(pathToRoot.resolve(i.getUrl()),
                        contents.getNonBreakString(i.getLabel())))
                .toList();
        content.add(contents.join(getVerticalSeparator(), pageLinks));
    }

    private Content getVerticalSeparator() {
        return HtmlTree.SPAN(HtmlStyle.verticalSeparator, Text.of("|"));
    }
}
