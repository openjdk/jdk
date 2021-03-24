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
package javax.xml.crypto.dsig.spec;

import javax.xml.crypto.dsig.SignatureMethod;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Objects;

/**
 * Parameters for the <a href="http://www.w3.org/2007/05/xmldsig-more#rsa-pss">
 * XML Signature RSASSA-PSS Algorithm</a>. The parameters are expressed as a
 * {@link PSSParameterSpec} object encapsulated.
 * <p>
 * The XML Schema Definition is defined as:
 * <pre><code>
 *    &lt;xs:element name="RSAPSSParams" type="pss:RSAPSSParamsType"&gt;
 *        &lt;xs:annotation&gt;
 *            &lt;xs:documentation&gt;
 *    Top level element that can be used in xs:any namespace="#other"
 *    wildcard of ds:SignatureMethod content.
 *            &lt;/xs:documentation&gt;
 *        &lt;/xs:annotation&gt;
 *    &lt;/xs:element&gt;
 *    &lt;xs:complexType name="RSAPSSParamsType"&gt;
 *        &lt;xs:sequence&gt;
 *            &lt;xs:element ref="ds:DigestMethod" minOccurs="0"/&gt;
 *            &lt;xs:element name="MaskGenerationFunction"
 *               type="pss:MaskGenerationFunctionType" minOccurs="0"/&gt;
 *            &lt;xs:element name="SaltLength" type="xs:int"
 *               minOccurs="0"/&gt;
 *            &lt;xs:element name="TrailerField" type="xs:int"
 *               minOccurs="0"/&gt;
 *        &lt;/xs:sequence&gt;
 *    &lt;/xs:complexType&gt;
 *    &lt;xs:complexType name="MaskGenerationFunctionType"&gt;
 *        &lt;xs:sequence&gt;
 *            &lt;xs:element ref="ds:DigestMethod" minOccurs="0"/&gt;
 *        &lt;/xs:sequence&gt;
 *        &lt;xs:attribute name="Algorithm" type="xs:anyURI"
 *           default="http://www.w3.org/2007/05/xmldsig-more#MGF1"/&gt;
 *    &lt;/xs:complexType&gt;
 * </code></pre>
 *
 * @implSpec
 * If the parameter is not specified when using the RSASSA-PSS signature
 * algorithm, the default parameter is used, which uses SHA-256 as the
 * {@code DigestMethod}, MGF1 with SHA-256 as the
 * {@code MaskGenerationFunction}, 32 as {@code SaltLength}, and 1 as
 * {@code TrailerField}. This is equivalent to the parameter-less signature
 * method as defined by http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1.
 *
 * @since 17
 * @see SignatureMethod
 * @see <a href="https://www.ietf.org/rfc/rfc6931.txt">RFC 6931</a>
 */
public final class RSAPSSParameterSpec implements SignatureMethodParameterSpec {

    private final PSSParameterSpec spec;

    /**
     * Creates a new {@code RSAPSSParameterSpec} object with the specified
     * {@link PSSParameterSpec} object encapsulated.
     *
     * @param spec the input {@code PSSParameterSpec} to be encapsulated
     */
    public RSAPSSParameterSpec(PSSParameterSpec spec) {
        this.spec = Objects.requireNonNull(spec);
    }

    /**
     * Returns the encapsulated {@code PSSParameterSpec} object.
     *
     * @return the encapsulated {@code PSSParameterSpec} object
     */
    public PSSParameterSpec getPSSParameterSpec() {
        return spec;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                spec.getDigestAlgorithm(),
                spec.getSaltLength(),
                spec.getTrailerField(),
                mgfParamsAsString(spec));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RSAPSSParameterSpec)) {
            return false;
        }
        PSSParameterSpec other = ((RSAPSSParameterSpec)obj).spec;
        return Objects.equals(spec.getDigestAlgorithm(), other.getDigestAlgorithm())
                && Objects.equals(mgfParamsAsString(spec), mgfParamsAsString(other))
                && spec.getSaltLength() == other.getSaltLength()
                && spec.getTrailerField() == other.getTrailerField();
    }

    private static String mgfParamsAsString(PSSParameterSpec spec) {
        String output = spec.getMGFAlgorithm() + ":";
        AlgorithmParameterSpec mgfSpec = spec.getMGFParameters();
        if (mgfSpec instanceof MGF1ParameterSpec) {
            output += ((MGF1ParameterSpec)mgfSpec).getDigestAlgorithm();
        } else {
            output += System.identityHashCode(mgfSpec);
        }
        return output;
    }

    @Override
    public String toString() {
        return "RSAPSSParameterSpec (dm:" + spec.getDigestAlgorithm()
                + ", mgf:" + mgfParamsAsString(spec)
                + ", sl:" + spec.getSaltLength()
                + ", tf:" + spec.getTrailerField() + ")";
    }
}
