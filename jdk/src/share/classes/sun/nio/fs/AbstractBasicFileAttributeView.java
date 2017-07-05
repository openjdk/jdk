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
import java.util.concurrent.TimeUnit;

/**
 * Base implementation of BasicFileAttributeView
 */

abstract class AbstractBasicFileAttributeView
    implements BasicFileAttributeView
{
    private static final String SIZE_NAME = "size";
    private static final String CREATION_TIME_NAME = "creationTime";
    private static final String LAST_ACCESS_TIME_NAME = "lastAccessTime";
    private static final String LAST_MODIFIED_TIME_NAME = "lastModifiedTime";
    private static final String RESOLUTION_NAME = "resolution";
    private static final String FILE_KEY_NAME = "fileKey";
    private static final String LINK_COUNT_NAME = "linkCount";
    private static final String IS_DIRECTORY_NAME = "isDirectory";
    private static final String IS_REGULAR_FILE_NAME = "isRegularFile";
    private static final String IS_SYMBOLIC_LINK_NAME = "isSymbolicLink";
    private static final String IS_OTHER_NAME = "isOther";

    protected AbstractBasicFileAttributeView() { }

    @Override
    public String name() {
        return "basic";
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        BasicFileAttributes attrs = readAttributes();
        if (attribute.equals(SIZE_NAME))
            return attrs.size();
        if (attribute.equals(CREATION_TIME_NAME))
            return attrs.creationTime();
        if (attribute.equals(LAST_ACCESS_TIME_NAME))
            return attrs.lastAccessTime();
        if (attribute.equals(LAST_MODIFIED_TIME_NAME))
            return attrs.lastModifiedTime();
        if (attribute.equals(RESOLUTION_NAME))
            return attrs.resolution();
        if (attribute.equals(FILE_KEY_NAME))
            return attrs.fileKey();
        if (attribute.equals(LINK_COUNT_NAME))
            return attrs.linkCount();
        if (attribute.equals(IS_DIRECTORY_NAME))
            return attrs.isDirectory();
        if (attribute.equals(IS_REGULAR_FILE_NAME))
            return attrs.isRegularFile();
        if (attribute.equals(IS_SYMBOLIC_LINK_NAME))
            return attrs.isSymbolicLink();
        if (attribute.equals(IS_OTHER_NAME))
            return attrs.isOther();
        return null;
    }

    private Long toTimeValue(Object value) {
        if (value == null)
            throw new NullPointerException();
        Long time = (Long)value;
        if (time < 0L && time != -1L)
            throw new IllegalArgumentException("time value cannot be negative");
        return time;
    }

    @Override
    public void setAttribute(String attribute, Object value)
        throws IOException
    {
        if (attribute.equals(LAST_MODIFIED_TIME_NAME)) {
            setTimes(toTimeValue(value), null, null, TimeUnit.MILLISECONDS);
            return;
        }
        if (attribute.equals(LAST_ACCESS_TIME_NAME)) {
            setTimes(null, toTimeValue(value), null, TimeUnit.MILLISECONDS);
            return;
        }
        if (attribute.equals(CREATION_TIME_NAME)) {
            setTimes(null, null, toTimeValue(value), TimeUnit.MILLISECONDS);
            return;
        }
        throw new UnsupportedOperationException("'" + attribute +
            "' is unknown or read-only attribute");
    }

    /**
     *
     */
    static class AttributesBuilder {
        private Set<String> set = new HashSet<String>();
        private Map<String,Object> map = new HashMap<String,Object>();
        private boolean copyAll;

        private AttributesBuilder(String first, String[] rest) {
            if (first.equals("*")) {
                copyAll = true;
            } else {
                set.add(first);
                // copy names into the given Set bailing out if "*" is found
                for (String attribute: rest) {
                    if (attribute.equals("*")) {
                        copyAll = true;
                        break;
                    }
                    set.add(attribute);
                }
            }
        }

        /**
         * Creates builder to build up a map of the matching attributes
         */
        static AttributesBuilder create(String first, String[] rest) {
            return new AttributesBuilder(first, rest);
        }

        /**
         * Returns true if the attribute should be returned in the map
         */
        boolean match(String attribute) {
            if (copyAll)
                return true;
            return set.contains(attribute);
        }

        void add(String attribute, Object value) {
            map.put(attribute, value);
        }

        /**
         * Returns the map. Discard all references to the AttributesBuilder
         * after invoking this method.
         */
        Map<String,Object> unmodifiableMap() {
            return Collections.unmodifiableMap(map);
        }
    }

    /**
     * Invoked by readAttributes or sub-classes to add all matching basic
     * attributes to the builder
     */
    final void addBasicAttributesToBuilder(BasicFileAttributes attrs,
                                           AttributesBuilder builder)
    {
        if (builder.match(SIZE_NAME))
            builder.add(SIZE_NAME, attrs.size());
        if (builder.match(CREATION_TIME_NAME))
            builder.add(CREATION_TIME_NAME, attrs.creationTime());
        if (builder.match(LAST_ACCESS_TIME_NAME))
            builder.add(LAST_ACCESS_TIME_NAME, attrs.lastAccessTime());
        if (builder.match(LAST_MODIFIED_TIME_NAME))
            builder.add(LAST_MODIFIED_TIME_NAME, attrs.lastModifiedTime());
        if (builder.match(RESOLUTION_NAME))
            builder.add(RESOLUTION_NAME, attrs.resolution());
        if (builder.match(FILE_KEY_NAME))
            builder.add(FILE_KEY_NAME, attrs.fileKey());
        if (builder.match(LINK_COUNT_NAME))
            builder.add(LINK_COUNT_NAME, attrs.linkCount());
        if (builder.match(IS_DIRECTORY_NAME))
            builder.add(IS_DIRECTORY_NAME, attrs.isDirectory());
        if (builder.match(IS_REGULAR_FILE_NAME))
            builder.add(IS_REGULAR_FILE_NAME, attrs.isRegularFile());
        if (builder.match(IS_SYMBOLIC_LINK_NAME))
            builder.add(IS_SYMBOLIC_LINK_NAME, attrs.isSymbolicLink());
        if (builder.match(IS_OTHER_NAME))
            builder.add(IS_OTHER_NAME, attrs.isOther());
    }

    @Override
    public Map<String,?> readAttributes(String first, String[] rest)
        throws IOException
    {
        AttributesBuilder builder = AttributesBuilder.create(first, rest);
        addBasicAttributesToBuilder(readAttributes(), builder);
        return builder.unmodifiableMap();
    }
}
