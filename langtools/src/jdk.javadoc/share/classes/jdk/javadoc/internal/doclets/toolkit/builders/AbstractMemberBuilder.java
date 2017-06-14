/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.builders;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;

import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;

/**
 * The superclass for all member builders.  Member builders are only executed
 * within Class Builders.  They essentially build sub-components.  For example,
 * method documentation is a sub-component of class documentation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */
public abstract class AbstractMemberBuilder extends AbstractBuilder {

    public final Comparator<Element> comparator;
    /**
     * Construct a SubBuilder.
     * @param context a context object, providing information used in this run
     *        of the doclet.
     */
    public AbstractMemberBuilder(Context context) {
        super(context);
        comparator = utils.makeGeneralPurposeComparator();
    }

    /**
     * This method is not supported by sub-builders.
     *
     * @throws AssertionError always
     */
    @Override
    public void build() {
        // You may not call the build method in a subbuilder.
        throw new AssertionError();
    }


    /**
     * Builds the sub component if there is anything to document.
     *
     * @param node the XML element that specifies which components to document.
     * @param contentTree content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    @Override
    public void build(XMLNode node, Content contentTree) throws DocletException {
        if (hasMembersToDocument()) {
            super.build(node, contentTree);
        }
    }

    /**
     * Returns true if this subbuilder has anything to document.
     *
     * @return true if this subbuilder has anything to document
     */
    public abstract boolean hasMembersToDocument();

    public SortedSet<Element> asSortedSet(Collection<Element> members) {
        SortedSet<Element> out = new TreeSet<>(comparator);
        out.addAll(members);
        return out;
    }
}
