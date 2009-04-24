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


package com.sun.xml.internal.ws.binding;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.FeatureConstructor;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLFeaturedObject;
import com.sun.xml.internal.ws.model.RuntimeModelerException;
import com.sun.xml.internal.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.internal.ws.resources.ModelerMessages;

import javax.xml.ws.RespectBinding;
import javax.xml.ws.RespectBindingFeature;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.soap.Addressing;
import javax.xml.ws.soap.AddressingFeature;
import javax.xml.ws.soap.MTOM;
import javax.xml.ws.soap.MTOMFeature;
import javax.xml.ws.spi.WebServiceFeatureAnnotation;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.Logger;

/**
 * Represents a list of {@link WebServiceFeature}s that has bunch of utility methods
 * pertaining to web service features.
 *
 * @author Rama Pulavarthi
 */
public final class WebServiceFeatureList implements WSFeatureList {
    private Map<Class<? extends WebServiceFeature>, WebServiceFeature> wsfeatures =
            new HashMap<Class<? extends WebServiceFeature>, WebServiceFeature>();
    public WebServiceFeatureList() {
    }

    /**
     * Delegate to this parent if non-null.
     */
    private
    @Nullable
    WSDLFeaturedObject parent;

    public WebServiceFeatureList(@NotNull WebServiceFeature... features) {
        if (features != null)
            for (WebServiceFeature f : features) {
                wsfeatures.put(f.getClass(), f);
            }
    }

    /**
     * Creates a list by reading featuers from the annotation on a class.
     */
    public WebServiceFeatureList(@NotNull Class<?> endpointClass) {
        parseAnnotations(endpointClass);
    }

    /**
     * Reads {@link WebServiceFeatureAnnotation feature annotations} on a class
     * and adds them to the list.
     */
    public void parseAnnotations(Class<?> endpointClass) {
        for (Annotation a : endpointClass.getAnnotations()) {
            // TODO: this really needs generalization
            WebServiceFeature ftr;
            if (!(a.annotationType().isAnnotationPresent(WebServiceFeatureAnnotation.class))) {
                continue;
            } else if (a instanceof Addressing) {
                Addressing addAnn = (Addressing) a;
                ftr = new AddressingFeature(addAnn.enabled(), addAnn.required());
            } else if (a instanceof MTOM) {
                MTOM mtomAnn = (MTOM) a;
                ftr = new MTOMFeature(mtomAnn.enabled(), mtomAnn.threshold());

                // check conflict with @BindingType
                BindingID bindingID = BindingID.parse(endpointClass);
                MTOMFeature bindingMtomSetting = bindingID.createBuiltinFeatureList().get(MTOMFeature.class);
                if (bindingMtomSetting != null && bindingMtomSetting.isEnabled() ^ ftr.isEnabled()) {
                    throw new RuntimeModelerException(
                            ModelerMessages.RUNTIME_MODELER_MTOM_CONFLICT(bindingID, ftr.isEnabled()));
                }

            } else if (a instanceof RespectBinding) {
                RespectBinding rbAnn = (RespectBinding) a;
                ftr = new RespectBindingFeature(rbAnn.enabled());
            } else {
                ftr = getWebServiceFeatureBean(a);
            }
            add(ftr);
        }
    }

    private static WebServiceFeature getWebServiceFeatureBean(Annotation a) {
        WebServiceFeatureAnnotation wsfa = a.annotationType().getAnnotation(WebServiceFeatureAnnotation.class);
        Class<? extends WebServiceFeature> beanClass = wsfa.bean();
        WebServiceFeature bean;

        Constructor ftrCtr = null;
        String[] paramNames = null;
        for (Constructor con : beanClass.getConstructors()) {
            FeatureConstructor ftrCtrAnn = (FeatureConstructor) con.getAnnotation(FeatureConstructor.class);
            if (ftrCtrAnn != null) {
                if (ftrCtr == null) {
                    ftrCtr = con;
                    paramNames = ftrCtrAnn.value();
                } else {
                    throw new WebServiceException(ModelerMessages.RUNTIME_MODELER_WSFEATURE_MORETHANONE_FTRCONSTRUCTOR(a, beanClass));
                }
            }
        }
        if (ftrCtr == null) {
            throw new WebServiceException(ModelerMessages.RUNTIME_MODELER_WSFEATURE_NO_FTRCONSTRUCTOR(a, beanClass));
        }
        if (ftrCtr.getParameterTypes().length != paramNames.length) {
            throw new WebServiceException(ModelerMessages.RUNTIME_MODELER_WSFEATURE_ILLEGAL_FTRCONSTRUCTOR(a, beanClass));
        }

        try {
            Object[] params = new Object[paramNames.length];
            for (int i = 0; i < paramNames.length; i++) {
                Method m = a.annotationType().getDeclaredMethod(paramNames[i]);
                params[i] = m.invoke(a);
            }
            bean = (WebServiceFeature) ftrCtr.newInstance(params);
        } catch (Exception e) {
            throw new WebServiceException(e);
        }
        return bean;
    }

