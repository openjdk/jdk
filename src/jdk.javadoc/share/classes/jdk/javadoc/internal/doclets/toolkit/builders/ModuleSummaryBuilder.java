/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.ModuleElement;

import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.ModuleSummaryWriter;


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
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    @Override
    public void build() throws DocletException {
        if (moduleWriter == null) {
            //Doclet does not support this output.
            return;
        }
        buildModuleDoc(contentTree);
    }

    /**
     * Build the module documentation.
     *
     * @param contentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildModuleDoc(Content contentTree) throws DocletException {
        contentTree = moduleWriter.getModuleHeader(mdle.getQualifiedName().toString());

        buildContent(contentTree);

        moduleWriter.addModuleFooter(contentTree);
        moduleWriter.printDocument(contentTree);
        // uncomment to support doc-files in modules
        // DocFilesHandler docFilesHandler = configuration.getWriterFactory().getDocFilesWriter(mdle);
        // docFilesHandler.copyDocFiles();
    }

    /**
     * Build the content for the module doc.
     *
     * @param contentTree the content tree to which the module contents
     *                    will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildContent(Content contentTree) throws DocletException {
        Content moduleContentTree = moduleWriter.getContentHeader();

        buildModuleDescription(moduleContentTree);
        buildModuleTags(moduleContentTree);
        buildSummary(moduleContentTree);

        moduleWriter.addModuleContent(contentTree, moduleContentTree);
    }

    /**
     * Build the module summary.
     *
     * @param moduleContentTree the module content tree to which the summaries will
     *                           be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildSummary(Content moduleContentTree) throws DocletException {
        Content summaryContentTree = moduleWriter.getSummaryHeader();

        buildPackagesSummary(summaryContentTree);
        buildModulesSummary(summaryContentTree);
        buildServicesSummary(summaryContentTree);

        moduleContentTree.add(moduleWriter.getSummaryTree(summaryContentTree));
    }

    /**
     * Build the modules summary.
     *
     * @param summaryContentTree the content tree to which the summaries will
     *                           be added
     */
    protected void buildModulesSummary(Content summaryContentTree) {
        moduleWriter.addModulesSummary(summaryContentTree);
    }

    /**
     * Build the package summary.
     *
     * @param summaryContentTree the content tree to which the summaries will be added
     */
    protected void buildPackagesSummary(Content summaryContentTree) {
        moduleWriter.addPackagesSummary(summaryContentTree);
    }

    /**
     * Build the services summary.
     *
     * @param summaryContentTree the content tree to which the summaries will be added
     */
    protected void buildServicesSummary(Content summaryContentTree) {
        moduleWriter.addServicesSummary(summaryContentTree);
    }

    /**
     * Build the description for the module.
     *
     * @param moduleContentTree the tree to which the module description will
     *                           be added
     */
    protected void buildModuleDescription(Content moduleContentTree) {
        if (!configuration.nocomment) {
            moduleWriter.addModuleDescription(moduleContentTree);
        }
    }

    /**
     * Build the tags of the summary.
     *
     * @param moduleContentTree the tree to which the module tags will be added
     */
    protected void buildModuleTags(Content moduleContentTree) {
        if (!configuration.nocomment) {
            moduleWriter.addModuleTags(moduleContentTree);
        }
    }
}
