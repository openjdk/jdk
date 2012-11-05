/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * An inline Taglet representing the value tag. This tag should only be used with
 * constant fields that have a value.  It is used to access the value of constant
 * fields.  This inline tag has an optional field name parameter.  If the name is
 * specified, the constant value is retrieved from the specified field.  A link
 * is also created to the specified field.  If a name is not specified, the value
 * is retrieved for the field that the inline tag appears on.  The name is specifed
 * in the following format:  [fully qualified class name]#[constant field name].
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.4
 */

public class ValueTaglet extends BaseInlineTaglet {

    /**
     * Construct a new ValueTaglet.
     */
    public ValueTaglet() {
        name = "value";
    }

    /**
     * Will return false because this inline tag may
     * only appear in Fields.
     * @return false since this is not a method.
     */
    public boolean inMethod() {
        return true;
    }

    /**
     * Will return false because this inline tag may
     * only appear in Fields.
     * @return false since this is not a method.
     */
    public boolean inConstructor() {
        return true;
    }

    /**
     * Will return false because this inline tag may
     * only appear in Fields.
     * @return false since this is not a method.
     */
    public boolean inOverview() {
        return true;
    }

    /**
     * Will return false because this inline tag may
     * only appear in Fields.
     * @return false since this is not a method.
     */
    public boolean inPackage() {
        return true;
    }

    /**
     * Will return false because this inline tag may
     * only appear in Fields.
     * @return false since this is not a method.
     */
    public boolean inType() {
        return true;
    }

    /**
     * Given the name of the field, return the corresponding FieldDoc.
     *
     * @param config the current configuration of the doclet.
     * @param tag the value tag.
     * @param name the name of the field to search for.  The name should be in
     * {@code <qualified class name>#<field name>} format. If the class name is omitted,
     * it is assumed that the field is in the current class.
     *
     * @return the corresponding FieldDoc. If the name is null or empty string,
     * return field that the value tag was used in.
     *
     * @throws DocletAbortException if the value tag does not specify a name to
     * a value field and it is not used within the comments of a valid field.
     */
    private FieldDoc getFieldDoc(Configuration config, Tag tag, String name) {
        if (name == null || name.length() == 0) {
            //Base case: no label.
            if (tag.holder() instanceof FieldDoc) {
                return (FieldDoc) tag.holder();
            } else {
                //This should never ever happen.
                throw new DocletAbortException();
            }
        }
        StringTokenizer st = new StringTokenizer(name, "#");
        String memberName = null;
        ClassDoc cd = null;
        if (st.countTokens() == 1) {
            //Case 2:  @value in same class.
            Doc holder = tag.holder();
            if (holder instanceof MemberDoc) {
                cd = ((MemberDoc) holder).containingClass();
            } else if (holder instanceof ClassDoc) {
                cd = (ClassDoc) holder;
            }
            memberName = st.nextToken();
        } else {
            //Case 3: @value in different class.
            cd = config.root.classNamed(st.nextToken());
            memberName = st.nextToken();
        }
        if (cd == null) {
            return null;
        }
        FieldDoc[] fields = cd.fields();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].name().equals(memberName)) {
                return fields[i];
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getTagletOutput(Tag tag, TagletWriter writer) {
        FieldDoc field = getFieldDoc(
            writer.configuration(), tag, tag.text());
        if (field == null) {
            //Reference is unknown.
            writer.getMsgRetriever().warning(tag.holder().position(),
                "doclet.value_tag_invalid_reference", tag.text());
        } else if (field.constantValue() != null) {
            return writer.valueTagOutput(field,
                Util.escapeHtmlChars(field.constantValueExpression()),
                ! field.equals(tag.holder()));
        } else {
            //Referenced field is not a constant.
            writer.getMsgRetriever().warning(tag.holder().position(),
                "doclet.value_tag_invalid_constant", field.name());
        }
        return writer.getOutputInstance();
    }
}
