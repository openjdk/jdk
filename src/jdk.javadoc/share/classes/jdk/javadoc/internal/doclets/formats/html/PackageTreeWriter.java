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

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;


/**
 * Class to generate Tree page for a package. The name of the file generated is
 * "package-tree.html" and it is generated in the respective package directory.
 */
public class PackageTreeWriter extends AbstractTreeWriter {

    /**
     * Package for which tree is to be generated.
     */
    protected PackageElement packageElement;

    private final BodyContents bodyContents = new BodyContents();

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
    }

    /**
     * Construct a PackageTreeWriter object and then use it to generate the
     * package tree page.
     *
     * @param configuration the configuration for this run.
     * @param pkg      Package for which tree file is to be generated.
     * @param noDeprecated  If true, do not generate any information for
     * deprecated classes or interfaces.
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
     * Generate a separate tree file.
     *
     * @throws DocFileIOException if there is a problem generating the package tree file
     */
    protected void generatePackageTreeFile() throws DocFileIOException {
        HtmlTree body = getPackageTreeHeader();
        Content mainContent = new ContentBuilder();
        Content headContent = packageElement.isUnnamed()
                ? contents.getContent("doclet.Hierarchy_For_Unnamed_Package")
                : contents.getContent("doclet.Hierarchy_For_Package",
                getLocalizedPackageName(packageElement));
        var heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING,
                HtmlStyle.title, headContent);
        var div = HtmlTree.DIV(HtmlStyle.header, heading);
        if (configuration.packages.size() > 1) {
            addLinkToAllPackages(div);
        }
        mainContent.add(div);
        addTree(classtree.baseClasses(), "doclet.Class_Hierarchy", mainContent);
        addTree(classtree.baseInterfaces(), "doclet.Interface_Hierarchy", mainContent);
        addTree(classtree.baseAnnotationTypes(), "doclet.Annotation_Type_Hierarchy", mainContent);
        addTree(classtree.baseEnums(), "doclet.Enum_Hierarchy", mainContent, true);
        bodyContents.addMainContent(mainContent);
        bodyContents.setFooter(getFooter());
        body.add(bodyContents);
        printHtmlDocument(null, getDescription("tree", packageElement), body);
    }

    /**
     * Get the package tree header.
     *
     * @return the package tree header
     */
    protected HtmlTree getPackageTreeHeader() {
        String packageName = packageElement.isUnnamed() ? "" : utils.getPackageName(packageElement);
        String title = packageName + " " + resources.getText("doclet.Window_Class_Hierarchy");
        HtmlTree body = getBody(getWindowTitle(title));
        bodyContents.setHeader(getHeader(PageMode.TREE, packageElement));
        return body;
    }

    @Override
    protected Navigation getNavBar(PageMode pageMode, Element element) {
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(packageElement),
                contents.moduleLabel);
        return super.getNavBar(pageMode, element)
                .setNavLinkModule(linkContent);
    }

    /**
     * Add a link to the tree for all the packages.
     *
     * @param target the content to which the link will be added
     */
    protected void addLinkToAllPackages(Content target) {
        var span = HtmlTree.SPAN(HtmlStyle.packageHierarchyLabel,
                contents.packageHierarchies);
        target.add(span);
        var ul = HtmlTree.UL(HtmlStyle.horizontal);
        // TODO the link should be more specific:
        //  it should point to the "all packages" section of the overview tree
        ul.add(getNavLinkToOverviewTree(resources.getText("doclet.All_Packages")));
        target.add(ul);
    }
}
