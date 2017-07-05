/*
 * Copyright 2001-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.imageio.plugins.jpeg;

import java.util.ListResourceBundle;

public class JPEGImageReaderResources extends ListResourceBundle {

    public JPEGImageReaderResources() {}

    protected Object[][] getContents() {
        return new Object[][] {

        {Integer.toString(JPEGImageReader.WARNING_NO_EOI),
         "Truncated File - Missing EOI marker"},
        {Integer.toString(JPEGImageReader.WARNING_NO_JFIF_IN_THUMB),
         "JFIF markers not allowed in JFIF JPEG thumbnail; ignored"},
        {Integer.toString(JPEGImageReader.WARNING_IGNORE_INVALID_ICC),
         "Embedded color profile is invalid; ignored"}

        };
    }
}
