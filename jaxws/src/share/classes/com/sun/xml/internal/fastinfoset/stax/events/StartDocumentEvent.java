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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */


package com.sun.xml.internal.fastinfoset.stax.events ;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.StartDocument;

import com.sun.xml.internal.fastinfoset.stax.events.XMLConstants;

public class StartDocumentEvent extends EventBase implements StartDocument {

    protected String _systemId;
    protected String _encoding = XMLConstants.ENCODING; //default
    protected boolean _standalone = true;
    protected String _version = XMLConstants.XMLVERSION;
    private boolean _encodingSet = false;
    private boolean _standaloneSet = false;

    public void reset() {
        _encoding = XMLConstants.ENCODING;
        _standalone = true;
        _version = XMLConstants.XMLVERSION;
        _encodingSet = false;
        _standaloneSet=false;
    }
    public StartDocumentEvent() {
        this(null ,null);
    }

    public StartDocumentEvent(String encoding){
        this(encoding, null);
    }

    public StartDocumentEvent(String encoding, String version){
        if (encoding != null) {
            _encoding = encoding;
            _encodingSet = true;
        }
        if (version != null)
            _version = version;
        setEventType(XMLStreamConstants.START_DOCUMENT);
    }


    // ------------------- methods defined in StartDocument -------------------------
    /**
    * Returns the system ID of the XML data
    * @return the system ID, defaults to ""
    */
    public String getSystemId() {
        return super.getSystemId();
    }

    /**
    * Returns the encoding style of the XML data
    * @return the character encoding, defaults to "UTF-8"
    */
    public String getCharacterEncodingScheme() {
        return _encoding;
    }
    /**
    * Returns true if CharacterEncodingScheme was set in
    * the encoding declaration of the document
    */
    public boolean encodingSet() {
        return _encodingSet;
    }


  /**
   * Returns if this XML is standalone
   * @return the standalone state of XML, defaults to "no"
   */
    public boolean isStandalone() {
        return _standalone;
    }
    /**
    * Returns true if the standalone attribute was set in
    * the encoding declaration of the document.
    */
    public boolean standaloneSet() {
        return _standaloneSet;
    }

  /**
   * Returns the version of XML of this XML stream
   * @return the version of XML, defaults to "1.0"
   */
    public String getVersion() {
        return _version;
    }
    // ------------------- end of methods defined in StartDocument -------------------------

    public void setStandalone(boolean standalone) {
        _standaloneSet = true;
        _standalone = standalone;
    }

    public void setStandalone(String s) {
        _standaloneSet = true;
        if(s == null) {
            _standalone = true;
            return;
        }
        if(s.equals("yes"))
            _standalone = true;
        else
            _standalone = false;
    }


    public void setEncoding(String encoding) {
        _encoding = encoding;
        _encodingSet = true;
    }

    void setDeclaredEncoding(boolean value){
        _encodingSet = value;
    }

    public void setVersion(String s) {
        _version = s;
    }

    void clear() {
        _encoding = "UTF-8";
        _standalone = true;
        _version = "1.0";
        _encodingSet = false;
        _standaloneSet = false;
    }

    public String toString() {
        String s = "<?xml version=\"" + _version + "\"";
        s = s + " encoding='" + _encoding + "'";
        if(_standaloneSet) {
            if(_standalone)
                s = s + " standalone='yes'?>";
            else
                s = s + " standalone='no'?>";
        } else {
            s = s + "?>";
        }
        return s;
    }

    public boolean isStartDocument() {
        return true;
    }
}
