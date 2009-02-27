/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.fs;

import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;

/**
 * Base implementation of FileStoreSpaceAttributeView
 */

abstract class AbstractFileStoreSpaceAttributeView
    implements FileStoreSpaceAttributeView
{
    private static final String TOTAL_SPACE_NAME = "totalSpace";
    private static final String USABLE_SPACE_NAME = "usableSpace";
    private static final String UNALLOCATED_SPACE_NAME = "unallocatedSpace";

    @Override
    public final String name() {
        return "space";
    }

    @Override
    public final Object getAttribute(String attribute) throws IOException {
        FileStoreSpaceAttributes attrs = readAttributes();
        if (attribute.equals(TOTAL_SPACE_NAME))
            return attrs.totalSpace();
        if (attribute.equals(USABLE_SPACE_NAME))
            return attrs.usableSpace();
        if (attribute.equals(UNALLOCATED_SPACE_NAME))
            return attrs.unallocatedSpace();
        return null;
    }

    @Override
    public final void setAttribute(String attribute, Object value)
        throws IOException
    {
        if (attribute == null || value == null)
            throw new NullPointerException();
        throw new UnsupportedOperationException();
    }

    @Override
    public final Map<String,?> readAttributes(String first, String[] rest)
        throws IOException
    {
        boolean total = false;
        boolean usable = false;
        boolean unallocated = false;

        if (first.equals(TOTAL_SPACE_NAME)) total = true;
        else if (first.equals(USABLE_SPACE_NAME)) usable = true;
        else if (first.equals(UNALLOCATED_SPACE_NAME)) unallocated = true;
        else if (first.equals("*")) {
            total = true;
            usable = true;
            unallocated = true;
        }

        if (!total || !usable || !unallocated) {
            for (String attribute: rest) {
                if (attribute.equals("*")) {
                    total = true;
                    usable = true;
                    unallocated = true;
                    break;
                }
                if (attribute.equals(TOTAL_SPACE_NAME)) {
                    total = true;
                    continue;
                }
                if (attribute.equals(USABLE_SPACE_NAME)) {
                    usable = true;
                    continue;
                }
                if (attribute.equals(UNALLOCATED_SPACE_NAME)) {
                    unallocated = true;
                    continue;
                }
            }
        }

        FileStoreSpaceAttributes attrs = readAttributes();
        Map<String,Object> result = new HashMap<String,Object>(2);
        if (total)
            result.put(TOTAL_SPACE_NAME, attrs.totalSpace());
        if (usable)
            result.put(USABLE_SPACE_NAME, attrs.usableSpace());
        if (unallocated)
            result.put(UNALLOCATED_SPACE_NAME, attrs.unallocatedSpace());
        return Collections.unmodifiableMap(result);
    }
}
