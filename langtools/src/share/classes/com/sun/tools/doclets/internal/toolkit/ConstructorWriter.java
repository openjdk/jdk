/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */

public interface ConstructorWriter {

    /**
     * Write the header for the constructor documentation.
     *
     * @param classDoc the class that the constructors belong to.
     * @param header the header to write.
     */
    public void writeHeader(ClassDoc classDoc, String header);

    /**
     * Write the constructor header for the given constructor.
     *
     * @param constructor the constructor being documented.
     * @param isFirst the flag to indicate whether or not the constructor is the
     *        first to be documented.
     */
    public void writeConstructorHeader(ConstructorDoc constructor, boolean isFirst);

    /**
     * Write the signature for the given constructor.
     *
     * @param constructor the constructor being documented.
     */
    public void writeSignature(ConstructorDoc constructor);

    /**
     * Write the deprecated output for the given constructor.
     *
     * @param constructor the constructor being documented.
     */
    public void writeDeprecated(ConstructorDoc constructor);

    /**
     * Write the comments for the given constructor.
     *
     * @param constructor the constructor being documented.
     */
    public void writeComments(ConstructorDoc constructor);

    /**
     * Write the tag output for the given constructor.
     *
     * @param constructor the constructor being documented.
     */
    public void writeTags(ConstructorDoc constructor);

    /**
     * Write the constructor footer.
     */
    public void writeConstructorFooter();

    /**
     * Write the footer for the constructor documentation.
     *
     * @param classDoc the class that the constructors belong to.
     */
    public void writeFooter(ClassDoc classDoc);

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
