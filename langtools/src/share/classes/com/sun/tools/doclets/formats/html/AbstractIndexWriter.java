/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
     * @param unicode Unicode for which member list information to be generated
     * @param memberlist List of members for the unicode character
     * @param contentTree the content tree to which the information will be added
     */
    protected void addContents(Character uc, List<? extends Doc> memberlist,
            Content contentTree) {
        String unicode = uc.toString();
        contentTree.addContent(getMarkerAnchorForIndex(unicode));
        Content headContent = new StringContent(unicode);
        Content heading = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING, false,
                HtmlStyle.title, headContent);
        contentTree.addContent(heading);
        int memberListSize = memberlist.size();
        // Display the list only if there are elements to be displayed.
        if (memberListSize > 0) {
            Content dl = new HtmlTree(HtmlTag.DL);
            for (int i = 0; i < memberListSize; i++) {
                Doc element = memberlist.get(i);
                if (element instanceof MemberDoc) {
                    addDescription((MemberDoc)element, dl);
                } else if (element instanceof ClassDoc) {
                    addDescription((ClassDoc)element, dl);
                } else if (element instanceof PackageDoc) {
                    addDescription((PackageDoc)element, dl);
                }
            }
            contentTree.addContent(dl);
        }
    }

    /**
     * Add one line summary comment for the package.
     *
     * @param pkg the package to be documented
     * @param dlTree the content tree to which the description will be added
     */
    protected void addDescription(PackageDoc pkg, Content dlTree) {
        Content link = getPackageLink(pkg, new StringContent(Util.getPackageName(pkg)));
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
    protected void addDescription(ClassDoc cd, Content dlTree) {
        Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.INDEX, cd).strong(true));
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
                Util.getTypeName(configuration, cd, false),
                getPackageLink(cd.containingPackage(),
                    Util.getPackageName(cd.containingPackage()))
                ));
    }

    /**
     * Add description for Class, Field, Method or Constructor.
     *
     * @param member MemberDoc for the member of the Class Kind
     * @param dlTree the content tree to which the description will be added
     */
    protected void addDescription(MemberDoc member, Content dlTree) {
        String name = (member instanceof ExecutableMemberDoc)?
            member.name() + ((ExecutableMemberDoc)member).flatSignature() :
            member.name();
        Content span = HtmlTree.SPAN(HtmlStyle.strong,
                getDocLink(LinkInfoImpl.Kind.INDEX, member, name));
        Content dt = HtmlTree.DT(span);
        dt.addContent(" - ");
        addMemberDesc(member, dt);
        dlTree.addContent(dt);
        Content dd = new HtmlTree(HtmlTag.DD);
        addComment(member, dd);
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
        Content span = HtmlTree.SPAN(HtmlStyle.strong, deprecatedPhrase);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.block);
        if (Util.isDeprecated(element)) {
            div.addContent(span);
            if ((tags = element.tags("deprecated")).length > 0)
                addInlineDeprecatedComment(element, tags[0], div);
            contentTree.addContent(div);
        } else {
            ClassDoc cont = element.containingClass();
            while (cont != null) {
                if (Util.isDeprecated(cont)) {
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
        String classdesc = Util.getTypeName(
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
}
