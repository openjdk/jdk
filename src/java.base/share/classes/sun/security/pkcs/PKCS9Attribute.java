/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import sun.security.x509.CertificateExtensions;
import sun.security.util.*;

/**
 * Class supporting any PKCS9 attributes.
 * Supports DER decoding/encoding and access to attribute values.
 *
 * @author Douglas Hoover
 */
public class PKCS9Attribute implements DerEncoder {

    /* Are we debugging ? */
    private static final Debug debug = Debug.getInstance("jar");

    /* OID Constants */
    public static final ObjectIdentifier EMAIL_ADDRESS_OID =
        ObjectIdentifier.of(KnownOIDs.EmailAddress);
    public static final ObjectIdentifier UNSTRUCTURED_NAME_OID =
        ObjectIdentifier.of(KnownOIDs.UnstructuredName);
    public static final ObjectIdentifier CONTENT_TYPE_OID =
        ObjectIdentifier.of(KnownOIDs.ContentType);
    public static final ObjectIdentifier MESSAGE_DIGEST_OID =
        ObjectIdentifier.of(KnownOIDs.MessageDigest);
    public static final ObjectIdentifier SIGNING_TIME_OID =
        ObjectIdentifier.of(KnownOIDs.SigningTime);
    public static final ObjectIdentifier COUNTERSIGNATURE_OID =
        ObjectIdentifier.of(KnownOIDs.CounterSignature);
    public static final ObjectIdentifier CHALLENGE_PASSWORD_OID =
        ObjectIdentifier.of(KnownOIDs.ChallengePassword);
    public static final ObjectIdentifier UNSTRUCTURED_ADDRESS_OID =
        ObjectIdentifier.of(KnownOIDs.UnstructuredAddress);
    public static final ObjectIdentifier EXTENDED_CERTIFICATE_ATTRIBUTES_OID =
        ObjectIdentifier.of(KnownOIDs.ExtendedCertificateAttributes);
    public static final ObjectIdentifier ISSUER_SERIALNUMBER_OID =
        ObjectIdentifier.of(KnownOIDs.IssuerAndSerialNumber);
    public static final ObjectIdentifier EXTENSION_REQUEST_OID =
        ObjectIdentifier.of(KnownOIDs.ExtensionRequest);
    public static final ObjectIdentifier SIGNING_CERTIFICATE_OID =
        ObjectIdentifier.of(KnownOIDs.SigningCertificate);
    public static final ObjectIdentifier SIGNATURE_TIMESTAMP_TOKEN_OID =
        ObjectIdentifier.of(KnownOIDs.SignatureTimestampToken);
    public static final ObjectIdentifier CMS_ALGORITHM_PROTECTION_OID =
        ObjectIdentifier.of(KnownOIDs.CMSAlgorithmProtection);

    private record AttributeInfo(boolean singleValued, Class<?> valueClass,
                                 Function<DerValue[], Object> getValue,
                                 BiFunction<Object, DerOutputStream, DerOutputStream> encoder,
                                 byte... valueTags) {}

    private static final Map<ObjectIdentifier, AttributeInfo> infoMap = new HashMap<>();

    private static void add(ObjectIdentifier oid, boolean singleValued,
                            Class<?> valueClass, Function<DerValue[], Object> getValue,
                            BiFunction<Object, DerOutputStream, DerOutputStream> encoder,
                            byte... valueTags) {

        AttributeInfo info =
            new AttributeInfo(singleValued, valueClass, getValue, encoder, valueTags);

        if (infoMap.put(oid, info) != null) {
            throw new RuntimeException("Duplicate oid: " + oid);
        }
    }

    private static DerOutputStream mkDerStream(DerOutputStream t, DerOutputStream r) {
        return t.write(DerValue.tag_Set, r.toByteArray());
    }

    @FunctionalInterface
    public interface Throws<T, R, E extends Throwable> {
        R apply(T t) throws E;

