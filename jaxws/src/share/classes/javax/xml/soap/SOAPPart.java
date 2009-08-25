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
/*
 * $Id: SOAPPart.java,v 1.10 2006/03/30 00:59:42 ofung Exp $
 * $Revision: 1.10 $
 * $Date: 2006/03/30 00:59:42 $
 */


package javax.xml.soap;

import java.util.Iterator;

import javax.xml.transform.Source;

/**
 * The container for the SOAP-specific portion of a <code>SOAPMessage</code>
 * object. All messages are required to have a SOAP part, so when a
 * <code>SOAPMessage</code> object is created, it will automatically
 * have a <code>SOAPPart</code> object.
 *<P>
 * A <code>SOAPPart</code> object is a MIME part and has the MIME headers
 * Content-Id, Content-Location, and Content-Type.  Because the value of
 * Content-Type must be "text/xml", a <code>SOAPPart</code> object automatically
 * has a MIME header of Content-Type with its value set to "text/xml".
 * The value must be "text/xml" because content in the SOAP part of a
 * message must be in XML format.  Content that is not of type "text/xml"
 * must be in an <code>AttachmentPart</code> object rather than in the
 * <code>SOAPPart</code> object.
 * <P>
 * When a message is sent, its SOAP part must have the MIME header Content-Type
 * set to "text/xml". Or, from the other perspective, the SOAP part of any
 * message that is received must have the MIME header Content-Type with a
 * value of "text/xml".
 * <P>
 * A client can access the <code>SOAPPart</code> object of a
 * <code>SOAPMessage</code> object by
 * calling the method <code>SOAPMessage.getSOAPPart</code>. The
 * following  line of code, in which <code>message</code> is a
 * <code>SOAPMessage</code> object, retrieves the SOAP part of a message.
 * <PRE>
 *   SOAPPart soapPart = message.getSOAPPart();
 * </PRE>
 * <P>
 * A <code>SOAPPart</code> object contains a <code>SOAPEnvelope</code> object,
 * which in turn contains a <code>SOAPBody</code> object and a
 * <code>SOAPHeader</code> object.
 * The <code>SOAPPart</code> method <code>getEnvelope</code> can be used
 * to retrieve the <code>SOAPEnvelope</code> object.
 * <P>
 */
public abstract class SOAPPart implements org.w3c.dom.Document, Node {

    /**
     * Gets the <code>SOAPEnvelope</code> object associated with this
     * <code>SOAPPart</code> object. Once the SOAP envelope is obtained, it
     * can be used to get its contents.
     *
     * @return the <code>SOAPEnvelope</code> object for this
     *           <code>SOAPPart</code> object
     * @exception SOAPException if there is a SOAP error
     */
    public abstract SOAPEnvelope getEnvelope() throws SOAPException;

