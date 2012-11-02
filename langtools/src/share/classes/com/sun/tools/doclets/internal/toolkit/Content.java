/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * A class to create content for javadoc output pages.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public abstract class Content {

    /**
     * Returns a string representation of the content.
     *
     * @return string representation of the content
     */
    @Override
    public String toString() {
        StringWriter out = new StringWriter();
        try {
            write(out, true);
        } catch (IOException e) {
            // cannot happen from StringWriter
            throw new DocletAbortException();
        }
        return out.toString();
    }

    /**
     * Adds content to the existing content.
     *
     * @param content content that needs to be added
     */
    public abstract void addContent(Content content);

    /**
     * Adds a string content to the existing content.
     *
     * @param stringContent the string content to be added
     */
    public abstract void addContent(String stringContent);

    /**
     * Writes content to a writer.
     *
     */
    public abstract boolean write(Writer writer, boolean atNewline) throws IOException ;

    /**
     * Returns true if the content is empty.
     *
     * @return true if no content to be displayed else return false
     */
    public abstract boolean isEmpty();

    /**
     * Returns true if the content is valid.
     *
     * @return true if the content is valid else return false
     */
    public boolean isValid() {
        return !isEmpty();
    }

    /**
     * Checks for null values.
     *
     * @param t reference type to check for null values
     * @return the reference type if not null or else throws a null pointer exception
     */
    protected static <T> T nullCheck(T t) {
        t.getClass();
        return t;
    }

    /**
     * Returns true if the content ends with a newline character. Empty content
     * is considered as ending with new line.
     *
     * @param contentBuilder content to test for newline character at the end
     * @return true if the content ends with newline.
     */
    protected boolean endsWithNewLine(StringBuilder contentBuilder) {
        int contentLength = contentBuilder.length();
        if (contentLength == 0) {
            return true;
        }
        int nlLength = DocletConstants.NL.length();
        if (contentLength < nlLength) {
            return false;
        }
        int contentIndex = contentLength - 1;
        int nlIndex = nlLength - 1;
        while (nlIndex >= 0) {
            if (contentBuilder.charAt(contentIndex) != DocletConstants.NL.charAt(nlIndex)) {
                return false;
            }
            contentIndex--;
            nlIndex--;
        }
        return true;
    }
}
