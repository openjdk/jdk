/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;
import java.io.*;

/**
 * Generate the Serialized Form Information Page.
 *
 * @author Atul M Dambalkar
 */
public class SerializedFormWriterImpl extends SubWriterHolderWriter
    implements com.sun.tools.doclets.internal.toolkit.SerializedFormWriter {

    private static final String FILE_NAME = "serialized-form.html";

    /**
     * @throws IOException
     * @throws DocletAbortException
     */
    public SerializedFormWriterImpl() throws IOException {
        super(ConfigurationImpl.getInstance(), FILE_NAME);
    }

    /**
     * Writes the given header.
     *
     * @param header the header to write.
     */
    public void writeHeader(String header) {
        printHtmlHeader(header, null, true);
        printTop();
        navLinks(true);
        hr();
        center();
        h1();
        print(header);
        h1End();
        centerEnd();
    }

    /**
     * Write the given package header.
     *
     * @param packageName the package header to write.
     */
    public void writePackageHeader(String packageName) {
        hr(4, "noshade");
        tableHeader();
        thAlign("center");
        font("+2");
        strongText("doclet.Package");
        print(' ');
        strong(packageName);
        tableFooter();
    }

    /**
     * Write the serial UID info.
     *
     * @param header the header that will show up before the UID.
     * @param serialUID the serial UID to print.
     */
    public void writeSerialUIDInfo(String header, String serialUID) {
        strong(header + "&nbsp;");
        println(serialUID);
        p();
    }

    /**
     * Write the footer.
     */
    public void writeFooter() {
        p();
        hr();
        navLinks(false);
        printBottom();
        printBodyHtmlEnd();
    }


    /**
     * Write the serializable class heading.
     *
     * @param classDoc the class being processed.
     */
    public void writeClassHeader(ClassDoc classDoc) {
        String classLink = (classDoc.isPublic() || classDoc.isProtected())?
            getLink(new LinkInfoImpl(classDoc,
                configuration.getClassName(classDoc))):
            classDoc.qualifiedName();
        p();
        anchor(classDoc.qualifiedName());
        String superClassLink =
            classDoc.superclassType() != null ?
                getLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_SERIALIZED_FORM,
                    classDoc.superclassType())) :
                null;

        //Print the heading.
        String className = superClassLink == null ?
            configuration.getText(
                "doclet.Class_0_implements_serializable", classLink) :
            configuration.getText(
                "doclet.Class_0_extends_implements_serializable", classLink,
                    superClassLink);
        tableHeader();
        thAlignColspan("left", 2);
        font("+2");
        strong(className);
        tableFooter();
        p();
    }

    private void tableHeader() {
        tableIndexSummary();
        trBgcolorStyle("#CCCCFF", "TableSubHeadingColor");
    }

    private void tableFooter() {
        fontEnd();
        thEnd(); trEnd(); tableEnd();
    }

    /**
     * Return an instance of a SerialFieldWriter.
     *
     * @return an instance of a SerialFieldWriter.
     */
    public SerialFieldWriter getSerialFieldWriter(ClassDoc classDoc) {
        return new HtmlSerialFieldWriter(this, classDoc);
    }

    /**
     * Return an instance of a SerialMethodWriter.
     *
     * @return an instance of a SerialMethodWriter.
     */
    public SerialMethodWriter getSerialMethodWriter(ClassDoc classDoc) {
        return new HtmlSerialMethodWriter(this, classDoc);
    }
}
