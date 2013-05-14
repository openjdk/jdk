/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.formats.html.markup.ContentBuilder;
import com.sun.tools.doclets.formats.html.markup.RawHtml;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.taglets.*;

/**
 * The output for HTML taglets.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.5
 * @author Jamie Ho
 * @author Jonathan Gibbons (rewrite)
 */

public class TagletOutputImpl implements TagletOutput {

    private ContentBuilder content;

    public TagletOutputImpl() {
        content = new ContentBuilder();
    }

    public TagletOutputImpl(String o) {
        setOutput(o);
    }

    public TagletOutputImpl(Content c) {
        setOutput(c);
    }

    /**
     * {@inheritDoc}
     */
    public void setOutput (Object o) {
        content = new ContentBuilder();
        if (o != null) {
            if (o instanceof String)
                content.addContent(new RawHtml((String) o));
            else if (o instanceof Content)
                content.addContent((Content) o);
            else if (o instanceof TagletOutputImpl)
                content.addContent(((TagletOutputImpl) o).content);
            else
                throw new IllegalArgumentException(o.getClass().getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void appendOutput(TagletOutput o) {
        if (o instanceof TagletOutputImpl)
            content.addContent(((TagletOutputImpl) o).content);
        else
            throw new IllegalArgumentException(o.getClass().getName());
    }

    public String toString() {
        return content.toString();
    }

}
