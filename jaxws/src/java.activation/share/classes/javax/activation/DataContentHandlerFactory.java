/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package javax.activation;

/**
 * This interface defines a factory for <code>DataContentHandlers</code>. An
 * implementation of this interface should map a MIME type into an
 * instance of DataContentHandler. The design pattern for classes implementing
 * this interface is the same as for the ContentHandler mechanism used in
 * <code>java.net.URL</code>.
 *
 * @since 1.6
 */

public interface DataContentHandlerFactory {

    /**
     * Creates a new DataContentHandler object for the MIME type.
     *
     * @param mimeType the MIME type to create the DataContentHandler for.
     * @return The new <code>DataContentHandler</code>, or <i>null</i>
     * if none are found.
     */
    public DataContentHandler createDataContentHandler(String mimeType);
}
