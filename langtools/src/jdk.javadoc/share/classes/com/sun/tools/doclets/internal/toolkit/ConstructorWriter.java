/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit;

import java.io.*;
import com.sun.javadoc.*;

/**
 * The interface for writing constructor output.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
 */

public interface ConstructorWriter {

    /**
     * Get the constructor details tree header.
     *
     * @param classDoc the class being documented
     * @param memberDetailsTree the content tree representing member details
     * @return content tree for the constructor details header
     */
    public Content getConstructorDetailsTreeHeader(ClassDoc classDoc,
            Content memberDetailsTree);

    /**
     * Get the constructor documentation tree header.
     *
     * @param constructor the constructor being documented
     * @param constructorDetailsTree the content tree representing constructor details
     * @return content tree for the constructor documentation header
     */
    public Content getConstructorDocTreeHeader(ConstructorDoc constructor,
            Content constructorDetailsTree);

    /**
     * Get the signature for the given constructor.
     *
     * @param constructor the constructor being documented
     * @return content tree for the constructor signature
     */
    public Content getSignature(ConstructorDoc constructor);

    /**
     * Add the deprecated output for the given constructor.
     *
     * @param constructor the constructor being documented
     * @param constructorDocTree content tree to which the deprecated information will be added
     */
    public void addDeprecated(ConstructorDoc constructor, Content constructorDocTree);

    /**
     * Add the comments for the given constructor.
     *
     * @param constructor the constructor being documented
     * @param constructorDocTree the content tree to which the comments will be added
     */
    public void addComments(ConstructorDoc constructor, Content constructorDocTree);

    /**
     * Add the tags for the given constructor.
     *
     * @param constructor the constructor being documented
     * @param constructorDocTree the content tree to which the tags will be added
     */
    public void addTags(ConstructorDoc constructor, Content constructorDocTree);

    /**
     * Get the constructor details tree.
     *
     * @param memberDetailsTree the content tree representing member details
     * @return content tree for the constructor details
     */
    public Content getConstructorDetails(Content memberDetailsTree);

    /**
     * Get the constructor documentation.
     *
     * @param constructorDocTree the content tree representing constructor documentation
     * @param isLastContent true if the content to be added is the last content
     * @return content tree for the constructor documentation
     */
    public Content getConstructorDoc(Content constructorDocTree, boolean isLastContent);

    /**
     * Let the writer know whether a non public constructor was found.
     *
     * @param foundNonPubConstructor true if we found a non public constructor.
     */
    public void setFoundNonPubConstructor(boolean foundNonPubConstructor);

    /**
     * Close the writer.
     */
    public void close() throws IOException;
}