    /**
     * Retrieves the value of the MIME header whose name is "Content-Id".
     *
     * @return a <code>String</code> giving the value of the MIME header
     *         named "Content-Id"
     * @see #setContentId
     */
    public String getContentId() {
        String[] values = getMimeHeader("Content-Id");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    /**
     * Retrieves the value of the MIME header whose name is "Content-Location".
     *
     * @return a <code>String</code> giving the value of the MIME header whose
     *          name is "Content-Location"
     * @see #setContentLocation
     */
    public String getContentLocation() {
        String[] values = getMimeHeader("Content-Location");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    /**
     * Sets the value of the MIME header named "Content-Id"
     * to the given <code>String</code>.
     *
     * @param contentId a <code>String</code> giving the value of the MIME
     *        header "Content-Id"
     *
     * @exception IllegalArgumentException if there is a problem in
     * setting the content id
     * @see #getContentId
     */
    public void setContentId(String contentId)
    {
        setMimeHeader("Content-Id", contentId);
    }
    /**
     * Sets the value of the MIME header "Content-Location"
     * to the given <code>String</code>.
     *
     * @param contentLocation a <code>String</code> giving the value
     *        of the MIME
     *        header "Content-Location"
     * @exception IllegalArgumentException if there is a problem in
     *            setting the content location.
     * @see #getContentLocation
     */
    public void setContentLocation(String contentLocation)
    {
        setMimeHeader("Content-Location", contentLocation);
    }
    /**
     * Removes all MIME headers that match the given name.
     *
     * @param header a <code>String</code> giving the name of the MIME header(s) to
     *               be removed
     */
    public abstract void removeMimeHeader(String header);

    /**
     * Removes all the <code>MimeHeader</code> objects for this
     * <code>SOAPEnvelope</code> object.
     */
    public abstract void removeAllMimeHeaders();

    /**
     * Gets all the values of the <code>MimeHeader</code> object
     * in this <code>SOAPPart</code> object that
     * is identified by the given <code>String</code>.
     *
     * @param name the name of the header; example: "Content-Type"
     * @return a <code>String</code> array giving all the values for the
     *         specified header
     * @see #setMimeHeader
     */
    public abstract String[] getMimeHeader(String name);

    /**
     * Changes the first header entry that matches the given header name
     * so that its value is the given value, adding a new header with the
     * given name and value if no
     * existing header is a match. If there is a match, this method clears
     * all existing values for the first header that matches and sets the
     * given value instead. If more than one header has
     * the given name, this method removes all of the matching headers after
     * the first one.
     * <P>
     * Note that RFC822 headers can contain only US-ASCII characters.
     *
     * @param   name    a <code>String</code> giving the header name
     *                  for which to search
     * @param   value   a <code>String</code> giving the value to be set.
     *                  This value will be substituted for the current value(s)
     *                  of the first header that is a match if there is one.
     *                  If there is no match, this value will be the value for
     *                  a new <code>MimeHeader</code> object.
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified mime header name or value
     * @see #getMimeHeader
     */
    public abstract void setMimeHeader(String name, String value);

    /**
     * Creates a <code>MimeHeader</code> object with the specified
     * name and value and adds it to this <code>SOAPPart</code> object.
     * If a <code>MimeHeader</code> with the specified name already
     * exists, this method adds the specified value to the already
     * existing value(s).
     * <P>
     * Note that RFC822 headers can contain only US-ASCII characters.
     *
     * @param   name    a <code>String</code> giving the header name
     * @param   value   a <code>String</code> giving the value to be set
     *                  or added
     * @exception IllegalArgumentException if there was a problem with
     *            the specified mime header name or value
     */
    public abstract void addMimeHeader(String name, String value);

    /**
     * Retrieves all the headers for this <code>SOAPPart</code> object
     * as an iterator over the <code>MimeHeader</code> objects.
     *
     * @return  an <code>Iterator</code> object with all of the Mime
     *          headers for this <code>SOAPPart</code> object
     */
    public abstract Iterator getAllMimeHeaders();

    /**
     * Retrieves all <code>MimeHeader</code> objects that match a name in
     * the given array.
     *
     * @param names a <code>String</code> array with the name(s) of the
     *        MIME headers to be returned
     * @return  all of the MIME headers that match one of the names in the
     *           given array, returned as an <code>Iterator</code> object
     */
    public abstract Iterator getMatchingMimeHeaders(String[] names);

    /**
     * Retrieves all <code>MimeHeader</code> objects whose name does
     * not match a name in the given array.
     *
     * @param names a <code>String</code> array with the name(s) of the
     *        MIME headers not to be returned
     * @return  all of the MIME headers in this <code>SOAPPart</code> object
     *          except those that match one of the names in the
     *           given array.  The nonmatching MIME headers are returned as an
     *           <code>Iterator</code> object.
     */
    public abstract Iterator getNonMatchingMimeHeaders(String[] names);

    /**
     * Sets the content of the <code>SOAPEnvelope</code> object with the data
     * from the given <code>Source</code> object. This <code>Source</code>
     * must contain a valid SOAP document.
     *
     * @param source the <code>javax.xml.transform.Source</code> object with the
     *        data to be set
     *
     * @exception SOAPException if there is a problem in setting the source
     * @see #getContent
     */
    public abstract void setContent(Source source) throws SOAPException;

    /**
     * Returns the content of the SOAPEnvelope as a JAXP <code>Source</code>
     * object.
     *
     * @return the content as a <code>javax.xml.transform.Source</code> object
     *
     * @exception SOAPException if the implementation cannot convert
     *                          the specified <code>Source</code> object
     * @see #setContent
     */
    public abstract Source getContent() throws SOAPException;
}
