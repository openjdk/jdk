/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * $Id: Utils.java,v 1.14 2005/09/23 19:49:20 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.*;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Miscellaneous static utility methods for use in JSR 105 RI.
 *
 * @author Sean Mullan
 */
public final class Utils {

    private Utils() {}

    public static byte[] readBytesFromStream(InputStream is)
        throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (true) {
            int read = is.read(buf);
            if (read == -1) { // EOF
                break;
            }
            baos.write(buf, 0, read);
            if (read < 1024) {
                break;
            }
        }
        return baos.toByteArray();
    }

    /**
     * Converts an Iterator to a Set of Nodes, according to the XPath
     * Data Model.
     *
     * @param i the Iterator
     * @return the Set of Nodes
     */
    static Set toNodeSet(Iterator i) {
        Set nodeSet = new HashSet();
        while (i.hasNext()) {
            Node n = (Node) i.next();
            nodeSet.add(n);
            // insert attributes nodes to comply with XPath
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap nnm = n.getAttributes();
                for (int j = 0, length = nnm.getLength(); j < length; j++) {
                    nodeSet.add(nnm.item(j));
                }
            }
        }
        return nodeSet;
    }

    /**
     * Returns the ID from a same-document URI (ex: "#id")
     */
    public static String parseIdFromSameDocumentURI(String uri) {
        if (uri.length() == 0) {
            return null;
        }
        String id = uri.substring(1);
        if (id != null && id.startsWith("xpointer(id(")) {
            int i1 = id.indexOf('\'');
            int i2 = id.indexOf('\'', i1+1);
            id = id.substring(i1+1, i2);
        }
        return id;
    }

    /**
     * Returns true if uri is a same-document URI, false otherwise.
     */
    public static boolean sameDocumentURI(String uri) {
        return (uri != null && (uri.length() == 0 || uri.charAt(0) == '#'));
    }
}
