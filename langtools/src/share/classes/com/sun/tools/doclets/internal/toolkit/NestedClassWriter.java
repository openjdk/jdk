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
 * The interface for writing class output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */

public interface NestedClassWriter {

    /**
     * Write the classes summary header for the given class.
     *
     * @param nestedClass the class the summary belongs to.
     */
    public void writeNestedClassSummaryHeader(ClassDoc nestedClass);

    /**
     * Write the class summary for the given class and class.
     *
     * @param classDoc the class the summary belongs to.
     * @param nestedClass the nested class that I am summarizing.
     */
    public void writeNestedClassSummary(ClassDoc classDoc, ClassDoc nestedClass);

    /**
     * Write the classes summary footer for the given class.
     *
     * @param nestedClass the class the summary belongs to.
     */
    public void writeNestedClassSummaryFooter(ClassDoc nestedClass);

    /**
     * Write the inherited classes summary header for the given class.
     *
     * @param nestedClass the class the summary belongs to.
     */
    public void writeInheritedNestedClassSummaryHeader(ClassDoc nestedClass);

    /**
     * Write the inherited nested class summary for the given class and nested
     * class.
     *
     * @param classDoc the class the inherited nested class belongs to.
     * @param nestedClass the inherited nested class that I am summarizing.
     * @param isFirst true if this is the first member in the list.
     */
    public void writeInheritedNestedClassSummary(ClassDoc classDoc,
            ClassDoc nestedClass, boolean isFirst);

    /**
     * Write the inherited classes summary footer for the given class.
     *
     * @param nestedClass the class the summary belongs to.
     */
    public void writeInheritedNestedClassSummaryFooter(ClassDoc nestedClass);

    /**
     * Close the writer.
     */
    public void close() throws IOException;
}
