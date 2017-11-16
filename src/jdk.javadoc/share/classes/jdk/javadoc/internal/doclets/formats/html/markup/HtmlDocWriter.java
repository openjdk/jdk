/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.markup;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;


/**
 * Class for the Html Format Code Generation specific to JavaDoc.
 * This Class contains methods related to the Html Code Generation which
 * are used by the Sub-Classes in the package jdk.javadoc.internal.tool.standard.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 */
public abstract class HtmlDocWriter {

    private final HtmlConfiguration configuration;

    /**
     * Constructor.
     *
     * @param configuration the configuration for this doclet
     * @param filename the path for the output file
     */
    public HtmlDocWriter(HtmlConfiguration configuration, DocPath filename) {
        this.configuration = configuration;
        Messages messages = configuration.getMessages();
        messages.notice("doclet.Generating_0",
            DocFile.createFileForOutput(configuration, filename).getPath());
    }

    public Content getModuleFramesHyperLink(ModuleElement mdle, Content label, String target) {
        DocLink mdlLink = new DocLink(DocPaths.moduleFrame(mdle));
        DocLink mtFrameLink = new DocLink(DocPaths.moduleTypeFrame(mdle));
        DocLink cFrameLink = new DocLink(DocPaths.moduleSummary(mdle));
        HtmlTree anchor = HtmlTree.A(mdlLink.toString(), label);
        String onclickStr = "updateModuleFrame('" + mtFrameLink + "','" + cFrameLink + "');";
        anchor.addAttr(HtmlAttr.TARGET, target);
        anchor.addAttr(HtmlAttr.ONCLICK, onclickStr);
        return anchor;
    }

    /**
     * Get the enclosed name of the package
     *
     * @param te  TypeElement
     * @return the name
     */
    public String getEnclosingPackageName(TypeElement te) {

        PackageElement encl = configuration.utils.containingPackage(te);
        return (encl.isUnnamed()) ? "" : (encl.getQualifiedName() + ".");
    }
}
