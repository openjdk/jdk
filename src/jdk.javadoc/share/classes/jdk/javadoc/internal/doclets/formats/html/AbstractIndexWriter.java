/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.SearchIndexItem.Category;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;

/**
 * Generate Index for all the Member Names with Indexing in
 * Unicode Order. This class is a base class for {@link SingleIndexWriter} and
 * {@link SplitIndexWriter}. It uses the functionality from
 * {@link HtmlDocletWriter} to generate the Index Contents.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see    IndexBuilder
 */
public class AbstractIndexWriter extends HtmlDocletWriter {

    /**
     * The index of all the members with unicode character.
     */
    protected IndexBuilder indexBuilder;

    protected Navigation navBar;

    protected final Map<Character, List<SearchIndexItem>> tagSearchIndexMap;

    /**
     * This constructor will be used by {@link SplitIndexWriter}. Initializes
     * path to this file and relative path from this file.
     *
     * @param configuration  The current configuration
     * @param path       Path to the file which is getting generated.
     * @param indexBuilder Unicode based Index from {@link IndexBuilder}
     */
    protected AbstractIndexWriter(HtmlConfiguration configuration,
                                  DocPath path,
                                  IndexBuilder indexBuilder) {
        super(configuration, path);
        this.indexBuilder = indexBuilder;
        this.navBar = new Navigation(null, configuration, PageMode.INDEX, path);
        Stream<SearchIndexItem> items =
                searchItems.itemsOfCategories(Category.INDEX, Category.SYSTEM_PROPERTY)
                        .sorted(comparators.makeGenericSearchIndexComparator());
        this.tagSearchIndexMap = buildSearchTagIndex(items);
    }

    protected void addContents(Character uc, List<IndexItem> memberlist,
            Content contentTree) {
        addHeading(uc, contentTree);

        HtmlTree dl = HtmlTree.DL(HtmlStyle.index);
        Map<String,Integer> duplicateLabelCheck = new HashMap<>();
        memberlist.forEach(e -> duplicateLabelCheck.compute(e.getFullyQualifiedLabel(utils),
                (k, v) -> v == null ? 1 : v + 1));
        for (IndexItem indexItem : memberlist) {
            addDescription(indexItem, dl,
                    duplicateLabelCheck.get(indexItem.getFullyQualifiedLabel(utils)) > 1);
        }
        contentTree.add(dl);
    }

    protected void addHeading(Character uc, Content contentTree) {
        String unicode = uc.toString();
        Content headContent = new StringContent(unicode);
        HtmlTree heading = HtmlTree.HEADING(Headings.CONTENT_HEADING,
                HtmlStyle.title, headContent);
        heading.setId(getNameForIndex(unicode));
        contentTree.add(heading);
    }

    protected void addDescription(IndexItem indexItem, Content dl, boolean addModuleInfo) {
        SearchIndexItem si = indexItem.getSearchTag();
        if (si != null) {
            addDescription(si, dl);
        } else {
            si = new SearchIndexItem();
            si.setLabel(indexItem.getLabel());
            addElementDescription(indexItem, dl, si, addModuleInfo);
            searchItems.add(si);
        }
    }

