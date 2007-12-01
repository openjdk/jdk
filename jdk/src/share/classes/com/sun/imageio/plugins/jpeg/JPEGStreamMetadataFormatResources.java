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

public class JPEGStreamMetadataFormatResources
       extends JPEGMetadataFormatResources {

    public JPEGStreamMetadataFormatResources() {}

    protected Object[][] getContents() {
        // return a copy of commonContents; in theory we want a deep clone
        // of commonContents, but since it only contains (immutable) Strings,
        // this shallow copy is sufficient
        Object[][] commonCopy = new Object[commonContents.length][2];
        for (int i = 0; i < commonContents.length; i++) {
            commonCopy[i][0] = commonContents[i][0];
            commonCopy[i][1] = commonContents[i][1];
        }
        return commonCopy;
    }
}
