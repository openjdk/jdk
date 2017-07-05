/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.cs.ext;

import java.nio.charset.Charset;

public class ISO2022_JP_2 extends ISO2022_JP
{
    public ISO2022_JP_2() {
        super("ISO-2022-JP-2",
              ExtendedCharsets.aliasesFor("ISO-2022-JP-2"));
    }

    public String historicalName() {
        return "ISO2022JP2";
    }

    public boolean contains(Charset cs) {
      return super.contains(cs) ||
             (cs instanceof JIS_X_0212) ||
             (cs instanceof ISO2022_JP_2);
    }

    protected DoubleByteDecoder get0212Decoder() {
        return new JIS_X_0212_Decoder(this);
    }

    protected DoubleByteEncoder get0212Encoder() {
        return new JIS_X_0212_Encoder(this);
    }

}