    /**
     * Add one line summary comment for the element.
     *
     * @param indexItem the element to be documented
     * @param dlTree the content tree to which the description will be added
     * @param si the search index item
     * @param addModuleInfo whether to include module information
     */
    protected void addElementDescription(IndexItem indexItem, Content dlTree, SearchIndexItem si,
                                         boolean addModuleInfo) {
        Content dt;
        Element element = indexItem.getElement();
        String label = indexItem.getLabel();
        switch (element.getKind()) {
            case MODULE:
                dt = HtmlTree.DT(getModuleLink((ModuleElement)element, new StringContent(label)));
                si.setCategory(Category.MODULES);
                dt.add(" - ").add(contents.module_).add(" " + label);
                break;
            case PACKAGE:
                dt = HtmlTree.DT(getPackageLink((PackageElement)element, new StringContent(label)));
                if (configuration.showModules) {
                    si.setContainingModule(utils.getFullyQualifiedName(utils.containingModule(element)));
                }
                si.setCategory(Category.PACKAGES);
                dt.add(" - ").add(contents.package_).add(" " + label);
                break;
            case CLASS:
            case ENUM:
            case RECORD:
            case ANNOTATION_TYPE:
            case INTERFACE:
                dt = HtmlTree.DT(getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.INDEX, (TypeElement)element).strong(true)));
                si.setContainingPackage(utils.getPackageName(utils.containingPackage(element)));
                if (configuration.showModules && addModuleInfo) {
                    si.setContainingModule(utils.getFullyQualifiedName(utils.containingModule(element)));
                }
                si.setCategory(Category.TYPES);
                dt.add(" - ");
                addClassInfo((TypeElement)element, dt);
                break;
            default:
                TypeElement containingType = indexItem.getTypeElement();
                dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.memberNameLink,
                        getDocLink(LinkInfoImpl.Kind.INDEX, containingType, element, new StringContent(label))));
                si.setContainingPackage(utils.getPackageName(utils.containingPackage(element)));
                si.setContainingClass(utils.getSimpleName(containingType));
                if (configuration.showModules && addModuleInfo) {
                    si.setContainingModule(utils.getFullyQualifiedName(utils.containingModule(element)));
                }
                if (utils.isExecutableElement(element)) {
                    String url = HtmlTree.encodeURL(links.getName(getAnchor((ExecutableElement)element)));
                    if (!label.equals(url)) {
                        si.setUrl(url);
                    }
                }
                si.setCategory(Category.MEMBERS);
                dt.add(" - ");
                addMemberDesc(element, containingType, dt);
                break;
        }
        dlTree.add(dt);
        Content dd = new HtmlTree(TagName.DD);
        if (element.getKind() == ElementKind.MODULE || element.getKind() == ElementKind.PACKAGE) {
            addSummaryComment(element, dd);
        } else {
            addComment(element, dd);
        }
        dlTree.add(dd);
    }

    /**
     * Add the classkind (class, interface, exception), error of the class
     * passed.
     *
     * @param te the class being documented
     * @param contentTree the content tree to which the class info will be added
     */
    protected void addClassInfo(TypeElement te, Content contentTree) {
        contentTree.add(contents.getContent("doclet.in",
                utils.getTypeElementName(te, false),
                getPackageLink(utils.containingPackage(te),
                    utils.getPackageName(utils.containingPackage(te)))
                ));
    }

    protected void addDescription(SearchIndexItem sii, Content dlTree) {
        String siiPath = pathToRoot.isEmpty() ? "" : pathToRoot.getPath() + "/";
        siiPath += sii.getUrl();
        HtmlTree labelLink = HtmlTree.A(siiPath, new StringContent(sii.getLabel()));
        Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.searchTagLink, labelLink));
        dt.add(" - ");
        dt.add(contents.getContent("doclet.Search_tag_in", sii.getHolder()));
        dlTree.add(dt);
        Content dd = new HtmlTree(TagName.DD);
        if (sii.getDescription().isEmpty()) {
            dd.add(Entity.NO_BREAK_SPACE);
        } else {
            dd.add(sii.getDescription());
        }
        dlTree.add(dd);
    }

    /**
     * Add comment for each element in the index. If the element is deprecated
     * and it has a @deprecated tag, use that comment. Else if the containing
     * class for this element is deprecated, then add the word "Deprecated." at
     * the start and then print the normal comment.
     *
     * @param element Index element
     * @param contentTree the content tree to which the comment will be added
     */
    protected void addComment(Element element, Content contentTree) {
        List<? extends DocTree> tags;
        Content span = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(element));
        HtmlTree div = new HtmlTree(TagName.DIV);
        div.setStyle(HtmlStyle.deprecationBlock);
        if (utils.isDeprecated(element)) {
            div.add(span);
            tags = utils.getBlockTags(element, DocTree.Kind.DEPRECATED);
            if (!tags.isEmpty())
                addInlineDeprecatedComment(element, tags.get(0), div);
            contentTree.add(div);
        } else {
            TypeElement encl = utils.getEnclosingTypeElement(element);
            while (encl != null) {
                if (utils.isDeprecated(encl)) {
                    div.add(span);
                    contentTree.add(div);
                    break;
                }
                encl = utils.getEnclosingTypeElement(encl);
            }
            addSummaryComment(element, contentTree);
        }
    }

    /**
     * Add description about the Static Variable/Method/Constructor for a
     * member.
     *
     * @param member element for the member
     * @param enclosing the enclosing type element
     * @param contentTree the content tree to which the member description will be added
     */
    protected void addMemberDesc(Element member, TypeElement enclosing, Content contentTree) {
        String classdesc = utils.getTypeElementName(enclosing, true) + " ";
        if (utils.isField(member)) {
            Content resource = contents.getContent(utils.isStatic(member)
                    ? "doclet.Static_variable_in"
                    : "doclet.Variable_in", classdesc);
            contentTree.add(resource);
        } else if (utils.isConstructor(member)) {
            contentTree.add(
                    contents.getContent("doclet.Constructor_for", classdesc));
        } else if (utils.isMethod(member)) {
            Content resource = contents.getContent(utils.isStatic(member)
                    ? "doclet.Static_method_in"
                    : "doclet.Method_in", classdesc);
            contentTree.add(resource);
        }
        addPreQualifiedClassLink(LinkInfoImpl.Kind.INDEX, enclosing,
                false, contentTree);
    }

    /**
     * Generate a valid HTML name for member index page.
     *
     * @param unicode the string that needs to be converted to valid HTML name.
     * @return a valid HTML name string.
     */
    public String getNameForIndex(String unicode) {
        return "I:" + links.getName(unicode);
    }

    /**
     * @throws DocFileIOException if there is a problem creating any of the search index files
     */
    protected void createSearchIndexFiles() throws DocFileIOException {
        createSearchIndexFile(DocPaths.MODULE_SEARCH_INDEX_JS,
                              searchItems.itemsOfCategories(Category.MODULES),
                              "moduleSearchIndex");
        if (!configuration.packages.isEmpty()) {
            SearchIndexItem si = new SearchIndexItem();
            si.setCategory(Category.PACKAGES);
            si.setLabel(resources.getText("doclet.All_Packages"));
            si.setUrl(DocPaths.ALLPACKAGES_INDEX.getPath());
            searchItems.add(si);
        }
        createSearchIndexFile(DocPaths.PACKAGE_SEARCH_INDEX_JS,
                              searchItems.itemsOfCategories(Category.PACKAGES),
                              "packageSearchIndex");
        SearchIndexItem si = new SearchIndexItem();
        si.setCategory(Category.TYPES);
        si.setLabel(resources.getText("doclet.All_Classes"));
        si.setUrl(DocPaths.ALLCLASSES_INDEX.getPath());
        searchItems.add(si);
        createSearchIndexFile(DocPaths.TYPE_SEARCH_INDEX_JS,
                              searchItems.itemsOfCategories(Category.TYPES),
                              "typeSearchIndex");
        createSearchIndexFile(DocPaths.MEMBER_SEARCH_INDEX_JS,
                              searchItems.itemsOfCategories(Category.MEMBERS),
                              "memberSearchIndex");
        createSearchIndexFile(DocPaths.TAG_SEARCH_INDEX_JS,
                              searchItems.itemsOfCategories(Category.INDEX, Category.SYSTEM_PROPERTY),
                              "tagSearchIndex");
    }

    /**
     * Creates a search index file.
     *
     * @param searchIndexJS     the file for the JavaScript to be generated
     * @param searchIndex       the search index items
     * @param varName           the variable name to write in the JavaScript file
     * @throws DocFileIOException if there is a problem creating the search index file
     */
    protected void createSearchIndexFile(DocPath searchIndexJS,
                                         Stream<SearchIndexItem> searchIndex,
                                         String varName)
            throws DocFileIOException
    {
        // The file needs to be created even if there are no searchIndex items
        // File could be written straight-through, without an intermediate StringBuilder
        Iterator<SearchIndexItem> index = searchIndex.iterator();
        StringBuilder searchVar = new StringBuilder("[");
        boolean first = true;
        while (index.hasNext()) {
            SearchIndexItem item = index.next();
            if (first) {
                searchVar.append(item.toString());
                first = false;
            } else {
                searchVar.append(",").append(item.toString());
            }
        }
        searchVar.append("];");
        DocFile jsFile = DocFile.createFileForOutput(configuration, searchIndexJS);
        try (Writer wr = jsFile.openWriter()) {
            wr.write(varName);
            wr.write(" = ");
            wr.write(searchVar.toString());
            wr.write("updateSearchResults();");
        } catch (IOException ie) {
            throw new DocFileIOException(jsFile, DocFileIOException.Mode.WRITE, ie);
        }
    }

    private static Map<Character, List<SearchIndexItem>> buildSearchTagIndex(
            Stream<? extends SearchIndexItem> searchItems)
    {
        return searchItems.collect(Collectors.groupingBy(i -> keyCharacter(i.getLabel())));
    }

    protected static Character keyCharacter(String s) {
        return s.isEmpty() ? '*' : Character.toUpperCase(s.charAt(0));
    }
}
