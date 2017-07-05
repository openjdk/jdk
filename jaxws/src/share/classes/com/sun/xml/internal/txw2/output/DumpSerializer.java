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

package com.sun.xml.internal.txw2.output;

import java.io.PrintStream;

/**
 * Shows the call sequence of {@link XmlSerializer} methods.
 *
 * Useful for debugging and learning how TXW works.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class DumpSerializer implements XmlSerializer {
    private final PrintStream out;

    public DumpSerializer(PrintStream out) {
        this.out = out;
    }

    public void beginStartTag(String uri, String localName, String prefix) {
        out.println('<'+prefix+':'+localName);
    }

    public void writeAttribute(String uri, String localName, String prefix, StringBuilder value) {
        out.println('@'+prefix+':'+localName+'='+value);
    }

    public void writeXmlns(String prefix, String uri) {
        out.println("xmlns:"+prefix+'='+uri);
    }

    public void endStartTag(String uri, String localName, String prefix) {
        out.println('>');
    }

    public void endTag() {
        out.println("</  >");
    }

    public void text(StringBuilder text) {
        out.println(text);
    }

    public void cdata(StringBuilder text) {
        out.println("<![CDATA[");
        out.println(text);
        out.println("]]>");
    }

    public void comment(StringBuilder comment) {
        out.println("<!--");
        out.println(comment);
        out.println("-->");
    }

    public void startDocument() {
        out.println("<?xml?>");
    }

    public void endDocument() {
        out.println("done");
    }

    public void flush() {
        out.println("flush");
    }
}
