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

package com.sun.xml.internal.stream.events;

import javax.xml.stream.events.NotationDeclaration;
import javax.xml.stream.events.XMLEvent;
import com.sun.xml.internal.stream.dtd.nonvalidating.XMLNotationDecl;

/**
 * Implementation of NotationDeclaration event.
 *
 * @author k.venugopal@sun.com
 */
public class NotationDeclarationImpl extends DummyEvent implements NotationDeclaration {

    String fName = null;
    String fPublicId = null;
    String fSystemId = null;

    /** Creates a new instance of NotationDeclarationImpl */
    public NotationDeclarationImpl() {
        setEventType(XMLEvent.NOTATION_DECLARATION);
    }

    public NotationDeclarationImpl(String name,String publicId,String systemId){
        this.fName = name;
        this.fPublicId = publicId;
        this.fSystemId = systemId;
        setEventType(XMLEvent.NOTATION_DECLARATION);
    }

    public NotationDeclarationImpl(XMLNotationDecl notation){
        this.fName = notation.name;
        this.fPublicId = notation.publicId;
        this.fSystemId = notation.systemId;
        setEventType(XMLEvent.NOTATION_DECLARATION);
    }

    public String getName() {
        return fName;
    }

    public String getPublicId() {
        return fPublicId;
    }

    public String getSystemId() {
        return fSystemId;
    }

    void setPublicId(String publicId){
        this.fPublicId = publicId;
    }

    void setSystemId(String systemId){
        this.fSystemId = systemId;
    }

    void setName(String name){
        this.fName = name;
    }
}
