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

package com.sun.xml.internal.fastinfoset.stax.events;

import javax.xml.stream.events.EntityDeclaration;


public class EntityDeclarationImpl extends EventBase implements EntityDeclaration {
    private String _publicId;
    private String _systemId;
    private String _baseURI;
    private String _entityName;
    private String _replacement;
    private String _notationName;

    /** Creates a new instance of EntityDeclarationImpl */
    public EntityDeclarationImpl() {
        init();
    }

    public EntityDeclarationImpl(String entityName , String replacement){
        init();
        _entityName = entityName;
        _replacement = replacement;
    }

    /**
    * The entity's public identifier, or null if none was given
    * @return the public ID for this declaration or null
    */
    public String getPublicId(){
        return _publicId;
    }

    /**
    * The entity's system identifier.
    * @return the system ID for this declaration or null
    */
    public String getSystemId(){
        return _systemId;
    }

    /**
    * The entity's name
    * @return the name, may not be null
    */
    public String getName(){
        return _entityName;
    }

    /**
    * The name of the associated notation.
    * @return the notation name
    */
    public String getNotationName() {
        return _notationName;
    }

    /**
    * The replacement text of the entity.
    * This method will only return non-null
    * if this is an internal entity.
    * @return null or the replacment text
    */
    public String getReplacementText() {
        return _replacement;
    }

    /**
    * Get the base URI for this reference
    * or null if this information is not available
    * @return the base URI or null
    */
    public String getBaseURI() {
        return _baseURI;
    }

    public void setPublicId(String publicId) {
        _publicId = publicId;
    }

    public void setSystemId(String systemId) {
        _systemId = systemId;
    }

    public void setBaseURI(String baseURI) {
        _baseURI = baseURI;
    }

    public void setName(String entityName){
        _entityName = entityName;
    }

    public void setReplacementText(String replacement){
        _replacement = replacement;
    }

    public void setNotationName(String notationName){
        _notationName = notationName;
    }

    protected void init(){
        setEventType(ENTITY_DECLARATION);
    }
}
