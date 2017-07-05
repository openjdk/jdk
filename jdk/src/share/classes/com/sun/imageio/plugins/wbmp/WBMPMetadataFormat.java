/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.imageio.plugins.wbmp;

import java.util.Arrays;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataFormat;
import javax.imageio.metadata.IIOMetadataFormatImpl;

public class WBMPMetadataFormat extends IIOMetadataFormatImpl {

    private static IIOMetadataFormat instance = null;

    private WBMPMetadataFormat() {
        super(WBMPMetadata.nativeMetadataFormatName,
              CHILD_POLICY_SOME);

        // root -> ImageDescriptor
        addElement("ImageDescriptor",
                   WBMPMetadata.nativeMetadataFormatName,
                   CHILD_POLICY_EMPTY);

        addAttribute("ImageDescriptor", "WBMPType",
                     DATATYPE_INTEGER, true, "0");

        addAttribute("ImageDescriptor", "Width",
                     DATATYPE_INTEGER, true, null,
                     "0", "65535", true, true);
        addAttribute("ImageDescriptor", "Height",
                     DATATYPE_INTEGER, true, null,
                     "1", "65535", true, true);
    }



    public boolean canNodeAppear(String elementName,
                                 ImageTypeSpecifier imageType) {
        return true;
    }

    public static synchronized IIOMetadataFormat getInstance() {
        if (instance == null) {
            instance = new WBMPMetadataFormat();
        }
        return instance;
    }
}