        static <T, R, E extends Throwable> Function<T, R> unchecked(Throws<T, R, E> f) {
            return t -> {
                try {
                    return f.apply(t);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    private static final Class<?> BYTE_ARRAY_CLASS;

    static {
        try {
            BYTE_ARRAY_CLASS = Class.forName("[B");
            Class<?> str = Class.forName("[Ljava.lang.String;");

            add(EMAIL_ADDRESS_OID, false, str,
                a -> Arrays.stream(a).map(
                    Throws.unchecked(DerValue::getAsString)).toArray(),
                (v,t) -> t.putIA5String((String) v),
                DerValue.tag_IA5String);

            add(UNSTRUCTURED_NAME_OID, false, str,
                a -> Arrays.stream(a).map(
                    Throws.unchecked(DerValue::getAsString)).toArray(),
                (v,t) -> t.putIA5String((String) v),
                DerValue.tag_IA5String,
                DerValue.tag_PrintableString,
                DerValue.tag_T61String,
                DerValue.tag_BMPString,
                DerValue.tag_UniversalString,
                DerValue.tag_UTF8String);

            add(CONTENT_TYPE_OID, true, Class.forName("sun.security.util.ObjectIdentifier"),
                Throws.unchecked(a -> a[0].getOID()),
                (v,t) -> mkDerStream(t, new DerOutputStream().putOID((ObjectIdentifier) v)),
                DerValue.tag_ObjectId);

            add(MESSAGE_DIGEST_OID, true, byte[].class,
                Throws.unchecked(a -> a[0].getOctetString()),
                (v,t) -> mkDerStream(t, new DerOutputStream().putOctetString((byte[]) v)),
                DerValue.tag_OctetString);

            //CHECK - Original converts to DerInputStream and back??
            add(SIGNING_TIME_OID, true, Class.forName("java.util.Date"),
                Throws.unchecked(a -> a[0].getTime()),
                (v,t) -> mkDerStream(t, new DerOutputStream().putTime((Date) v)),
                DerValue.tag_UtcTime,
                DerValue.tag_GeneralizedTime);

            add(COUNTERSIGNATURE_OID, false,
                Class.forName("[Lsun.security.pkcs.SignerInfo;"),
                a -> Arrays.stream(a).map(Throws.unchecked(
                    e -> new SignerInfo(e.toDerInputStream()))).toArray(),
                (v,t) -> t.putOrderedSetOf(DerValue.tag_Set, (DerEncoder[]) v),
                DerValue.tag_Sequence);

            add(CHALLENGE_PASSWORD_OID, true, Class.forName("java.lang.String"),
                Throws.unchecked(a -> a[0].getAsString()),
                (v,t) -> mkDerStream(t, new DerOutputStream().putPrintableString((String) v)),
                DerValue.tag_PrintableString,
                DerValue.tag_T61String,
                DerValue.tag_BMPString,
                DerValue.tag_UniversalString,
                DerValue.tag_UTF8String);

            add(UNSTRUCTURED_ADDRESS_OID, false, str,
                a -> Arrays.stream(a).map(
                    Throws.unchecked(DerValue::getAsString)).toArray(String[]::new),
                (v,t) -> t.putPrintableString((String) v),
                DerValue.tag_PrintableString,
                DerValue.tag_T61String,
                DerValue.tag_BMPString,
                DerValue.tag_UniversalString,
                DerValue.tag_UTF8String);

            add(EXTENSION_REQUEST_OID, true,
                Class.forName("sun.security.x509.CertificateExtensions"),
                Throws.unchecked(a -> new CertificateExtensions(
                    new DerInputStream(a[0].toByteArray()))),
                (v,t) -> {
                    DerOutputStream temp = new DerOutputStream();
                    CertificateExtensions exts = (CertificateExtensions) v;
                    exts.encode(temp, true);
                    return mkDerStream(t, temp);},
                DerValue.tag_Sequence);

            add(SIGNING_CERTIFICATE_OID, true,
                Class.forName("sun.security.pkcs.SigningCertificateInfo"),
                Throws.unchecked(a -> new SigningCertificateInfo(a[0].toByteArray())),
                (v,t) -> {
                    DerOutputStream temp = new DerOutputStream();
                    SigningCertificateInfo info = (SigningCertificateInfo) v;
                    temp.writeBytes(info.toByteArray());
                    return mkDerStream(t, temp);},
                DerValue.tag_Sequence);

            add(SIGNATURE_TIMESTAMP_TOKEN_OID, true, byte[].class,
                a -> a[0].toByteArray(),
                (v,t) -> t.write(DerValue.tag_Set, (byte[]) v),
                DerValue.tag_Sequence);

            add(CMS_ALGORITHM_PROTECTION_OID, true, byte[].class,
                a -> a[0].toByteArray(),
                (v,t) -> t.write(DerValue.tag_Set, (byte[]) v),
                DerValue.tag_Sequence);

        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e.toString());
        }
    }

    /**
     * The OID of this attribute.
     */
    private final ObjectIdentifier oid;

    /**
     * The AttributeInfo of this attribute. Can be null if oid is unknown.
     */
    private final AttributeInfo info;

    /**
     * Value set of this attribute.  Its class is given by
     * <code>AttributeInfo.valueClass</code>. The SET itself
     * as byte[] if unknown.
     */
    private final Object value;

    /**
     * Construct an attribute object from the attribute's OID and
     * value.  If the attribute is single-valued, provide only one
     * value.  If the attribute is multi-valued, provide an array
     * containing all the values.
     * Arrays of length zero are accepted, though probably useless.
     *
     * <P> The
     * <a href=#classTable>table</a> gives the class that <code>value</code>
     * must have for a given attribute.
     *
     * @exception IllegalArgumentException
     * if the <code>value</code> has the wrong type.
     */
    public PKCS9Attribute(ObjectIdentifier oid, Object value)
        throws IllegalArgumentException {

        this.oid = oid;
        this.info = infoMap.get(oid);
        Class<?> clazz = info == null ? BYTE_ARRAY_CLASS : info.valueClass();
        if (!clazz.isInstance(value)) {
            throw new IllegalArgumentException(
                "Wrong value class " +
                    " for attribute " + oid +
                    " constructing PKCS9Attribute; was " +
                    value.getClass().toString() + ", should be " +
                    clazz.toString());
        }
        this.value = value;
    }

    /**
     * Construct a PKCS9Attribute from its encoding on an input
     * stream.
     *
     * @param derVal the DerValue representing the DER encoding of the attribute.
     * @exception IOException on parsing error.
     */
    public PKCS9Attribute(DerValue derVal) throws IOException {

        DerInputStream derIn = new DerInputStream(derVal.toByteArray());
        DerValue[] val =  derIn.getSequence(2);

        if (derIn.available() != 0)
            throw new IOException("Excess data parsing PKCS9Attribute");

        if (val.length != 2)
            throw new IOException("PKCS9Attribute doesn't have two components");

        // get the oid
        oid = val[0].getOID();
        byte[] content = val[1].toByteArray();
        DerValue[] elems = new DerInputStream(content).getSet(1);

        info = infoMap.get(oid);
        if (info == null) {
            if (debug != null) {
                debug.println("Unsupported signer attribute: " + oid);
            }
            value = content;
            return;
        }

        // check single valued have only one value
        if (info.singleValued() && elems.length > 1)
            throwSingleValuedException();

        // check for illegal element tags
        byte tag;
        for (DerValue elem : elems) {
            tag = elem.tag;
            if (indexOf(tag, info.valueTags(), 0) == -1)
                throwTagException(tag);
        }

        value = info.getValue.apply(elems);
    }

    /**
     * Write the DER encoding of this attribute to an output stream.
     *
     * <P> N.B.: This method always encodes values of
     * ChallengePassword and UnstructuredAddress attributes as ASN.1
     * <code>PrintableString</code>s, without checking whether they
     * should be encoded as <code>T61String</code>s.
     */
    @Override
    public void encode(DerOutputStream out) {
        DerOutputStream temp = new DerOutputStream();
        temp.putOID(oid);

        if (info == null) {
            temp.writeBytes((byte[])value);
            out.write(DerValue.tag_Sequence, temp.toByteArray());
            return;
        }

        if (info.singleValued) {
            info.encoder.apply(value, temp);
        } else {
            Object[] values = (Object[]) value;
            DerOutputStream[] temps = new
                DerOutputStream[values.length];

            for (int i=0; i < values.length; i++) {
                temps[i] = new DerOutputStream();
                info.encoder.apply((String) values[i], temps[i]);
            }
            temp.putOrderedSetOf(DerValue.tag_Set, temps);
        }

        out.write(DerValue.tag_Sequence, temp.toByteArray());
    }

    /**
     * Returns if the attribute is known. Unknown attributes can be created
     * from DER encoding with unknown OIDs.
     */
    public boolean isKnown() {
        return info != null;
    }

    /**
     * Get the value of this attribute.  If the attribute is
     * single-valued, return just the one value.  If the attribute is
     * multi-valued, return an array containing all the values.
     * It is possible for this array to be of length 0.
     *
     * <P> The
     * <a href=#classTable>table</a> gives the class of the value returned,
     * depending on the type of this attribute.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Show whether this attribute is single-valued.
     */
    public boolean isSingleValued() {
        return info == null || info.singleValued();
    }

    /**
     *  Return the OID of this attribute.
     */
    public ObjectIdentifier getOID() {
        return oid;
    }

    public static Set<ObjectIdentifier> getOIDs() { return infoMap.keySet(); }

    /**
     *  Return the name of this attribute.
     */
    public String getName() {
        String n = oid.toString();
        KnownOIDs os = KnownOIDs.findMatch(n);
        return os == null ? n : os.stdName();
    }

    /**
     * Return the OID for a given attribute name or null if we don't recognize
     * the name.
     */
    public static ObjectIdentifier getOID(String name) {
        KnownOIDs o = KnownOIDs.findMatch(name);
        if (o != null) {
            return ObjectIdentifier.of(o);
        } else {
            return null;
        }
    }

    /**
     * Return the attribute name for a given OID or null if we don't recognize
     * the oid.
     */
    public static String getName(ObjectIdentifier oid) {
        return KnownOIDs.findMatch(oid.toString()).stdName();
    }

    /**
     * Returns a string representation of this attribute.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(100);

        sb.append("[");

        if (info == null) {
            sb.append(oid.toString());
        } else {
            sb.append(getName(oid));
        }
        sb.append(": ");

        if (info == null || info.singleValued()) {
            if (value instanceof byte[]) { // special case for octet string
                HexDumpEncoder hexDump = new HexDumpEncoder();
                sb.append(hexDump.encodeBuffer((byte[]) value));
            } else {
                sb.append(value.toString());
            }
            sb.append("]");
        } else { // multi-valued
            boolean first = true;
            Object[] values = (Object[]) value;

            for (Object curVal : values) {
                if (first)
                    first = false;
                else
                    sb.append(", ");
                sb.append(curVal.toString());
            }
        }
        return sb.toString();
    }

    /**
     * Beginning the search at <code>start</code>, find the first
     * index <code>i</code> such that <code>a[i] = obj</code>.
     *
     * @return the index, if found, and -1 otherwise.
     */
    static int indexOf(byte b, byte[] bs, int start) {
        for (int i=start; i < bs.length; i++) {
            if (b == bs[i]) return i;
        }
        return -1;
    }

    /**
     * Throw an exception when there are multiple values for
     * a single-valued attribute.
     */
    private void throwSingleValuedException() throws IOException {
        throw new IOException("Single-value attribute " +
                              oid + " (" + getName() + ")" +
                              " has multiple values.");
    }

    /**
     * Throw an exception when the tag on a value encoding is
     * wrong for the attribute whose value it is. This method
     * will only be called for known tags.
     */
    private void throwTagException(byte tag)
    throws IOException {
        byte[] expectedTags = info.valueTags();
        StringBuilder msg = new StringBuilder(100);
        msg.append("Value of attribute ");
        msg.append(oid.toString());
        msg.append(" (");
        msg.append(getName());
        msg.append(") has wrong tag: ");
        msg.append(tag);
        msg.append(".  Expected tags: ");

        msg.append(expectedTags[0]);

        for (int i = 1; i < expectedTags.length; i++) {
            msg.append(", ");
            msg.append(expectedTags[i]);
        }
        msg.append(".");
        throw new IOException(msg.toString());
    }
}