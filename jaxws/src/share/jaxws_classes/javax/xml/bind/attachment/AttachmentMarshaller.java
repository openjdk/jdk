/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind.attachment;

import javax.activation.DataHandler;
import javax.xml.bind.Marshaller;

/**
 * <p>Enable JAXB marshalling to optimize storage of binary data.</p>
 *
 * <p>This API enables an efficient cooperative creation of optimized
 * binary data formats between a JAXB marshalling process and a MIME-based package
 * processor. A JAXB implementation marshals the root body of a MIME-based package,
 * delegating the creation of referenceable MIME parts to
 * the MIME-based package processor that implements this abstraction.</p>
 *
 * <p>XOP processing is enabled when {@link #isXOPPackage()} is true.
 *    See {@link #addMtomAttachment(DataHandler, String, String)} for details.
 * </p>
 *
 * <p>WS-I Attachment Profile 1.0 is supported by
 * {@link #addSwaRefAttachment(DataHandler)} being called by the
 * marshaller for each JAXB property related to
 * {http://ws-i.org/profiles/basic/1.1/xsd}swaRef.</p>
 *
 *
 * @author Marc Hadley
 * @author Kohsuke Kawaguchi
 * @author Joseph Fialli
 * @since JAXB 2.0
 *
 * @see Marshaller#setAttachmentMarshaller(AttachmentMarshaller)
 *
 * @see <a href="http://www.w3.org/TR/2005/REC-xop10-20050125/">XML-binary Optimized Packaging</a>
 * @see <a href="http://www.ws-i.org/Profiles/AttachmentsProfile-1.0-2004-08-24.html">WS-I Attachments Profile Version 1.0.</a>
 */
public abstract class AttachmentMarshaller {

    /**
     * <p>Consider MIME content <code>data</code> for optimized binary storage as an attachment.
     *
     * <p>
     * This method is called by JAXB marshal process when {@link #isXOPPackage()} is
     * <code>true</code>, for each element whose datatype is "base64Binary", as described in
     * Step 3 in
     * <a href="http://www.w3.org/TR/2005/REC-xop10-20050125/#creating_xop_packages">Creating XOP Packages</a>.
     *
     * <p>
     * The method implementor determines whether <code>data</code> shall be attached separately
     * or inlined as base64Binary data. If the implementation chooses to optimize the storage
     * of the binary data as a MIME part, it is responsible for attaching <code>data</code> to the
     * MIME-based package, and then assigning an unique content-id, cid, that identifies
     * the MIME part within the MIME message. This method returns the cid,
     * which enables the JAXB marshaller to marshal a XOP element that refers to that cid in place
     * of marshalling the binary data. When the method returns null, the JAXB marshaller
     * inlines <code>data</code> as base64binary data.
     *
     * <p>
     * The caller of this method is required to meet the following constraint.
     * If the element infoset item containing <code>data</code> has the attribute
     * <code>xmime:contentType</code> or if the JAXB property/field representing
     * <code>data</code>is annotated with a known MIME type,
     * <code>data.getContentType()</code> should be set to that MIME type.
     *
     * <p>
     * The <code>elementNamespace</code> and <code>elementLocalName</code>
     * parameters provide the
     * context that contains the binary data. This information could
     * be used by the MIME-based package processor to determine if the
     * binary data should be inlined or optimized as an attachment.
     *
     * @param data
     *       represents the data to be attached. Must be non-null.
     * @param elementNamespace
     *      the namespace URI of the element that encloses the base64Binary data.
     *      Can be empty but never null.
     * @param elementLocalName
     *      The local name of the element. Always a non-null valid string.
     *
     * @return
     *     a valid content-id URI (see <a href="http://www.w3.org/TR/xop10/#RFC2387">RFC 2387</a>) that identifies the attachment containing <code>data</code>.
     *     Otherwise, null if the attachment was not added and should instead be inlined in the message.
     *
     * @see <a href="http://www.w3.org/TR/2005/REC-xop10-20050125/">XML-binary Optimized Packaging</a>
     * @see <a href="http://www.w3.org/TR/xml-media-types/">Describing Media Content of Binary Data in XML</a>
     */
    public abstract String addMtomAttachment(DataHandler data, String elementNamespace, String elementLocalName);

