/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.soap;

import java.io.InputStream;
import java.io.Reader;
import java.util.Iterator;

import javax.activation.DataHandler;

/**
 * A single attachment to a <code>SOAPMessage</code> object. A <code>SOAPMessage</code>
 * object may contain zero, one, or many <code>AttachmentPart</code> objects.
 * Each <code>AttachmentPart</code> object consists of two parts,
 * application-specific content and associated MIME headers. The
 * MIME headers consists of name/value pairs that can be used to
 * identify and describe the content.
 * <p>
 * An <code>AttachmentPart</code> object must conform to certain standards.
 * <OL>
 * <LI>It must conform to <a href="http://www.ietf.org/rfc/rfc2045.txt">
 *     MIME [RFC2045] standards</a>
 * <LI>It MUST contain content
 * <LI>The header portion MUST include the following header:
 *  <UL>
 *   <LI><code>Content-Type</code><br>
 *       This header identifies the type of data in the content of an
 *       <code>AttachmentPart</code> object and MUST conform to [RFC2045].
 *       The following is an example of a Content-Type header:
 *       <PRE>
 *       Content-Type:  application/xml
 *       </PRE>
 *       The following line of code, in which <code>ap</code> is an
 *       <code>AttachmentPart</code> object, sets the header shown in
 *       the previous example.
 *       <PRE>
 *       ap.setMimeHeader("Content-Type", "application/xml");
 *       </PRE>
 * <p>
 *  </UL>
 * </OL>
 * <p>
 * There are no restrictions on the content portion of an <code>
 * AttachmentPart</code> object. The content may be anything from a
 * simple plain text object to a complex XML document or image file.
 *
 * <p>
 * An <code>AttachmentPart</code> object is created with the method
 * <code>SOAPMessage.createAttachmentPart</code>. After setting its MIME headers,
 *  the <code>AttachmentPart</code> object is added to the message
 * that created it with the method <code>SOAPMessage.addAttachmentPart</code>.
 *
 * <p>
 * The following code fragment, in which <code>m</code> is a
 * <code>SOAPMessage</code> object and <code>contentStringl</code> is a
 * <code>String</code>, creates an instance of <code>AttachmentPart</code>,
 * sets the <code>AttachmentPart</code> object with some content and
 * header information, and adds the <code>AttachmentPart</code> object to
 * the <code>SOAPMessage</code> object.
 * <PRE>
 *     AttachmentPart ap1 = m.createAttachmentPart();
 *     ap1.setContent(contentString1, "text/plain");
 *     m.addAttachmentPart(ap1);
 * </PRE>
 *
 *
 * <p>
 * The following code fragment creates and adds a second
 * <code>AttachmentPart</code> instance to the same message. <code>jpegData</code>
 * is a binary byte buffer representing the jpeg file.
 * <PRE>
 *     AttachmentPart ap2 = m.createAttachmentPart();
 *     byte[] jpegData =  ...;
 *     ap2.setContent(new ByteArrayInputStream(jpegData), "image/jpeg");
 *     m.addAttachmentPart(ap2);
 * </PRE>
 * <p>
 * The <code>getContent</code> method retrieves the contents and header from
 * an <code>AttachmentPart</code> object. Depending on the
 * <code>DataContentHandler</code> objects present, the returned
 * <code>Object</code> can either be a typed Java object corresponding
 * to the MIME type or an <code>InputStream</code> object that contains the
 * content as bytes.
 * <PRE>
 *     String content1 = ap1.getContent();
 *     java.io.InputStream content2 = ap2.getContent();
 * </PRE>
 *
 * The method <code>clearContent</code> removes all the content from an
 * <code>AttachmentPart</code> object but does not affect its header information.
 * <PRE>
 *     ap1.clearContent();
 * </PRE>
 */

public abstract class AttachmentPart {
    /**
     * Returns the number of bytes in this <code>AttachmentPart</code>
     * object.
     *
     * @return the size of this <code>AttachmentPart</code> object in bytes
     *         or -1 if the size cannot be determined
     * @exception SOAPException if the content of this attachment is
     *            corrupted of if there was an exception while trying
     *            to determine the size.
     */
    public abstract int getSize() throws SOAPException;

