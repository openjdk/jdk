/*
 * Copyright 2006-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.ssl;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import java.security.spec.ECParameterSpec;

import javax.net.ssl.SSLProtocolException;

/**
 * This file contains all the classes relevant to TLS Extensions for the
 * ClientHello and ServerHello messages. The extension mechanism and
 * several extensions are defined in RFC 3546. Additional extensions are
 * defined in the ECC RFC 4492.
 *
 * Currently, only the two ECC extensions are fully supported.
 *
 * The classes contained in this file are:
 *  . HelloExtensions: a List of extensions as used in the client hello
 *      and server hello messages.
 *  . ExtensionType: an enum style class for the extension type
 *  . HelloExtension: abstract base class for all extensions. All subclasses
 *      must be immutable.
 *
 *  . UnknownExtension: used to represent all parsed extensions that we do not
 *      explicitly support.
 *  . ServerNameExtension: partially implemented server_name extension.
 *  . SupportedEllipticCurvesExtension: the ECC supported curves extension.
 *  . SupportedEllipticPointFormatsExtension: the ECC supported point formats
 *      (compressed/uncompressed) extension.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 */
final class HelloExtensions {

    private List<HelloExtension> extensions;
    private int encodedLength;

    HelloExtensions() {
        extensions = Collections.emptyList();
    }

    HelloExtensions(HandshakeInStream s) throws IOException {
        int len = s.getInt16();
        extensions = new ArrayList<HelloExtension>();
        encodedLength = len + 2;
        while (len > 0) {
            int type = s.getInt16();
            int extlen = s.getInt16();
            ExtensionType extType = ExtensionType.get(type);
            HelloExtension extension;
            if (extType == ExtensionType.EXT_SERVER_NAME) {
                extension = new ServerNameExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_ELLIPTIC_CURVES) {
                extension = new SupportedEllipticCurvesExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_EC_POINT_FORMATS) {
                extension = new SupportedEllipticPointFormatsExtension(s, extlen);
            } else {
                extension = new UnknownExtension(s, extlen, extType);
            }
            extensions.add(extension);
            len -= extlen + 4;
        }
        if (len != 0) {
            throw new SSLProtocolException("Error parsing extensions: extra data");
        }
    }

    // Return the List of extensions. Must not be modified by the caller.
    List<HelloExtension> list() {
        return extensions;
    }

    void add(HelloExtension ext) {
        if (extensions.isEmpty()) {
            extensions = new ArrayList<HelloExtension>();
        }
        extensions.add(ext);
        encodedLength = -1;
    }

    HelloExtension get(ExtensionType type) {
        for (HelloExtension ext : extensions) {
            if (ext.type == type) {
                return ext;
            }
        }
        return null;
    }

    int length() {
        if (encodedLength >= 0) {
            return encodedLength;
        }
        if (extensions.isEmpty()) {
            encodedLength = 0;
        } else {
            encodedLength = 2;
            for (HelloExtension ext : extensions) {
                encodedLength += ext.length();
            }
        }
        return encodedLength;
    }

    void send(HandshakeOutStream s) throws IOException {
        int length = length();
        if (length == 0) {
            return;
        }
        s.putInt16(length - 2);
        for (HelloExtension ext : extensions) {
            ext.send(s);
        }
    }

    void print(PrintStream s) throws IOException {
        for (HelloExtension ext : extensions) {
            s.println(ext.toString());
        }
    }
}

final class ExtensionType {

    final int id;
    final String name;

    private ExtensionType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public String toString() {
        return name;
    }

    static List<ExtensionType> knownExtensions = new ArrayList<ExtensionType>(8);

    static ExtensionType get(int id) {
        for (ExtensionType ext : knownExtensions) {
            if (ext.id == id) {
                return ext;
            }
        }
        return new ExtensionType(id, "type_" + id);
    }

