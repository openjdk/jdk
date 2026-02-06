/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.util.Base64;
import java.util.Locale;
import javax.crypto.spec.Argon2ParameterSpec;
import javax.crypto.spec.Argon2ParameterSpec.Type;
import javax.crypto.spec.Argon2ParameterSpec.Version;

/*
 * Utility class for handling the Argon2 PHC String following the
 * <a href=https://github.com/P-H-C/phc-string-format/blob/master/phc-sf-spec.md>
 * PHC string format</a> specification
 */
public final class Argon2Util {

    private static Base64.Encoder enc = Base64.getEncoder().withoutPadding();
    private static Base64.Decoder dec = Base64.getDecoder();

    // encode the Argon2 parameters in the order of m, t, p, keyid, data
    private static String encodeParams(int m, int t, int p, byte[] k,
            byte[] x) {
        String baseFormat = "m=%d,t=%d,p=%d" +
                (k.length != 0 ? ",keyid=%s" : "") +
                (x.length != 0 ? ",data=%s" : "");
        switch (baseFormat.length()) {
            case 14 ->{ return String.format(baseFormat, m, t, p); }
            case 23 ->{ return String.format(baseFormat, m, t, p,
                    enc.encodeToString(k)); }
            case 22 ->{ return String.format(baseFormat, m, t, p,
                    enc.encodeToString(x)); }
            case 31 ->{ return String.format(baseFormat, m, t, p,
                    enc.encodeToString(k), enc.encodeToString(x)); }
            default ->{ throw new RuntimeException
                    ("Unsupported params format: " + baseFormat);
            }
        }
    }

    // Used by both javax.crypto.spec.Argon2ParameterSpec and
    // com.sun.crypto.provider.Argon2DerivedKey
    public static String encodeHash(Argon2ParameterSpec spec, byte[] tag) {
        String params = encodeParams(spec.memoryKB(), spec.iterations(),
                spec.parallelism(), spec.secret(), spec.ad());
        if (tag != null) {
            return String.format("$%s$v=%d$%s$%s$%s",
                spec.type().name().toLowerCase(Locale.ROOT),
                spec.version().value(), params,
                enc.encodeToString(spec.nonce()),
                enc.encodeToString(tag));
        } else { // special case for Argon2ParameterSpec.toString()
            return String.format("$%s$v=%d$%s$%s",
                spec.type().name().toLowerCase(Locale.ROOT),
                spec.version().value(), params,
                enc.encodeToString(spec.nonce()));
        }
    }

    // Used by javax.crypto.spec.Argon2ParameterSpec.Builder
    public static Argon2ParameterSpec.Builder decodeHash(String str)
            throws IllegalArgumentException {
        // parse the encoded hash; format = $type$version$params$salt$hash
        String[] values = str.split("\\$");
        if (values.length != 6) {
            throw new IllegalArgumentException("Invalid Argon2 encoded hash");
        }

        if (values[0].trim().length() != 0) {
            throw new IllegalArgumentException("Illegal prefix " + values[0]);
        } else {
            Type type = Type.valueOf(values[1].toUpperCase());
            Version ver = Version.get(values[2].split("=")[1]);
            int mKB = -1, passes = -1, p = -1;
            byte[] k = null, x = null;
            String[] assignments = values[3].split(",");
            for (int i = 0; i < assignments.length; i++) {
                String[] pair = assignments[i].split("=");
                String varName = pair[0].trim();
                String varValue = pair[1].trim();
                switch (varName) {
                    case "m"->{ mKB = Integer.parseInt(varValue); }
                    case "t"->{ passes = Integer.parseInt(varValue); }
                    case "p"->{ p = Integer.parseInt(varValue); }
                    case "keyid"->{ k = dec.decode(varValue); }
                    case "data"->{ x = dec.decode(varValue); }
                    default ->{ throw new IllegalArgumentException
                            ("Unsupported parameter " + varName);
                    }
                }
            }
            byte[] salt = dec.decode(values[4].trim());
            // use the trailing hash to decide tagLen
            byte[] tag = dec.decode(values[5].trim());
            int tagLen = tag.length;
            Argon2ParameterSpec.Builder builder =
                    Argon2ParameterSpec.newBuilder(type).version(ver)
                    .memoryKB(mKB).iterations(passes).parallelism(p)
                    .nonce(salt).tagLen(tagLen);
            if (k != null) {
                builder.secret(k);
            }
            if (x != null) {
                builder.ad(x);
            }
            return builder;
        }
    }
}
