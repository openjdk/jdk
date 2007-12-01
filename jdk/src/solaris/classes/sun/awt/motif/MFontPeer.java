/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.motif;

import java.awt.GraphicsEnvironment;
import sun.awt.PlatformFont;

public class MFontPeer extends PlatformFont {

    /*
     * XLFD name for XFontSet.
     */
    private String xfsname;

    /*
     * converter name for this XFontSet encoding.
     */
    private String converter;

    static {
        if (!GraphicsEnvironment.isHeadless()) {
            initIDs();
        }
    }

    /**
     * Initialize JNI field and method IDs for fields that may be
       accessed from C.
     */
    private static native void initIDs();

    public MFontPeer(String name, int style){
        super(name, style);

        if (fontConfig != null) {
            xfsname = ((MFontConfiguration) fontConfig).getMotifFontSet(familyName, style);
        }
    }

    protected char getMissingGlyphCharacter() {
        return '\u274F';
    }
}