    /**
     * Clears out the content of this <code>AttachmentPart</code> object.
     * The MIME header portion is left untouched.
     */
    public abstract void clearContent();

    /**
     * Gets the content of this <code>AttachmentPart</code> object as a Java
     * object. The type of the returned Java object depends on (1) the
     * <code>DataContentHandler</code> object that is used to interpret the bytes
     * and (2) the <code>Content-Type</code> given in the header.
     * <p>
     * For the MIME content types "text/plain", "text/html" and "text/xml", the
     * <code>DataContentHandler</code> object does the conversions to and
     * from the Java types corresponding to the MIME types.
     * For other MIME types,the <code>DataContentHandler</code> object
     * can return an <code>InputStream</code> object that contains the content data
     * as raw bytes.
     * <p>
     * A SAAJ-compliant implementation must, as a minimum, return a
     * <code>java.lang.String</code> object corresponding to any content
     * stream with a <code>Content-Type</code> value of
     * <code>text/plain</code>, a
     * <code>javax.xml.transform.stream.StreamSource</code> object corresponding to a
     * content stream with a <code>Content-Type</code> value of
     * <code>text/xml</code>, a <code>java.awt.Image</code> object
     * corresponding to a content stream with a
     * <code>Content-Type</code> value of <code>image/gif</code> or
     * <code>image/jpeg</code>.  For those content types that an
     * installed <code>DataContentHandler</code> object does not understand, the
     * <code>DataContentHandler</code> object is required to return a
     * <code>java.io.InputStream</code> object with the raw bytes.
     *
     * @return a Java object with the content of this <code>AttachmentPart</code>
     *         object
     *
     * @exception SOAPException if there is no content set into this
     *            <code>AttachmentPart</code> object or if there was a data
     *            transformation error
     */
    public abstract Object getContent() throws SOAPException;

    /**
     * Gets the content of this <code>AttachmentPart</code> object as an
     * InputStream as if a call had been made to <code>getContent</code> and no
     * <code>DataContentHandler</code> had been registered for the
     * <code>content-type</code> of this <code>AttachmentPart</code>.
     *<p>
     * Note that reading from the returned InputStream would result in consuming
     * the data in the stream. It is the responsibility of the caller to reset
     * the InputStream appropriately before calling a Subsequent API. If a copy
     * of the raw attachment content is required then the {@link #getRawContentBytes} API
     * should be used instead.
     *
     * @return an <code>InputStream</code> from which the raw data contained by
     *      the <code>AttachmentPart</code> can be accessed.
     *
     * @throws SOAPException if there is no content set into this
     *      <code>AttachmentPart</code> object or if there was a data
     *      transformation error.
     *
     * @since SAAJ 1.3
     * @see #getRawContentBytes
     */
    public abstract InputStream getRawContent() throws SOAPException;

    /**
     * Gets the content of this <code>AttachmentPart</code> object as a
     * byte[] array as if a call had been made to <code>getContent</code> and no
     * <code>DataContentHandler</code> had been registered for the
     * <code>content-type</code> of this <code>AttachmentPart</code>.
     *
     * @return a <code>byte[]</code> array containing the raw data of the
     *      <code>AttachmentPart</code>.
     *
     * @throws SOAPException if there is no content set into this
     *      <code>AttachmentPart</code> object or if there was a data
     *      transformation error.
     *
     * @since SAAJ 1.3
     */
    public abstract byte[] getRawContentBytes() throws SOAPException;

    /**
     * Returns an <code>InputStream</code> which can be used to obtain the
     * content of <code>AttachmentPart</code>  as Base64 encoded
     * character data, this method would base64 encode the raw bytes
     * of the attachment and return.
     *
     * @return an <code>InputStream</code> from which the Base64 encoded
     *       <code>AttachmentPart</code> can be read.
     *
     * @throws SOAPException if there is no content set into this
     *      <code>AttachmentPart</code> object or if there was a data
     *      transformation error.
     *
     * @since SAAJ 1.3
     */
    public abstract InputStream getBase64Content() throws SOAPException;

