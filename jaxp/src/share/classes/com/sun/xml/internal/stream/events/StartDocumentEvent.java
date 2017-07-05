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

package com.sun.xml.internal.stream.events ;

import javax.xml.stream.events.StartDocument;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;

/** Implementation of StartDocumentEvent.
 *
 * @author Neeraj Bajaj Sun Microsystems,Inc.
 * @author K.Venugopal Sun Microsystems,Inc.
 *
 */

public class StartDocumentEvent extends DummyEvent
implements StartDocument {

    protected String fSystemId;
    protected String fEncodingScheam;
    protected boolean fStandalone;
    protected String fVersion;
    private boolean fEncodingSchemeSet;
    private boolean fStandaloneSet;

    public StartDocumentEvent() {
        this("UTF-8","1.0",true,null);
    }

    public StartDocumentEvent(String encoding){
        this(encoding,"1.0",true,null);
    }

    public StartDocumentEvent(String encoding, String version){
        this(encoding,version,true,null);
    }

    public StartDocumentEvent(String encoding, String version, boolean standalone){
        this(encoding,version,standalone,null);
    }

    public StartDocumentEvent(String encoding, String version, boolean standalone,Location loc){
        init();
        this.fEncodingScheam = encoding;
        this.fVersion = version;
        this.fStandalone = standalone;
        this.fEncodingSchemeSet = false;
        this.fStandaloneSet = false;
        if (loc != null) {
            this.fLocation = loc;
        }
    }
    protected void init() {
        setEventType(XMLStreamConstants.START_DOCUMENT);
    }

    public String getSystemId() {
        if(fLocation == null )
            return "";
        else
            return fLocation.getSystemId();
    }


    public String getCharacterEncodingScheme() {
        return fEncodingScheam;
    }

    public boolean isStandalone() {
        return fStandalone;
    }

    public String getVersion() {
        return fVersion;
    }

    public void setStandalone(boolean flag) {
        fStandaloneSet = true;
        fStandalone = flag;
    }

    public void setStandalone(String s) {
        fStandaloneSet = true;
        if(s == null) {
            fStandalone = true;
            return;
        }
        if(s.equals("yes"))
            fStandalone = true;
        else
            fStandalone = false;
    }

    public boolean encodingSet() {
        return fEncodingSchemeSet;
    }

    public boolean standaloneSet() {
        return fStandaloneSet;
    }

    public void setEncoding(String encoding) {
        fEncodingScheam = encoding;
    }

    void setDeclaredEncoding(boolean value){
        fEncodingSchemeSet = value;
    }

    public void setVersion(String s) {
        fVersion = s;
    }

    void clear() {
        fEncodingScheam = "UTF-8";
        fStandalone = true;
        fVersion = "1.0";
        fEncodingSchemeSet = false;
        fStandaloneSet = false;
    }

    public String toString() {
        String s = "<?xml version=\"" + fVersion + "\"";
        s = s + " encoding='" + fEncodingScheam + "'";
        if(fStandaloneSet) {
            if(fStandalone)
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
