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

package com.sun.tools.doclets.internal.toolkit.taglets;

import java.util.Map;

import com.sun.tools.doclets.Taglet;
import com.sun.javadoc.Tag;

/**
 * An inline Taglet used to denote information for experts.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 */
public class ExpertTaglet implements Taglet {

    private static final String NAME = "expert";
    private static final String START_TAG = "<sub id=\"expert\">";
    private static final String END_TAG = "</sub>";

    /**
     * {@inheritDoc}
     */
    public boolean inField() {
        return true;
    }

    public boolean inConstructor() {
        return true;
    }

    public boolean inMethod() {
        return true;
    }

    public boolean inOverview() {
        return true;
    }

    public boolean inPackage() {
        return true;
    }

    public boolean inType() {
        return true;
    }

    public boolean isInlineTag() {
        return false;
    }

    public String getName() {
        return NAME;
    }

    public static void register(Map<String, Taglet> map) {
        map.remove(NAME);
        map.put(NAME, new ExpertTaglet());
    }

    public String toString(Tag tag) {
        return (tag.text() == null || tag.text().length() == 0) ? null :
            START_TAG + LiteralTaglet.textToString(tag.text()) + END_TAG;
    }


    public String toString(Tag[] tags) {
        if (tags == null || tags.length == 0) return null;

        StringBuffer sb = new StringBuffer(START_TAG);

        for(Tag t:tags) {
            sb.append(LiteralTaglet.textToString(t.text()));
        }
        sb.append(END_TAG);
        return sb.toString();
    }

}