    /**
     * Sets the content of this attachment part to that of the given
     * <code>Object</code> and sets the value of the <code>Content-Type</code>
     * header to the given type. The type of the
     * <code>Object</code> should correspond to the value given for the
     * <code>Content-Type</code>. This depends on the particular
     * set of <code>DataContentHandler</code> objects in use.
     *
     *
     * @param object the Java object that makes up the content for
     *               this attachment part
     * @param contentType the MIME string that specifies the type of
     *                  the content
     *
     * @exception IllegalArgumentException may be thrown if the contentType
     *            does not match the type of the content object, or if there
     *            was no <code>DataContentHandler</code> object for this
     *            content object
     *
     * @see #getContent
     */
    public abstract void setContent(Object object, String contentType);

    /**
     * Sets the content of this attachment part to that contained by the
     * <code>InputStream</code> <code>content</code> and sets the value of the
     * <code>Content-Type</code> header to the value contained in
     * <code>contentType</code>.
     * <P>
     *  A subsequent call to getSize() may not be an exact measure
     *  of the content size.
     *
     * @param content the raw data to add to the attachment part
     * @param contentType the value to set into the <code>Content-Type</code>
     * header
     *
     * @exception SOAPException if an there is an error in setting the content
     * @exception NullPointerException if <code>content</code> is null
     * @since SAAJ 1.3
     */
    public abstract void setRawContent(InputStream content, String contentType) throws SOAPException;

    /**
     * Sets the content of this attachment part to that contained by the
     * <code>byte[]</code> array <code>content</code> and sets the value of the
     * <code>Content-Type</code> header to the value contained in
     * <code>contentType</code>.
     *
     * @param content the raw data to add to the attachment part
     * @param contentType the value to set into the <code>Content-Type</code>
     * header
     * @param offset the offset in the byte array of the content
     * @param len the number of bytes that form the content
     *
     * @exception SOAPException if an there is an error in setting the content
     * or content is null
     * @since SAAJ 1.3
     */
    public abstract void setRawContentBytes(
        byte[] content, int offset, int len,  String contentType)
        throws SOAPException;


    /**
     * Sets the content of this attachment part from the Base64 source
     * <code>InputStream</code>  and sets the value of the
     * <code>Content-Type</code> header to the value contained in
     * <code>contentType</code>, This method would first decode the base64
     * input and write the resulting raw bytes to the attachment.
     * <P>
     *  A subsequent call to getSize() may not be an exact measure
     *  of the content size.
     *
     * @param content the base64 encoded data to add to the attachment part
     * @param contentType the value to set into the <code>Content-Type</code>
     * header
     *
     * @exception SOAPException if an there is an error in setting the content
     * @exception NullPointerException if <code>content</code> is null
     *
     * @since SAAJ 1.3
     */
    public abstract void setBase64Content(
        InputStream content, String contentType) throws SOAPException;


    /**
     * Gets the <code>DataHandler</code> object for this <code>AttachmentPart</code>
     * object.
     *
     * @return the <code>DataHandler</code> object associated with this
     *         <code>AttachmentPart</code> object
     *
     * @exception SOAPException if there is no data in
     * this <code>AttachmentPart</code> object
     */
    public abstract DataHandler getDataHandler()
        throws SOAPException;

    /**
     * Sets the given <code>DataHandler</code> object as the data handler
     * for this <code>AttachmentPart</code> object. Typically, on an incoming
     * message, the data handler is automatically set. When
     * a message is being created and populated with content, the
     * <code>setDataHandler</code> method can be used to get data from
     * various data sources into the message.
     *
     * @param dataHandler the <code>DataHandler</code> object to be set
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified <code>DataHandler</code> object
     */
    public abstract void setDataHandler(DataHandler dataHandler);


