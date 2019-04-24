/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor9;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;

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
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class AbstractIndexWriter extends HtmlDocletWriter {

    /**
     * The index of all the members with unicode character.
     */
    protected IndexBuilder indexbuilder;

    protected Navigation navBar;

    /**
     * This constructor will be used by {@link SplitIndexWriter}. Initializes
     * path to this file and relative path from this file.
     *
     * @param configuration  The current configuration
     * @param path       Path to the file which is getting generated.
     * @param indexbuilder Unicode based Index from {@link IndexBuilder}
     */
    protected AbstractIndexWriter(HtmlConfiguration configuration,
                                  DocPath path,
                                  IndexBuilder indexbuilder) {
        super(configuration, path);
        this.indexbuilder = indexbuilder;
        this.navBar = new Navigation(null, configuration, fixedNavDiv, PageMode.INDEX, path);
    }

    /**
     * Add the member information for the unicode character along with the
     * list of the members.
     *
     * @param uc Unicode for which member list information to be generated
     * @param memberlist List of members for the unicode character
     * @param contentTree the content tree to which the information will be added
     */
    protected void addContents(Character uc, Collection<? extends Element> memberlist,
            Content contentTree) {
        addHeading(uc, contentTree);
        // Display the list only if there are elements to be displayed.
        if (!memberlist.isEmpty()) {
            Content dl = new HtmlTree(HtmlTag.DL);
            for (Element element : memberlist) {
                addDescription(dl, element);
            }
            contentTree.add(dl);
        }
    }

    protected void addSearchContents(Character uc, List<SearchIndexItem> searchList,
            Content contentTree) {
        addHeading(uc, contentTree);
        // Display the list only if there are elements to be displayed.
        if (!searchList.isEmpty()) {
            Content dl = new HtmlTree(HtmlTag.DL);
            for (SearchIndexItem sii : searchList) {
                addDescription(sii, dl);
            }
            contentTree.add(dl);
        }
    }

    protected void addContents(Character uc, List<? extends Element> memberlist,
            List<SearchIndexItem> searchList, Content contentTree) {
        addHeading(uc, contentTree);
        int memberListSize = memberlist.size();
        int searchListSize = searchList.size();
        int i = 0;
        int j = 0;
        Content dl = new HtmlTree(HtmlTag.DL);
        while (i < memberListSize && j < searchListSize) {
            Element elem = memberlist.get(i);
            String name = (utils.isModule(elem))
                    ? utils.getFullyQualifiedName(elem) : utils.getSimpleName(elem);
            if (name.compareTo(searchList.get(j).getLabel()) < 0) {
                addDescription(dl, memberlist.get(i));
                i++;
            } else if (name.compareTo(searchList.get(j).getLabel()) > 0) {
                addDescription(searchList.get(j), dl);
                j++;
            } else {
                addDescription(dl, memberlist.get(i));
                addDescription(searchList.get(j), dl);
                j++;
                i++;
            }
        }
        if (i >= memberListSize) {
            while (j < searchListSize) {
                addDescription(searchList.get(j), dl);
                j++;
            }
        }
        if (j >= searchListSize) {
            while (i < memberListSize) {
                addDescription(dl, memberlist.get(i));
                i++;
            }
        }
        contentTree.add(dl);
    }

    protected void addHeading(Character uc, Content contentTree) {
        String unicode = uc.toString();
        contentTree.add(getMarkerAnchorForIndex(unicode));
        Content headContent = new StringContent(unicode);
        Content heading = HtmlTree.HEADING(Headings.CONTENT_HEADING, false,
                HtmlStyle.title, headContent);
        contentTree.add(heading);
    }

    protected void addDescription(Content dl, Element element) {
        SearchIndexItem si = new SearchIndexItem();
        new SimpleElementVisitor9<Void, Void>() {

            @Override
            public Void visitModule(ModuleElement e, Void p) {
                if (configuration.showModules) {
                    addDescription(e, dl, si);
                    configuration.moduleSearchIndex.add(si);
                }
                return null;
            }

            @Override
            public Void visitPackage(PackageElement e, Void p) {
                addDescription(e, dl, si);
                configuration.packageSearchIndex.add(si);
                return null;
            }

            @Override
            public Void visitType(TypeElement e, Void p) {
                addDescription(e, dl, si);
                configuration.typeSearchIndex.add(si);
                return null;
            }

            @Override
            protected Void defaultAction(Element e, Void p) {
                addDescription(e, dl, si);
                configuration.memberSearchIndex.add(si);
                return null;
            }

        }.visit(element);
    }

    /**
     * Add one line summary comment for the module.
     *
     * @param mdle the module to be documented
     * @param dlTree the content tree to which the description will be added
     * @param si the search index item
     */
    protected void addDescription(ModuleElement mdle, Content dlTree, SearchIndexItem si) {
        String moduleName = utils.getFullyQualifiedName(mdle);
        Content link = getModuleLink(mdle, new StringContent(moduleName));
        si.setLabel(moduleName);
        si.setCategory(SearchIndexItem.Category.MODULES);
        Content dt = HtmlTree.DT(link);
        dt.add(" - ");
        dt.add(contents.module_);
        dt.add(" " + moduleName);
        dlTree.add(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        addSummaryComment(mdle, dd);
        dlTree.add(dd);
    }

    /**
     * Add one line summary comment for the package.
     *
     * @param pkg the package to be documented
     * @param dlTree the content tree to which the description will be added
     * @param si the search index item to be updated
     */
    protected void addDescription(PackageElement pkg, Content dlTree, SearchIndexItem si) {
        Content link = getPackageLink(pkg, new StringContent(utils.getPackageName(pkg)));
        if (configuration.showModules) {
            si.setContainingModule(utils.getFullyQualifiedName(utils.containingModule(pkg)));
        }
        si.setLabel(utils.getPackageName(pkg));
        si.setCategory(SearchIndexItem.Category.PACKAGES);
        Content dt = HtmlTree.DT(link);
        dt.add(" - ");
        dt.add(contents.package_);
        dt.add(" " + utils.getPackageName(pkg));
        dlTree.add(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        addSummaryComment(pkg, dd);
        dlTree.add(dd);
    }

    /**
     * Add one line summary comment for the class.
     *
     * @param typeElement the class being documented
     * @param dlTree the content tree to which the description will be added
     * @param si the search index item to be updated
     */
    protected void addDescription(TypeElement typeElement, Content dlTree, SearchIndexItem si) {
        Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.INDEX, typeElement).strong(true));
        si.setContainingPackage(utils.getPackageName(utils.containingPackage(typeElement)));
        si.setLabel(utils.getSimpleName(typeElement));
        si.setCategory(SearchIndexItem.Category.TYPES);
        Content dt = HtmlTree.DT(link);
        dt.add(" - ");
        addClassInfo(typeElement, dt);
        dlTree.add(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        addComment(typeElement, dd);
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

    /**
     * Add description for Class, Field, Method or Constructor.
     *
     * @param member the member of the Class Kind
     * @param dlTree the content tree to which the description will be added
     * @param si search index item
     */
    protected void addDescription(Element member, Content dlTree, SearchIndexItem si) {

        si.setContainingPackage(utils.getPackageName(utils.containingPackage(member)));
        si.setContainingClass(utils.getSimpleName(utils.getEnclosingTypeElement(member)));
        String name = utils.getSimpleName(member);
        if (utils.isExecutableElement(member)) {
            ExecutableElement ee = (ExecutableElement)member;
            name = name + utils.flatSignature(ee);
            si.setLabel(name);
            String url = HtmlTree.encodeURL(links.getName(getAnchor(ee)));
            if (!name.equals(url)) {
                si.setUrl(url);
            }
        }  else {
            si.setLabel(name);
        }
        si.setCategory(SearchIndexItem.Category.MEMBERS);
        Content span = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                getDocLink(LinkInfoImpl.Kind.INDEX, member, name));
        Content dt = HtmlTree.DT(span);
        dt.add(" - ");
        addMemberDesc(member, dt);
        dlTree.add(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        addComment(member, dd);
        dlTree.add(dd);
    }

    protected void addDescription(SearchIndexItem sii, Content dlTree) {
        String siiPath = pathToRoot.isEmpty() ? "" : pathToRoot.getPath() + "/";
        siiPath += sii.getUrl();
        HtmlTree labelLink = HtmlTree.A(siiPath, new StringContent(sii.getLabel()));
        Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.searchTagLink, labelLink));
        dt.add(" - ");
        dt.add(contents.getContent("doclet.Search_tag_in", sii.getHolder()));
        dlTree.add(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
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
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
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
     * @param member MemberDoc for the member within the Class Kind
     * @param contentTree the content tree to which the member description will be added
     */
    protected void addMemberDesc(Element member, Content contentTree) {
        TypeElement containing = utils.getEnclosingTypeElement(member);
        String classdesc = utils.getTypeElementName(containing, true) + " ";
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
        addPreQualifiedClassLink(LinkInfoImpl.Kind.INDEX, containing,
                false, contentTree);
    }

    /**
     * Get the marker anchor which will be added to the index documentation tree.
     *
     * @param anchorNameForIndex the anchor name attribute for index page
     * @return a content tree for the marker anchor
     */
    public Content getMarkerAnchorForIndex(String anchorNameForIndex) {
        return links.createAnchor(getNameForIndex(anchorNameForIndex), null);
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
        if (configuration.showModules) {
            createSearchIndexFile(DocPaths.MODULE_SEARCH_INDEX_JSON, DocPaths.MODULE_SEARCH_INDEX_ZIP,
                    DocPaths.MODULE_SEARCH_INDEX_JS, configuration.moduleSearchIndex, "moduleSearchIndex");
        }
        if (!configuration.packages.isEmpty()) {
            SearchIndexItem si = new SearchIndexItem();
            si.setCategory(SearchIndexItem.Category.PACKAGES);
            si.setLabel(resources.getText("doclet.All_Packages"));
            si.setUrl(DocPaths.ALLPACKAGES_INDEX.getPath());
            configuration.packageSearchIndex.add(si);
        }
        createSearchIndexFile(DocPaths.PACKAGE_SEARCH_INDEX_JSON, DocPaths.PACKAGE_SEARCH_INDEX_ZIP,
                DocPaths.PACKAGE_SEARCH_INDEX_JS, configuration.packageSearchIndex, "packageSearchIndex");
        SearchIndexItem si = new SearchIndexItem();
        si.setCategory(SearchIndexItem.Category.TYPES);
        si.setLabel(resources.getText("doclet.All_Classes"));
        si.setUrl(DocPaths.ALLCLASSES_INDEX.getPath());
        configuration.typeSearchIndex.add(si);
        createSearchIndexFile(DocPaths.TYPE_SEARCH_INDEX_JSON, DocPaths.TYPE_SEARCH_INDEX_ZIP,
                DocPaths.TYPE_SEARCH_INDEX_JS, configuration.typeSearchIndex, "typeSearchIndex");
        createSearchIndexFile(DocPaths.MEMBER_SEARCH_INDEX_JSON, DocPaths.MEMBER_SEARCH_INDEX_ZIP,
                DocPaths.MEMBER_SEARCH_INDEX_JS, configuration.memberSearchIndex, "memberSearchIndex");
        createSearchIndexFile(DocPaths.TAG_SEARCH_INDEX_JSON, DocPaths.TAG_SEARCH_INDEX_ZIP,
                DocPaths.TAG_SEARCH_INDEX_JS, configuration.tagSearchIndex, "tagSearchIndex");
    }

    /**
     * Creates a search index file.
     *
     * @param searchIndexFile   the file to be generated
     * @param searchIndexZip    the zip file to be generated
     * @param searchIndexJS     the file for the JavaScript to be generated
     * @param searchIndex       the search index items
     * @param varName           the variable name to write in the JavaScript file
     * @throws DocFileIOException if there is a problem creating the search index file
     */
    protected void createSearchIndexFile(DocPath searchIndexFile, DocPath searchIndexZip,
            DocPath searchIndexJS, Collection<SearchIndexItem> searchIndex, String varName) throws DocFileIOException {
        if (!searchIndex.isEmpty()) {
            StringBuilder searchVar = new StringBuilder("[");
            boolean first = true;
            for (SearchIndexItem item : searchIndex) {
                if (first) {
                    searchVar.append(item.toString());
                    first = false;
                } else {
                    searchVar.append(",").append(item.toString());
                }
            }
            searchVar.append("]");
            DocFile jsFile = DocFile.createFileForOutput(configuration, searchIndexJS);
            try (Writer wr = jsFile.openWriter()) {
                wr.write(varName);
                wr.write(" = ");
                wr.write(searchVar.toString());
            } catch (IOException ie) {
                throw new DocFileIOException(jsFile, DocFileIOException.Mode.WRITE, ie);
            }

            DocFile zipFile = DocFile.createFileForOutput(configuration, searchIndexZip);
            try (OutputStream fos = zipFile.openOutputStream();
                    ZipOutputStream zos = new ZipOutputStream(fos)) {
                try {
                    ZipEntry ze = new ZipEntry(searchIndexFile.getPath());
                    zos.putNextEntry(ze);
                    zos.write(searchVar.toString().getBytes());
                } finally {
                    zos.closeEntry();
                }
            } catch (IOException ie) {
                throw new DocFileIOException(zipFile, DocFileIOException.Mode.WRITE, ie);
            }
        }
    }
}
