/*
 * Copyright (c) 1997, 2004, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.datatransfer;

import java.util.Map;


/**
 * A two-way Map between "natives" (Strings), which correspond to platform-
 * specfic data formats, and "flavors" (DataFlavors), which corerspond to
 * platform-independent MIME types. FlavorMaps need not be symmetric, but
 * typically are.
 *
 *
 * @since 1.2
 */
public interface FlavorMap {

    /**
     * Returns a <code>Map</code> of the specified <code>DataFlavor</code>s to
     * their corresponding <code>String</code> native. The returned
     * <code>Map</code> is a modifiable copy of this <code>FlavorMap</code>'s
     * internal data. Client code is free to modify the <code>Map</code>
     * without affecting this object.
     *
     * @param flavors an array of <code>DataFlavor</code>s which will be the
     *        key set of the returned <code>Map</code>. If <code>null</code> is
     *        specified, a mapping of all <code>DataFlavor</code>s currently
     *        known to this <code>FlavorMap</code> to their corresponding
     *        <code>String</code> natives will be returned.
     * @return a <code>java.util.Map</code> of <code>DataFlavor</code>s to
     *         <code>String</code> natives
     */
    Map<DataFlavor,String> getNativesForFlavors(DataFlavor[] flavors);

    /**
     * Returns a <code>Map</code> of the specified <code>String</code> natives
     * to their corresponding <code>DataFlavor</code>. The returned
     * <code>Map</code> is a modifiable copy of this <code>FlavorMap</code>'s
     * internal data. Client code is free to modify the <code>Map</code>
     * without affecting this object.
     *
     * @param natives an array of <code>String</code>s which will be the
     *        key set of the returned <code>Map</code>. If <code>null</code> is
     *        specified, a mapping of all <code>String</code> natives currently
     *        known to this <code>FlavorMap</code> to their corresponding
     *        <code>DataFlavor</code>s will be returned.
     * @return a <code>java.util.Map</code> of <code>String</code> natives to
     *         <code>DataFlavor</code>s
     */
    Map<String,DataFlavor> getFlavorsForNatives(String[] natives);
}