    public Iterator<WebServiceFeature> iterator() {
        if (parent != null)
            return new MergedFeatures(parent.getFeatures());
        return wsfeatures.values().iterator();
    }

    public
    @NotNull
    WebServiceFeature[] toArray() {
        if (parent != null)
            return new MergedFeatures(parent.getFeatures()).toArray();
        return wsfeatures.values().toArray(new WebServiceFeature[]{});
    }

    public boolean isEnabled(@NotNull Class<? extends WebServiceFeature> feature) {
        WebServiceFeature ftr = get(feature);
        return ftr != null && ftr.isEnabled();
    }


    public
    @Nullable
            <F extends WebServiceFeature> F get(@NotNull Class<F> featureType) {
        WebServiceFeature f = featureType.cast(wsfeatures.get(featureType));
        if (f == null && parent != null) {
            return parent.getFeatures().get(featureType);
        }
        return (F) f;
    }

    /**
     * Adds a feature to the list if it's not already added.
     */
    public void add(@NotNull WebServiceFeature f) {
        if (!wsfeatures.containsKey(f.getClass())) {
            wsfeatures.put(f.getClass(), f);
        }
    }

    /**
     * Adds features to the list if it's not already added.
     */
    public void addAll(@NotNull WSFeatureList list) {
        for (WebServiceFeature f : list)
            add(f);
    }

    /**
     * Extracts features from {@link WSDLPortImpl#getFeatures()}.
     * Extra features that are not already set on binding.
     * i.e, if a feature is set already on binding through someother API
     * the coresponding wsdlFeature is not set.
     *
     * @param wsdlPort          WSDLPort model
     * @param honorWsdlRequired If this is true add WSDL Feature only if wsd:Required=true
     *                          In SEI case, it should be false
     *                          In Provider case, it should be true
     * @param reportConflicts   If true, checks if the feature setting in WSDL (wsdl extension or
     *                          policy configuration) colflicts with feature setting in Deployed Service and
     *                          logs warning if there are any conflicts.
     */
    public void mergeFeatures(@NotNull WSDLPort wsdlPort, boolean honorWsdlRequired, boolean reportConflicts) {
        if (honorWsdlRequired && !isEnabled(RespectBindingFeature.class))
            return;
        if (!honorWsdlRequired) {
            addAll(wsdlPort.getFeatures());
            return;
        }
        // Add only if isRequired returns true, when honorWsdlRequired is true
        for (WebServiceFeature wsdlFtr : wsdlPort.getFeatures()) {
            if (get(wsdlFtr.getClass()) == null) {
                try {
                    // if it is a WSDL Extension , it will have required attribute
                    Method m = (wsdlFtr.getClass().getMethod("isRequired"));
                    try {
                        boolean required = (Boolean) m.invoke(wsdlFtr);
                        if (required)
                            add(wsdlFtr);
                    } catch (IllegalAccessException e) {
                        throw new WebServiceException(e);
                    } catch (InvocationTargetException e) {
                        throw new WebServiceException(e);
                    }
                } catch (NoSuchMethodException e) {
                    // this wsdlFtr is not an WSDL extension, just add it
                    add(wsdlFtr);
                }
            } else if (reportConflicts) {
                if (isEnabled(wsdlFtr.getClass()) != wsdlFtr.isEnabled()) {
                    LOGGER.warning(ModelerMessages.RUNTIME_MODELER_FEATURE_CONFLICT(
                            get(wsdlFtr.getClass()), wsdlFtr));
                }

            }
        }
    }

    /**
     * Set the parent features. Basically the parent feature list will be overriden
     * by this feature list.
     */
    public void setParentFeaturedObject(@NotNull WSDLFeaturedObject parent) {
        this.parent = parent;
    }

    /**
     * A Union of this WebServiceFeatureList and the parent.
     */
    private final class MergedFeatures implements Iterator<WebServiceFeature> {
        private final Stack<WebServiceFeature> features = new Stack<WebServiceFeature>();

        public MergedFeatures(@NotNull WSFeatureList parent) {

            for (WebServiceFeature f : wsfeatures.values()) {
                features.push(f);
            }

            for (WebServiceFeature f : parent) {
                if (!wsfeatures.containsKey(f.getClass())) {
                    features.push(f);
                }
            }
        }

        public boolean hasNext() {
            return !features.empty();
        }

        public WebServiceFeature next() {
            if (!features.empty()) {
                return features.pop();
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            if (!features.empty()) {
                features.pop();
            }
        }

        public WebServiceFeature[] toArray() {
            return features.toArray(new WebServiceFeature[]{});
        }
    }

    private static final Logger LOGGER = Logger.getLogger(WebServiceFeatureList.class.getName());
}
