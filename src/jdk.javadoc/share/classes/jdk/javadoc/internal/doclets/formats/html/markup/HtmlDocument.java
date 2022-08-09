/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;

/**
 * Class for generating an HTML document for javadoc output.
 */
public class HtmlDocument {
    private final DocType docType = DocType.HTML5;
    private final Content docContent;

    /**
     * Constructs an HTML document.
     *
     * @param html the {@link TagName#HTML HTML} element of the document
     */
    public HtmlDocument(Content html) {
        docContent = html;
    }

    /**
     * Writes the content of this document to the specified file.
     *
     * @param docFile the file
     * @throws DocFileIOException if an {@code IOException} occurs while writing the file
     */
    public void write(DocFile docFile) throws DocFileIOException {
        try (Writer writer = docFile.openWriter()) {
            write(writer);
        } catch (IOException e) {
            throw new DocFileIOException(docFile, DocFileIOException.Mode.WRITE, e);
        }
    }

    @Override
    public String toString() {
        try (Writer writer = new StringWriter()) {
            write(writer);
            return writer.toString();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private void write(Writer writer) throws IOException {
        writer.write(docType.text);
        writer.write(DocletConstants.NL);
        docContent.write(writer, true);
    }
}
