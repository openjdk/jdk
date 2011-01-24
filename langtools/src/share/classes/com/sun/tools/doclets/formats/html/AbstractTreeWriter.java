/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.javadoc.*;

/**
 * Abstract class to print the class hierarchy page for all the Classes. This
 * is sub-classed by {@link PackageTreeWriter} and {@link TreeWriter} to
 * generate the Package Tree and global Tree(for all the classes and packages)
 * pages.
 *
 * @author Atul M Dambalkar
 */
public abstract class AbstractTreeWriter extends HtmlDocletWriter {

    /**
     * The class and interface tree built by using {@link ClassTree}
     */
    protected final ClassTree classtree;

    private static final String LI_CIRCLE  = "circle";

    /**
     * Constructor initilises classtree variable. This constructor will be used
     * while generating global tree file "overview-tree.html".
     *
     * @param filename   File to be generated.
     * @param classtree  Tree built by {@link ClassTree}.
     * @throws IOException
     * @throws DocletAbortException
     */
    protected AbstractTreeWriter(ConfigurationImpl configuration,
                                 String filename, ClassTree classtree)
                                 throws IOException {
        super(configuration, filename);
        this.classtree = classtree;
    }

    /**
     * Create appropriate directory for the package and also initilise the
     * relative path from this generated file to the current or
     * the destination directory. This constructor will be used while
     * generating "package tree" file.
     *
     * @param path Directories in this path will be created if they are not
     * already there.
     * @param filename Name of the package tree file to be generated.
     * @param classtree The tree built using {@link ClassTree}.
     * for the package pkg.
     * @param pkg PackageDoc for which tree file will be generated.
     * @throws IOException
     * @throws DocletAbortException
     */
    protected AbstractTreeWriter(ConfigurationImpl configuration,
                                 String path, String filename,
                                 ClassTree classtree, PackageDoc pkg)
                                 throws IOException {
        super(configuration,
              path, filename, DirectoryManager.getRelativePath(pkg.name()));
        this.classtree = classtree;
    }

    /**
     * Add each level of the class tree. For each sub-class or
     * sub-interface indents the next level information.
     * Recurses itself to add subclasses info.
     *
     * @param parent the superclass or superinterface of the list
     * @param list list of the sub-classes at this level
     * @param isEnum true if we are generating a tree for enums
     * @param contentTree the content tree to which the level information will be added
     */
    protected void addLevelInfo(ClassDoc parent, List<ClassDoc> list,
            boolean isEnum, Content contentTree) {
        int size = list.size();
        if (size > 0) {
            Content ul = new HtmlTree(HtmlTag.UL);
            for (int i = 0; i < size; i++) {
                ClassDoc local = list.get(i);
                HtmlTree li = new HtmlTree(HtmlTag.LI);
                li.addAttr(HtmlAttr.TYPE, LI_CIRCLE);
                addPartialInfo(local, li);
                addExtendsImplements(parent, local, li);
                addLevelInfo(local, classtree.subs(local, isEnum),
                        isEnum, li);   // Recurse
                ul.addContent(li);
            }
            contentTree.addContent(ul);
        }
    }

    /**
     * Add the heading for the tree depending upon tree type if it's a
     * Class Tree or Interface tree.
     *
     * @param list List of classes which are at the most base level, all the
     * other classes in this run will derive from these classes
     * @param heading heading for the tree
     * @param div the content tree to which the tree will be added
     */
    protected void addTree(List<ClassDoc> list, String heading, Content div) {
        if (list.size() > 0) {
            ClassDoc firstClassDoc = list.get(0);
            Content headingContent = getResource(heading);
            div.addContent(HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING, true,
                    headingContent));
            addLevelInfo(!firstClassDoc.isInterface()? firstClassDoc : null,
                    list, list == classtree.baseEnums(), div);
        }
    }

    /**
     * Add information regarding the classes which this class extends or
     * implements.
     *
     * @param parent the parent class of the class being documented
     * @param cd the classdoc under consideration
     * @param contentTree the content tree to which the information will be added
     */
    protected void addExtendsImplements(ClassDoc parent, ClassDoc cd,
            Content contentTree) {
        ClassDoc[] interfaces = cd.interfaces();
        if (interfaces.length > (cd.isInterface()? 1 : 0)) {
            Arrays.sort(interfaces);
            int counter = 0;
            for (int i = 0; i < interfaces.length; i++) {
                if (parent != interfaces[i]) {
                    if (! (interfaces[i].isPublic() ||
                            Util.isLinkable(interfaces[i], configuration()))) {
                        continue;
                    }
                    if (counter == 0) {
                        if (cd.isInterface()) {
                            contentTree.addContent(" (");
                            contentTree.addContent(getResource("doclet.also"));
                            contentTree.addContent(" extends ");
                        } else {
                            contentTree.addContent(" (implements ");
                        }
                    } else {
                        contentTree.addContent(", ");
                    }
                    addPreQualifiedClassLink(LinkInfoImpl.CONTEXT_TREE,
                            interfaces[i], contentTree);
                    counter++;
                }
            }
            if (counter > 0) {
                contentTree.addContent(")");
            }
        }
    }

    /**
     * Add information about the class kind, if it's a "class" or "interface".
     *
     * @param cd the class being documented
     * @param contentTree the content tree to which the information will be added
     */
    protected void addPartialInfo(ClassDoc cd, Content contentTree) {
        addPreQualifiedStrongClassLink(LinkInfoImpl.CONTEXT_TREE, cd, contentTree);
    }

    /**
     * Get the tree label for the navigation bar.
     *
     * @return a content tree for the tree label
     */
    protected Content getNavLinkTree() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, treeLabel);
        return li;
    }
}
