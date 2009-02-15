/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.file;

import java.io.IOException;

/**
 * An interface that is implemented by objects that operate on a file. An
 * implementation of this interface is provided to the {@link Files#withDirectory
 * withDirectory} utility method so that the file action is {@link #invoke
 * invoked} for all accepted entries in the directory, after which, the directory
 * is automatically closed.
 *
 * <p> <b>Usage Example:</b>
 * Suppose we require to perform a task on all class files in a directory:
 * <pre>
 *     Path dir = ...
 *     Files.withDirectory(dir, "*.class", new FileAction&lt;Path&gt;() {
 *         public void invoke(Path entry) {
 *             :
 *         }
 *     });
 * </pre>
 *
 * @param   <T>     The type of file reference
 *
 * @since 1.7
 */

public interface FileAction<T extends FileRef> {
    /**
     * Invoked for a file.
     *
     * @param   file
     *          The file
     *
     * @throws  IOException
     *          If the block terminates due an uncaught I/O exception
     */
    void invoke(T file) throws IOException;
}
