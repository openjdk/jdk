/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef FILEATTRIBUTE_H
#define FILEATTRIBUTE_H

enum FileAttribute {
    faArchive = FILE_ATTRIBUTE_ARCHIVE,
    faCompressed = FILE_ATTRIBUTE_COMPRESSED,
    faDevice = FILE_ATTRIBUTE_DEVICE,
    faDirectory = FILE_ATTRIBUTE_DIRECTORY,
    faEncrypted = FILE_ATTRIBUTE_ENCRYPTED,
    faHidden = FILE_ATTRIBUTE_HIDDEN,
    faNormal = FILE_ATTRIBUTE_NORMAL,
    faNotContentIndexed = FILE_ATTRIBUTE_NOT_CONTENT_INDEXED,
    faOffline = FILE_ATTRIBUTE_OFFLINE,
    faSystem = FILE_ATTRIBUTE_SYSTEM,
    faSymbolicLink = FILE_ATTRIBUTE_REPARSE_POINT,
    faSparceFile = FILE_ATTRIBUTE_SPARSE_FILE,
    faReadOnly = FILE_ATTRIBUTE_READONLY,
    faTemporary = FILE_ATTRIBUTE_TEMPORARY,
    faVirtual = FILE_ATTRIBUTE_VIRTUAL
};

#endif // FILEATTRIBUTE_H