    private static ExtensionType e(int id, String name) {
        ExtensionType ext = new ExtensionType(id, name);
        knownExtensions.add(ext);
        return ext;
    }

    // extensions defined in RFC 3546
    final static ExtensionType EXT_SERVER_NAME            = e( 0, "server_name");
    final static ExtensionType EXT_MAX_FRAGMENT_LENGTH    = e( 1, "max_fragment_length");
    final static ExtensionType EXT_CLIENT_CERTIFICATE_URL = e( 2, "client_certificate_url");
    final static ExtensionType EXT_TRUSTED_CA_KEYS        = e( 3, "trusted_ca_keys");
    final static ExtensionType EXT_TRUNCATED_HMAC         = e( 4, "truncated_hmac");
    final static ExtensionType EXT_STATUS_REQUEST         = e( 5, "status_request");

    // extensions defined in RFC 4492 (ECC)
    final static ExtensionType EXT_ELLIPTIC_CURVES        = e(10, "elliptic_curves");
    final static ExtensionType EXT_EC_POINT_FORMATS       = e(11, "ec_point_formats");

}

abstract class HelloExtension {

    final ExtensionType type;

    HelloExtension(ExtensionType type) {
        this.type = type;
    }

    // Length of the encoded extension, including the type and length fields
    abstract int length();

    abstract void send(HandshakeOutStream s) throws IOException;

    public abstract String toString();

}

final class UnknownExtension extends HelloExtension {

    private final byte[] data;

    UnknownExtension(HandshakeInStream s, int len, ExtensionType type)
            throws IOException {
        super(type);
        data = new byte[len];
        // s.read() does not handle 0-length arrays.
        if (len != 0) {
            s.read(data);
        }
    }

    int length() {
        return 4 + data.length;
    }

    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        s.putBytes16(data);
    }

    public String toString() {
        return "Unsupported extension " + type + ", data: " + Debug.toString(data);
    }
}

// Support for the server_name extension is incomplete. Parsing is implemented
// so that we get nicer debug output, but we neither send it nor do we do
// act on it if we receive it.
final class ServerNameExtension extends HelloExtension {

    final static int NAME_HOST_NAME = 0;

    private List<ServerName> names;

    ServerNameExtension(HandshakeInStream s, int len)
            throws IOException {
        super(ExtensionType.EXT_SERVER_NAME);
        names = new ArrayList<ServerName>();
        while (len > 0) {
            ServerName name = new ServerName(s);
            names.add(name);
            len -= name.length + 2;
        }
        if (len != 0) {
            throw new SSLProtocolException("Invalid server_name extension");
        }
    }

    static class ServerName {
        final int length;
        final int type;
        final byte[] data;
        final String hostname;

        ServerName(HandshakeInStream s) throws IOException {
            length = s.getInt16();
            type = s.getInt8();
            data = s.getBytes16();
            if (type == NAME_HOST_NAME) {
                hostname = new String(data, "UTF8");
            } else {
                hostname = null;
            }
        }

        public String toString() {
            if (type == NAME_HOST_NAME) {
                return "host_name: " + hostname;
            } else {
                return "unknown-" + type + ": " + Debug.toString(data);
            }
        }
    }

    int length() {
        throw new RuntimeException("not yet supported");
    }

    void send(HandshakeOutStream s) throws IOException {
        throw new RuntimeException("not yet supported");
    }

    public String toString() {
        return "Unsupported extension " + type + ", " + names.toString();
    }
}

final class SupportedEllipticCurvesExtension extends HelloExtension {

    // the extension value to send in the ClientHello message
    static final SupportedEllipticCurvesExtension DEFAULT;

    private static final boolean fips;

