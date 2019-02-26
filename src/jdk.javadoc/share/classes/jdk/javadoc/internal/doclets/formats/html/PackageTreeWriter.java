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

import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;


/**
 * Class to generate Tree page for a package. The name of the file generated is
 * "package-tree.html" and it is generated in the respective package directory.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class PackageTreeWriter extends AbstractTreeWriter {

    /**
     * Package for which tree is to be generated.
     */
    protected PackageElement packageElement;

    private final Navigation navBar;

    /**
     * Constructor.
     * @param configuration the configuration
     * @param path the docpath to generate files into
     * @param packageElement the current package
     */
    public PackageTreeWriter(HtmlConfiguration configuration, DocPath path, PackageElement packageElement) {
        super(configuration, path,
              new ClassTree(configuration.typeElementCatalog.allClasses(packageElement), configuration));
        this.packageElement = packageElement;
        this.navBar = new Navigation(packageElement, configuration, fixedNavDiv, PageMode.TREE, path);
    }

    /**
     * Construct a PackageTreeWriter object and then use it to generate the
     * package tree page.
     *
     * @param configuration the configuration for this run.
     * @param pkg      Package for which tree file is to be generated.
     * @param noDeprecated  If true, do not generate any information for
     * deprecated classe or interfaces.
     * @throws DocFileIOException if there is a problem generating the package tree page
     */
    public static void generate(HtmlConfiguration configuration,
                                PackageElement pkg, boolean noDeprecated)
            throws DocFileIOException {
        DocPath path = configuration.docPaths.forPackage(pkg).resolve(DocPaths.PACKAGE_TREE);
        PackageTreeWriter packgen = new PackageTreeWriter(configuration, path, pkg);
        packgen.generatePackageTreeFile();
    }

    /**
     * Generate a separate tree file for each package.
     * @throws DocFileIOException if there is a problem generating the package tree file
     */
    protected void generatePackageTreeFile() throws DocFileIOException {
        HtmlTree body = getPackageTreeHeader();
        HtmlTree mainTree = HtmlTree.MAIN();
        Content headContent = contents.getContent("doclet.Hierarchy_For_Package",
                utils.getPackageName(packageElement));
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, false,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        if (configuration.packages.size() > 1) {
            addLinkToMainTree(div);
        }
        mainTree.addContent(div);
        HtmlTree divTree = new HtmlTree(HtmlTag.DIV);
        divTree.setStyle(HtmlStyle.contentContainer);
        addTree(classtree.baseClasses(), "doclet.Class_Hierarchy", divTree);
        addTree(classtree.baseInterfaces(), "doclet.Interface_Hierarchy", divTree);
        addTree(classtree.baseAnnotationTypes(), "doclet.Annotation_Type_Hierarchy", divTree);
        addTree(classtree.baseEnums(), "doclet.Enum_Hierarchy", divTree, true);
        mainTree.addContent(divTree);
        body.addContent(mainTree);
        HtmlTree footer = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.addContent(navBar.getContent(false));
        addBottom(footer);
        body.addContent(footer);
        printHtmlDocument(null, getDescription("tree", packageElement), body);
    }

    /**
     * Get the package tree header.
     *
     * @return a content tree for the header
     */
    protected HtmlTree getPackageTreeHeader() {
        String packageName = packageElement.isUnnamed() ? "" : utils.getPackageName(packageElement);
        String title = packageName + " " + resources.getText("doclet.Window_Class_Hierarchy");
        HtmlTree bodyTree = getBody(true, getWindowTitle(title));
        HtmlTree htmlTree = HtmlTree.HEADER();
        addTop(htmlTree);
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(packageElement),
                contents.moduleLabel);
        navBar.setNavLinkModule(linkContent);
        navBar.setUserHeader(getUserHeaderFooter(true));
        htmlTree.addContent(navBar.getContent(true));
        bodyTree.addContent(htmlTree);
        return bodyTree;
    }

    /**
     * Add a link to the tree for all the packages.
     *
     * @param div the content tree to which the link will be added
     */
    protected void addLinkToMainTree(Content div) {
        Content span = HtmlTree.SPAN(HtmlStyle.packageHierarchyLabel,
                contents.packageHierarchies);
        div.addContent(span);
        HtmlTree ul = new HtmlTree (HtmlTag.UL);
        ul.setStyle(HtmlStyle.horizontal);
        ul.addContent(getNavLinkMainTree(resources.getText("doclet.All_Packages")));
        div.addContent(ul);
    }
}
