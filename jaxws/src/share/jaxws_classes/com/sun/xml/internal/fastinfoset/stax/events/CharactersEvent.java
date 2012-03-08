/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset.stax.events ;

import com.sun.xml.internal.fastinfoset.org.apache.xerces.util.XMLChar;
import javax.xml.stream.events.Characters;


public class CharactersEvent extends EventBase implements Characters {
    private String _text;
    private boolean isCData=false;
    private boolean isSpace=false;
    private boolean isIgnorable=false;
    private boolean needtoCheck = true;

    public CharactersEvent() {
        super(CHARACTERS);
    }
    /**
     *
     * @param data Character Data.
     */
    public CharactersEvent(String data) {
        super(CHARACTERS);
        _text = data;
    }

    /**
     *
     * @param data Character Data.
     * @param isCData true if is CData
     */
    public CharactersEvent(String data, boolean isCData) {
        super(CHARACTERS);
        _text = data;
        this.isCData = isCData;
    }

  /**
   * Get the character data of this event
   */
   public String getData() {
        return _text;
    }

    public void setData(String data){
        _text = data;
    }

    /**
     *
     * @return boolean returns true if the data is CData
     */
    public boolean isCData() {
        return isCData;
    }

    /**
     *
     * @return String return the String representation of this event.
     */
    public String toString() {
        if(isCData)
            return "<![CDATA[" + _text + "]]>";
        else
            return _text;
    }

    /**
     * Return true if this is ignorableWhiteSpace.  If
     * this event is ignorableWhiteSpace its event type will
     * be SPACE.
     * @return boolean true if this is ignorableWhiteSpace.
     */
    public boolean isIgnorableWhiteSpace() {
        return isIgnorable;
    }

    /**
     * Returns true if this set of Characters are all whitespace.  Whitspace inside a document
     * is reported as CHARACTERS.  This method allows checking of CHARACTERS events to see
     * if they are composed of only whitespace characters
     * @return boolean true if this set of Characters are all whitespace
     */
    public boolean isWhiteSpace() {
        //no synchronization checks made.
        if(needtoCheck){
            checkWhiteSpace();
            needtoCheck = false;
        }
        return isSpace;
    }

    public void setSpace(boolean isSpace) {
        this.isSpace = isSpace;
        needtoCheck = false;
    }
    public void setIgnorable(boolean isIgnorable){
        this.isIgnorable = isIgnorable;
        setEventType(SPACE);
    }
    private void checkWhiteSpace(){
        //refer to xerces XMLChar
        if(!Util.isEmptyString(_text)){
            isSpace = true;
            for(int i=0;i<_text.length();i++){
                if(!XMLChar.isSpace(_text.charAt(i))){
                    isSpace = false;
                    break;
                }
            }
        }
    }
}
