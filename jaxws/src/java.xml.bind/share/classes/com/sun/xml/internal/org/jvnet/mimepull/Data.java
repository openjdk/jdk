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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.nio.ByteBuffer;

/**
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
interface Data {

    /**
     * size of the chunk given by the parser
     *
     * @return size of the chunk
     */
    int size();

    /**
     * TODO: should the return type be ByteBuffer ??
     * Return part's partial data. The data is read only.
     *
     * @return a byte array which contains {#size()} bytes. The returned
     *         array may be larger than {#size()} bytes and contains data
     *         from offset 0.
     */
    byte[] read();

    /**
     * Write this partial data to a file
     *
     * @param file to which the data needs to be written
     * @return file pointer before the write operation(at which the data is
     *         written from)
     */
    long writeTo(DataFile file);

    /**
     * Factory method to create a Data. The implementation could
     * be file based one or memory based one.
     *
     * @param dataHead start of the linked list of data objects
     * @param buf contains partial content for a part
     * @return Data
     */
    Data createNext(DataHead dataHead, ByteBuffer buf);
}