    static {
        int[] ids;
        fips = SunJSSE.isFIPS();
        if (fips == false) {
            ids = new int[] {
                // NIST curves first
                // prefer NIST P-256, rest in order of increasing key length
                23, 1, 3, 19, 21, 6, 7, 9, 10, 24, 11, 12, 25, 13, 14,
                // non-NIST curves
                15, 16, 17, 2, 18, 4, 5, 20, 8, 22,
            };
        } else {
            ids = new int[] {
                // same as above, but allow only NIST curves in FIPS mode
                23, 1, 3, 19, 21, 6, 7, 9, 10, 24, 11, 12, 25, 13, 14,
            };
        }
        DEFAULT = new SupportedEllipticCurvesExtension(ids);
    }

    private final int[] curveIds;

    private SupportedEllipticCurvesExtension(int[] curveIds) {
        super(ExtensionType.EXT_ELLIPTIC_CURVES);
        this.curveIds = curveIds;
    }

    SupportedEllipticCurvesExtension(HandshakeInStream s, int len)
            throws IOException {
        super(ExtensionType.EXT_ELLIPTIC_CURVES);
        int k = s.getInt16();
        if (((len & 1) != 0) || (k + 2 != len)) {
            throw new SSLProtocolException("Invalid " + type + " extension");
        }
        curveIds = new int[k >> 1];
        for (int i = 0; i < curveIds.length; i++) {
            curveIds[i] = s.getInt16();
        }
    }

    boolean contains(int index) {
        for (int curveId : curveIds) {
            if (index == curveId) {
                return true;
            }
        }
        return false;
    }

    // Return a reference to the internal curveIds array.
    // The caller must NOT modify the contents.
    int[] curveIds() {
        return curveIds;
    }

