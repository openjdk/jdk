/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.doclets.internal.toolkit.util;
import com.sun.javadoc.*;


/**
 * A tag that holds nothing but plain text.  This is useful for passing
 * text to methods that only accept inline tags as a parameter.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class TextTag implements Tag {
    protected final String text;
    protected final String name = "Text";
    protected final Doc holder;

    /**
     *  Constructor
     */
    public TextTag(Doc holder, String text) {
        super();
        this.holder = holder;
        this.text = text;
    }

    /**
     * {@inheritDoc}
     */
    public String name() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public Doc holder() {
        return holder;
    }

    /**
     * {@inheritDoc}
     */
    public String kind() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public String text() {
        return text;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return name + ":" + text;
    }

    /**
     * {@inheritDoc}
     */
    public Tag[] inlineTags() {
        return new Tag[] {this};
    }

    /**
     * {@inheritDoc}
     */
    public Tag[] firstSentenceTags() {
        return new Tag[] {this};
    }

    /**
     * {@inheritDoc}
     */
    public SourcePosition position() {
        return holder.position();
    }
}
