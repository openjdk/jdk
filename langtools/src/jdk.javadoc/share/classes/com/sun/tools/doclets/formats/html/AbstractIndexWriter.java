/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.util.zip.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

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

    /**
     * This constructor will be used by {@link SplitIndexWriter}. Initializes
     * path to this file and relative path from this file.
     *
     * @param configuration  The current configuration
     * @param path       Path to the file which is getting generated.
     * @param indexbuilder Unicode based Index from {@link IndexBuilder}
     */
    protected AbstractIndexWriter(ConfigurationImpl configuration,
                                  DocPath path,
                                  IndexBuilder indexbuilder)
                                  throws IOException {
        super(configuration, path);
        this.indexbuilder = indexbuilder;
    }

    /**
     * Get the index label for navigation bar.
     *
     * @return a content tree for the tree label
     */
    protected Content getNavLinkIndex() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, indexLabel);
        return li;
    }

    /**
     * Add the member information for the unicode character along with the
     * list of the members.
     *
     * @param uc Unicode for which member list information to be generated
     * @param memberlist List of members for the unicode character
     * @param contentTree the content tree to which the information will be added
     */
    protected void addContents(Character uc, List<? extends Doc> memberlist,
            Content contentTree) {
        addHeading(uc, contentTree);
        int memberListSize = memberlist.size();
        // Display the list only if there are elements to be displayed.
        if (memberListSize > 0) {
            Content dl = new HtmlTree(HtmlTag.DL);
            for (Doc element : memberlist) {
                addDescription(dl, element);
            }
            contentTree.addContent(dl);
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
            contentTree.addContent(dl);
        }
    }

    protected void addContents(Character uc, List<? extends Doc> memberlist, List<SearchIndexItem> searchList,
            Content contentTree) {
        addHeading(uc, contentTree);
        int memberListSize = memberlist.size();
        int searchListSize = searchList.size();
        int i = 0;
        int j = 0;
        Content dl = new HtmlTree(HtmlTag.DL);
        while (i < memberListSize && j < searchListSize) {
            if (memberlist.get(i).name().compareTo(searchList.get(j).getLabel()) < 0) {
                addDescription(dl, memberlist.get(i));
                i++;
            } else if (memberlist.get(i).name().compareTo(searchList.get(j).getLabel()) > 0) {
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
        contentTree.addContent(dl);
    }

    protected void addHeading(Character uc, Content contentTree) {
        String unicode = uc.toString();
        contentTree.addContent(getMarkerAnchorForIndex(unicode));
        Content headContent = new StringContent(unicode);
        Content heading = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING, false,
                HtmlStyle.title, headContent);
        contentTree.addContent(heading);
    }

    protected void addDescription(Content dl, Doc element) {
        SearchIndexItem si = new SearchIndexItem();
        if (element instanceof MemberDoc) {
            addDescription((MemberDoc) element, dl, si);
            configuration.memberSearchIndex.add(si);
        } else if (element instanceof ClassDoc) {
            addDescription((ClassDoc) element, dl, si);
            configuration.typeSearchIndex.add(si);
        } else if (element instanceof PackageDoc) {
            addDescription((PackageDoc) element, dl, si);
            configuration.packageSearchIndex.add(si);
        }
    }
    /**
     * Add one line summary comment for the package.
     *
     * @param pkg the package to be documented
     * @param dlTree the content tree to which the description will be added
     */
    protected void addDescription(PackageDoc pkg, Content dlTree, SearchIndexItem si) {
        Content link = getPackageLink(pkg, new StringContent(utils.getPackageName(pkg)));
        si.setLabel(utils.getPackageName(pkg));
        si.setCategory(getResource("doclet.Packages").toString());
        Content dt = HtmlTree.DT(link);
        dt.addContent(" - ");
        dt.addContent(getResource("doclet.package"));
        dt.addContent(" " + pkg.name());
        dlTree.addContent(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        addSummaryComment(pkg, dd);
        dlTree.addContent(dd);
    }

    /**
     * Add one line summary comment for the class.
     *
     * @param cd the class being documented
     * @param dlTree the content tree to which the description will be added
     */
    protected void addDescription(ClassDoc cd, Content dlTree, SearchIndexItem si) {
        Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.INDEX, cd).strong(true));
        si.setContainingPackage(utils.getPackageName(cd.containingPackage()));
        si.setLabel(cd.typeName());
        si.setCategory(getResource("doclet.Types").toString());
        Content dt = HtmlTree.DT(link);
        dt.addContent(" - ");
        addClassInfo(cd, dt);
        dlTree.addContent(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        addComment(cd, dd);
        dlTree.addContent(dd);
    }

    /**
     * Add the classkind (class, interface, exception), error of the class
     * passed.
     *
     * @param cd the class being documented
     * @param contentTree the content tree to which the class info will be added
     */
    protected void addClassInfo(ClassDoc cd, Content contentTree) {
        contentTree.addContent(getResource("doclet.in",
                utils.getTypeName(configuration, cd, false),
                getPackageLink(cd.containingPackage(),
                    utils.getPackageName(cd.containingPackage()))
                ));
    }

    /**
     * Add description for Class, Field, Method or Constructor.
     *
     * @param member MemberDoc for the member of the Class Kind
     * @param dlTree the content tree to which the description will be added
     */
    protected void addDescription(MemberDoc member, Content dlTree, SearchIndexItem si) {
        String name = (member instanceof ExecutableMemberDoc)?
            member.name() + ((ExecutableMemberDoc)member).flatSignature() :
            member.name();
        si.setContainingPackage(utils.getPackageName((member.containingClass()).containingPackage()));
        si.setContainingClass((member.containingClass()).typeName());
        if (member instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc emd = (ExecutableMemberDoc)member;
            si.setLabel(member.name() + emd.flatSignature());
            if (!((emd.signature()).equals(emd.flatSignature()))) {
                si.setUrl(getName(getAnchor((ExecutableMemberDoc) member)));
            }
        } else {
            si.setLabel(member.name());
        }
        si.setCategory(getResource("doclet.Members").toString());
        Content span = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                getDocLink(LinkInfoImpl.Kind.INDEX, member, name));
        Content dt = HtmlTree.DT(span);
        dt.addContent(" - ");
        addMemberDesc(member, dt);
        dlTree.addContent(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        addComment(member, dd);
        dlTree.addContent(dd);
    }

    protected void addDescription(SearchIndexItem sii, Content dlTree) {
        String path = pathToRoot.isEmpty() ? "" : pathToRoot.getPath() + "/";
        path += sii.getUrl();
        HtmlTree labelLink = HtmlTree.A(path, new StringContent(sii.getLabel()));
        Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.searchTagLink, labelLink));
        dt.addContent(" - ");
        dt.addContent(getResource("doclet.Search_tag_in", sii.getHolder()));
        dlTree.addContent(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        if (sii.getDescription().isEmpty()) {
            dd.addContent(getSpace());
        } else {
            dd.addContent(sii.getDescription());
        }
        dlTree.addContent(dd);
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
    protected void addComment(ProgramElementDoc element, Content contentTree) {
        Tag[] tags;
        Content span = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.block);
        if (utils.isDeprecated(element)) {
            div.addContent(span);
            if ((tags = element.tags("deprecated")).length > 0)
                addInlineDeprecatedComment(element, tags[0], div);
            contentTree.addContent(div);
        } else {
            ClassDoc cont = element.containingClass();
            while (cont != null) {
                if (utils.isDeprecated(cont)) {
                    div.addContent(span);
                    contentTree.addContent(div);
                    break;
                }
                cont = cont.containingClass();
            }
            addSummaryComment(element, contentTree);
        }
    }

    /**
     * Add description about the Static Varible/Method/Constructor for a
     * member.
     *
     * @param member MemberDoc for the member within the Class Kind
     * @param contentTree the content tree to which the member description will be added
     */
    protected void addMemberDesc(MemberDoc member, Content contentTree) {
        ClassDoc containing = member.containingClass();
        String classdesc = utils.getTypeName(
                configuration, containing, true) + " ";
        if (member.isField()) {
            if (member.isStatic()) {
                contentTree.addContent(
                        getResource("doclet.Static_variable_in", classdesc));
            } else {
                contentTree.addContent(
                        getResource("doclet.Variable_in", classdesc));
            }
        } else if (member.isConstructor()) {
            contentTree.addContent(
                    getResource("doclet.Constructor_for", classdesc));
        } else if (member.isMethod()) {
            if (member.isStatic()) {
                contentTree.addContent(
                        getResource("doclet.Static_method_in", classdesc));
            } else {
                contentTree.addContent(
                        getResource("doclet.Method_in", classdesc));
            }
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
        return getMarkerAnchor(getNameForIndex(anchorNameForIndex), null);
    }

    /**
     * Generate a valid HTML name for member index page.
     *
     * @param unicode the string that needs to be converted to valid HTML name.
     * @return a valid HTML name string.
     */
    public String getNameForIndex(String unicode) {
        return "I:" + getName(unicode);
    }

    protected void createSearchIndexFiles() {
        createSearchIndexFile(DocPaths.PACKAGE_SEARCH_INDEX_JSON, DocPaths.PACKAGE_SEARCH_INDEX_ZIP,
                configuration.packageSearchIndex);
        createSearchIndexFile(DocPaths.TYPE_SEARCH_INDEX_JSON, DocPaths.TYPE_SEARCH_INDEX_ZIP,
                configuration.typeSearchIndex);
        createSearchIndexFile(DocPaths.MEMBER_SEARCH_INDEX_JSON, DocPaths.MEMBER_SEARCH_INDEX_ZIP,
                configuration.memberSearchIndex);
        createSearchIndexFile(DocPaths.TAG_SEARCH_INDEX_JSON, DocPaths.TAG_SEARCH_INDEX_ZIP,
                configuration.tagSearchIndex);
    }

    protected void createSearchIndexFile(DocPath searchIndexFile, DocPath searchIndexZip,
            List<SearchIndexItem> searchIndex) {
        if (!searchIndex.isEmpty()) {
            try {
                StringBuilder searchVar = new StringBuilder("[");
                boolean first = true;
                DocFile searchFile = DocFile.createFileForOutput(configuration, searchIndexFile);
                Path p = Paths.get(searchFile.getPath());
                for (SearchIndexItem item : searchIndex) {
                    if (first) {
                        searchVar.append(item.toString());
                        first = false;
                    } else {
                        searchVar.append(",").append(item.toString());
                    }
                }
                searchVar.append("]");
                Files.write(p, searchVar.toString().getBytes());
                DocFile zipFile = DocFile.createFileForOutput(configuration, searchIndexZip);
                try (FileOutputStream fos = new FileOutputStream(zipFile.getPath());
                        ZipOutputStream zos = new ZipOutputStream(fos)) {
                    zipFile(searchFile.getPath(), searchIndexFile, zos);
                }
                Files.delete(p);
            } catch (IOException ie) {
                throw new DocletAbortException(ie);
            }
        }
    }

    protected void zipFile(String inputFile, DocPath file, ZipOutputStream zos) {
        try {
            try {
                ZipEntry ze = new ZipEntry(file.getPath());
                zos.putNextEntry(ze);
                try (FileInputStream fis = new FileInputStream(new File(inputFile))) {
                    byte[] buf = new byte[2048];
                    int len = fis.read(buf);
                    while (len > 0) {
                        zos.write(buf, 0, len);
                        len = fis.read(buf);
                    }
                }
            } finally {
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new DocletAbortException(e);
        }
    }
}
