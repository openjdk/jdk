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

package jdk.javadoc.internal.doclets.formats.html;

import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Generate the module index for the left-hand frame in the generated output.
 * A click on the module name in this frame will update the page in the top
 * left hand frame with the listing of packages of the clicked module.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ModuleIndexFrameWriter extends AbstractModuleIndexWriter {

    /**
     * Construct the ModuleIndexFrameWriter object.
     *
     * @param configuration the configuration object
     * @param filename Name of the module index file to be generated.
     */
    public ModuleIndexFrameWriter(HtmlConfiguration configuration,
                                   DocPath filename) {
        super(configuration, filename);
    }

    /**
     * Generate the module index file named "module-overview-frame.html".
     * @throws DocFileIOException
     * @param configuration the configuration object
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        DocPath filename = DocPaths.MODULE_OVERVIEW_FRAME;
        ModuleIndexFrameWriter modulegen = new ModuleIndexFrameWriter(configuration, filename);
        modulegen.buildModuleIndexFile("doclet.Window_Overview", "module overview (frame)", false);
    }

    /**
     * {@inheritDoc}
     */
    protected void addModulesList(Content main) {
        Content heading = HtmlTree.HEADING(HtmlConstants.MODULE_HEADING, true,
                contents.modulesLabel);
        HtmlTree htmlTree = HtmlTree.DIV(HtmlStyle.indexContainer, heading);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setTitle(contents.modulesLabel);
        for (ModuleElement mdle: configuration.modules) {
            ul.addContent(getModuleLink(mdle));
        }
        htmlTree.addContent(ul);
        main.addContent(htmlTree);
    }

    /**
     * Returns each module name as a separate link.
     *
     * @param mdle the module being documented
     * @return content for the module link
     */
    protected Content getModuleLink(ModuleElement mdle) {
        Content moduleLinkContent;
        Content mdlLabel = new StringContent(mdle.getQualifiedName());
        moduleLinkContent = getModuleFramesHyperLink(mdle, mdlLabel, "packageListFrame");
        Content li = HtmlTree.LI(moduleLinkContent);
        return li;
    }

    private Content getModuleFramesHyperLink(ModuleElement mdle, Content label, String target) {
        DocLink mdlLink = new DocLink(docPaths.moduleFrame(mdle));
        DocLink mtFrameLink = new DocLink(docPaths.moduleTypeFrame(mdle));
        DocLink cFrameLink = new DocLink(docPaths.moduleSummary(mdle));
        HtmlTree anchor = HtmlTree.A(mdlLink.toString(), label);
        String onclickStr = "updateModuleFrame('" + mtFrameLink + "','" + cFrameLink + "');";
        anchor.addAttr(HtmlAttr.TARGET, target);
        anchor.addAttr(HtmlAttr.ONCLICK, onclickStr);
        return anchor;
    }

    /**
     * {@inheritDoc}
     */
    protected void addNavigationBarHeader(Content header) {
        Content headerContent;
        if (configuration.packagesheader.length() > 0) {
            headerContent = new RawHtml(replaceDocRootDir(configuration.packagesheader));
        } else {
            headerContent = new RawHtml(replaceDocRootDir(configuration.header));
        }
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.bar, headerContent);
        header.addContent(heading);
    }

    /**
     * Do nothing as there is no overview information in this page.
     */
    protected void addOverviewHeader(Content body) {
    }

    /**
     * Adds "All Classes" link for the top of the left-hand frame page to the
     * documentation tree.
     *
     * @param ul the Content object to which the all classes link should be added
     */
    protected void addAllClassesLink(Content ul) {
        Content linkContent = links.createLink(DocPaths.ALLCLASSES_FRAME,
                contents.allClassesLabel, "", "packageFrame");
        Content li = HtmlTree.LI(linkContent);
        ul.addContent(li);
    }

    /**
     * Adds "All Packages" link for the top of the left-hand frame page to the
     * documentation tree.
     *
     * @param ul the Content object to which the all packages link should be added
     */
    protected void addAllPackagesLink(Content ul) {
        Content linkContent = links.createLink(DocPaths.OVERVIEW_FRAME,
                contents.allPackagesLabel, "", "packageListFrame");
        Content li = HtmlTree.LI(linkContent);
        ul.addContent(li);
    }

    /**
     * {@inheritDoc}
     */
    protected void addNavigationBarFooter(Content footer) {
        Content p = HtmlTree.P(Contents.SPACE);
        footer.addContent(p);
    }

    protected void addModulePackagesList(Map<ModuleElement, Set<PackageElement>> modules, String text,
            String tableSummary, Content main, ModuleElement mdle) {
    }
}
