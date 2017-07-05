/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util.stream;

/**
 * A {@code CloseableStream} is a {@code Stream} that can be closed.
 * The close method is invoked to release resources that the object is
 * holding (such as open files).
 *
 * @param <T> The type of stream elements
 * @since 1.8
 */
public interface CloseableStream<T> extends Stream<T>, AutoCloseable {

    /**
     * Closes this resource, relinquishing any underlying resources.
     * This method is invoked automatically on objects managed by the
     * {@code try}-with-resources statement.  Does nothing if called when
     * the resource has already been closed.
     *
     * This method does not allow throwing checked {@code Exception}s like
     * {@link AutoCloseable#close() AutoCloseable.close()}. Cases where the
     * close operation may fail require careful attention by implementers. It
     * is strongly advised to relinquish the underlying resources and to
     * internally <em>mark</em> the resource as closed. The {@code close}
     * method is unlikely to be invoked more than once and so this ensures
     * that the resources are released in a timely manner. Furthermore it
     * reduces problems that could arise when the resource wraps, or is
     * wrapped, by another resource.
     *
     * @see AutoCloseable#close()
     */
    void close();
}
