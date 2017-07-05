/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api;

import com.sun.istack.internal.NotNull;

/**
 * Extended version of {@link Component}.  Allows component to return multiple
 * SPI implementations through an {@link Iterable}.
 *
 * @since 2.2.6
 */
public interface ComponentEx extends Component {
    /**
     * Gets an iterator of implementations of the specified SPI.
     *
     * <p>
     * This method works as a kind of directory service
     * for SPIs, allowing various components to define private contract
     * and talk to each other.  However unlike {@link Component#getSPI(java.lang.Class)}, this
     * method can support cases where there is an ordered collection (defined
     * by {@link Iterable} of implementations.  The SPI contract should define
     * whether lookups are for the first appropriate implementation or whether
     * all returned implementations should be used.
     *
     * @return
     *      non-null {@link Iterable} of the SPI's provided by this object.  Iterator may have no values.
     */
    @NotNull <S> Iterable<S> getIterableSPI(@NotNull Class<S> spiType);
}
