/*
 * Copyright (c) 1996, 2005, Oracle and/or its affiliates. All rights reserved.
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
package sun.awt;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import sun.nio.cs.HistoricallyNamedCharset;

public class FontDescriptor implements Cloneable {

    static {
        NativeLibLoader.loadLibraries();
        initIDs();
    }

    String nativeName;
    public CharsetEncoder encoder;
    String charsetName;
    private int[] exclusionRanges;

    public FontDescriptor(String nativeName, CharsetEncoder encoder,
                          int[] exclusionRanges){

        this.nativeName = nativeName;
        this.encoder = encoder;
        this.exclusionRanges = exclusionRanges;
        this.useUnicode = false;
        Charset cs = encoder.charset();
        if (cs instanceof HistoricallyNamedCharset)
            this.charsetName = ((HistoricallyNamedCharset)cs).historicalName();
        else
            this.charsetName = cs.name();

    }

    public String getNativeName() {
        return nativeName;
    }

    public CharsetEncoder getFontCharsetEncoder() {
        return encoder;
    }

    public String getFontCharsetName() {
        return charsetName;
    }

    public int[] getExclusionRanges() {
        return exclusionRanges;
    }

    /**
     * Return true if the character is exclusion character.
     */
    public boolean isExcluded(char ch){
        for (int i = 0; i < exclusionRanges.length; ){

            int lo = (exclusionRanges[i++]);
            int up = (exclusionRanges[i++]);

            if (ch >= lo && ch <= up){
                return true;
            }
        }
        return false;
    }

    public String toString() {
        return super.toString() + " [" + nativeName + "|" + encoder + "]";
    }

    /**
     * Initialize JNI field and method IDs
     */
    private static native void initIDs();


    public CharsetEncoder unicodeEncoder;
    boolean useUnicode; // set to true from native code on Unicode-based systems

    public boolean useUnicode() {
        if (useUnicode && unicodeEncoder == null) {
            try {
                this.unicodeEncoder = isLE?
                    StandardCharsets.UTF_16LE.newEncoder():
                    StandardCharsets.UTF_16BE.newEncoder();
            } catch (IllegalArgumentException x) {}
        }
        return useUnicode;
    }
    static boolean isLE;
    static {
        String enc = (String) java.security.AccessController.doPrivileged(
           new sun.security.action.GetPropertyAction("sun.io.unicode.encoding",
                                                          "UnicodeBig"));
        isLE = !"UnicodeBig".equals(enc);
    }
}
