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
 * $Id: MimeHeaders.java,v 1.5 2005/04/05 20:49:49 mk125090 Exp $
 * $Revision: 1.5 $
 * $Date: 2005/04/05 20:49:49 $
 */


package javax.xml.soap;

import java.util.Iterator;
import java.util.Vector;

/**
 * A container for <code>MimeHeader</code> objects, which represent
 * the MIME headers present in a MIME part of a message.
 *
 * <p>This class is used primarily when an application wants to
 * retrieve specific attachments based on certain MIME headers and
 * values. This class will most likely be used by implementations of
 * <code>AttachmentPart</code> and other MIME dependent parts of the SAAJ
 * API.
 * @see SOAPMessage#getAttachments
 * @see AttachmentPart
 */
public class MimeHeaders {
    private Vector headers;

   /**
    * Constructs a default <code>MimeHeaders</code> object initialized with
    * an empty <code>Vector</code> object.
    */
    public MimeHeaders() {
        headers = new Vector();
    }

    /**
     * Returns all of the values for the specified header as an array of
     * <code>String</code> objects.
     *
     * @param   name the name of the header for which values will be returned
     * @return a <code>String</code> array with all of the values for the
     *         specified header
     * @see #setHeader
     */
    public String[] getHeader(String name) {
        Vector values = new Vector();

        for(int i = 0; i < headers.size(); i++) {
            MimeHeader hdr = (MimeHeader) headers.elementAt(i);
            if (hdr.getName().equalsIgnoreCase(name)
                && hdr.getValue() != null)
                values.addElement(hdr.getValue());
        }

        if (values.size() == 0)
            return null;

        String r[] = new String[values.size()];
        values.copyInto(r);
        return r;
    }

    /**
     * Replaces the current value of the first header entry whose name matches
     * the given name with the given value, adding a new header if no existing header
     * name matches. This method also removes all matching headers after the first one.
     * <P>
     * Note that RFC822 headers can contain only US-ASCII characters.
     *
     * @param   name a <code>String</code> with the name of the header for
     *          which to search
     * @param   value a <code>String</code> with the value that will replace the
     *          current value of the specified header
     *
     * @exception IllegalArgumentException if there was a problem in the
     * mime header name or the value being set
     * @see #getHeader
     */
    public void setHeader(String name, String value)
    {
        boolean found = false;

        if ((name == null) || name.equals(""))
            throw new IllegalArgumentException("Illegal MimeHeader name");

        for(int i = 0; i < headers.size(); i++) {
            MimeHeader hdr = (MimeHeader) headers.elementAt(i);
            if (hdr.getName().equalsIgnoreCase(name)) {
                if (!found) {
                    headers.setElementAt(new MimeHeader(hdr.getName(),
                                                        value), i);
                    found = true;
                }
                else
                    headers.removeElementAt(i--);
            }
        }

        if (!found)
            addHeader(name, value);
    }

    /**
     * Adds a <code>MimeHeader</code> object with the specified name and value
     * to this <code>MimeHeaders</code> object's list of headers.
     * <P>
     * Note that RFC822 headers can contain only US-ASCII characters.
     *
     * @param   name a <code>String</code> with the name of the header to
     *          be added
     * @param   value a <code>String</code> with the value of the header to
     *          be added
     *
     * @exception IllegalArgumentException if there was a problem in the
     * mime header name or value being added
     */
    public void addHeader(String name, String value)
    {
        if ((name == null) || name.equals(""))
            throw new IllegalArgumentException("Illegal MimeHeader name");

        int pos = headers.size();

        for(int i = pos - 1 ; i >= 0; i--) {
            MimeHeader hdr = (MimeHeader) headers.elementAt(i);
            if (hdr.getName().equalsIgnoreCase(name)) {
                headers.insertElementAt(new MimeHeader(name, value),
                                        i+1);
                return;
            }
        }
        headers.addElement(new MimeHeader(name, value));
    }

    /**
     * Remove all <code>MimeHeader</code> objects whose name matches the
     * given name.
     *
     * @param   name a <code>String</code> with the name of the header for
     *          which to search
     */
    public void removeHeader(String name) {
        for(int i = 0; i < headers.size(); i++) {
            MimeHeader hdr = (MimeHeader) headers.elementAt(i);
            if (hdr.getName().equalsIgnoreCase(name))
                headers.removeElementAt(i--);
        }
    }

    /**
     * Removes all the header entries from this <code>MimeHeaders</code> object.
     */
    public void removeAllHeaders() {
        headers.removeAllElements();
    }


    /**
     * Returns all the <code>MimeHeader</code>s in this <code>MimeHeaders</code> object.
     *
     * @return  an <code>Iterator</code> object over this <code>MimeHeaders</code>
     *          object's list of <code>MimeHeader</code> objects
     */
    public Iterator getAllHeaders() {
        return headers.iterator();
    }

    class MatchingIterator implements Iterator {
        private boolean match;
        private Iterator iterator;
        private String[] names;
        private Object nextHeader;

        MatchingIterator(String[] names, boolean match) {
            this.match = match;
            this.names = names;
            this.iterator = headers.iterator();
        }

        private Object nextMatch() {
        next:
            while (iterator.hasNext()) {
                MimeHeader hdr = (MimeHeader) iterator.next();

                if (names == null)
                    return match ? null : hdr;

                for(int i = 0; i < names.length; i++)
                    if (hdr.getName().equalsIgnoreCase(names[i]))
                        if (match)
                            return hdr;
                        else
                            continue next;
                if (!match)
                    return hdr;
            }
            return null;
        }


        public boolean hasNext() {
            if (nextHeader == null)
                nextHeader = nextMatch();
            return nextHeader != null;
        }

        public Object next() {
            // hasNext should've prefetched the header for us,
            // return it.
            if (nextHeader != null) {
                Object ret = nextHeader;
                nextHeader = null;
                return ret;
            }
            if (hasNext())
                return nextHeader;
            return null;
        }

        public void remove() {
            iterator.remove();
        }
    }


    /**
     * Returns all the <code>MimeHeader</code> objects whose name matches
     * a name in the given array of names.
     *
     * @param names an array of <code>String</code> objects with the names
     *         for which to search
     * @return  an <code>Iterator</code> object over the <code>MimeHeader</code>
     *          objects whose name matches one of the names in the given list
     */
    public Iterator getMatchingHeaders(String[] names) {
        return new MatchingIterator(names, true);
    }

    /**
     * Returns all of the <code>MimeHeader</code> objects whose name does not
     * match a name in the given array of names.
     *
     * @param names an array of <code>String</code> objects with the names
     *         for which to search
     * @return  an <code>Iterator</code> object over the <code>MimeHeader</code>
     *          objects whose name does not match one of the names in the given list
     */
    public Iterator getNonMatchingHeaders(String[] names) {
        return new MatchingIterator(names, false);
    }
}
