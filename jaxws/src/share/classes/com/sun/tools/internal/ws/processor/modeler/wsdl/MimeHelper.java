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
package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.tools.internal.ws.processor.modeler.JavaSimpleTypeCreator;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Vivek Pandey
 *
 */
public class MimeHelper {
    /**
     * @param mimePart
     * @return unique attachment ID
     */
    protected static String getAttachmentUniqueID(String mimePart) {
        //return "uuid@" + mimePart;
        return mimePart;
    }

    /**
     * @param mimeType
     * @return true if mimeType is a binary type
     */
    protected static boolean isMimeTypeBinary(String mimeType) {
        if (mimeType.equals(JPEG_IMAGE_MIME_TYPE)
            || mimeType.equals(GIF_IMAGE_MIME_TYPE)
        ) {
            return true;
        } else if (
            mimeType.equals(TEXT_XML_MIME_TYPE)
                || mimeType.equals(TEXT_HTML_MIME_TYPE)
                || mimeType.equals(TEXT_PLAIN_MIME_TYPE)
                || mimeType.equals(APPLICATION_XML_MIME_TYPE)
                || mimeType.equals(MULTIPART_MIME_TYPE)) {
            return false;
        }
        //some unknown mime type, will be mapped to DataHandler java type so
        // return true
        return true;
    }

    protected static void initMimeTypeToJavaType() {
        mimeTypeToJavaType.put(JPEG_IMAGE_MIME_TYPE, javaType.IMAGE_JAVATYPE);
        //mimeTypeToJavaType.put(PNG_IMAGE_MIME_TYPE, javaType.IMAGE_JAVATYPE);
        mimeTypeToJavaType.put(GIF_IMAGE_MIME_TYPE,
         javaType.IMAGE_JAVATYPE);
        mimeTypeToJavaType.put(TEXT_XML_MIME_TYPE, javaType.SOURCE_JAVATYPE);
        //mimeTypeToJavaType.put(TEXT_HTML_MIME_TYPE, javaType.SOURCE_JAVATYPE);
        mimeTypeToJavaType.put(
            APPLICATION_XML_MIME_TYPE,
            javaType.SOURCE_JAVATYPE);
        mimeTypeToJavaType.put(TEXT_PLAIN_MIME_TYPE, javaType.STRING_JAVATYPE);
        mimeTypeToJavaType.put(
            MULTIPART_MIME_TYPE,
            javaType.MIME_MULTIPART_JAVATYPE);

    }

    protected static Map mimeTypeToJavaType;
    protected static JavaSimpleTypeCreator javaType;

    public static final String JPEG_IMAGE_MIME_TYPE = "image/jpeg";
    //public static final String PNG_IMAGE_MIME_TYPE = "image/png";
    public static final String GIF_IMAGE_MIME_TYPE = "image/gif";
    public static final String TEXT_XML_MIME_TYPE = "text/xml";
    public static final String TEXT_HTML_MIME_TYPE = "text/html";
    public static final String TEXT_PLAIN_MIME_TYPE = "text/plain";
    public static final String APPLICATION_XML_MIME_TYPE = "application/xml";
    public static final String MULTIPART_MIME_TYPE = "multipart/*";

    /**
     *
     */
    public MimeHelper() {
        mimeTypeToJavaType = new HashMap();
        javaType = new JavaSimpleTypeCreator();
        initMimeTypeToJavaType();
    }

}
