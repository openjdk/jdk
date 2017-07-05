/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package client;

import java.security.Provider;
import java.security.Security;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Modular test for client using different mechanism to find the custom security
 * provider. It uses ServiceLoader and ClassLoader to find the TEST provider
 * available in classPath/modulePath. It also tries to find, if the provider is
 * configured through "java.security" file.
 */
public class TestSecurityProviderClient {

    private static final String CUSTOM_PROVIDER_NAME = "TEST";
    private static final String EXCEPTION_MESSAGE
            = "Unable to find Test Security Provider";
    private static final String SERVICE_LOADER = "SERVICE_LOADER";
    private static final String CLASS_LOADER = "CLASS_LOADER";

    public static void main(String[] args) {
        Provider provider = null;
        //Try to find the TEST provider loaded by ServiceLoader.
        if (args != null && args.length > 0
                && SERVICE_LOADER.equals(args[0])) {
            System.out.println(
                    "Using service loader to find Security provider.");
            ServiceLoader<Provider> services
                    = ServiceLoader.load(java.security.Provider.class);
            Iterator<Provider> iterator = services.iterator();
            while (iterator.hasNext()) {
                Provider p = iterator.next();
                if (p.getName().equals(CUSTOM_PROVIDER_NAME)) {
                    provider = p;
                    break;
                }
            }
        } else if (args != null && args.length > 0
                && CLASS_LOADER.equals(args[0])) {
            System.out.println("Using class loader to find Security provider.");
            //Find the TEST provider loaded by ClassLoader.
            provider = new provider.TestSecurityProvider();
        } else {
            //Find the TEST provider configured through Security.getProvider().
            System.out.println("Finding Security provider through"
                    + " Security.getProvider().");
            provider = Security.getProvider(CUSTOM_PROVIDER_NAME);
        }

        if (provider != null) {
            System.out.format("%nTest Security provider named '%s' loaded "
                    + "successfully", CUSTOM_PROVIDER_NAME);
        } else {
            throw new RuntimeException(EXCEPTION_MESSAGE);
        }
    }
}
