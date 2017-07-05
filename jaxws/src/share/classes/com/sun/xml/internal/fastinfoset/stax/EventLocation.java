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


package com.sun.xml.internal.fastinfoset.stax;

import javax.xml.stream.Location;


public class EventLocation implements Location{
    String _systemId = null;
    String _publicId = null;
    int _column = -1;
    int _line = -1;
    int _charOffset = -1;

    EventLocation() {
    }

    //explicitly create a nil location
    public static Location getNilLocation() {
        return new EventLocation();
    }
    /**
    * Return the line number where the current event ends,
    * returns -1 if none is available.
    * @return the current line number
    */
    public int getLineNumber(){
        return _line;
    }
    /**
    * Return the column number where the current event ends,
    * returns -1 if none is available.
    * @return the current column number
    */
    public int getColumnNumber() {
        return _column;
    }

  /**
   * Return the byte or character offset into the input source this location
   * is pointing to. If the input source is a file or a byte stream then
   * this is the byte offset into that stream, but if the input source is
   * a character media then the offset is the character offset.
   * Returns -1 if there is no offset available.
   * @return the current offset
   */
    public int getCharacterOffset(){
        return _charOffset;
    }

    /**
    * Returns the public ID of the XML
    * @return the public ID, or null if not available
    */
    public String getPublicId(){
        return _publicId;
    }

  /**
   * Returns the system ID of the XML
   * @return the system ID, or null if not available
   */
    public String getSystemId(){
        return _systemId;
    }

    public void setLineNumber(int line) {
        _line = line;
    }
    public void setColumnNumber(int col) {
        _column = col;
    }
    public void setCharacterOffset(int offset) {
        _charOffset = offset;
    }
    public void setPublicId(String id) {
        _publicId = id;
    }
    public void setSystemId(String id) {
        _systemId = id;
    }

    public String toString(){
        StringBuffer sbuffer = new StringBuffer() ;
        sbuffer.append("Line number = " + _line);
        sbuffer.append("\n") ;
        sbuffer.append("Column number = " + _column);
        sbuffer.append("\n") ;
        sbuffer.append("System Id = " + _systemId);
        sbuffer.append("\n") ;
        sbuffer.append("Public Id = " + _publicId);
        sbuffer.append("\n") ;
        sbuffer.append("CharacterOffset = " + _charOffset);
        sbuffer.append("\n") ;
        return sbuffer.toString();
    }

}
