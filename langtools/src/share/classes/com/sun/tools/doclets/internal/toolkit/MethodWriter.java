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
 * The interface for writing method output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */

public interface MethodWriter {

    /**
     * Write the header for the method documentation.
     *
     * @param classDoc the class that the methods belong to.
     * @param header the header to write.
     */
    public void writeHeader(ClassDoc classDoc, String header);

    /**
     * Write the method header for the given method.
     *
     * @param method the method being documented.
     * @param isFirst the flag to indicate whether or not the method is the
     *        first to be documented.
     */
    public void writeMethodHeader(MethodDoc method, boolean isFirst);

    /**
     * Write the signature for the given method.
     *
     * @param method the method being documented.
     */
    public void writeSignature(MethodDoc method);

    /**
     * Write the deprecated output for the given method.
     *
     * @param method the method being documented.
     */
    public void writeDeprecated(MethodDoc method);

    /**
     * Write the comments for the given method.
     *
     * @param holder the holder type (not erasure) of the method.
     * @param method the method being documented.
     */
    public void writeComments(Type holder, MethodDoc method);

    /**
     * Write the tag output for the given method.
     *
     * @param method the method being documented.
     */
    public void writeTags(MethodDoc method);

    /**
     * Write the method footer.
     */
    public void writeMethodFooter();

    /**
     * Write the footer for the method documentation.
     *
     * @param classDoc the class that the methods belong to.
     */
    public void writeFooter(ClassDoc classDoc);

    /**
     * Close the writer.
     */
    public void close() throws IOException;
}
