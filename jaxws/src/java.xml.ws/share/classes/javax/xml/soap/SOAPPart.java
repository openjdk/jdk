/*
 * Copyright (c) 2004, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import javax.xml.transform.Source;

/**
 * The container for the SOAP-specific portion of a {@code SOAPMessage}
 * object. All messages are required to have a SOAP part, so when a
 * {@code SOAPMessage} object is created, it will automatically
 * have a {@code SOAPPart} object.
 * <P>
 * A {@code SOAPPart} object is a MIME part and has the MIME headers
 * Content-Id, Content-Location, and Content-Type.  Because the value of
 * Content-Type must be "text/xml", a {@code SOAPPart} object automatically
 * has a MIME header of Content-Type with its value set to "text/xml".
 * The value must be "text/xml" because content in the SOAP part of a
 * message must be in XML format.  Content that is not of type "text/xml"
 * must be in an {@code AttachmentPart} object rather than in the
 * {@code SOAPPart} object.
 * <P>
 * When a message is sent, its SOAP part must have the MIME header Content-Type
 * set to "text/xml". Or, from the other perspective, the SOAP part of any
 * message that is received must have the MIME header Content-Type with a
 * value of "text/xml".
 * <P>
 * A client can access the {@code SOAPPart} object of a
 * {@code SOAPMessage} object by
 * calling the method {@code SOAPMessage.getSOAPPart}. The
 * following  line of code, in which {@code message} is a
 * {@code SOAPMessage} object, retrieves the SOAP part of a message.
 * <pre>{@code
 *   SOAPPart soapPart = message.getSOAPPart();
 * }</pre>
 * <P>
 * A {@code SOAPPart} object contains a {@code SOAPEnvelope} object,
 * which in turn contains a {@code SOAPBody} object and a
 * {@code SOAPHeader} object.
 * The {@code SOAPPart} method {@code getEnvelope} can be used
 * to retrieve the {@code SOAPEnvelope} object.
 *
 * @since 1.6
 */
public abstract class SOAPPart implements org.w3c.dom.Document, Node {

    /**
     * Gets the {@code SOAPEnvelope} object associated with this
     * {@code SOAPPart} object. Once the SOAP envelope is obtained, it
     * can be used to get its contents.
     *
     * @return the {@code SOAPEnvelope} object for this
     *           {@code SOAPPart} object
     * @exception SOAPException if there is a SOAP error
     */
    public abstract SOAPEnvelope getEnvelope() throws SOAPException;

    /**
     * Retrieves the value of the MIME header whose name is "Content-Id".
     *
     * @return a {@code String} giving the value of the MIME header
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
     * @return a {@code String} giving the value of the MIME header whose
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
     * to the given {@code String}.
     *
     * @param contentId a {@code String} giving the value of the MIME
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
     * to the given {@code String}.
     *
     * @param contentLocation a {@code String} giving the value
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
     * @param header a {@code String} giving the name of the MIME header(s) to
     *               be removed
     */
    public abstract void removeMimeHeader(String header);

    /**
     * Removes all the {@code MimeHeader} objects for this
     * {@code SOAPEnvelope} object.
     */
    public abstract void removeAllMimeHeaders();

    /**
     * Gets all the values of the {@code MimeHeader} object
     * in this {@code SOAPPart} object that
     * is identified by the given {@code String}.
     *
     * @param name the name of the header; example: "Content-Type"
     * @return a {@code String} array giving all the values for the
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
     * @param   name    a {@code String} giving the header name
     *                  for which to search
     * @param   value   a {@code String} giving the value to be set.
     *                  This value will be substituted for the current value(s)
     *                  of the first header that is a match if there is one.
     *                  If there is no match, this value will be the value for
     *                  a new {@code MimeHeader} object.
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified mime header name or value
     * @see #getMimeHeader
     */
    public abstract void setMimeHeader(String name, String value);

    /**
     * Creates a {@code MimeHeader} object with the specified
     * name and value and adds it to this {@code SOAPPart} object.
     * If a {@code MimeHeader} with the specified name already
     * exists, this method adds the specified value to the already
     * existing value(s).
     * <P>
     * Note that RFC822 headers can contain only US-ASCII characters.
     *
     * @param   name    a {@code String} giving the header name
     * @param   value   a {@code String} giving the value to be set
     *                  or added
     * @exception IllegalArgumentException if there was a problem with
     *            the specified mime header name or value
     */
    public abstract void addMimeHeader(String name, String value);

    /**
     * Retrieves all the headers for this {@code SOAPPart} object
     * as an iterator over the {@code MimeHeader} objects.
     *
     * @return  an {@code Iterator} object with all of the Mime
     *          headers for this {@code SOAPPart} object
     */
    public abstract Iterator<MimeHeader> getAllMimeHeaders();

    /**
     * Retrieves all {@code MimeHeader} objects that match a name in
     * the given array.
     *
     * @param names a {@code String} array with the name(s) of the
     *        MIME headers to be returned
     * @return  all of the MIME headers that match one of the names in the
     *           given array, returned as an {@code Iterator} object
     */
    public abstract Iterator<MimeHeader> getMatchingMimeHeaders(String[] names);

    /**
     * Retrieves all {@code MimeHeader} objects whose name does
     * not match a name in the given array.
     *
     * @param names a {@code String} array with the name(s) of the
     *        MIME headers not to be returned
     * @return  all of the MIME headers in this {@code SOAPPart} object
     *          except those that match one of the names in the
     *           given array.  The nonmatching MIME headers are returned as an
     *           {@code Iterator} object.
     */
    public abstract Iterator<MimeHeader> getNonMatchingMimeHeaders(String[] names);

    /**
     * Sets the content of the {@code SOAPEnvelope} object with the data
     * from the given {@code Source} object. This {@code Source}
     * must contain a valid SOAP document.
     *
     * @param source the {@code javax.xml.transform.Source} object with the
     *        data to be set
     *
     * @exception SOAPException if there is a problem in setting the source
     * @see #getContent
     */
    public abstract void setContent(Source source) throws SOAPException;

    /**
     * Returns the content of the SOAPEnvelope as a JAXP {@code Source}
     * object.
     *
     * @return the content as a {@code javax.xml.transform.Source} object
     *
     * @exception SOAPException if the implementation cannot convert
     *                          the specified {@code Source} object
     * @see #setContent
     */
    public abstract Source getContent() throws SOAPException;
}
