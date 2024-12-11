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

package jdk.javadoc.internal.html;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * Class for generating a comment for HTML pages of javadoc output.
 */
public class Comment extends Content {

    private final String commentText;

    /**
     * Constructor to construct a Comment object.
     *
     * @param comment comment text for the comment
     */
    public Comment(String comment) {
        commentText = Objects.requireNonNull(comment);
    }

    @Override
    public boolean isEmpty() {
        return commentText.isEmpty();
    }

    @Override
    public boolean write(Writer out, String newline, boolean atNewline) throws IOException {
        if (!atNewline) {
            out.write(newline);
        }
        out.write("<!-- ");
        out.write(commentText.replace("\n", newline));
        out.write(" -->");
        out.write(newline);
        return true;
    }
}
