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

import java.util.HashMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class StAXManager {
    protected static final String STAX_NOTATIONS = "javax.xml.stream.notations";
    protected static final String STAX_ENTITIES = "javax.xml.stream.entities";

    HashMap features = new HashMap();

    public static final int CONTEXT_READER = 1;
    public static final int CONTEXT_WRITER = 2;


    /** Creates a new instance of StAXManager */
    public StAXManager() {
    }

    public StAXManager(int context) {
        switch(context){
            case CONTEXT_READER:{
                initConfigurableReaderProperties();
                break;
            }
            case CONTEXT_WRITER:{
                initWriterProps();
                break;
            }
        }
    }

    public StAXManager(StAXManager manager){

        HashMap properties = manager.getProperties();
        features.putAll(properties);
    }

    private HashMap getProperties(){
        return features ;
    }

    private void initConfigurableReaderProperties(){
        //spec v1.0 default values
        features.put(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        features.put(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        features.put(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        features.put(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.TRUE);
        features.put(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        features.put(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        features.put(XMLInputFactory.REPORTER, null);
        features.put(XMLInputFactory.RESOLVER, null);
        features.put(XMLInputFactory.ALLOCATOR, null);
        features.put(STAX_NOTATIONS,null );
    }

    private void initWriterProps(){
        features.put(XMLOutputFactory.IS_REPAIRING_NAMESPACES , Boolean.FALSE);
    }

    /**
     * public void reset(){
     * features.clear() ;
     * }
     */
    public boolean containsProperty(String property){
        return features.containsKey(property) ;
    }

    public Object getProperty(String name){
        checkProperty(name);
        return features.get(name);
    }

    public void setProperty(String name, Object value){
        checkProperty(name);
        if (name.equals(XMLInputFactory.IS_VALIDATING) &&
                Boolean.TRUE.equals(value)){
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.validationNotSupported") +
                    CommonResourceBundle.getInstance().getString("support_validation"));
        } else if (name.equals(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES) &&
                Boolean.TRUE.equals(value)) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.externalEntities") +
                    CommonResourceBundle.getInstance().getString("resolve_external_entities_"));
        }
        features.put(name,value);

    }

    public void checkProperty(String name) {
        if (!features.containsKey(name))
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.propertyNotSupported", new Object[]{name}));
    }

    public String toString(){
        return features.toString();
    }

}
