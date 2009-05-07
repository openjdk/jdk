/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package javax.xml.bind.attachment;

import javax.activation.DataHandler;

/**
 * <p>Enables JAXB unmarshalling of a root document containing optimized binary data formats.</p>
 *
 * <p>This API enables an efficient cooperative processing of optimized
 * binary data formats between a JAXB 2.0 implementation and MIME-based package
 * processor (MTOM/XOP and WS-I AP 1.0). JAXB unmarshals the body of a package, delegating the
 * understanding of the packaging format being used to a MIME-based
 * package processor that implements this abstract class.</p>
 *
 * <p>This abstract class identifies if a package requires XOP processing, {@link #isXOPPackage()} and provides retrieval of binary content stored as attachments by content-id.</p>
 *
 * <h2>Identifying the content-id, cid, to pass to <code>getAttachment*(String cid)</code></h2>
 * <ul>
 * <li>
 * For XOP processing, the infoset representation of the cid is described
 * in step 2a in
 * <a href="http://www.w3.org/TR/2005/REC-xop10-20050125/#interpreting_xop_packages">Section 3.2 Interpreting XOP Packages</a>
 * </li>
 * <li>
 * For WS-I AP 1.0, the cid is identified as an element or attribute of
 * type <code>ref:swaRef </code> specified in
 * <a href="http://www.ws-i.org/Profiles/AttachmentsProfile-1.0-2004-08-24.html#Referencing_Attachments_from_the_SOAP_Envelope">Section 4.4 Referencing Attachments from the SOAP Envelope</a>
 * </li>
 * </ul>
 *
 * @author Marc Hadley
 * @author Kohsuke Kawaguchi
 * @author Joseph Fialli
 *
 * @since JAXB 2.0
 *
 * @see javax.xml.bind.Unmarshaller#setAttachmentUnmarshaller(AttachmentUnmarshaller)
 *
 * @see <a href="http://www.w3.org/TR/2005/REC-xop10-20050125/">XML-binary Optimized Packaging</a>
 * @see <a href="http://www.ws-i.org/Profiles/AttachmentsProfile-1.0-2004-08-24.html">WS-I Attachments Profile Version 1.0.</a>
 * @see <a href="http://www.w3.org/TR/xml-media-types/">Describing Media Content of Binary Data in XML</a>
 */
public abstract class AttachmentUnmarshaller {
   /**
    * <p>Lookup MIME content by content-id, <code>cid</code>, and return as a {@link DataHandler}.</p>
    *
    * <p>The returned <code>DataHandler</code> instance must be configured
    * to meet the following required mapping constaint.
    * <table border="2" rules="all" cellpadding="4">
    *   <thead>
    *     <tr>
    *       <th align="center" colspan="2">
    *       Required Mappings between MIME and Java Types
    *       </tr>
    *     <tr>
    *       <th>MIME Type</th>
    *       <th>Java Type</th>
    *     </tr>
    *     </tr>
    *     <tr>
    *       <th><code>DataHandler.getContentType()</code></th>
    *       <th><code>instanceof DataHandler.getContent()</code></th>
    *     </tr>
    *   </thead>
    *   <tbody>
    *     <tr>
    *       <td>image/gif</td>
    *       <td>java.awt.Image</td>
    *     </tr>
    *     <tr>
    *       <td>image/jpeg</td>
    *       <td>java.awt.Image</td>
    *     </tr>
    *     <tr>
    *       <td>text/xml  or application/xml</td>
    *       <td>javax.xml.transform.Source</td>
    *     </tr>
    *   </tbody>
    *  </table>
    * Note that it is allowable to support additional mappings.</p>
    *
    * @param cid It is expected to be a valid lexical form of the XML Schema
    * <code>xs:anyURI</code> datatype. If <code>{@link #isXOPPackage()}
    * ==true</code>, it must be a valid URI per the <code>cid:</code> URI scheme (see <a href="http://www.ietf.org/rfc/rfc2387.txt">RFC 2387</a>)
    *
    * @return
    *       a {@link DataHandler} that represents the MIME attachment.
    *
    * @throws IllegalArgumentException if the attachment for the given cid is not found.
    */
   public abstract DataHandler getAttachmentAsDataHandler(String cid);

    /**
     * <p>Retrieve the attachment identified by content-id, <code>cid</code>,  as a <tt>byte[]</tt></p>.
     *
     * @param cid It is expected to be a valid lexical form of the XML Schema
     * <code>xs:anyURI</code> datatype. If <code>{@link #isXOPPackage()}
     * ==true</code>, it must be a valid URI per the <code>cid:</code> URI scheme (see <a href="http://www.ietf.org/rfc/rfc2387.txt">RFC 2387</a>)
     *
     * @return byte[] representation of attachment identified by cid.
     *
    * @throws IllegalArgumentException if the attachment for the given cid is not found.
     */
    public abstract byte[] getAttachmentAsByteArray(String cid);

    /**
     * <p>Read-only property that returns true if JAXB unmarshaller needs to perform XOP processing.</p>
     *
     * <p>This method returns <code>true</code> when the constraints specified
     * in  <a href="http://www.w3.org/TR/2005/REC-xop10-20050125/#identifying_xop_documents">Identifying XOP Documents</a> are met.
     * This value must not change during the unmarshalling process.</p>
     *
     * @return true when MIME context is a XOP Document.
     */
    public boolean isXOPPackage() { return false; }
}
