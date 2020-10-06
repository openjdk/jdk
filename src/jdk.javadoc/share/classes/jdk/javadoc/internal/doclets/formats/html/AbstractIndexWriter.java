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

import java.util.List;
import java.util.SortedSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
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
 * @see IndexBuilder
 */
public class AbstractIndexWriter extends HtmlDocletWriter {

    protected final IndexBuilder mainIndex;

    protected final Navigation navBar;

    /**
     * Initializes the common data for writers that can generate index files
     * based on the information in {@code configuration.mainIndex}.
     *
     * @param configuration  the current configuration
     * @param path           path to the file which is getting generated.
     */
    protected AbstractIndexWriter(HtmlConfiguration configuration,
                                  DocPath path) {
        super(configuration, path);
        this.mainIndex = configuration.mainIndex;
        this.navBar = new Navigation(null, configuration, PageMode.INDEX, path);
    }

    protected void addContents(Character uc, SortedSet<IndexItem> memberlist,
            Content contentTree) {
        addHeading(uc, contentTree);

        HtmlTree dl = HtmlTree.DL(HtmlStyle.index);
        for (IndexItem item : memberlist) {
            addDescription(item, dl);
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

    protected void addDescription(IndexItem indexItem, Content dl) {
        if (indexItem.isTagItem()) {
            addTagDescription(indexItem, dl);
        } else if (indexItem.isElementItem()) {
            addElementDescription(indexItem, dl);
        }
    }

    /**
     * Add one line summary comment for the element.
     *
     * @param item the element to be documented
     * @param dlTree the content tree to which the description will be added
     */
    protected void addElementDescription(IndexItem item, Content dlTree) {
        Content dt;
        Element element = item.getElement();
        String label = item.getLabel();
        switch (element.getKind()) {
            case MODULE:
                dt = HtmlTree.DT(getModuleLink((ModuleElement) element, new StringContent(label)));
                dt.add(" - ").add(contents.module_).add(" " + label);
                break;

            case PACKAGE:
                dt = HtmlTree.DT(getPackageLink((PackageElement) element, new StringContent(label)));
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
                dt = HtmlTree.DT(getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.INDEX, (TypeElement) element).strong(true)));
                dt.add(" - ");
                addClassInfo((TypeElement) element, dt);
                break;

            case CONSTRUCTOR:
            case METHOD:
            case FIELD:
            case ENUM_CONSTANT:
                TypeElement containingType = item.getContainingTypeElement();
                dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.memberNameLink,
                        getDocLink(LinkInfoImpl.Kind.INDEX, containingType, element, new StringContent(label))));
                dt.add(" - ");
                addMemberDesc(element, containingType, dt);
                break;

            default:
                throw new Error();
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

    protected void addTagDescription(IndexItem item, Content dlTree) {
        String itemPath = pathToRoot.isEmpty() ? "" : pathToRoot.getPath() + "/";
        itemPath += item.getUrl();
        HtmlTree labelLink = HtmlTree.A(itemPath, new StringContent(item.getLabel()));
        Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.searchTagLink, labelLink));
        dt.add(" - ");
        dt.add(contents.getContent("doclet.Search_tag_in", item.getHolder()));
        dlTree.add(dt);
        Content dd = new HtmlTree(TagName.DD);
        if (item.getDescription().isEmpty()) {
            dd.add(Entity.NO_BREAK_SPACE);
        } else {
            dd.add(item.getDescription());
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

}
