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

package com.sun.xml.internal.ws.util;

import java.util.UUID;
import java.util.regex.Pattern;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.File;
import java.io.IOException;

import javax.xml.namespace.QName;

/**
 * @author Vivek Pandey
 *
 * Wrapper utility class to be used from the generated code or run time.
 */
public final class JAXWSUtils {
    public static String getUUID(){
         return UUID.randomUUID().toString();
    }



    public static String getFileOrURLName(String fileOrURL) {
        try{
            try {
                return escapeSpace(new URL(fileOrURL).toExternalForm());
            } catch (MalformedURLException e) {
                return new File(fileOrURL).getCanonicalFile().toURL().toExternalForm();
            }
        } catch (Exception e) {
            // try it as an URL
            return fileOrURL;
        }
    }

    public static URL getFileOrURL(String fileOrURL) throws IOException {
        try {
            return new URL(fileOrURL);
        } catch (MalformedURLException e) {
            return new File(fileOrURL).toURL();
        }
    }
    private static String escapeSpace( String url ) {
        // URLEncoder didn't work.
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < url.length(); i++) {
            // TODO: not sure if this is the only character that needs to be escaped.
            if (url.charAt(i) == ' ')
                buf.append("%20");
            else
                buf.append(url.charAt(i));
        }
        return buf.toString();
    }

    public static String absolutize(String name) {
        // absolutize all the system IDs in the input,
        // so that we can map system IDs to DOM trees.
        try {
            URL baseURL = new File(".").getCanonicalFile().toURL();
            return new URL(baseURL, name).toExternalForm();
        } catch( IOException e ) {
            ; // ignore
        }
        return name;
    }

    /**
     * Checks if the system ID is absolute.
     */
    public static  void checkAbsoluteness(String systemId) {
        // we need to be able to handle system IDs like "urn:foo", which java.net.URL can't process,
        // but OTOH we also need to be able to process system IDs like "file://a b c/def.xsd",
        // which java.net.URI can't process. So for now, let's fail only if both of them fail.
        // eventually we need a proper URI class that works for us.
        try {
            new URL(systemId);
        } catch( MalformedURLException _ ) {
            try {
                new URI(systemId);
            } catch (URISyntaxException e ) {
                throw new IllegalArgumentException("system ID '"+systemId+"' isn't absolute",e);
            }
        }
    }

    /*
     * To match, both QNames must have the same namespace and the local
     * part of the target must match the local part of the 'pattern'
     * QName, which may contain wildcard characters.
     */
    public static boolean matchQNames(QName target, QName pattern) {
        if ((target == null) || (pattern == null))  {
            // if no service or port is in descriptor
            return false;
        }
        if (pattern.getNamespaceURI().equals(target.getNamespaceURI())) {
            String regex = pattern.getLocalPart().replaceAll("\\*",  ".*");
            return Pattern.matches(regex, target.getLocalPart());
        }
        return false;
    }

}
