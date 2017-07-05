/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.client;

import javax.xml.bind.JAXBContext;
import javax.xml.ws.BindingProvider;
import static javax.xml.ws.BindingProvider.*;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class ContextMap extends HashMap<String,Object>
    implements BindingProviderProperties {

    /**
     * Read-only list of known properties.
     */
    private static final Set<String> KNOWN_PROPERTIES;

    private static final HashMap<String,Class> _allowedClass = new HashMap<String, Class>();

    protected BindingProvider _owner;
    protected PortInfoBase portInfo;

    public abstract ContextMap copy();

    static {
        //JAXWS 2.0 defined
        _allowedClass.put(USERNAME_PROPERTY, java.lang.String.class);
        _allowedClass.put(PASSWORD_PROPERTY, java.lang.String.class);
        _allowedClass.put(ENDPOINT_ADDRESS_PROPERTY, java.lang.String.class);
        _allowedClass.put(SESSION_MAINTAIN_PROPERTY, java.lang.Boolean.class);
        _allowedClass.put(SOAPACTION_USE_PROPERTY, java.lang.Boolean.class);
        _allowedClass.put(SOAPACTION_URI_PROPERTY, java.lang.String.class);

        //now defined in jaxwscontext
        _allowedClass.put(BindingProviderProperties.JAXB_CONTEXT_PROPERTY, JAXBContext.class);

        Set<String> temp = new HashSet<String>();
        //JAXWS 2.0 defined
        temp.add(USERNAME_PROPERTY);
        temp.add(PASSWORD_PROPERTY);
        temp.add(ENDPOINT_ADDRESS_PROPERTY);
        temp.add(SESSION_MAINTAIN_PROPERTY);
        temp.add(SOAPACTION_USE_PROPERTY);
        temp.add(SOAPACTION_URI_PROPERTY);

        temp.add(BindingProviderProperties.JAXB_CONTEXT_PROPERTY);
        //implementation specific
        temp.add(BindingProviderProperties.ACCEPT_ENCODING_PROPERTY);
        temp.add(BindingProviderProperties.CLIENT_TRANSPORT_FACTORY);
        //used to get stub in runtime for handler chain
        temp.add(BindingProviderProperties.JAXWS_CLIENT_HANDLE_PROPERTY);
        temp.add(BindingProviderProperties.JAXWS_CLIENT_HANDLE_PROPERTY);

        //JAXRPC 1.0 - 1.1 DEFINED - implementation specific
        temp.add(BindingProviderProperties.HTTP_COOKIE_JAR);
        temp.add(BindingProviderProperties.ONE_WAY_OPERATION);
        temp.add(BindingProviderProperties.HTTP_STATUS_CODE);
        temp.add(BindingProviderProperties.HOSTNAME_VERIFICATION_PROPERTY);
        temp.add(BindingProviderProperties.REDIRECT_REQUEST_PROPERTY);
        temp.add(BindingProviderProperties.SECURITY_CONTEXT);
        temp.add(BindingProviderProperties.SET_ATTACHMENT_PROPERTY);
        temp.add(BindingProviderProperties.GET_ATTACHMENT_PROPERTY);
        //Tod:check with mark regarding property modification
        //KNOWN_PROPERTIES = Collections.unmodifiableSet(temp);

        temp.add(MessageContext.INBOUND_MESSAGE_ATTACHMENTS);
        temp.add(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS);
        temp.add(MessageContext.WSDL_DESCRIPTION);
        temp.add(MessageContext.WSDL_INTERFACE);
        temp.add(MessageContext.WSDL_OPERATION);
        temp.add(MessageContext.WSDL_PORT);
        temp.add(MessageContext.WSDL_SERVICE);
        temp.add(MessageContext.HTTP_REQUEST_METHOD);
        temp.add(MessageContext.HTTP_REQUEST_HEADERS);
        temp.add(MessageContext.HTTP_RESPONSE_CODE);
        temp.add(MessageContext.HTTP_RESPONSE_HEADERS);
        temp.add(MessageContext.PATH_INFO);
        temp.add(MessageContext.QUERY_STRING);
        // Content negotiation property for FI -- "none", "pessimistic", "optimistic"
        temp.add(BindingProviderProperties.CONTENT_NEGOTIATION_PROPERTY);
        temp.add(BindingProviderProperties.MTOM_THRESHOLOD_VALUE);
        KNOWN_PROPERTIES = temp;
    }

    //used for dispatch
    public ContextMap(PortInfoBase info, BindingProvider provider) {
        _owner = provider;
        if (info != null) {
            this.portInfo = info;
        }
    }

    /**
     * Copy constructor.
     */
    public ContextMap(ContextMap original) {
        super(original);
        this._owner = original._owner;
    }

    //may not need this
    public ContextMap(BindingProvider owner) {
        this(null, owner);
    }

    boolean doValidation() {
        return _owner != null;
    }

    public Object put(String name, Object value) {
        if (doValidation()) {
            validateProperty(name, value, true);
            return super.put(name, value);
        }
        return null;
    }

    public Object get(String name) {
        if (doValidation()) {
            validateProperty(name, null, false);
            return super.get(name);
        }
        return null;
    }

    public Iterator<String> getPropertyNames() {
        return keySet().iterator();
    }


    public Object remove(String name) {
        if (doValidation()) {
            validateProperty(name, null, false);
            return super.remove(name);
        }
        return null;
    }

    private boolean isAllowedValue(String name, Object value) {
        if ( name.equals(MessageContext.PATH_INFO) ||
            name.equals(MessageContext.QUERY_STRING))
                return true;

        if (value == null)
            return false;

        return true;
    }
// no value check needed today
//        Object[] values = _allowedValues.get(name);
//        if (values != null) {
//            boolean allowed = false;
//            for (Object o : values) {
//                if (STRING_CLASS.isInstance(o) && (STRING_CLASS.isInstance(value))) {
//                    if (((java.lang.String) o).equalsIgnoreCase((java.lang.String) value)) {
//                        allowed = true;
//                        break;
//                    }
//                } else if (BOOLEAN_CLASS.isInstance(o) && (BOOLEAN_CLASS.isInstance(value))) {
//                    if (Boolean.FALSE.equals(o) || Boolean.TRUE.equals(o)) {
//                        allowed = true;
//                        break;
//                    }
//                } else {
//                    //log this
//                }
//            }
//            return allowed;
//        }



    private boolean isAllowedClass(String propName, Object value) {

        Class allowedClass = _allowedClass.get(propName);
        if (allowedClass != null) {
            return allowedClass.isInstance(value);
        }
        return true;
    }

    private void validateProperty(String name, Object value, boolean isSetter) {
        if (name == null)
            throw new WebServiceException(name + " is a User-defined property - property name can not be null. ",
                new IllegalArgumentException("Name of property is null.  This is an invalid property name. "));

        /* Spec clarifies that this check is not needed.
        if (!KNOWN_PROPERTIES.contains(name)) {
            //do validation check on not "javax.xml.ws."
            if (name.startsWith("javax.xml.ws"))
                throw new WebServiceException(name + " is a User-defined property - can not start with javax.xml.ws. package",
                    new IllegalArgumentException("can not start with javax.xml.ws. package"));                                            //let's check the propertyContext
        }
        */
        //is it alreadySet
        //Object currentPropValue = get(name);
        //if (currentPropValue != null) {
        //  if (!isDynamic(name))
        //      throw new WebServiceException("Property bound to Binding Instance",
        //          new IllegalArgumentException("Cannot overwrite the Static Property"));
        //}

        if (isSetter) {
            if (!isAllowedClass(name, value))
                throw new WebServiceException(value + " is Not Allowed Class for property " + name,
                    new IllegalArgumentException("Not Allowed Class for property"));

            if (!isAllowedValue(name, value))
                throw new WebServiceException(value + " is Not Allowed Value for property " + name,
                    new IllegalArgumentException("Not Allowed value"));
        }

    }

 //currently not used
 /*   public static enum StyleAndUse {
        RPC_LITERAL,
        DOCLIT_WRAPPER_STYLE, DOCLIT_NONWRAPPER_STYLE
    }
*/
}
