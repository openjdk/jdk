/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

/**
 * macOS sets the creation time through setattrlist(2), which the other BSDs
 * do not have.  There is nothing to add to the generic Unix views there, so
 * these are the Unix ones under the name BsdFileSystemProvider asks for.
 */
class BsdFileAttributeViews {
    static UnixFileAttributeViews.Basic createBasicView(UnixPath file,
                                                        boolean followLinks) {
        return UnixFileAttributeViews.createBasicView(file, followLinks);
    }

    static UnixFileAttributeViews.Posix createPosixView(UnixPath file,
                                                        boolean followLinks) {
        return UnixFileAttributeViews.createPosixView(file, followLinks);
    }

    static UnixFileAttributeViews.Unix createUnixView(UnixPath file,
                                                      boolean followLinks) {
        return UnixFileAttributeViews.createUnixView(file, followLinks);
    }
}
