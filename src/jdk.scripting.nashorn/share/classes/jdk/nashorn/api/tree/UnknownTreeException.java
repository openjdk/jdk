/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.tree;


/**
 * Indicates that an unknown kind of Tree was encountered.  This
 * can occur if the language evolves and new kinds of Trees are
 * added to the {@code Tree} hierarchy.  May be thrown by a
 * {@linkplain TreeVisitor tree visitor} to indicate that the
 * visitor was created for a prior version of the language.
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @since 9
 */
@Deprecated(since="11", forRemoval=true)
public class UnknownTreeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private transient final Tree tree;
    private transient final Object parameter;

    /**
     * Creates a new {@code UnknownTreeException}.  The {@code p}
     * parameter may be used to pass in an additional argument with
     * information about the context in which the unknown element was
     * encountered; for example, the visit methods of {@link
     * TreeVisitor} may pass in their additional parameter.
     *
     * @param t the unknown tree, may be {@code null}
     * @param p an additional parameter, may be {@code null}
     */
    public UnknownTreeException(final Tree t, final Object p) {
        super("Unknown tree: " + t);
        this.tree = t;
        this.parameter = p;
    }

    /**
     * Returns the unknown tree.
     * The value may be unavailable if this exception has been
     * serialized and then read back in.
     *
     * @return the unknown element, or {@code null} if unavailable
     */
    public Tree getUnknownTree() {
        return tree;
    }

    /**
     * Returns the additional argument.
     * The value may be unavailable if this exception has been
     * serialized and then read back in.
     *
     * @return the additional argument
     */
    public Object getArgument() {
        return parameter;
    }
}
