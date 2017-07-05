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

package com.sun.xml.internal.ws.util.exception;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.resources.UtilMessages;
import org.xml.sax.Locator;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.WebServiceException;
import java.util.Arrays;
import java.util.List;

/**
 * {@link WebServiceException} with source location informaiton.
 *
 * <p>
 * This exception should be used wherever the location information is available,
 * so that the location information is carried forward to users (to assist
 * error diagnostics.)
 *
 * @author Kohsuke Kawaguchi
 */
public class LocatableWebServiceException extends WebServiceException {
    /**
     * Locations related to error.
     */
    private final Locator[] location;

    public LocatableWebServiceException(String message, Locator... location) {
        this(message,null,location);
    }

    public LocatableWebServiceException(String message, Throwable cause, Locator... location) {
        super(appendLocationInfo(message,location), cause);
        this.location = location;
    }

    public LocatableWebServiceException(Throwable cause, Locator... location) {
        this(cause.toString(),cause,location);
    }

    public LocatableWebServiceException(String message, XMLStreamReader locationSource) {
        this(message,toLocation(locationSource));
    }

    public LocatableWebServiceException(String message, Throwable cause, XMLStreamReader locationSource) {
        this(message,cause,toLocation(locationSource));
    }

    public LocatableWebServiceException(Throwable cause, XMLStreamReader locationSource) {
        this(cause,toLocation(locationSource));
    }

    /**
     * Locations related to this exception.
     *
     * @return
     *      Can be empty but never null.
     */
    public @NotNull List<Locator> getLocation() {
        return Arrays.asList(location);
    }

    private static String appendLocationInfo(String message, Locator[] location) {
        StringBuilder buf = new StringBuilder(message);
        for( Locator loc : location )
            buf.append('\n').append(UtilMessages.UTIL_LOCATION( loc.getLineNumber(), loc.getSystemId() ));
        return buf.toString();
    }

    private static Locator toLocation(XMLStreamReader xsr) {
        LocatorImpl loc = new LocatorImpl();
        Location in = xsr.getLocation();
        loc.setSystemId(in.getSystemId());
        loc.setPublicId(in.getPublicId());
        loc.setLineNumber(in.getLineNumber());
        loc.setColumnNumber(in.getColumnNumber());
        return loc;
    }
}