    int length() {
        return 6 + (curveIds.length << 1);
    }

    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        int k = curveIds.length << 1;
        s.putInt16(k + 2);
        s.putInt16(k);
        for (int curveId : curveIds) {
            s.putInt16(curveId);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Extension " + type + ", curve names: {");
        boolean first = true;
        for (int curveId : curveIds) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            // first check if it is a known named curve, then try other cases.
            String oid = getCurveOid(curveId);
            if (oid != null) {
                ECParameterSpec spec = JsseJce.getECParameterSpec(oid);
                // this toString() output will look nice for the current
                // implementation of the ECParameterSpec class in the Sun
                // provider, but may not look good for other implementations.
                if (spec != null) {
                    sb.append(spec.toString().split(" ")[0]);
                } else {
                    sb.append(oid);
                }
            } else if (curveId == ARBITRARY_PRIME) {
                sb.append("arbitrary_explicit_prime_curves");
            } else if (curveId == ARBITRARY_CHAR2) {
                sb.append("arbitrary_explicit_char2_curves");
            } else {
                sb.append("unknown curve " + curveId);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // Test whether we support the curve with the given index.
    static boolean isSupported(int index) {
        if ((index <= 0) || (index >= NAMED_CURVE_OID_TABLE.length)) {
            return false;
        }
        if (fips == false) {
            // in non-FIPS mode, we support all valid indices
            return true;
        }
        return DEFAULT.contains(index);
    }

    static int getCurveIndex(ECParameterSpec params) {
        String oid = JsseJce.getNamedCurveOid(params);
        if (oid == null) {
            return -1;
        }
        Integer n = curveIndices.get(oid);
        return (n == null) ? -1 : n;
    }

    static String getCurveOid(int index) {
        if ((index > 0) && (index < NAMED_CURVE_OID_TABLE.length)) {
            return NAMED_CURVE_OID_TABLE[index];
        }
        return null;
    }

    private final static int ARBITRARY_PRIME = 0xff01;
    private final static int ARBITRARY_CHAR2 = 0xff02;

    // See sun.security.ec.NamedCurve for the OIDs
    private final static String[] NAMED_CURVE_OID_TABLE = new String[] {
        null,                   //  (0) unused
        "1.3.132.0.1",          //  (1) sect163k1, NIST K-163
        "1.3.132.0.2",          //  (2) sect163r1
        "1.3.132.0.15",         //  (3) sect163r2, NIST B-163
        "1.3.132.0.24",         //  (4) sect193r1
        "1.3.132.0.25",         //  (5) sect193r2
        "1.3.132.0.26",         //  (6) sect233k1, NIST K-233
        "1.3.132.0.27",         //  (7) sect233r1, NIST B-233
        "1.3.132.0.3",          //  (8) sect239k1
        "1.3.132.0.16",         //  (9) sect283k1, NIST K-283
        "1.3.132.0.17",         // (10) sect283r1, NIST B-283
        "1.3.132.0.36",         // (11) sect409k1, NIST K-409
        "1.3.132.0.37",         // (12) sect409r1, NIST B-409
        "1.3.132.0.38",         // (13) sect571k1, NIST K-571
        "1.3.132.0.39",         // (14) sect571r1, NIST B-571
        "1.3.132.0.9",          // (15) secp160k1
        "1.3.132.0.8",          // (16) secp160r1
        "1.3.132.0.30",         // (17) secp160r2
        "1.3.132.0.31",         // (18) secp192k1
        "1.2.840.10045.3.1.1",  // (19) secp192r1, NIST P-192
        "1.3.132.0.32",         // (20) secp224k1
        "1.3.132.0.33",         // (21) secp224r1, NIST P-224
        "1.3.132.0.10",         // (22) secp256k1
        "1.2.840.10045.3.1.7",  // (23) secp256r1, NIST P-256
        "1.3.132.0.34",         // (24) secp384r1, NIST P-384
        "1.3.132.0.35",         // (25) secp521r1, NIST P-521
    };

    private final static Map<String,Integer> curveIndices;

    static {
        curveIndices = new HashMap<String,Integer>();
        for (int i = 1; i < NAMED_CURVE_OID_TABLE.length; i++) {
            curveIndices.put(NAMED_CURVE_OID_TABLE[i], i);
        }
    }

}

final class SupportedEllipticPointFormatsExtension extends HelloExtension {

    final static int FMT_UNCOMPRESSED = 0;
    final static int FMT_ANSIX962_COMPRESSED_PRIME = 1;
    final static int FMT_ANSIX962_COMPRESSED_CHAR2 = 2;

    static final HelloExtension DEFAULT =
        new SupportedEllipticPointFormatsExtension(new byte[] {FMT_UNCOMPRESSED});

    private final byte[] formats;

    private SupportedEllipticPointFormatsExtension(byte[] formats) {
        super(ExtensionType.EXT_EC_POINT_FORMATS);
        this.formats = formats;
    }

    SupportedEllipticPointFormatsExtension(HandshakeInStream s, int len)
            throws IOException {
        super(ExtensionType.EXT_EC_POINT_FORMATS);
        formats = s.getBytes8();
        // RFC 4492 says uncompressed points must always be supported.
        // Check just to make sure.
        boolean uncompressed = false;
        for (int format : formats) {
            if (format == FMT_UNCOMPRESSED) {
                uncompressed = true;
                break;
            }
        }
        if (uncompressed == false) {
            throw new SSLProtocolException
                ("Peer does not support uncompressed points");
        }
    }

    int length() {
        return 5 + formats.length;
    }

    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        s.putInt16(formats.length + 1);
        s.putBytes8(formats);
    }

    private static String toString(byte format) {
        int f = format & 0xff;
        switch (f) {
        case FMT_UNCOMPRESSED:
            return "uncompressed";
        case FMT_ANSIX962_COMPRESSED_PRIME:
            return "ansiX962_compressed_prime";
        case FMT_ANSIX962_COMPRESSED_CHAR2:
            return "ansiX962_compressed_char2";
        default:
            return "unknown-" + f;
        }
    }

    public String toString() {
        List<String> list = new ArrayList<String>();
        for (byte format : formats) {
            list.add(toString(format));
        }
        return "Extension " + type + ", formats: " + list;
    }
}
