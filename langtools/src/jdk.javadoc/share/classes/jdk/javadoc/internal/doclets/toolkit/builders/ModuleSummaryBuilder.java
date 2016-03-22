/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.builders;

import java.io.IOException;
import java.util.Set;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.tools.StandardLocation;

import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.ModuleSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;


/**
 * Builds the summary for a given module.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ModuleSummaryBuilder extends AbstractBuilder {
    /**
     * The root element of the module summary XML is {@value}.
     */
    public static final String ROOT = "ModuleDoc";

    /**
     * The module being documented.
     */
    private final ModuleElement mdle;

    /**
     * The doclet specific writer that will output the result.
     */
    private final ModuleSummaryWriter moduleWriter;

    /**
     * The content that will be added to the module summary documentation tree.
     */
    private Content contentTree;

    /**
     * The module package being documented.
     */
    private PackageElement pkg;

    /**
     * Construct a new ModuleSummaryBuilder.
     *
     * @param context  the build context.
     * @param mdle the module being documented.
     * @param moduleWriter the doclet specific writer that will output the
     *        result.
     */
    private ModuleSummaryBuilder(Context context,
            ModuleElement mdle, ModuleSummaryWriter moduleWriter) {
        super(context);
        this.mdle = mdle;
        this.moduleWriter = moduleWriter;
    }

    /**
     * Construct a new ModuleSummaryBuilder.
     *
     * @param context  the build context.
     * @param mdle the module being documented.
     * @param moduleWriter the doclet specific writer that will output the
     *        result.
     *
     * @return an instance of a ModuleSummaryBuilder.
     */
    public static ModuleSummaryBuilder getInstance(Context context,
            ModuleElement mdle, ModuleSummaryWriter moduleWriter) {
        return new ModuleSummaryBuilder(context, mdle, moduleWriter);
    }

    /**
     * Build the module summary.
     */
    public void build() throws IOException {
        if (moduleWriter == null) {
            //Doclet does not support this output.
            return;
        }
        build(layoutParser.parseXML(ROOT), contentTree);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return ROOT;
    }

    /**
     * Build the module documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param contentTree the content tree to which the documentation will be added
     */
    public void buildModuleDoc(XMLNode node, Content contentTree) throws Exception {
        contentTree = moduleWriter.getModuleHeader(mdle.getSimpleName().toString());
        buildChildren(node, contentTree);
        moduleWriter.addModuleFooter(contentTree);
        moduleWriter.printDocument(contentTree);
        moduleWriter.close();
        // TEMPORARY:
        // The use of SOURCE_PATH on the next line is temporary. As we transition into the
        // modules world, this should migrate into using a location for the appropriate module
        // on the MODULE_SOURCE_PATH, or (in the old doclet) simply deleted.
        utils.copyDocFiles(configuration, StandardLocation.SOURCE_PATH, DocPaths.moduleSummary(mdle));
    }

    /**
     * Build the content for the module doc.
     *
     * @param node the XML element that specifies which components to document
     * @param contentTree the content tree to which the module contents
     *                    will be added
     */
    public void buildContent(XMLNode node, Content contentTree) {
        Content moduleContentTree = moduleWriter.getContentHeader();
        buildChildren(node, moduleContentTree);
        moduleWriter.addModuleContent(contentTree, moduleContentTree);
    }

    /**
     * Build the module summary.
     *
     * @param node the XML element that specifies which components to document
     * @param moduleContentTree the module content tree to which the summaries will
     *                           be added
     */
    public void buildSummary(XMLNode node, Content moduleContentTree) {
        Content summaryContentTree = moduleWriter.getSummaryHeader();
        buildChildren(node, summaryContentTree);
        moduleContentTree.addContent(moduleWriter.getSummaryTree(summaryContentTree));
    }

    /**
     * Build the module package summary.
     *
     * @param node the XML element that specifies which components to document
     * @param summaryContentTree the content tree to which the summaries will
     *                           be added
     */
    public void buildPackageSummary(XMLNode node, Content summaryContentTree) {
        Set<PackageElement> packages = configuration.modulePackages.get(mdle);
        if (!packages.isEmpty()) {
            String packageTableSummary
                    = configuration.getText("doclet.Member_Table_Summary",
                            configuration.getText("doclet.Package_Summary"),
                            configuration.getText("doclet.packages"));
            moduleWriter.addPackagesSummary(
                    packages, configuration.getText("doclet.Package_Summary"),
                    packageTableSummary, summaryContentTree);
        }
    }
}
