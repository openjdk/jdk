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
 */

package com.sun.xml.internal.ws.api;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.binding.SOAPBindingImpl;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;
import com.sun.xml.internal.ws.encoding.SOAPBindingCodec;
import com.sun.xml.internal.ws.encoding.XMLHTTPBindingCodec;
import com.sun.xml.internal.ws.util.ServiceFinder;

import javax.xml.ws.BindingType;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.soap.MTOMFeature;
import javax.xml.ws.soap.SOAPBinding;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed binding ID string.
 *
 * <p>
 * {@link BindingID} is an immutable object that represents a binding ID,
 * much like how {@link URL} is a representation of an URL.
 * Like {@link URL}, this class offers a bunch of methods that let you
 * query various traits/properties of a binding ID.
 *
 * <p>
 * {@link BindingID} is extensible; one can plug in a parser from
 * {@link String} to {@link BindingID} to interpret binding IDs that
 * the JAX-WS RI does no a-priori knowledge of.
 * Technologies such as Tango uses this to make the JAX-WS RI understand
 * binding IDs defined in their world.
 *
 * Such technologies are free to extend this class and expose more characterstics.
 *
 * <p>
 * Even though this class defines a few well known constants, {@link BindingID}
 * instances do not necessarily have singleton semantics. Use {@link #equals(Object)}
 * for the comparison.
 *
 * <h3>{@link BindingID} and {@link WSBinding}</h3>
 * <p>
 * {@link WSBinding} is mutable and represents a particular "use" of a {@link BindingID}.
 * As such, it has state like a list of {@link Handler}s, which are inherently local
 * to a particular usage. For example, if you have two proxies, you need two instances.
 *
 * {@link BindingID}, OTOH, is immutable and thus the single instance
 * that represents "SOAP1.2/HTTP" can be shared and reused by all proxies in the same VM.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BindingID {

    /**
     * Creates an instance of {@link WSBinding} (which is conceptually an "use"
     * of {@link BindingID}) from a {@link BindingID}.
     *
     * @return
     *      Always a new instance.
     */
    public final @NotNull WSBinding createBinding() {
        return BindingImpl.create(this);
    }

    public final @NotNull WSBinding createBinding(WebServiceFeature... features) {
        return BindingImpl.create(this, features);
    }

    public final @NotNull WSBinding createBinding(WSFeatureList features) {
        return createBinding(features.toArray());
    }

    /**
     * Gets the SOAP version of this binding.
     *
     * TODO: clarify what to do with XML/HTTP binding
     *
     * @return
     *      If the binding is using SOAP, this method returns
     *      a {@link SOAPVersion} constant.
     *
     *      If the binding is not based on SOAP, this method
     *      returns null. See {@link Message} for how a non-SOAP
     *      binding shall be handled by {@link Tube}s.
     */
    public abstract SOAPVersion getSOAPVersion();

    /**
     * Creates a new {@link Codec} for this binding.
     *
     * @param binding
     *      Ocassionally some aspects of binding can be overridden by
     *      {@link WSBinding} at runtime by users, so some {@link Codec}s
     *      need to have access to {@link WSBinding} that it's working for.
     */
    public abstract @NotNull Codec createEncoder(@NotNull WSBinding binding);

    /**
     * Gets the binding ID, which uniquely identifies the binding.
     *
     * <p>
     * The relevant specs define the binding IDs and what they mean.
     * The ID is used in many places to identify the kind of binding
     * (such as SOAP1.1, SOAP1.2, REST, ...)
     *
     * @return
     *      Always non-null same value.
     */
    public abstract String toString();

    /**
     * Returna a new {@link WebServiceFeatureList} instance
     * that represents the features that are built into this binding ID.
     *
     * <p>
     * For example, {@link BindingID} for
     * <tt>"{@value SOAPBinding#SOAP11HTTP_MTOM_BINDING}"</tt>
     * would always return a list that has {@link MTOMFeature} enabled.
     */
    public WebServiceFeatureList createBuiltinFeatureList() {
        return new WebServiceFeatureList();
    }

    /**
     * Returns true if this binding can generate WSDL.
     *
     * <p>
     * For e.g.: SOAP 1.1 and "XSOAP 1.2" is supposed to return true
     * from this method. For SOAP1.2, there is no standard WSDL, so the
     * runtime is not generating one and it expects the WSDL is packaged.
     *
     */
    public boolean canGenerateWSDL() {
        return false;
    }

    /**
     * Returns a parameter of this binding ID.
     *
     * <p>
     * Some binding ID, such as those for SOAP/HTTP, uses the URL
     * query syntax (like <tt>?mtom=true</tt>) to control
     * the optional part of the binding. This method obtains
     * the value for such optional parts.
     *
     * <p>
     * For implementors of the derived classes, if your binding ID
     * does not define such optional parts (such as the XML/HTTP binding ID),
     * then you should simply return the specified default value
     * (which is what this implementation does.)
     *
     * @param parameterName
     *      The parameter name, such as "mtom" in the above example.
     * @param defaultValue
     *      If this binding ID doesn't have the specified parameter explicitly,
     *      this value will be returned.
     *
     * @return
     *      the value of the parameter, if it's present (such as "true"
     *      in the above example.) If not present, this method returns
     *      the {@code defaultValue}.
     */
    public String getParameter(String parameterName, String defaultValue) {
        return defaultValue;
    }

    /**
     * Compares the equality based on {@link #toString()}.
     */
    public boolean equals(Object obj) {
        if(!(obj instanceof BindingID))
            return false;
        return toString().equals(obj.toString());
    }

    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Parses a binding ID string into a {@link BindingID} object.
     *
     * <p>
     * This method first checks for a few known values and then delegate
     * the parsing to {@link BindingIDFactory}.
     *
     * <p>
     * If parsing succeeds this method returns a value. Otherwise
     * throws {@link WebServiceException}.
     *
     * @throws WebServiceException
     *      If the binding ID is not understood.
     */
    public static @NotNull BindingID parse(String lexical) {
        if(lexical.equals(XML_HTTP.toString()))
            return XML_HTTP;
        if(belongsTo(lexical,SOAP11_HTTP.toString()))
            return customize(lexical,SOAP11_HTTP);
        if(belongsTo(lexical,SOAP12_HTTP.toString()))
            return customize(lexical,SOAP12_HTTP);
        if(belongsTo(lexical,SOAPBindingImpl.X_SOAP12HTTP_BINDING))
            return customize(lexical,X_SOAP12_HTTP);

        // OK, it's none of the values JAX-WS understands.
        for( BindingIDFactory f : ServiceFinder.find(BindingIDFactory.class) ) {
            BindingID r = f.parse(lexical);
            if(r!=null)
                return r;
        }

        // nobody understood this value
        throw new WebServiceException("Wrong binding ID: "+lexical);
    }

    private static boolean belongsTo(String lexical, String id) {
        return lexical.equals(id) || lexical.startsWith(id+'?');
    }

    /**
     * Parses parameter portion and returns appropriately populated {@link SOAPHTTPImpl}
     */
    private static SOAPHTTPImpl customize(String lexical, SOAPHTTPImpl base) {
        if(lexical.equals(base.toString()))
            return base;

        // otherwise we must have query parameter
        // we assume the spec won't define any tricky parameters that require
        // complicated handling (such as %HH or non-ASCII char), so this parser
        // is quite simple-minded.
        SOAPHTTPImpl r = new SOAPHTTPImpl(base.getSOAPVersion(), lexical, base.canGenerateWSDL());
        try {
            // With X_SOAP12_HTTP, base != lexical and lexical does n't have any query string
            if(lexical.indexOf('?') == -1) {
                return r;
            }
            String query = URLDecoder.decode(lexical.substring(lexical.indexOf('?')+1),"UTF-8");
            for( String token : query.split("&") ) {
                int idx = token.indexOf('=');
                if(idx<0)
                    throw new WebServiceException("Malformed binding ID (no '=' in "+token+")");
                r.parameters.put(token.substring(0,idx),token.substring(idx+1));
            }
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);    // UTF-8 is supported everywhere
        }

        return r;
    }


    /**
     * Figures out the binding from {@link BindingType} annotation.
     *
     * @return
     *      default to {@link BindingID#SOAP11_HTTP}, if no such annotation is present.
     * @see #parse(String)
     */
    public static @NotNull BindingID parse(Class<?> implClass) {
        BindingType bindingType = implClass.getAnnotation(BindingType.class);
        if (bindingType != null) {
            String bindingId = bindingType.value();
            if (bindingId.length() > 0) {
                return BindingID.parse(bindingId);
            }
        }
        return SOAP11_HTTP;
    }

    /**
     * Constant that represents implementation specific SOAP1.2/HTTP which is
     * used to generate non-standard WSDLs
     */
    public static final SOAPHTTPImpl X_SOAP12_HTTP = new SOAPHTTPImpl(
        SOAPVersion.SOAP_12, SOAPBindingImpl.X_SOAP12HTTP_BINDING, true);

    /**
     * Constant that represents SOAP1.2/HTTP.
     */
    public static final SOAPHTTPImpl SOAP12_HTTP = new SOAPHTTPImpl(
        SOAPVersion.SOAP_12, SOAPBinding.SOAP12HTTP_BINDING, false);
    /**
     * Constant that represents SOAP1.1/HTTP.
     */
    public static final SOAPHTTPImpl SOAP11_HTTP = new SOAPHTTPImpl(
        SOAPVersion.SOAP_11, SOAPBinding.SOAP11HTTP_BINDING, true);

    /**
     * Constant that represents SOAP1.2/HTTP.
     */
    public static final SOAPHTTPImpl SOAP12_HTTP_MTOM = new SOAPHTTPImpl(
        SOAPVersion.SOAP_12, SOAPBinding.SOAP12HTTP_MTOM_BINDING, false, true);
    /**
     * Constant that represents SOAP1.1/HTTP.
     */
    public static final SOAPHTTPImpl SOAP11_HTTP_MTOM = new SOAPHTTPImpl(
        SOAPVersion.SOAP_11, SOAPBinding.SOAP11HTTP_MTOM_BINDING, true, true);


    /**
     * Constant that represents REST.
     */
    public static final BindingID XML_HTTP = new Impl(SOAPVersion.SOAP_11, HTTPBinding.HTTP_BINDING,false) {
        public Codec createEncoder(WSBinding binding) {
            return new XMLHTTPBindingCodec();
        }
    };

    private static abstract class Impl extends BindingID {
        final SOAPVersion version;
        private final String lexical;
        private final boolean canGenerateWSDL;

        public Impl(SOAPVersion version, String lexical, boolean canGenerateWSDL) {
            this.version = version;
            this.lexical = lexical;
            this.canGenerateWSDL = canGenerateWSDL;
        }

        public SOAPVersion getSOAPVersion() {
            return version;
        }

        public String toString() {
            return lexical;
        }

        @Deprecated
        public boolean canGenerateWSDL() {
            return canGenerateWSDL;
        }
    }

    /**
     * Internal implementation for SOAP/HTTP.
     */
    private static final class SOAPHTTPImpl extends Impl implements Cloneable {
        /*final*/ Map<String,String> parameters = new HashMap<String,String>();

        static final String MTOM_PARAM = "mtom";
        Boolean mtomSetting = null;

        public SOAPHTTPImpl(SOAPVersion version, String lexical, boolean canGenerateWSDL) {
            super(version, lexical, canGenerateWSDL);
        }

        public SOAPHTTPImpl(SOAPVersion version, String lexical, boolean canGenerateWSDL,
                           boolean mtomEnabled) {
            this(version, lexical, canGenerateWSDL);
            String mtomStr = mtomEnabled ? "true" : "false";
            parameters.put(MTOM_PARAM, mtomStr);
            mtomSetting = mtomEnabled;
        }

        public @NotNull Codec createEncoder(WSBinding binding) {
            return new SOAPBindingCodec(binding);
        }

        private Boolean isMTOMEnabled() {
            String mtom = parameters.get(MTOM_PARAM);
            return mtom==null?null:Boolean.valueOf(mtom);
        }

        public WebServiceFeatureList createBuiltinFeatureList() {
            WebServiceFeatureList r=super.createBuiltinFeatureList();
            Boolean mtom = isMTOMEnabled();
            if(mtom != null)
                r.add(new MTOMFeature(mtom));
            return r;
        }

        public String getParameter(String parameterName, String defaultValue) {
            if (parameters.get(parameterName) == null)
                return super.getParameter(parameterName, defaultValue);
            return parameters.get(parameterName);
        }
    }
}
