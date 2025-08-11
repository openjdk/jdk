/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package doccheckutils;

import java.nio.file.Path;
import java.util.Map;

/**
 * Base class for HTML checkers.
 * <p>
 * For details on HTML syntax and the terms used in this API, see
 * W3C <a href="https://html.spec.whatwg.org/multipage/syntax.html#syntax">The HTML syntax</a>.
 */
public interface HtmlChecker extends Checker {
    /**
     * Starts checking a new file,
     * <p>
     * The file becomes the <em>current</em> file until {@link #endFile endFile}
     * is called.
     *
     * @param path the file.
     */
    void startFile(Path path);

    /**
     * Ends checking the current file.
     */
    void endFile();

    /**
     * Checks the content of a {@code <?xml ... ?>} declaration in the
     * current file.
     *
     * @param line  the line number on which the declaration was found
     * @param attrs the content of the declaration
     */
    void xml(int line, Map<String, String> attrs);

    /**
     * Checks the content of a {@code <!doctype ... >} declaration in the
     * current file.
     *
     * @param line    the line number on which the declaration was found
     * @param docType the content of the declaration
     */
    void docType(int line, String docType);

    /**
     * Checks the start of an HTML tag in the current file.
     *
     * @param line        the line number on which the start tag for an element was found
     * @param name        the name of the tag
     * @param attrs       the attributes of the tag
     * @param selfClosing whether the tag is self-closing
     */
    void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing);

    /**
     * Checks the end of an HTML tag in the current file.
     *
     * @param line the line number on which the end tag for an element was found
     * @param name the name of the tag
     */
    void endElement(int line, String name);

    /**
     * Checks the content appearing in between HTML tags.
     *
     * @param line    the line number on which the content was found
     * @param content the content
     */
    default void content(int line, String content) {
    }
}
