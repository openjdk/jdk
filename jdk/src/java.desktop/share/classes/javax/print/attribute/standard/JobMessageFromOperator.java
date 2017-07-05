/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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
package javax.print.attribute.standard;

import java.util.Locale;

import javax.print.attribute.Attribute;
import javax.print.attribute.TextSyntax;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class JobMessageFromOperator is a printing attribute class, a text attribute,
 * that provides a message from an operator, system administrator, or
 * "intelligent" process to indicate to the end user the reasons for
 * modification or other management action taken on a job.
 * <P>
 * A Print Job's attribute set includes zero instances or one instance of a
 * JobMessageFromOperator attribute, not more than one instance. A new
 * JobMessageFromOperator attribute replaces an existing JobMessageFromOperator
 * attribute, if any. In other words, JobMessageFromOperator is not intended to
 * be a history log. If it wishes, the client can detect changes to a Print
 * Job's JobMessageFromOperator attribute and maintain the client's own history
 * log of the JobMessageFromOperator attribute values.
 * <P>
 * <B>IPP Compatibility:</B> The string value gives the IPP name value. The
 * locale gives the IPP natural language. The category name returned by
 * {@code getName()} gives the IPP attribute name.
 *
 * @author  Alan Kaminsky
 */
public final class JobMessageFromOperator extends TextSyntax
        implements PrintJobAttribute {

    private static final long serialVersionUID = -4620751846003142047L;

    /**
     * Constructs a new job message from operator attribute with the given
     * message and locale.
     *
     * @param  message  Message.
     * @param  locale   Natural language of the text string. null
     * is interpreted to mean the default locale as returned
     * by {@code Locale.getDefault()}
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if {@code message} is null.
     */
    public JobMessageFromOperator(String message, Locale locale) {
        super (message, locale);
    }

    /**
     * Returns whether this job message from operator attribute is equivalent to
     * the passed in object. To be equivalent, all of the following conditions
     * must be true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class JobMessageFromOperator.
     * <LI>
     * This job message from operator attribute's underlying string and
     * {@code object}'s underlying string are equal.
     * <LI>
     * This job message from operator attribute's locale and
     * {@code object}'s locale are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this job
     *          message from operator attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals (object) &&
                object instanceof JobMessageFromOperator);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class JobMessageFromOperator, the
     * category is class JobMessageFromOperator itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return JobMessageFromOperator.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class JobMessageFromOperator, the
     * category name is {@code "job-message-from-operator"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "job-message-from-operator";
    }

}
