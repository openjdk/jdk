/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.resources;

import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class XmlmessageMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.xmlmessage");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableXML_NULL_HEADERS() {
        return messageFactory.getMessage("xml.null.headers");
    }

    /**
     * Invalid argument. MimeHeaders=null
     *
     */
    public static String XML_NULL_HEADERS() {
        return localizer.localize(localizableXML_NULL_HEADERS());
    }

    public static Localizable localizableXML_SET_PAYLOAD_ERR() {
        return messageFactory.getMessage("xml.set.payload.err");
    }

    /**
     * Couldn't set Payload in XMLMessage
     *
     */
    public static String XML_SET_PAYLOAD_ERR() {
        return localizer.localize(localizableXML_SET_PAYLOAD_ERR());
    }

    public static Localizable localizableXML_CONTENT_TYPE_MUSTBE_MULTIPART() {
        return messageFactory.getMessage("xml.content-type.mustbe.multipart");
    }

    /**
     * Content-Type needs to be Multipart/Related and with type=text/xml
     *
     */
    public static String XML_CONTENT_TYPE_MUSTBE_MULTIPART() {
        return localizer.localize(localizableXML_CONTENT_TYPE_MUSTBE_MULTIPART());
    }

    public static Localizable localizableXML_UNKNOWN_CONTENT_TYPE() {
        return messageFactory.getMessage("xml.unknown.Content-Type");
    }

    /**
     * Unrecognized Content-Type
     *
     */
    public static String XML_UNKNOWN_CONTENT_TYPE() {
        return localizer.localize(localizableXML_UNKNOWN_CONTENT_TYPE());
    }

    public static Localizable localizableXML_GET_DS_ERR() {
        return messageFactory.getMessage("xml.get.ds.err");
    }

    /**
     * Couldn't get DataSource
     *
     */
    public static String XML_GET_DS_ERR() {
        return localizer.localize(localizableXML_GET_DS_ERR());
    }

    public static Localizable localizableXML_CONTENT_TYPE_PARSE_ERR() {
        return messageFactory.getMessage("xml.Content-Type.parse.err");
    }

    /**
     * Error while parsing MimeHeaders for Content-Type
     *
     */
    public static String XML_CONTENT_TYPE_PARSE_ERR() {
        return localizer.localize(localizableXML_CONTENT_TYPE_PARSE_ERR());
    }

    public static Localizable localizableXML_GET_SOURCE_ERR() {
        return messageFactory.getMessage("xml.get.source.err");
    }

    /**
     * Couldn't return Source
     *
     */
    public static String XML_GET_SOURCE_ERR() {
        return localizer.localize(localizableXML_GET_SOURCE_ERR());
    }

    public static Localizable localizableXML_CANNOT_INTERNALIZE_MESSAGE() {
        return messageFactory.getMessage("xml.cannot.internalize.message");
    }

    /**
     * Cannot create XMLMessage
     *
     */
    public static String XML_CANNOT_INTERNALIZE_MESSAGE() {
        return localizer.localize(localizableXML_CANNOT_INTERNALIZE_MESSAGE());
    }

    public static Localizable localizableXML_NO_CONTENT_TYPE() {
        return messageFactory.getMessage("xml.no.Content-Type");
    }

    /**
     * MimeHeaders doesn't contain Content-Type header
     *
     */
    public static String XML_NO_CONTENT_TYPE() {
        return localizer.localize(localizableXML_NO_CONTENT_TYPE());
    }

    public static Localizable localizableXML_ROOT_PART_INVALID_CONTENT_TYPE(Object arg0) {
        return messageFactory.getMessage("xml.root.part.invalid.Content-Type", arg0);
    }

    /**
     * Bad Content-Type for Root Part : {0}
     *
     */
    public static String XML_ROOT_PART_INVALID_CONTENT_TYPE(Object arg0) {
        return localizer.localize(localizableXML_ROOT_PART_INVALID_CONTENT_TYPE(arg0));
    }

    public static Localizable localizableXML_INVALID_CONTENT_TYPE(Object arg0) {
        return messageFactory.getMessage("xml.invalid.content-type", arg0);
    }

    /**
     * Invalid Content-Type: {0}
     *
     */
    public static String XML_INVALID_CONTENT_TYPE(Object arg0) {
        return localizer.localize(localizableXML_INVALID_CONTENT_TYPE(arg0));
    }

}
