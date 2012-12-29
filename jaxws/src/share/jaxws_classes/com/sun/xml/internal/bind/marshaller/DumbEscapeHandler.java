/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.marshaller;

import java.io.IOException;
import java.io.Writer;

/**
 * Escape everything above the US-ASCII code range.
 * A fallback position.
 *
 * Works with any JDK, any encoding.
 *
 * @since 1.0.1
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class DumbEscapeHandler implements CharacterEscapeHandler {

    private DumbEscapeHandler() {}  // no instanciation please

    public static final CharacterEscapeHandler theInstance = new DumbEscapeHandler();

    public void escape(char[] ch, int start, int length, boolean isAttVal, Writer out) throws IOException {
        int limit = start+length;
        for (int i = start; i < limit; i++) {
            switch (ch[i]) {
            case '&':
                out.write("&amp;");
                break;
            case '<':
                out.write("&lt;");
                break;
            case '>':
                out.write("&gt;");
                break;
            case '\"':
                if (isAttVal) {
                    out.write("&quot;");
                } else {
                    out.write('\"');
                }
                break;
            default:
                if (ch[i] > '\u007f') {
                    out.write("&#");
                    out.write(Integer.toString(ch[i]));
                    out.write(';');
                } else {
                    out.write(ch[i]);
                }
            }
        }
    }

}
