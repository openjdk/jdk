/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
 */

package com.sun.org.apache.xerces.internal.impl;

import com.sun.org.apache.xerces.internal.utils.XMLSecurityPropertyManager;
import com.sun.xml.internal.stream.StaxEntityResolverWrapper;
import java.util.HashMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLResolver;

/**
 *  This class manages different properties related to Stax specification and its implementation.
 * This class constructor also takes itself (PropertyManager object) as parameter and initializes the
 * object with the property taken from the object passed.
 *
 * @author  Neeraj Bajaj, neeraj.bajaj@sun.com
 * @author K.Venugopal@sun.com
 * @author Sunitha Reddy, sunitha.reddy@sun.com
 */

public class PropertyManager {


    public static final String STAX_NOTATIONS = "javax.xml.stream.notations";
    public static final String STAX_ENTITIES = "javax.xml.stream.entities";

    private static final String STRING_INTERNING = "http://xml.org/sax/features/string-interning";

    /** Property identifier: Security property manager. */
    private static final String XML_SECURITY_PROPERTY_MANAGER =
            Constants.XML_SECURITY_PROPERTY_MANAGER;

    HashMap supportedProps = new HashMap();

    private XMLSecurityPropertyManager fSecurityPropertyMgr;

    public static final int CONTEXT_READER = 1;
    public static final int CONTEXT_WRITER = 2;

    /** Creates a new instance of PropertyManager */
    public PropertyManager(int context) {
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

    /**
     * Initialize this object with the properties taken from passed PropertyManager object.
     */
    public PropertyManager(PropertyManager propertyManager){

        HashMap properties = propertyManager.getProperties();
        supportedProps.putAll(properties);
        fSecurityPropertyMgr = (XMLSecurityPropertyManager)getProperty(XML_SECURITY_PROPERTY_MANAGER);
    }

    private HashMap getProperties(){
        return supportedProps ;
    }


    /**
     * Important point:
     * 1. We are not exposing Xerces namespace property. Application should configure namespace through
     * Stax specific property.
     *
     */
    private void initConfigurableReaderProperties(){
        //spec default values
        supportedProps.put(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        supportedProps.put(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        supportedProps.put(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        supportedProps.put(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.TRUE);
        supportedProps.put(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        supportedProps.put(XMLInputFactory.SUPPORT_DTD, Boolean.TRUE);
        supportedProps.put(XMLInputFactory.REPORTER, null);
        supportedProps.put(XMLInputFactory.RESOLVER, null);
        supportedProps.put(XMLInputFactory.ALLOCATOR, null);
        supportedProps.put(STAX_NOTATIONS,null );

        //zephyr (implementation) specific properties which can be set by the application.
        //interning is always done
        supportedProps.put(Constants.SAX_FEATURE_PREFIX + Constants.STRING_INTERNING_FEATURE , new Boolean(true));
        //recognizing java encoding names by default
        supportedProps.put(Constants.XERCES_FEATURE_PREFIX + Constants.ALLOW_JAVA_ENCODINGS_FEATURE,  new Boolean(true)) ;
        //in stax mode, namespace declarations are not added as attributes
        supportedProps.put(Constants.ADD_NAMESPACE_DECL_AS_ATTRIBUTE ,  Boolean.FALSE) ;
        supportedProps.put(Constants.READER_IN_DEFINED_STATE, new Boolean(true));
        supportedProps.put(Constants.REUSE_INSTANCE, new Boolean(true));
        supportedProps.put(Constants.ZEPHYR_PROPERTY_PREFIX + Constants.STAX_REPORT_CDATA_EVENT , new Boolean(false));
        supportedProps.put(Constants.ZEPHYR_PROPERTY_PREFIX + Constants.IGNORE_EXTERNAL_DTD, Boolean.FALSE);
        supportedProps.put(Constants.XERCES_FEATURE_PREFIX + Constants.WARN_ON_DUPLICATE_ATTDEF_FEATURE, new Boolean(false));
        supportedProps.put(Constants.XERCES_FEATURE_PREFIX + Constants.WARN_ON_DUPLICATE_ENTITYDEF_FEATURE, new Boolean(false));
        supportedProps.put(Constants.XERCES_FEATURE_PREFIX + Constants.WARN_ON_UNDECLARED_ELEMDEF_FEATURE, new Boolean(false));

        fSecurityPropertyMgr = new XMLSecurityPropertyManager();
        supportedProps.put(XML_SECURITY_PROPERTY_MANAGER, fSecurityPropertyMgr);
    }

    private void initWriterProps(){
        supportedProps.put(XMLOutputFactory.IS_REPAIRING_NAMESPACES , Boolean.FALSE);
        //default value of escaping characters is 'true'
        supportedProps.put(Constants.ESCAPE_CHARACTERS , Boolean.TRUE);
        supportedProps.put(Constants.REUSE_INSTANCE, new Boolean(true));
    }

    /**
     * public void reset(){
     * supportedProps.clear() ;
     * }
     */
    public boolean containsProperty(String property){
        return supportedProps.containsKey(property) ||
                fSecurityPropertyMgr.getIndex(property) > -1 ;
    }

    public Object getProperty(String property){
        return supportedProps.get(property);
    }

    public void setProperty(String property, Object value){
        String equivalentProperty = null ;
        if(property == XMLInputFactory.IS_NAMESPACE_AWARE || property.equals(XMLInputFactory.IS_NAMESPACE_AWARE)){
            equivalentProperty = Constants.XERCES_FEATURE_PREFIX + Constants.NAMESPACES_FEATURE ;
        }
        else if(property == XMLInputFactory.IS_VALIDATING || property.equals(XMLInputFactory.IS_VALIDATING)){
            if( (value instanceof Boolean) && ((Boolean)value).booleanValue()){
                throw new java.lang.IllegalArgumentException("true value of isValidating not supported") ;
            }
        }
        else if(property == STRING_INTERNING || property.equals(STRING_INTERNING)){
            if( (value instanceof Boolean) && !((Boolean)value).booleanValue()){
                throw new java.lang.IllegalArgumentException("false value of " + STRING_INTERNING + "feature is not supported") ;
            }
        }
        else if(property == XMLInputFactory.RESOLVER || property.equals(XMLInputFactory.RESOLVER)){
            //add internal stax property
            supportedProps.put( Constants.XERCES_PROPERTY_PREFIX + Constants.STAX_ENTITY_RESOLVER_PROPERTY , new StaxEntityResolverWrapper((XMLResolver)value)) ;
        }

        int index = fSecurityPropertyMgr.getIndex(property);
        if (index > -1) {
            fSecurityPropertyMgr.setValue(index,
                    XMLSecurityPropertyManager.State.APIPROPERTY, (String)value);
        } else {
            supportedProps.put(property, value);
        }

        if(equivalentProperty != null){
            supportedProps.put(equivalentProperty, value ) ;
        }
    }

    public String toString(){
        return supportedProps.toString();
    }

}//PropertyManager
