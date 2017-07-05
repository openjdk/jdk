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

package com.sun.xml.internal.messaging.saaj.util;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.transform.TransformerException;

/*
 * Class that parses the very first construct in the document i.e.
 *  <?xml ... ?>
 *
 * @author Panos Kougiouris (panos@acm.org)
 * @version $Revision: 1.1.1.1 $ $Date: 2006/01/27 13:10:58 $
 */

public class XMLDeclarationParser {
    private String m_encoding;
    private PushbackReader m_pushbackReader;
    private boolean m_hasHeader; // preserve the case where no XML Header exists
    private String xmlDecl = null;
    static String gt16 = null;
    static String utf16Decl = null;

    static {
         try {
             gt16 = new String(">".getBytes("utf-16"));
             utf16Decl = new String("<?xml".getBytes("utf-16"));
         } catch (Exception e) {}
    }

    //---------------------------------------------------------------------

    public XMLDeclarationParser(PushbackReader pr)
    {
        m_pushbackReader = pr;
        m_encoding = "utf-8";
        m_hasHeader = false;
    }

    //---------------------------------------------------------------------
    public String getEncoding()
    {
        return m_encoding;
    }

    public String getXmlDeclaration() {
        return xmlDecl;
    }

    //---------------------------------------------------------------------

     public void parse()  throws TransformerException, IOException
     {
        int c = 0;
        int index = 0;
        char[] aChar = new char[65535];
        StringBuffer xmlDeclStr = new StringBuffer();
        while ((c = m_pushbackReader.read()) != -1) {
            aChar[index] = (char)c;
            xmlDeclStr.append((char)c);
            index++;
            if (c == '>') {
                break;
            }
        }
        int len = index;

        String decl = xmlDeclStr.toString();
        boolean utf16 = false;
        boolean utf8 = false;

        int xmlIndex = decl.indexOf(utf16Decl);
        if (xmlIndex > -1) {
            utf16 = true;
        } else {
            xmlIndex = decl.indexOf("<?xml");
            if (xmlIndex > -1) {
                utf8 = true;
            }
        }

        // no XML decl
        if (!utf16 && !utf8) {
            m_pushbackReader.unread(aChar, 0, len);
            return;
        }
        m_hasHeader = true;

        if (utf16) {
            xmlDecl = new String(decl.getBytes(), "utf-16");
            xmlDecl = xmlDecl.substring(xmlDecl.indexOf("<"));
        } else {
            xmlDecl = decl;
        }
        // do we want to check that there are no other characters preceeding <?xml
        if (xmlIndex != 0) {
            throw new IOException("Unexpected characters before XML declaration");
        }

        int versionIndex =  xmlDecl.indexOf("version");
        if (versionIndex == -1) {
            throw new IOException("Mandatory 'version' attribute Missing in XML declaration");
        }

        // now set
        int encodingIndex = xmlDecl.indexOf("encoding");
        if (encodingIndex == -1) {
            return;
        }

        if (versionIndex > encodingIndex) {
            throw new IOException("The 'version' attribute should preceed the 'encoding' attribute in an XML Declaration");
        }

        int stdAloneIndex = xmlDecl.indexOf("standalone");
        if ((stdAloneIndex > -1) && ((stdAloneIndex < versionIndex) || (stdAloneIndex < encodingIndex))) {
            throw new IOException("The 'standalone' attribute should be the last attribute in an XML Declaration");
        }

        int eqIndex = xmlDecl.indexOf("=", encodingIndex);
        if (eqIndex == -1) {
            throw new IOException("Missing '=' character after 'encoding' in XML declaration");
        }

        m_encoding = parseEncoding(xmlDecl, eqIndex);
        if(m_encoding.startsWith("\"")){
            m_encoding = m_encoding.substring(m_encoding.indexOf("\"")+1, m_encoding.lastIndexOf("\""));
        } else if(m_encoding.startsWith("\'")){
            m_encoding = m_encoding.substring(m_encoding.indexOf("\'")+1, m_encoding.lastIndexOf("\'"));
        }
     }

     //--------------------------------------------------------------------

    public void writeTo(Writer wr) throws IOException {
        if (!m_hasHeader) return;
        wr.write(xmlDecl.toString());
    }

    private String parseEncoding(String xmlDeclFinal, int eqIndex) throws IOException {
        java.util.StringTokenizer strTok = new java.util.StringTokenizer(
            xmlDeclFinal.substring(eqIndex + 1));
        if (strTok.hasMoreTokens()) {
            String encodingTok = strTok.nextToken();
            int indexofQ = encodingTok.indexOf("?");
            if (indexofQ > -1) {
                return encodingTok.substring(0,indexofQ);
            } else {
                return encodingTok;
            }
        } else {
            throw new IOException("Error parsing 'encoding' attribute in XML declaration");
        }
    }

}
