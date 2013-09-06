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
public final class StreamingMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.streaming");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableFASTINFOSET_DECODING_NOT_ACCEPTED() {
        return messageFactory.getMessage("fastinfoset.decodingNotAccepted");
    }

    /**
     * Fast Infoset decoding is not accepted
     *
     */
    public static String FASTINFOSET_DECODING_NOT_ACCEPTED() {
        return localizer.localize(localizableFASTINFOSET_DECODING_NOT_ACCEPTED());
    }

    public static Localizable localizableSTAX_CANT_CREATE() {
        return messageFactory.getMessage("stax.cantCreate");
    }

    /**
     * Unable to create StAX reader or writer
     *
     */
    public static String STAX_CANT_CREATE() {
        return localizer.localize(localizableSTAX_CANT_CREATE());
    }

    public static Localizable localizableSTREAMING_IO_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("streaming.ioException", arg0);
    }

    /**
     * XML parsing error: {0}
     *
     */
    public static String STREAMING_IO_EXCEPTION(Object arg0) {
        return localizer.localize(localizableSTREAMING_IO_EXCEPTION(arg0));
    }

    public static Localizable localizableSOURCEREADER_INVALID_SOURCE(Object arg0) {
        return messageFactory.getMessage("sourcereader.invalidSource", arg0);
    }

    /**
     * Unable to create reader from source "{0}"
     *
     */
    public static String SOURCEREADER_INVALID_SOURCE(Object arg0) {
        return localizer.localize(localizableSOURCEREADER_INVALID_SOURCE(arg0));
    }

    public static Localizable localizableXMLREADER_UNEXPECTED_STATE(Object arg0, Object arg1) {
        return messageFactory.getMessage("xmlreader.unexpectedState", arg0, arg1);
    }

    /**
     * unexpected XML reader state. expected: {0} but found: {1}
     *
     */
    public static String XMLREADER_UNEXPECTED_STATE(Object arg0, Object arg1) {
        return localizer.localize(localizableXMLREADER_UNEXPECTED_STATE(arg0, arg1));
    }

    public static Localizable localizableWOODSTOX_CANT_LOAD(Object arg0) {
        return messageFactory.getMessage("woodstox.cant.load", arg0);
    }

    /**
     * Unable to load Woodstox class {0}
     *
     */
    public static String WOODSTOX_CANT_LOAD(Object arg0) {
        return localizer.localize(localizableWOODSTOX_CANT_LOAD(arg0));
    }

    public static Localizable localizableXMLREADER_IO_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("xmlreader.ioException", arg0);
    }

    /**
     * XML reader error: {0}
     *
     */
    public static String XMLREADER_IO_EXCEPTION(Object arg0) {
        return localizer.localize(localizableXMLREADER_IO_EXCEPTION(arg0));
    }

    public static Localizable localizableFASTINFOSET_NO_IMPLEMENTATION() {
        return messageFactory.getMessage("fastinfoset.noImplementation");
    }

    /**
     * Unable to locate compatible implementation of Fast Infoset in classpath
     *
     */
    public static String FASTINFOSET_NO_IMPLEMENTATION() {
        return localizer.localize(localizableFASTINFOSET_NO_IMPLEMENTATION());
    }

    public static Localizable localizableINVALID_PROPERTY_VALUE_INTEGER(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("invalid.property.value.integer", arg0, arg1, arg2);
    }

    /**
     * Ignoring system property "{0}" as value "{1}" is invalid, property value must be a valid integer. Using default value "{2}".
     *
     */
    public static String INVALID_PROPERTY_VALUE_INTEGER(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableINVALID_PROPERTY_VALUE_INTEGER(arg0, arg1, arg2));
    }

    public static Localizable localizableXMLWRITER_IO_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("xmlwriter.ioException", arg0);
    }

    /**
     * XML writer error: {0}
     *
     */
    public static String XMLWRITER_IO_EXCEPTION(Object arg0) {
        return localizer.localize(localizableXMLWRITER_IO_EXCEPTION(arg0));
    }

    public static Localizable localizableXMLREADER_UNEXPECTED_CHARACTER_CONTENT(Object arg0) {
        return messageFactory.getMessage("xmlreader.unexpectedCharacterContent", arg0);
    }

    /**
     * XML reader error: unexpected character content: "{0}"
     *
     */
    public static String XMLREADER_UNEXPECTED_CHARACTER_CONTENT(Object arg0) {
        return localizer.localize(localizableXMLREADER_UNEXPECTED_CHARACTER_CONTENT(arg0));
    }

    public static Localizable localizableSTREAMING_PARSE_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("streaming.parseException", arg0);
    }

    /**
     * XML parsing error: {0}
     *
     */
    public static String STREAMING_PARSE_EXCEPTION(Object arg0) {
        return localizer.localize(localizableSTREAMING_PARSE_EXCEPTION(arg0));
    }

    public static Localizable localizableXMLWRITER_NO_PREFIX_FOR_URI(Object arg0) {
        return messageFactory.getMessage("xmlwriter.noPrefixForURI", arg0);
    }

    /**
     * XML writer error: no prefix for URI: "{0}"
     *
     */
    public static String XMLWRITER_NO_PREFIX_FOR_URI(Object arg0) {
        return localizer.localize(localizableXMLWRITER_NO_PREFIX_FOR_URI(arg0));
    }

    public static Localizable localizableXMLREADER_NESTED_ERROR(Object arg0) {
        return messageFactory.getMessage("xmlreader.nestedError", arg0);
    }

    /**
     * XML reader error: {0}
     *
     */
    public static String XMLREADER_NESTED_ERROR(Object arg0) {
        return localizer.localize(localizableXMLREADER_NESTED_ERROR(arg0));
    }

    public static Localizable localizableINVALID_PROPERTY_VALUE_LONG(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("invalid.property.value.long", arg0, arg1, arg2);
    }

    /**
     * Ignoring system property "{0}" as value "{1}" is invalid, property value must be a valid long. Using default value "{2}".
     *
     */
    public static String INVALID_PROPERTY_VALUE_LONG(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableINVALID_PROPERTY_VALUE_LONG(arg0, arg1, arg2));
    }

    public static Localizable localizableSTAXREADER_XMLSTREAMEXCEPTION(Object arg0) {
        return messageFactory.getMessage("staxreader.xmlstreamexception", arg0);
    }

    /**
     * XML stream reader exception: {0}
     *
     */
    public static String STAXREADER_XMLSTREAMEXCEPTION(Object arg0) {
        return localizer.localize(localizableSTAXREADER_XMLSTREAMEXCEPTION(arg0));
    }

    public static Localizable localizableXMLWRITER_NESTED_ERROR(Object arg0) {
        return messageFactory.getMessage("xmlwriter.nestedError", arg0);
    }

    /**
     * XML writer error: {0}
     *
     */
    public static String XMLWRITER_NESTED_ERROR(Object arg0) {
        return localizer.localize(localizableXMLWRITER_NESTED_ERROR(arg0));
    }

    public static Localizable localizableXMLREADER_ILLEGAL_STATE_ENCOUNTERED(Object arg0) {
        return messageFactory.getMessage("xmlreader.illegalStateEncountered", arg0);
    }

    /**
     * XML reader internal error: illegal state ({0})
     *
     */
    public static String XMLREADER_ILLEGAL_STATE_ENCOUNTERED(Object arg0) {
        return localizer.localize(localizableXMLREADER_ILLEGAL_STATE_ENCOUNTERED(arg0));
    }

    public static Localizable localizableXMLREADER_UNEXPECTED_STATE_TAG(Object arg0, Object arg1) {
        return messageFactory.getMessage("xmlreader.unexpectedState.tag", arg0, arg1);
    }

    /**
     * unexpected XML tag. expected: {0} but found: {1}
     *
     */
    public static String XMLREADER_UNEXPECTED_STATE_TAG(Object arg0, Object arg1) {
        return localizer.localize(localizableXMLREADER_UNEXPECTED_STATE_TAG(arg0, arg1));
    }

    public static Localizable localizableXMLREADER_UNEXPECTED_STATE_MESSAGE(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("xmlreader.unexpectedState.message", arg0, arg1, arg2);
    }

    /**
     * unexpected XML reader state. expected: {0} but found: {1}. {2}
     *
     */
    public static String XMLREADER_UNEXPECTED_STATE_MESSAGE(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableXMLREADER_UNEXPECTED_STATE_MESSAGE(arg0, arg1, arg2));
    }

    public static Localizable localizableXMLREADER_PARSE_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("xmlreader.parseException", arg0);
    }

    /**
     * XML parsing error: {0}
     *
     */
    public static String XMLREADER_PARSE_EXCEPTION(Object arg0) {
        return localizer.localize(localizableXMLREADER_PARSE_EXCEPTION(arg0));
    }

    public static Localizable localizableXMLRECORDER_RECORDING_ENDED() {
        return messageFactory.getMessage("xmlrecorder.recording.ended");
    }

    /**
     * no more recorded elements available
     *
     */
    public static String XMLRECORDER_RECORDING_ENDED() {
        return localizer.localize(localizableXMLRECORDER_RECORDING_ENDED());
    }

}
