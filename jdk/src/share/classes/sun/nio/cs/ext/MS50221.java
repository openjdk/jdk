/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.cs.ext;

import java.nio.charset.Charset;

public class MS50221 extends ISO2022_JP
{
    public MS50221() {
        super("x-windows-50221",
              ExtendedCharsets.aliasesFor("x-windows-50221"));
    }

    public String historicalName() {
        return "MS50221";
    }

    public boolean contains(Charset cs) {
      return super.contains(cs) ||
             (cs instanceof JIS_X_0212) ||
             (cs instanceof MS50221);
    }

    protected short[] getDecIndex1() {
        return JIS_X_0208_MS5022X_Decoder.index1;
    }

    protected String[] getDecIndex2() {
        return JIS_X_0208_MS5022X_Decoder.index2;
    }

    protected DoubleByteDecoder get0212Decoder() {
        return new JIS_X_0212_MS5022X_Decoder(this);
    }

    protected short[] getEncIndex1() {
        return JIS_X_0208_MS5022X_Encoder.index1;
    }

    protected String[] getEncIndex2() {
        return JIS_X_0208_MS5022X_Encoder.index2;
    }

    protected DoubleByteEncoder get0212Encoder() {
        return new JIS_X_0212_MS5022X_Encoder(this);
    }

    protected boolean doSBKANA() {
        return true;
    }
}
