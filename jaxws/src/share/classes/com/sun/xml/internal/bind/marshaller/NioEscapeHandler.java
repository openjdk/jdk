/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.bind.marshaller;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Uses JDK1.4 NIO functionality to escape characters smartly.
 *
 * @since 1.0.1
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class NioEscapeHandler implements CharacterEscapeHandler {

    private final CharsetEncoder encoder;

    // exposing those variations upset javac 1.3.1, since it needs to
    // know about those classes to determine which overloaded version
    // of the method it wants to use. So comment it out for the compatibility.

//    public NioEscapeHandler(CharsetEncoder _encoder) {
//        this.encoder = _encoder;
//        if(encoder==null)
//            throw new NullPointerException();
//    }
//
//    public NioEscapeHandler(Charset charset) {
//        this(charset.newEncoder());
//    }

    public NioEscapeHandler(String charsetName) {
//        this(Charset.forName(charsetName));
        this.encoder = Charset.forName(charsetName).newEncoder();
    }

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
                if( encoder.canEncode(ch[i]) ) {
                    out.write(ch[i]);
                } else {
                    out.write("&#");
                    out.write(Integer.toString(ch[i]));
                    out.write(';');
                }
            }
        }
    }

}
