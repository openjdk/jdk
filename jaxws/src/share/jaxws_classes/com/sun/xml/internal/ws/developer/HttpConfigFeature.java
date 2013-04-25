/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.developer;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import java.lang.reflect.Constructor;
import java.net.CookieHandler;

/**
 * A proxy's HTTP configuration (e.g cookie handling) can be configured using
 * this feature. While creating the proxy, this can be passed just like other
 * features.
 *
 * <p>
 * <b>THIS feature IS EXPERIMENTAL AND IS SUBJECT TO CHANGE WITHOUT NOTICE IN FUTURE.</b>
 *
 * @author Jitendra Kotamraju
 */
public final class HttpConfigFeature extends WebServiceFeature {
    /**
     * Constant value identifying the {@link HttpConfigFeature} feature.
     */
    public static final String ID = "http://jax-ws.java.net/features/http-config";

    private static final Constructor cookieManagerConstructor;
    private static final Object cookiePolicy;
    static {
        Constructor tempConstructor;
        Object tempPolicy;
        try {
            /*
             * Using reflection to create CookieManger so that RI would continue to
             * work with JDK 5.
             */
            Class policyClass = Class.forName("java.net.CookiePolicy");
            Class storeClass = Class.forName("java.net.CookieStore");
            tempConstructor = Class.forName("java.net.CookieManager").getConstructor(storeClass, policyClass);
            // JDK's default policy is ACCEPT_ORIGINAL_SERVER, but ACCEPT_ALL
            // is used for backward compatibility
            tempPolicy = policyClass.getField("ACCEPT_ALL").get(null);
        } catch(Exception e) {
            try {
                /*
                 * Using reflection so that these classes won't have to be
                 * integrated in JDK 6.
                 */
                Class policyClass = Class.forName("com.sun.xml.internal.ws.transport.http.client.CookiePolicy");
                Class storeClass = Class.forName("com.sun.xml.internal.ws.transport.http.client.CookieStore");
                tempConstructor = Class.forName("com.sun.xml.internal.ws.transport.http.client.CookieManager").getConstructor(storeClass, policyClass);
                // JDK's default policy is ACCEPT_ORIGINAL_SERVER, but ACCEPT_ALL
                // is used for backward compatibility
                tempPolicy = policyClass.getField("ACCEPT_ALL").get(null);
            } catch(Exception ce) {
                throw new WebServiceException(ce);
            }
        }
        cookieManagerConstructor = tempConstructor;
        cookiePolicy = tempPolicy;
    }

    private final CookieHandler cookieJar;      // shared object among the tubes

    public HttpConfigFeature() {
        this(getInternalCookieHandler());
    }

    public HttpConfigFeature(CookieHandler cookieJar) {
        this.enabled = true;
        this.cookieJar = cookieJar;
    }

    private static CookieHandler getInternalCookieHandler() {
        try {
            return (CookieHandler)cookieManagerConstructor.newInstance(null, cookiePolicy);
        } catch(Exception e) {
            throw new WebServiceException(e);
        }
    }

    public String getID() {
        return ID;
    }

    public CookieHandler getCookieHandler() {
        return cookieJar;
    }

}
