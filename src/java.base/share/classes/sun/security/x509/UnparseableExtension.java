/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.x509;

import java.lang.reflect.Field;
import sun.security.util.HexDumpEncoder;

/**
 * An extension that cannot be parsed due to decoding errors or invalid
 * content.
 */
class UnparseableExtension extends Extension {
    private String name;
    private String exceptionDescription;
    private String exceptionMessage;

    UnparseableExtension(Extension ext, Throwable why) {
        super(ext);

        name = "";
        try {
            Class<?> extClass = OIDMap.getClass(ext.getExtensionId());
            if (extClass != null) {
                Field field = extClass.getDeclaredField("NAME");
                name = (String)(field.get(null)) + " ";
            }
        } catch (Exception e) {
            // If we cannot find the name, just ignore it
        }

        this.exceptionDescription = why.toString();
        this.exceptionMessage = why.getMessage();
    }

    String exceptionMessage() {
        return exceptionMessage;
    }

    @Override public String toString() {
        return super.toString() +
                "Unparseable " + name + "extension due to\n" +
                exceptionDescription + "\n\n" +
                new HexDumpEncoder().encodeBuffer(getExtensionValue());
    }
}
