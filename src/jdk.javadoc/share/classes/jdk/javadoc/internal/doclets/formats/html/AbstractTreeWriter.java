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

import java.util.*;

import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;


/**
 * Abstract class to print the class hierarchy page for all the Classes. This
 * is sub-classed by {@link PackageTreeWriter} and {@link TreeWriter} to
 * generate the Package Tree and global Tree(for all the classes and packages)
 * pages.
 */
public abstract class AbstractTreeWriter extends HtmlDocletWriter {

    /**
     * The class and interface tree built by using {@link ClassTree}
     */
    protected final ClassTree classtree;

    /**
     * Constructor initializes classtree variable. This constructor will be used
     * while generating global tree file "overview-tree.html".
     *
     * @param configuration  The current configuration
     * @param filename   File to be generated.
     * @param classtree  Tree built by {@link ClassTree}.
     */
    protected AbstractTreeWriter(HtmlConfiguration configuration,
                                 DocPath filename, ClassTree classtree) {
        super(configuration, filename);
        this.classtree = classtree;
    }

    /**
     * Add each level of the class tree. For each sub-class or
     * sub-interface indents the next level information.
     * Recurses itself to add sub-classes info.
     *
     * @param parent the superclass or superinterface of the sset
     * @param collection  a collection of the sub-classes at this level
     * @param isEnum true if we are generating a tree for enums
     * @param content the content to which the level information will be added
     */
    protected void addLevelInfo(TypeElement parent, Collection<TypeElement> collection,
            boolean isEnum, Content content) {
        if (!collection.isEmpty()) {
            var ul = new HtmlTree(TagName.UL);
            for (TypeElement local : collection) {
                var li = new HtmlTree(TagName.LI);
                li.setStyle(HtmlStyle.circle);
                addPartialInfo(local, li);
                addExtendsImplements(parent, local, li);
                addLevelInfo(local, classtree.directSubClasses(local, isEnum),
                             isEnum, li);   // Recurse
                ul.add(li);
            }
            content.add(ul);
        }
    }

    /**
     * Add the heading for the tree depending upon tree type if it's a
     * Class Tree or Interface tree.
     *
     * @param sset classes which are at the most base level, all the
     * other classes in this run will derive from these classes
     * @param heading heading for the tree
     * @param content the content to which the tree will be added
     */
    protected void addTree(SortedSet<TypeElement> sset, String heading, Content content) {
        addTree(sset, heading, content, false);
    }

    protected void addTree(SortedSet<TypeElement> sset, String heading,
                           Content content, boolean isEnums) {
        if (!sset.isEmpty()) {
            TypeElement firstTypeElement = sset.first();
            Content headingContent = contents.getContent(heading);
            var sectionHeading = HtmlTree.HEADING_TITLE(Headings.CONTENT_HEADING,
                    headingContent);
            var section = HtmlTree.SECTION(HtmlStyle.hierarchy, sectionHeading);
            addLevelInfo(!utils.isPlainInterface(firstTypeElement) ? firstTypeElement : null,
                    sset, isEnums, section);
            content.add(section);
        }
    }

    /**
     * Add information regarding the classes which this class extends or
     * implements.
     *
     * @param parent the parent class of the class being documented
     * @param typeElement the TypeElement under consideration
     * @param content the content to which the information will be added
     */
    protected void addExtendsImplements(TypeElement parent,
                                        TypeElement typeElement,
                                        Content content)
    {
        SortedSet<TypeElement> interfaces = new TreeSet<>(comparators.makeGeneralPurposeComparator());
        typeElement.getInterfaces().forEach(t -> interfaces.add(utils.asTypeElement(t)));
        if (interfaces.size() > (utils.isPlainInterface(typeElement) ? 1 : 0)) {
            boolean isFirst = true;
            for (TypeElement intf : interfaces) {
                if (parent != intf) {
                    if (utils.isPublic(intf) || utils.isLinkable(intf)) {
                        if (isFirst) {
                            isFirst = false;
                            if (utils.isPlainInterface(typeElement)) {
                                content.add(" (");
                                content.add(contents.also);
                                content.add(" extends ");
                            } else {
                                content.add(" (implements ");
                            }
                        } else {
                            content.add(", ");
                        }
                        addPreQualifiedClassLink(HtmlLinkInfo.Kind.TREE, intf, content);
                    }
                }
            }
            if (!isFirst) {
                content.add(")");
            }
        }
    }

    /**
     * Add information about the class kind, if it's a "class" or "interface".
     *
     * @param typeElement the class being documented
     * @param content the content to which the information will be added
     */
    protected void addPartialInfo(TypeElement typeElement, Content content) {
        addPreQualifiedStrongClassLink(HtmlLinkInfo.Kind.TREE, typeElement, content);
    }
}
