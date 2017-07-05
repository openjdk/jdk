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

import javax.xml.stream.events.Characters;
import java.io.Writer;
import javax.xml.stream.events.XMLEvent;
import com.sun.org.apache.xerces.internal.util.XMLChar;

/** Implementation of Character event.
 *
 *@author Neeraj Bajaj, Sun Microsystems
 *@author K.Venugopal, Sun Microsystems
 *
 */

public class CharacterEvent extends DummyEvent
implements Characters {
    /* data */
    private String fData;
    /*true if fData is CData */
    private boolean fIsCData;
    /* true if fData is ignorableWhitespace*/
    private boolean fIsIgnorableWhitespace;
    /* true if fData contet is whitespace*/
    private boolean fIsSpace = false;
    /*used to prevent scanning of  data multiple times */
    private boolean fCheckIfSpaceNeeded = true;

    public CharacterEvent() {
        fIsCData = false;
        init();
    }

    /**
     *
     * @param data Character Data.
     */
    public CharacterEvent(String data) {
        fIsCData = false;
        init();
        fData = data;
    }

    /**
     *
     * @param data Character Data.
     * @param flag true if CData
     */
    public CharacterEvent(String data, boolean flag) {
        init();
        fData = data;
        fIsCData = flag;
    }

    /**
     *
     * @param data Character Data.
     * @param flag true if CData
     * @param isIgnorableWhiteSpace true if data is ignorable whitespace.
     */
    public CharacterEvent(String data, boolean flag, boolean isIgnorableWhiteSpace) {
        init();
        fData = data;
        fIsCData = flag;
        fIsIgnorableWhitespace = isIgnorableWhiteSpace ;
    }

    protected void init() {
        setEventType(XMLEvent.CHARACTERS);
    }

    /**
     *
     * @return return data.
     */
    public String getData() {
        return fData;
    }

    /**
     *
     * @param String data
     */
    public void setData(String data){
        fData = data;
        fCheckIfSpaceNeeded = true;
    }

    /**
     *
     * @return boolean returns true if the data is CData
     */
    public boolean isCData() {
        return fIsCData;
    }

    /**
     *
     * @return String return the String representation of this event.
     */
    public String toString() {
        if(fIsCData)
            return "<![CDATA[" + getData() + "]]>";
        else
            return fData;
    }

    /** This method will write the XMLEvent as per the XML 1.0 specification as Unicode characters.
     * No indentation or whitespace should be outputted.
     *
     * Any user defined event type SHALL have this method
     * called when being written to on an output stream.
     * Built in Event types MUST implement this method,
     * but implementations MAY choose not call these methods
     * for optimizations reasons when writing out built in
     * Events to an output stream.
     * The output generated MUST be equivalent in terms of the
     * infoset expressed.
     *
     * @param writer The writer that will output the data
     * @throws XMLStreamException if there is a fatal error writing the event
     */
    public void writeAsEncodedUnicode(Writer writer) throws javax.xml.stream.XMLStreamException {
    }

    /**
     * Return true if this is ignorableWhiteSpace.  If
     * this event is ignorableWhiteSpace its event type will
     * be SPACE.
     * @return
     */
    public boolean isIgnorableWhiteSpace() {
        return fIsIgnorableWhitespace;
    }

    /**
     * Returns true if this set of Characters
     * is all whitespace.  Whitspace inside a document
     * is reported as CHARACTERS.  This method allows
     * checking of CHARACTERS events to see if they
     * are composed of only whitespace characters
     * @return
     */
    public boolean isWhiteSpace() {
        //no synchronization checks made.
        if(fCheckIfSpaceNeeded){
            checkWhiteSpace();
            fCheckIfSpaceNeeded = false;
        }
        return fIsSpace;
    }

    private void checkWhiteSpace(){
        //for now - remove dependancy of XMLChar
        if(fData != null && fData.length() >0 ){
            fIsSpace = true;
            for(int i=0;i<fData.length();i++){
                if(!XMLChar.isSpace(fData.charAt(i))){
                    fIsSpace = false;
                    break;
                }
            }
        }
    }
}