    /**
     * <p>Consider binary <code>data</code> for optimized binary storage as an attachment.
     *
     * <p>Since content type is not known, the attachment's MIME content type must be set to "application/octet-stream".</p>
     *
     * <p>
     * The <code>elementNamespace</code> and <code>elementLocalName</code>
     * parameters provide the
     * context that contains the binary data. This information could
     * be used by the MIME-based package processor to determine if the
     * binary data should be inlined or optimized as an attachment.
     *
     * @param data
     *      represents the data to be attached. Must be non-null. The actual data region is
     *      specified by <tt>(data,offset,length)</tt> tuple.
     *
     * @param offset
     *       The offset within the array of the first byte to be read;
     *       must be non-negative and no larger than array.length
     *
     * @param length
     *       The number of bytes to be read from the given array;
     *       must be non-negative and no larger than array.length
     *
     * @param mimeType
     *      If the data has an associated MIME type known to JAXB, that is passed
     *      as this parameter. If none is known, "application/octet-stream".
     *      This parameter may never be null.
     *
     * @param elementNamespace
     *      the namespace URI of the element that encloses the base64Binary data.
     *      Can be empty but never null.
     *
     * @param elementLocalName
     *      The local name of the element. Always a non-null valid string.
     *
     * @return content-id URI, cid, to the attachment containing
     *         <code>data</code> or null if data should be inlined.
     *
     * @see #addMtomAttachment(DataHandler, String, String)
     */
    public abstract String addMtomAttachment(byte[] data, int offset, int length, String mimeType, String elementNamespace, String elementLocalName);

    /**
     * <p>Read-only property that returns true if JAXB marshaller should enable XOP creation.</p>
     *
     * <p>This value must not change during the marshalling process. When this
     * value is true, the <code>addMtomAttachment(...)</code> method
     * is invoked when the appropriate binary datatypes are encountered by
     * the marshal process.</p>
     *
     * <p>Marshaller.marshal() must throw IllegalStateException if this value is <code>true</code>
     * and the XML content to be marshalled violates Step 1 in
     * <a href="http://www.w3.org/TR/2005/REC-xop10-20050125/#creating_xop_packages">Creating XOP Pacakges</a>
     * http://www.w3.org/TR/2005/REC-xop10-20050125/#creating_xop_packages.
     * <i>"Ensure the Original XML Infoset contains no element information item with a
     * [namespace name] of "http://www.w3.org/2004/08/xop/include" and a [local name] of Include"</i>
     *
     * <p>When this method returns true and during the marshal process
     * at least one call to <code>addMtomAttachment(...)</code> returns
     * a content-id, the MIME-based package processor must label the
     * root part with the application/xop+xml media type as described in
     * Step 5 of
     * <a href="http://www.w3.org/TR/2005/REC-xop10-20050125/#creating_xop_packages">Creating XOP Pacakges</a>.<p>
     *
     * @return true when MIME context is a XOP Package.
     */
    public boolean isXOPPackage() { return false; }

   /**
    * <p>Add MIME <code>data</code> as an attachment and return attachment's content-id, cid.</p>
    *
    * <p>
    * This method is called by JAXB marshal process for each element/attribute typed as
    * {http://ws-i.org/profiles/basic/1.1/xsd}swaRef. The MIME-based package processor
    * implementing this method is responsible for attaching the specified data to a
    * MIME attachment, and generating a content-id, cid, that uniquely identifies the attachment
    * within the MIME-based package.
    *
    * <p>Caller inserts the returned content-id, cid, into the XML content being marshalled.</p>
    *
    * @param data
    *       represents the data to be attached. Must be non-null.
    * @return
    *       must be a valid URI used as cid. Must satisfy Conformance Requirement R2928 from
    *       <a href="http://www.ws-i.org/Profiles/AttachmentsProfile-1.0-2004-08-24.html#Referencing_Attachments_from_the_SOAP_Envelope">WS-I Attachments Profile Version 1.0.</a>
    */
    public abstract String addSwaRefAttachment(DataHandler data);
}
