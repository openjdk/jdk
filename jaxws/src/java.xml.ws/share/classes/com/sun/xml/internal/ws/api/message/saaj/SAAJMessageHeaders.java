/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.message.saaj;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.MessageHeaders;
import com.sun.xml.internal.ws.binding.SOAPBindingImpl;
import com.sun.xml.internal.ws.message.saaj.SAAJHeader;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SAAJMessageHeaders implements MessageHeaders {
    SOAPMessage sm;
    Map<SOAPHeaderElement, Header> nonSAAJHeaders;
    Map<QName, Integer> notUnderstoodCount;
    SOAPVersion soapVersion;
    private Set<QName> understoodHeaders;

    public SAAJMessageHeaders(SOAPMessage sm, SOAPVersion version) {
        this.sm = sm;
        this.soapVersion = version;
        initHeaderUnderstanding();
    }

    /** Set the initial understood/not understood state of the headers in this
     * object
     */
    private void initHeaderUnderstanding() {
        SOAPHeader soapHeader = ensureSOAPHeader();
        if (soapHeader == null) {
            return;
        }

        Iterator allHeaders = soapHeader.examineAllHeaderElements();
        while(allHeaders.hasNext()) {
            SOAPHeaderElement nextHdrElem = (SOAPHeaderElement) allHeaders.next();
            if (nextHdrElem == null) {
                continue;
            }
            if (nextHdrElem.getMustUnderstand()) {
                notUnderstood(nextHdrElem.getElementQName());
            }
            //only headers explicitly marked as understood should be
            //in the understoodHeaders set, so don't add anything to
            //that set at the beginning
        }

    }

    @Override
    public void understood(Header header) {
        understood(header.getNamespaceURI(), header.getLocalPart());
    }

    @Override
    public void understood(String nsUri, String localName) {
        understood(new QName(nsUri, localName));
    }

    @Override
    public void understood(QName qName) {
        if (notUnderstoodCount == null) {
            notUnderstoodCount = new HashMap<QName, Integer>();
        }

        Integer count = notUnderstoodCount.get(qName);
        if (count != null && count.intValue() > 0) {
            //found the header in notUnderstood headers - decrement count
            count = count.intValue() - 1;
            if (count <= 0) {
                //if the value is zero or negative, remove that header name
                //since all headers by that name are understood now
                notUnderstoodCount.remove(qName);
            } else {
                notUnderstoodCount.put(qName, count);
            }
        }

        if (understoodHeaders == null) {
            understoodHeaders = new HashSet<QName>();
        }
        //also add it to the understood headers list (optimization for getUnderstoodHeaders)
        understoodHeaders.add(qName);

    }

    @Override
    public boolean isUnderstood(Header header) {
        return isUnderstood(header.getNamespaceURI(), header.getLocalPart());
    }
    @Override
    public boolean isUnderstood(String nsUri, String localName) {
        return isUnderstood(new QName(nsUri, localName));
    }

    @Override
    public boolean isUnderstood(QName name) {
        if (understoodHeaders == null) {
            return false;
        }
        return understoodHeaders.contains(name);
    }

    public boolean isUnderstood(int index) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Header get(String nsUri, String localName, boolean markAsUnderstood) {
        SOAPHeaderElement h = find(nsUri, localName);
        if (h != null) {
            if (markAsUnderstood) {
                understood(nsUri, localName);
            }
            return new SAAJHeader(h);
        }
        return null;
    }

    @Override
    public Header get(QName name, boolean markAsUnderstood) {
        return get(name.getNamespaceURI(), name.getLocalPart(), markAsUnderstood);
    }

    @Override
    public Iterator<Header> getHeaders(QName headerName,
            boolean markAsUnderstood) {
        return getHeaders(headerName.getNamespaceURI(), headerName.getLocalPart(), markAsUnderstood);
    }

    @Override
    public Iterator<Header> getHeaders(final String nsUri, final String localName,
            final boolean markAsUnderstood) {
        SOAPHeader soapHeader = ensureSOAPHeader();
        if (soapHeader == null) {
            return null;
        }
        Iterator allHeaders = soapHeader.examineAllHeaderElements();
        if (markAsUnderstood) {
            //mark all the matchingheaders as understood up front
            //make an iterator while we're doing that
            List<Header> headers = new ArrayList<Header>();
            while (allHeaders.hasNext()) {
                SOAPHeaderElement nextHdr = (SOAPHeaderElement) allHeaders.next();
                if (nextHdr != null &&
                        nextHdr.getNamespaceURI().equals(nsUri)) {
                    if (localName == null ||
                            nextHdr.getLocalName().equals(localName)) {
                        understood(nextHdr.getNamespaceURI(), nextHdr.getLocalName());
                        headers.add(new SAAJHeader(nextHdr));
                    }
                }
            }
            return headers.iterator();
        }
        //if we got here markAsUnderstood is false - return a lazy iterator rather
        //than traverse the entire list of headers now
        return new HeaderReadIterator(allHeaders, nsUri, localName);
    }

    @Override
    public Iterator<Header> getHeaders(String nsUri, boolean markAsUnderstood) {
        return getHeaders(nsUri, null, markAsUnderstood);
    }
    @Override
    public boolean add(Header header) {
        try {
            header.writeTo(sm);
        } catch (SOAPException e) {
            //TODO log exception
            return false;
        }

        //the newly added header is not understood by default
        notUnderstood(new QName(header.getNamespaceURI(), header.getLocalPart()));

        //track non saaj headers so that they can be retrieved later
        if (isNonSAAJHeader(header)) {
            //TODO assumes only one header with that name?
            addNonSAAJHeader(find(header.getNamespaceURI(), header.getLocalPart()),
                    header);
        }

        return true;
    }

    @Override
    public Header remove(QName name) {
        return remove(name.getNamespaceURI(), name.getLocalPart());
    }

    @Override
    public Header remove(String nsUri, String localName) {
        SOAPHeader soapHeader = ensureSOAPHeader();
        if (soapHeader == null) {
            return null;
        }
        SOAPHeaderElement headerElem = find(nsUri, localName);
        if (headerElem == null) {
            return null;
        }
        headerElem = (SOAPHeaderElement) soapHeader.removeChild(headerElem);

        //it might have been a nonSAAJHeader - remove from that map
        removeNonSAAJHeader(headerElem);

        //remove it from understoodHeaders and notUnderstoodHeaders if present
        QName hdrName = (nsUri == null) ? new QName(localName) : new QName(nsUri, localName);
        if (understoodHeaders != null) {
            understoodHeaders.remove(hdrName);
        }
        removeNotUnderstood(hdrName);

        return new SAAJHeader(headerElem);
    }

    private void removeNotUnderstood(QName hdrName) {
        if (notUnderstoodCount == null) {
            return;
        }
        Integer notUnderstood = notUnderstoodCount.get(hdrName);
        if (notUnderstood != null) {
            int intNotUnderstood = notUnderstood;
            intNotUnderstood--;
            if (intNotUnderstood <= 0) {
                notUnderstoodCount.remove(hdrName);
            }
        }

    }

    private SOAPHeaderElement find(QName qName) {
        return find(qName.getNamespaceURI(), qName.getLocalPart());
    }

    private SOAPHeaderElement find(String nsUri, String localName) {
        SOAPHeader soapHeader = ensureSOAPHeader();
        if (soapHeader == null) {
            return null;
        }
        Iterator allHeaders = soapHeader.examineAllHeaderElements();
        while(allHeaders.hasNext()) {
            SOAPHeaderElement nextHdrElem = (SOAPHeaderElement) allHeaders.next();
            if (nextHdrElem.getNamespaceURI().equals(nsUri) &&
                    nextHdrElem.getLocalName().equals(localName)) {
                return nextHdrElem;
            }
        }
        return null;
    }

    private void notUnderstood(QName qName) {
        if (notUnderstoodCount == null) {
            notUnderstoodCount = new HashMap<QName, Integer>();
        }
        Integer count = notUnderstoodCount.get(qName);
        if (count == null) {
            notUnderstoodCount.put(qName, 1);
        } else {
            notUnderstoodCount.put(qName, count + 1);
        }

        //if for some strange reason it was previously understood and now is not,
        //remove it from understoodHeaders if it exists there
        if (understoodHeaders != null) {
            understoodHeaders.remove(qName);
        }
    }

    /**
     * Utility method to get the SOAPHeader from a SOAPMessage, adding one if
     * one is not present in the original message.
     */
    private SOAPHeader ensureSOAPHeader() {
        SOAPHeader header;
        try {
            header = sm.getSOAPPart().getEnvelope().getHeader();
            if (header != null) {
                return header;
            } else {
                return sm.getSOAPPart().getEnvelope().addHeader();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNonSAAJHeader(Header header) {
        return !(header instanceof SAAJHeader);
    }

    private void addNonSAAJHeader(SOAPHeaderElement headerElem, Header header) {
        if (nonSAAJHeaders == null) {
            nonSAAJHeaders = new HashMap<>();
        }
        nonSAAJHeaders.put(headerElem, header);
    }

    private void removeNonSAAJHeader(SOAPHeaderElement headerElem) {
        if (nonSAAJHeaders != null) {
            nonSAAJHeaders.remove(headerElem);
        }
    }

    @Override
    public boolean addOrReplace(Header header) {
        remove(header.getNamespaceURI(), header.getLocalPart());
        return add(header);
    }

    @Override
    public void replace(Header old, Header header) {
        if (remove(old.getNamespaceURI(), old.getLocalPart()) == null)
            throw new IllegalArgumentException();
        add(header);
    }

    @Override
    public Set<QName> getUnderstoodHeaders() {
        return understoodHeaders;
    }

    @Override
    public Set<QName> getNotUnderstoodHeaders(Set<String> roles,
            Set<QName> knownHeaders, WSBinding binding) {
        Set<QName> notUnderstoodHeaderNames = new HashSet<QName>();
        if (notUnderstoodCount == null) {
            return notUnderstoodHeaderNames;
        }
        for (QName headerName : notUnderstoodCount.keySet()) {
            int count = notUnderstoodCount.get(headerName);
            if (count <= 0) {
                continue;
            }
            SOAPHeaderElement hdrElem = find(headerName);
            if (!hdrElem.getMustUnderstand()) {
                continue;
            }
            SAAJHeader hdr = new SAAJHeader(hdrElem);
            //mustUnderstand attribute is true - but there may be
            //additional criteria
            boolean understood = false;
            if (roles != null) {
                understood = !roles.contains(hdr.getRole(soapVersion));
            }
            if (understood) {
                continue;
            }
            //if it must be understood see if it is understood by the binding
            //or is in knownheaders
            if (binding != null && binding instanceof SOAPBindingImpl) {
                understood = ((SOAPBindingImpl) binding).understandsHeader(headerName);
                if (!understood) {
                    if (knownHeaders != null && knownHeaders.contains(headerName)) {
                        understood = true;
                    }
                }
            }
            if (!understood) {
                notUnderstoodHeaderNames.add(headerName);
            }
        }
        return notUnderstoodHeaderNames;
    }

    @Override
    public Iterator<Header> getHeaders() {
        SOAPHeader soapHeader = ensureSOAPHeader();
        if (soapHeader == null) {
            return null;
        }
        Iterator allHeaders = soapHeader.examineAllHeaderElements();
        return new HeaderReadIterator(allHeaders, null, null);
    }

    private static class HeaderReadIterator implements Iterator<Header> {
        SOAPHeaderElement current;
        Iterator soapHeaders;
        String myNsUri;
        String myLocalName;

        public HeaderReadIterator(Iterator allHeaders, String nsUri,
                String localName) {
            this.soapHeaders = allHeaders;
            this.myNsUri = nsUri;
            this.myLocalName = localName;
        }

        @Override
        public boolean hasNext() {
            if (current == null) {
                advance();
            }
            return (current != null);
        }

        @Override
        public Header next() {
            if (!hasNext()) {
                return null;
            }
            if (current == null) {
                return null;
            }

            SAAJHeader ret = new SAAJHeader(current);
            current = null;
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void advance() {
            while (soapHeaders.hasNext()) {
                SOAPHeaderElement nextHdr = (SOAPHeaderElement) soapHeaders.next();
                if (nextHdr != null &&
                        (myNsUri == null || nextHdr.getNamespaceURI().equals(myNsUri)) &&
                        (myLocalName == null || nextHdr.getLocalName().equals(myLocalName))) {
                        current = nextHdr;
                        //found it
                        return;
                    }
                }
            //if we got here we didn't find a match
            current = null;
        }

    }

    @Override
    public boolean hasHeaders() {
        SOAPHeader soapHeader = ensureSOAPHeader();
        if (soapHeader == null) {
            return false;
        }

        Iterator allHeaders = soapHeader.examineAllHeaderElements();
        return allHeaders.hasNext();
    }

    @Override
    public List<Header> asList() {
        SOAPHeader soapHeader = ensureSOAPHeader();
        if (soapHeader == null) {
            return Collections.emptyList();
        }

        Iterator allHeaders = soapHeader.examineAllHeaderElements();
        List<Header> headers = new ArrayList<Header>();
        while (allHeaders.hasNext()) {
            SOAPHeaderElement nextHdr = (SOAPHeaderElement) allHeaders.next();
            headers.add(new SAAJHeader(nextHdr));
        }
        return headers;
    }
}
