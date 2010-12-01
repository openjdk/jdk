/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html.markup;

import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Class for generating document type for HTML pages of javadoc output.
 *
 * @author Bhavesh Patel
 */
public class DocType extends Content{

    private String docType;

    private static DocType transitional;

    private static DocType frameset;

    /**
     * Constructor to construct a DocType object.
     *
     * @param type the doctype to be added
     */
    private DocType(String type, String dtd) {
        docType = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 " + type +
                "//EN\" \"" + dtd + "\">\n";
    }

     /**
     * Construct and return a HTML 4.01 transitional DocType content
     *
     * @return a content tree for transitional DocType
     */
    public static DocType Transitional() {
        if (transitional == null)
            transitional = new DocType("Transitional", "http://www.w3.org/TR/html4/loose.dtd");
        return transitional;
    }

    /**
     * Construct and return a HTML 4.01 frameset DocType content
     *
     * @return a content tree for frameset DocType
     */
    public static DocType Frameset() {
        if (frameset == null)
            frameset = new DocType("Frameset", "http://www.w3.org/TR/html4/frameset.dtd");
        return frameset;
    }

    /**
     * This method is not supported by the class.
     *
     * @param content content that needs to be added
     * @throws DocletAbortException this method will always throw a
     *                              DocletAbortException because it
     *                              is not supported.
     */
    public void addContent(Content content) {
        throw new DocletAbortException();
    }

    /**
     * This method is not supported by the class.
     *
     * @param stringContent string content that needs to be added
     * @throws DocletAbortException this method will always throw a
     *                              DocletAbortException because it
     *                              is not supported.
     */
    public void addContent(String stringContent) {
        throw new DocletAbortException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return (docType.length() == 0);
    }

    /**
     * {@inheritDoc}
     */
    public void write(StringBuilder contentBuilder) {
        contentBuilder.append(docType);
    }
}
