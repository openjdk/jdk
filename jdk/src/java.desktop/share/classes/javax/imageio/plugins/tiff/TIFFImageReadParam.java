/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
package javax.imageio.plugins.tiff;

import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageReadParam;

/**
 * A subclass of {@link ImageReadParam} allowing control over
 * the TIFF reading process.
 *
 * <p> Because TIFF is an extensible format, the reader requires
 * information about any tags used by TIFF extensions in order to emit
 * meaningful metadata.  Also, TIFF extensions may define new
 * compression types.  Both types of information about extensions may
 * be provided by this interface.
 *
 * <p> Additional TIFF tags must be organized into
 * <code>TIFFTagSet</code>s.  A <code>TIFFTagSet</code> may be
 * provided to the reader by means of the
 * <code>addAllowedTagSet</code> method.  By default, the tag sets
 * <code>BaselineTIFFTagSet</code>, <code>FaxTIFFTagSet</code>,
 * <code>ExifParentTIFFTagSet</code>, and <code>GeoTIFFTagSet</code>
 * are included.
 *
 * @since 1.9
 */
public class TIFFImageReadParam extends ImageReadParam {

    private List<TIFFTagSet> allowedTagSets = new ArrayList<TIFFTagSet>(4);

    /**
     * Constructs a <code>TIFFImageReadParam</code>.  Tags defined by
     * the <code>TIFFTagSet</code>s <code>BaselineTIFFTagSet</code>,
     * <code>FaxTIFFTagSet</code>, <code>ExifParentTIFFTagSet</code>, and
     * <code>GeoTIFFTagSet</code> will be supported.
     *
     * @see BaselineTIFFTagSet
     * @see FaxTIFFTagSet
     * @see ExifParentTIFFTagSet
     * @see GeoTIFFTagSet
     */
    public TIFFImageReadParam() {
        addAllowedTagSet(BaselineTIFFTagSet.getInstance());
        addAllowedTagSet(FaxTIFFTagSet.getInstance());
        addAllowedTagSet(ExifParentTIFFTagSet.getInstance());
        addAllowedTagSet(GeoTIFFTagSet.getInstance());
    }

    /**
     * Adds a <code>TIFFTagSet</code> object to the list of allowed
     * tag sets.
     *
     * @param tagSet a <code>TIFFTagSet</code>.
     *
     * @throws IllegalArgumentException if <code>tagSet</code> is
     * <code>null</code>.
     */
    public void addAllowedTagSet(TIFFTagSet tagSet) {
        if (tagSet == null) {
            throw new IllegalArgumentException("tagSet == null!");
        }
        allowedTagSets.add(tagSet);
    }

    /**
     * Removes a <code>TIFFTagSet</code> object from the list of
     * allowed tag sets.  Removal is based on the <code>equals</code>
     * method of the <code>TIFFTagSet</code>, which is normally
     * defined as reference equality.
     *
     * @param tagSet a <code>TIFFTagSet</code>.
     *
     * @throws IllegalArgumentException if <code>tagSet</code> is
     * <code>null</code>.
     */
    public void removeAllowedTagSet(TIFFTagSet tagSet) {
        if (tagSet == null) {
            throw new IllegalArgumentException("tagSet == null!");
        }
        allowedTagSets.remove(tagSet);
    }

    /**
     * Returns a <code>List</code> containing the allowed
     * <code>TIFFTagSet</code> objects.
     *
     * @return a <code>List</code> of <code>TIFFTagSet</code>s.
     */
    public List<TIFFTagSet> getAllowedTagSets() {
        return allowedTagSets;
    }
}
