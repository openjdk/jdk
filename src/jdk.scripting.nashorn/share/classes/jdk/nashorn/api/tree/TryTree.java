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

import java.util.List;

/**
 * A tree node for a 'try' statement.
 *
 * For example:
 * <pre>
 *   try
 *       <em>block</em>
 *   <em>catches</em>
 *   finally
 *       <em>finallyBlock</em>
 * </pre>
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @since 9
 */
@Deprecated(since="11", forRemoval=true)
public interface TryTree extends StatementTree {
    /**
     * Returns the 'try' block of this 'try' statement.
     *
     * @return the 'try' block
     */
    BlockTree getBlock();

    /**
     * Returns the list of 'catch' statements associated with this 'try'.
     *
     * @return the list of 'catch' statements associated with this 'try'.
     */
    List<? extends CatchTree> getCatches();

    /**
     * Returns the 'finally' block associated with this 'try'. This is
     * null if there is no 'finally' block associated with this 'try'.
     *
     * @return the 'finally' block associated with this 'try'.
     */
    BlockTree getFinallyBlock();
}
