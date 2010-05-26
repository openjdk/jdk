/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;

import java.util.regex.*;

import com.sun.javadoc.*;

/**
 * Represents an @param documentation tag.
 * Parses and stores the name and comment parts of the parameter tag.
 *
 * @author Robert Field
 *
 */
class ParamTagImpl extends TagImpl implements ParamTag {

    private static Pattern typeParamRE = Pattern.compile("<([^<>]+)>");

    private final String parameterName;
    private final String parameterComment;
    private final boolean isTypeParameter;

    ParamTagImpl(DocImpl holder, String name, String text) {
        super(holder, name, text);
        String[] sa = divideAtWhite();

        Matcher m = typeParamRE.matcher(sa[0]);
        isTypeParameter = m.matches();
        parameterName = isTypeParameter ? m.group(1) : sa[0];
        parameterComment = sa[1];
    }

    /**
     * Return the parameter name.
     */
    public String parameterName() {
        return parameterName;
    }

    /**
     * Return the parameter comment.
     */
    public String parameterComment() {
        return parameterComment;
    }

    /**
     * Return the kind of this tag.
     */
    public String kind() {
        return "@param";
    }

    /**
     * Return true if this ParamTag corresponds to a type parameter.
     */
    public boolean isTypeParameter() {
        return isTypeParameter;
    }

    /**
     * convert this object to a string.
     */
    public String toString() {
        return name + ":" + text;
    }

    /**
     * For the parameter comment with embedded @link tags return the array of
     * TagImpls consisting of SeeTagImpl(s) and text containing TagImpl(s).
     *
     * @return TagImpl[] Array of tags with inline SeeTagImpls.
     * @see TagImpl#inlineTagImpls()
     * @see ThrowsTagImpl#inlineTagImpls()
     */
    public Tag[] inlineTags() {
        return Comment.getInlineTags(holder, parameterComment);
    }
}
