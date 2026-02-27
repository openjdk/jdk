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
            case 14 -> { return String.format(baseFormat, m, t, p); }
            case 23 -> { return String.format(baseFormat, m, t, p,
                    enc.encodeToString(k)); }
            case 22 -> { return String.format(baseFormat, m, t, p,
                    enc.encodeToString(x)); }
            case 31 -> { return String.format(baseFormat, m, t, p,
                    enc.encodeToString(k), enc.encodeToString(x)); }
            default -> { throw new RuntimeException
                    ("Unsupported params format: " + baseFormat);
            }
        }
    }

    private static int parseInt(String s, String varName, int max) {
        String[] pair = s.split("=");
        String name = pair[0];
        if (!varName.equals(name)) {
            throw new IllegalArgumentException("Expected " + varName +
                    ", but got " + name);
        }
        int value = Integer.parseInt(pair[1]);
        if (max != -1 && value > max) {
            throw new IllegalArgumentException("Value exceeds max " + max +
                    ": " + value);
        }
        return value;
    }

    // decode the Argon2 parameters in the order of m, t, p, keyid, data
    private static void decodeAndSetParams(String paramStr,
            Argon2ParameterSpec.Builder builder)
            throws IllegalArgumentException {

        String[] assignments = paramStr.split(",");
        // must be in the order of m ,t, p, followed by optional keyid and data
        if (assignments.length < 3 || assignments.length > 5) {
            throw new IllegalArgumentException("Invalid params: " + paramStr);
        }
        builder.memoryKiB(parseInt(assignments[0], "m", -1));
        builder.iterations(parseInt(assignments[1], "t" , -1));
        builder.parallelism(parseInt(assignments[2], "p", 256));
        int index = 3;
        while (index < assignments.length) { // keyid or data
            String[] nextPair = assignments[index++].split("=");
            byte[] value = dec.decode(nextPair[1]);
            if (nextPair[0].equals("keyid")) {
                if (value.length > 8) {
                    throw new IllegalArgumentException
                            ("keyid length should not exceed 8: " +
                            value.length);
                }
                builder.secret(value);
            } else if (nextPair[0].equals("data")) {
                if (value.length > 32) {
                    throw new IllegalArgumentException
                            ("data length should not exceed 32: " +
                            value.length);
                }
                builder.ad(value);
                break;
            } else {
                throw new IllegalArgumentException
                                ("Unsupported param name " + nextPair[0]);
            }
        }
        if (index != assignments.length) {
            throw new IllegalArgumentException("Unparsed params" +
                    assignments[index]);
        }
    }

    // Used by both javax.crypto.spec.Argon2ParameterSpec and
    // com.sun.crypto.provider.Argon2DerivedKey
    public static String encodeHash(Argon2ParameterSpec spec, byte[] tag) {
        String params = encodeParams(spec.memory(), spec.iterations(),
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
        // parse the encoded hash: $type[$version][$params][$salt[$hash]]
        String[] values = str.split("\\$");
        // requires type, the rest are optional
        if (values.length < 2 || values.length > 6) {
            throw new IllegalArgumentException("Invalid Argon2 PHC String: " +
                    str);
        }
        if (values[0].length() != 0) { // nothing before the first $
            throw new IllegalArgumentException("Illegal prefix " + values[0]);
        }

        // parse type
        Type type = Type.valueOf(values[1].toUpperCase());
        Argon2ParameterSpec.Builder builder =
                Argon2ParameterSpec.newBuilder(type);
        int idx = 1;
        while (++idx < values.length) {
            switch (idx) {
                case 2 -> {
                    Version ver = Version.get(values[2].split("=")[1]);
                    builder.version(ver);
                }
                case 3 -> {
                    decodeAndSetParams(values[3], builder);
                }
                case 4 -> {
                    byte[] salt = dec.decode(values[4]);
                    if (salt.length < 8 || salt.length > 48) {
                        throw new IllegalArgumentException
                                ("salt length should be between 8 to 48 bytes: "
                                + salt.length);
                    }
                    builder.nonce(salt);
                }
                case 5 -> {
                    // use the trailing hash to decide tagLen
                    byte[] tag = dec.decode(values[5]);
                    int tagLen = tag.length;
                    if (tagLen < 12 || tagLen > 64) {
                        throw new IllegalArgumentException
                                ("hash output length should be between 12 to 64 bytes: "
                                + tagLen);
                    }
                    builder.tagLen(tagLen);
                }
                default -> {
                    throw new RuntimeException("should never happen");
                }
            }
        }
        return builder;
    }
}
