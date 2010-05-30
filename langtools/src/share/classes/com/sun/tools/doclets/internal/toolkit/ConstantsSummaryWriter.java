/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;
import java.util.*;
import java.io.*;

/**
 * The interface for writing constants summary output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */

public interface ConstantsSummaryWriter {

    /**
     * Write the header for the summary.
     */
    public abstract void writeHeader();

    /**
     * Write the footer for the summary.
     */
    public abstract void writeFooter();

    /**
     * Close the writer.
     */
    public abstract void close() throws IOException;

    /**
     * Write the header for the index.
     */
    public abstract void writeContentsHeader();

    /**
     * Write the footer for the index.
     */
    public abstract void writeContentsFooter();

    /**
     * Add the given package name to the index.
     * @param pkg                    the {@link PackageDoc} to index.
     * @param parsedPackageName      the parsed package name.  We only Write the
     *                               first 2 directory levels of the package
     *                               name. For example, java.lang.ref would be
     *                               indexed as java.lang.*.
     * @param WriteedPackageHeaders the set of package headers that have already
     *                              been indexed.  We don't want to index
     *                              something more than once.
     */
    public abstract void writeLinkToPackageContent(PackageDoc pkg, String parsedPackageName,
        Set<String> WriteedPackageHeaders);

    /**
     * Write the given package name.
     * @param pkg                    the {@link PackageDoc} to index.
     * @param parsedPackageName      the parsed package name.  We only Write the
     *                               first 2 directory levels of the package
     *                               name. For example, java.lang.ref would be
     *                               indexed as java.lang.*.
     */
    public abstract void writePackageName(PackageDoc pkg,
        String parsedPackageName);

    /**
     * Write the heading for the current table of constants for a given class.
     * @param cd the class whose constants are being documented.
     */
    public abstract void writeConstantMembersHeader(ClassDoc cd);

    /**
     * Document the given constants.
     * @param cd the class whose constants are being documented.
     * @param fields the constants being documented.
     */
    public abstract void writeConstantMembers(ClassDoc cd, List<FieldDoc> fields);

    /**
     * Document the given constants.
     * @param cd the class whose constants are being documented.
     */
    public abstract void writeConstantMembersFooter(ClassDoc cd);

}
