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

import javax.xml.stream.events.EntityDeclaration;
import javax.xml.stream.events.EntityReference;


public class EntityReferenceEvent extends EventBase implements EntityReference {
    private EntityDeclaration _entityDeclaration ;
    private String _entityName;

    public EntityReferenceEvent() {
        init();
    }

    public EntityReferenceEvent(String entityName , EntityDeclaration entityDeclaration) {
        init();
        _entityName = entityName;
        _entityDeclaration = entityDeclaration;
    }

  /**
   * The name of the entity
   * @return the entity's name, may not be null
   */
    public String getName() {
        return _entityName;
    }

  /**
   * Return the declaration of this entity.
   */
    public EntityDeclaration getDeclaration(){
        return _entityDeclaration ;
    }

    public void setName(String name){
        _entityName = name;
    }

    public void setDeclaration(EntityDeclaration declaration) {
        _entityDeclaration = declaration ;
    }

    public String toString() {
        String text = _entityDeclaration.getReplacementText();
        if(text == null)
            text = "";
        return "&" + getName() + ";='" + text + "'";
    }

    protected void init() {
        setEventType(ENTITY_REFERENCE);
    }


}