    /**
     * Gets the value of the MIME header whose name is "Content-ID".
     *
     * @return a <code>String</code> giving the value of the
     *          "Content-ID" header or <code>null</code> if there
     *          is none
     * @see #setContentId
     */
    public String getContentId() {
        String[] values = getMimeHeader("Content-ID");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    /**
     * Gets the value of the MIME header whose name is "Content-Location".
     *
     * @return a <code>String</code> giving the value of the
     *          "Content-Location" header or <code>null</code> if there
     *          is none
     */
    public String getContentLocation() {
        String[] values = getMimeHeader("Content-Location");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    /**
     * Gets the value of the MIME header whose name is "Content-Type".
     *
     * @return a <code>String</code> giving the value of the
     *          "Content-Type" header or <code>null</code> if there
     *          is none
     */
    public String getContentType() {
        String[] values = getMimeHeader("Content-Type");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    /**
     * Sets the MIME header whose name is "Content-ID" with the given value.
     *
     * @param contentId a <code>String</code> giving the value of the
     *          "Content-ID" header
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified <code>contentId</code> value
     * @see #getContentId
     */
    public void setContentId(String contentId)
    {
        setMimeHeader("Content-ID", contentId);
    }


    /**
     * Sets the MIME header whose name is "Content-Location" with the given value.
     *
     *
     * @param contentLocation a <code>String</code> giving the value of the
     *          "Content-Location" header
     * @exception IllegalArgumentException if there was a problem with
     *            the specified content location
     */
    public void setContentLocation(String contentLocation)
    {
        setMimeHeader("Content-Location", contentLocation);
    }

    /**
     * Sets the MIME header whose name is "Content-Type" with the given value.
     *
     * @param contentType a <code>String</code> giving the value of the
     *          "Content-Type" header
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified content type
     */
    public void setContentType(String contentType)
    {
        setMimeHeader("Content-Type", contentType);
    }

    /**
     * Removes all MIME headers that match the given name.
     *
     * @param header the string name of the MIME header/s to
     *               be removed
     */
    public abstract void removeMimeHeader(String header);

    /**
     * Removes all the MIME header entries.
     */
    public abstract void removeAllMimeHeaders();


    /**
     * Gets all the values of the header identified by the given
     * <code>String</code>.
     *
     * @param name the name of the header; example: "Content-Type"
     * @return a <code>String</code> array giving the value for the
     *         specified header
     * @see #setMimeHeader
     */
    public abstract String[] getMimeHeader(String name);


    /**
     * Changes the first header entry that matches the given name
     * to the given value, adding a new header if no existing header
     * matches. This method also removes all matching headers but the first. <p>
     *
     * Note that RFC822 headers can only contain US-ASCII characters.
     *
     * @param   name    a <code>String</code> giving the name of the header
     *                  for which to search
     * @param   value   a <code>String</code> giving the value to be set for
     *                  the header whose name matches the given name
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified mime header name or value
     */
    public abstract void setMimeHeader(String name, String value);


    /**
     * Adds a MIME header with the specified name and value to this
     * <code>AttachmentPart</code> object.
     * <p>
     * Note that RFC822 headers can contain only US-ASCII characters.
     *
     * @param   name    a <code>String</code> giving the name of the header
     *                  to be added
     * @param   value   a <code>String</code> giving the value of the header
     *                  to be added
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified mime header name or value
     */
    public abstract void addMimeHeader(String name, String value);

    /**
     * Retrieves all the headers for this <code>AttachmentPart</code> object
     * as an iterator over the <code>MimeHeader</code> objects.
     *
     * @return  an <code>Iterator</code> object with all of the Mime
     *          headers for this <code>AttachmentPart</code> object
     */
    public abstract Iterator getAllMimeHeaders();

    /**
     * Retrieves all <code>MimeHeader</code> objects that match a name in
     * the given array.
     *
     * @param names a <code>String</code> array with the name(s) of the
     *        MIME headers to be returned
     * @return  all of the MIME headers that match one of the names in the
     *           given array as an <code>Iterator</code> object
     */
    public abstract Iterator getMatchingMimeHeaders(String[] names);

    /**
     * Retrieves all <code>MimeHeader</code> objects whose name does
     * not match a name in the given array.
     *
     * @param names a <code>String</code> array with the name(s) of the
     *        MIME headers not to be returned
     * @return  all of the MIME headers in this <code>AttachmentPart</code> object
     *          except those that match one of the names in the
     *           given array.  The nonmatching MIME headers are returned as an
     *           <code>Iterator</code> object.
     */
    public abstract Iterator getNonMatchingMimeHeaders(String[] names);
}
