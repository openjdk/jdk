/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.Tag;

/**
 * An abstract class that implements the {@link Taglet} interface and
 * serves as a base for JavaFX property getter and setter taglets.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 */
public abstract class BasePropertyTaglet extends BaseTaglet {

    public BasePropertyTaglet() {
    }

    /**
     * This method returns the text to be put in the resulting javadoc before
     * the property name.
     *
     * @param tagletWriter the taglet writer for output
     * @return the string to be put in the resulting javadoc.
     */
    abstract String getText(TagletWriter tagletWriter);

    /**
     * Given the <code>Tag</code> representation of this custom
     * tag, return its string representation, which is output
     * to the generated page.
     * @param tag the <code>Tag</code> representation of this custom tag.
     * @param tagletWriter the taglet writer for output.
     * @return the TagletOutput representation of this <code>Tag</code>.
     */
    public TagletOutput getTagletOutput(Tag tag, TagletWriter tagletWriter) {
        TagletOutput tagletOutput = tagletWriter.getOutputInstance();
        StringBuilder output = new StringBuilder("<P>");
        output.append(getText(tagletWriter));
        output.append(" <CODE>");
        output.append(tag.text());
        output.append("</CODE>.</P>");
        tagletOutput.setOutput(output.toString());
        return tagletOutput;
    }

    /**
     * Will return false because this tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inConstructor() {
        return false;
    }

    /**
     * Will return false because this tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inOverview() {
        return false;
    }

    /**
     * Will return false because this tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inPackage() {
        return false;
    }

    /**
     * Will return false because this tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inType() {
        return false;
    }

    /**
     * Will return false because this tag is not inline.
     * @return false since this is not an inline tag.
     */
    public boolean isInlineTag() {
        return false;
    }

}
