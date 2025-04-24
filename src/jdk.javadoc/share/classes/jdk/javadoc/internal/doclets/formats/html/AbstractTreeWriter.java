/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree.Hierarchy;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;


/**
 * Abstract class to print the class hierarchy page for all the Classes. This
 * is subclassed by {@link PackageTreeWriter} and {@link TreeWriter} to
 * generate the Package Tree and global Tree(for all the classes and packages)
 * pages.
 */
public abstract class AbstractTreeWriter extends HtmlDocletWriter {

    /**
     * The class and interface tree built by using {@link ClassTree}
     */
    protected final ClassTree classTree;

    /**
     * Constructor. This constructor will be used while generating global tree file "overview-tree.html".
     *
     * @param configuration  The current configuration
     * @param filename   File to be generated.
     * @param classTree  Tree built by {@link ClassTree}.
     */
    protected AbstractTreeWriter(HtmlConfiguration configuration,
                                 DocPath filename, ClassTree classTree) {
        super(configuration, filename);
        this.classTree = classTree;
    }

    /**
     * Add each level of the class tree. For each subclass or
     * subinterface indents the next level information.
     * Recurses itself to add subclasses info.
     *
     * @param parent the superclass or superinterface of the sset
     * @param collection  a collection of the subclasses at this level
     * @param hierarchy the hierarchy for which we are generating a tree
     * @param content the content to which the level information will be added
     */
    protected void addLevelInfo(TypeElement parent, Collection<TypeElement> collection,
                                Hierarchy hierarchy, Content content) {
        if (!collection.isEmpty()) {
            var ul = HtmlTree.UL();
            for (TypeElement local : collection) {
                var li = HtmlTree.LI(HtmlStyles.circle);
                addPartialInfo(local, li);
                addExtendsImplements(parent, local, li);
                addLevelInfo(local, hierarchy.subtypes(local), hierarchy, li);   // Recurse
                ul.add(li);
            }
            content.add(ul);
        }
    }

    /**
     * Adds a class or interface hierarchy with a given heading to given content.
     *
     * @param hierarchy the hierarchy to add
     * @param heading   the heading
     * @param content   the content to which to add the hierarchy
     */
    protected void addTree(Hierarchy hierarchy, String heading, Content content) {
        SortedSet<TypeElement> roots = hierarchy.roots();
        if (!roots.isEmpty()) {
            TypeElement firstTypeElement = roots.first();
            Content headingContent = contents.getContent(heading);
            var sectionHeading = HtmlTree.HEADING_TITLE(Headings.CONTENT_HEADING,
                    headingContent);
            var section = HtmlTree.SECTION(HtmlStyles.hierarchy, sectionHeading);
            addLevelInfo(!utils.isPlainInterface(firstTypeElement) ? firstTypeElement : null,
                    roots, hierarchy, section);
            content.add(section);
        }
    }

    /**
     * Add information regarding the classes which this class extends or implements.
     *
     * @param parent the parent class of the class being documented
     * @param typeElement the TypeElement under consideration
     * @param content the content to which the information will be added
     */
    protected void addExtendsImplements(TypeElement parent,
                                        TypeElement typeElement,
                                        Content content)
    {
        SortedSet<TypeElement> interfaces = new TreeSet<>(comparators.generalPurposeComparator());
        typeElement.getInterfaces().forEach(t -> interfaces.add(utils.asTypeElement(t)));
        if (interfaces.size() > (utils.isPlainInterface(typeElement) ? 1 : 0)) {
            boolean isFirst = true;
            for (TypeElement intf : interfaces) {
                if (parent != intf && utils.isVisible(intf)) {
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
                    addPreQualifiedClassLink(HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS, intf, content);
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
        addPreQualifiedStrongClassLink(HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS, typeElement, content);
    }
}
