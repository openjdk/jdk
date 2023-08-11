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

package sun.security.x509;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.cert.CRLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import sun.security.util.*;

/**
 * This class defines the CRL Extensions.
 * It is used for both CRL Extensions and CRL Entry Extensions,
 * which are defined are follows:
 * <pre>
 * TBSCertList  ::=  SEQUENCE  {
 *    version              Version OPTIONAL,   -- if present, must be v2
 *    signature            AlgorithmIdentifier,
 *    issuer               Name,
 *    thisUpdate           Time,
 *    nextUpdate           Time  OPTIONAL,
 *    revokedCertificates  SEQUENCE OF SEQUENCE  {
 *        userCertificate         CertificateSerialNumber,
 *        revocationDate          Time,
 *        crlEntryExtensions      Extensions OPTIONAL  -- if present, must be v2
 *    }  OPTIONAL,
 *    crlExtensions        [0] EXPLICIT Extensions OPTIONAL  -- if present, must be v2
 * }
 * </pre>
 *
 * @author Hemma Prafullchandra
 */
public class CRLExtensions {

    private final Map<String,Extension> map = Collections.synchronizedMap(
            new TreeMap<>());
    private boolean unsupportedCritExt = false;

    /**
     * Default constructor.
     */
    public CRLExtensions() { }

    /**
     * Create the object, decoding the values from the passed DER stream.
     *
     * @param in the DerInputStream to read the Extension from, i.e. the
     *        sequence of extensions.
     * @exception CRLException on decoding errors.
     */
    public CRLExtensions(DerInputStream in) throws CRLException {
        init(in);
    }

    // helper routine
    private void init(DerInputStream derStrm) throws CRLException {
        try {
            DerInputStream str = derStrm;

            byte nextByte = (byte)derStrm.peekByte();
            // check for context specific byte 0; skip it
            if (((nextByte & 0x0c0) == 0x080) &&
                ((nextByte & 0x01f) == 0x000)) {
                DerValue val = str.getDerValue();
                str = val.data;
            }

            DerValue[] exts = str.getSequence(5);
            for (int i = 0; i < exts.length; i++) {
                Extension ext = new Extension(exts[i]);
                parseExtension(ext);
            }
        } catch (IOException e) {
            throw new CRLException("Parsing error: " + e.toString());
        }
    }

    private static final Class<?>[] PARAMS = {Boolean.class, Object.class};

    // Parse the encoded extension
    private void parseExtension(Extension ext) throws CRLException {
        try {
            Class<?> extClass = OIDMap.getClass(ext.getExtensionId());
            if (extClass == null) {   // Unsupported extension
                if (ext.isCritical())
                    unsupportedCritExt = true;
                if (map.put(ext.getExtensionId().toString(), ext) != null)
                    throw new CRLException("Duplicate extensions not allowed");
                return;
            }
            Constructor<?> cons = extClass.getConstructor(PARAMS);
            Object[] passed = new Object[] {Boolean.valueOf(ext.isCritical()),
                                            ext.getExtensionValue()};
            Extension crlExt = (Extension)cons.newInstance(passed);
            if (map.put(crlExt.getName(), crlExt) != null) {
                throw new CRLException("Duplicate extensions not allowed");
            }
        } catch (InvocationTargetException invk) {
            throw new CRLException(invk.getCause().getMessage());
        } catch (Exception e) {
            throw new CRLException(e.toString());
        }
    }

    /**
     * Encode the extensions in DER form to the stream.
     *
     * @param out the DerOutputStream to marshal the contents to.
     * @param isExplicit the tag indicating whether this is an entry
     * extension (false) or a CRL extension (true).
     */
    public void encode(DerOutputStream out, boolean isExplicit) {
        DerOutputStream extOut = new DerOutputStream();
        for (Extension ext : map.values()) {
            ext.encode(extOut);
        }

        DerOutputStream seq = new DerOutputStream();
        seq.write(DerValue.tag_Sequence, extOut);

        DerOutputStream tmp = new DerOutputStream();
        if (isExplicit)
            tmp.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                    true, (byte) 0), seq);
        else
            tmp = seq;

        out.writeBytes(tmp.toByteArray());
    }

    /**
     * Get the extension with this alias.
     *
     * @param alias the identifier string for the extension to retrieve.
     */
    public Extension getExtension(String alias) {
        String name;
        if (alias.startsWith(X509CertImpl.NAME)) {
            int index = alias.lastIndexOf('.');
            name = alias.substring(index + 1);
        } else {
            name = alias;
        }
        return map.get(name);
    }

    /**
     * Set the extension value with this alias.
     *
     * @param alias the identifier string for the extension to set.
     * @param ext the extension identified by the alias.
     */
    public void setExtension(String alias, Extension ext) {
        map.put(alias, ext);
    }

    /**
     * Delete the extension value with this alias.
     *
     * @param alias the identifier string for the extension to delete.
     */
    public void delete(String alias) {
        map.remove(alias);
    }

    /**
     * Return a collection view of the extensions.
     * @return a collection view of the extensions in this CRL.
     */
    public Collection<Extension> getAllExtensions() {
        return map.values();
    }

    /**
     * Return true if a critical extension is found that is
     * not supported, otherwise return false.
     */
    public boolean hasUnsupportedCriticalExtension() {
        return unsupportedCritExt;
    }

    /**
     * Compares this CRLExtensions for equality with the specified
     * object. If the {@code other} object is an
     * {@code instanceof} {@code CRLExtensions}, then
     * all the entries are compared with the entries from this.
     *
     * @param other the object to test for equality with this CRLExtensions.
     * @return true iff all the entries match that of the Other,
     * false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof CRLExtensions otherCX))
            return false;

        Collection<Extension> otherX = otherCX.getAllExtensions();
        if (otherX.size() != map.size())
            return false;

        Extension thisExt;
        String key;
        for (Extension otherExt : otherX) {
            key = otherExt.getName();
            thisExt = map.get(key);
            if (thisExt == null)
                return false;
            if (! thisExt.equals(otherExt))
                return false;
        }
        return true;
    }

    /**
     * {@return a hashcode value for this CRLExtensions}
     */
    @Override
    public int hashCode() {
        return map.hashCode();
    }

    /**
     * Returns a string representation of this {@code CRLExtensions} object
     * in the form of a set of entries, enclosed in braces and separated
     * by the ASCII characters "<code>,&nbsp;</code>" (comma and space).
     * <p>Overrides to {@code toString} method of {@code Object}.
     *
     * @return  a string representation of this CRLExtensions.
     */
    public String toString() {
        return map.toString();
    }
}
