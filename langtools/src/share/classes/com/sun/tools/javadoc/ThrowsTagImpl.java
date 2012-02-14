/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;

/**
 * Represents a @throws or @exception documentation tag.
 * Parses and holds the exception name and exception comment.
 * The exception name my be the name of a type variable.
 * Note: @exception is a backwards compatible synonymy for @throws.
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @see ExecutableMemberDocImpl#throwsTags()
 *
 */
class ThrowsTagImpl extends TagImpl implements ThrowsTag {

    private final String exceptionName;
    private final String exceptionComment;

    /**
     * Cached inline tags.
     */
    private Tag[] inlineTags;

    ThrowsTagImpl(DocImpl holder, String name, String text) {
        super(holder, name, text);
        String[] sa = divideAtWhite();
        exceptionName = sa[0];
        exceptionComment = sa[1];
    }

    /**
     * Return the exception name.
     */
    public String exceptionName() {
        return exceptionName;
    }

    /**
     * Return the exception comment.
     */
    public String exceptionComment() {
        return exceptionComment;
    }

    /**
     * Return the exception as a ClassDocImpl.
     */
    public ClassDoc exception() {
        ClassDocImpl exceptionClass;
        if (!(holder instanceof ExecutableMemberDoc)) {
            exceptionClass = null;
        } else {
            ExecutableMemberDocImpl emd = (ExecutableMemberDocImpl)holder;
            ClassDocImpl con = (ClassDocImpl)emd.containingClass();
            exceptionClass = (ClassDocImpl)con.findClass(exceptionName);
        }
        return exceptionClass;
    }

    /**
     * Return the type that represents the exception.
     * This may be a <code>ClassDoc</code> or a <code>TypeVariable</code>.
     */
    public Type exceptionType() {
        //###(gj) TypeVariable not yet supported.
        return exception();
    }


    /**
     * Return the kind of this tag.  Always "@throws" for instances
     * of ThrowsTagImpl.
     */
    @Override
    public String kind() {
        return "@throws";
    }

    /**
     * For the exception comment with embedded @link tags return the array of
     * TagImpls consisting of SeeTagImpl(s) and text containing TagImpl(s).
     *
     * @return TagImpl[] Array of tags with inline SeeTagImpls.
     * @see TagImpl#inlineTagImpls()
     * @see ParamTagImpl#inlineTagImpls()
     */
    @Override
    public Tag[] inlineTags() {
        if (inlineTags == null) {
            inlineTags = Comment.getInlineTags(holder, exceptionComment());
        }
        return inlineTags;
    }
}
