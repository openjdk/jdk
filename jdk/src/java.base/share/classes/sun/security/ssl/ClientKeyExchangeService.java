/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import sun.security.action.GetPropertyAction;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.*;

/**
 * Models a service that provides support for a particular client key exchange
 * mode. Currently used to implement Kerberos-related cipher suites.
 *
 * @since 9
 */
public interface ClientKeyExchangeService {

    static class Loader {
        private static final Map<String,ClientKeyExchangeService>
                providers = new HashMap<>();

        static {
            String path = GetPropertyAction.getProperty("java.home");
            ServiceLoader<ClientKeyExchangeService> sc =
                    AccessController.doPrivileged(
                            (PrivilegedAction<ServiceLoader<ClientKeyExchangeService>>)
                                    () -> ServiceLoader.loadInstalled(ClientKeyExchangeService.class),
                            null,
                            new FilePermission(new File(path, "-").toString(), "read"));
            Iterator<ClientKeyExchangeService> iter = sc.iterator();
            while (iter.hasNext()) {
                ClientKeyExchangeService cs = iter.next();
                for (String ex: cs.supported()) {
                    providers.put(ex, cs);
                }
            }
        }

    }

    public static ClientKeyExchangeService find(String ex) {
        return Loader.providers.get(ex);
    }


    /**
     * Returns the supported key exchange modes by this provider.
     * @return the supported key exchange modes
     */
    String[] supported();

    /**
     * Returns a generalized credential object on the server side. The server
     * side can use the info to determine if a cipher suite can be enabled.
     * @param acc the AccessControlContext of the SSL session
     * @return the credential object
     */
    Object getServiceCreds(AccessControlContext acc);

    /**
     * Returns the host name for a service principal. The info can be used in
     * SNI or host name verifier.
     * @param principal the principal of a service
     * @return the string formed host name
     */
    String getServiceHostName(Principal principal);

    /**
     * Returns whether the specified principal is related to the current
     * SSLSession. The info can be used to verify a SSL resume.
     * @param isClient if true called from client side, otherwise from server
     * @param acc the AccessControlContext of the SSL session
     * @param p the specified principal
     * @return true if related
     */
    boolean isRelated(boolean isClient, AccessControlContext acc, Principal p);

    /**
     * Creates the ClientKeyExchange object on the client side.
     * @param serverName the intented peer name
     * @param acc the AccessControlContext of the SSL session
     * @param protocolVersion the TLS protocol version
     * @param rand the SecureRandom that will used to generate the premaster
     * @return the new Exchanger object
     * @throws IOException if there is an error
     */
    ClientKeyExchange createClientExchange(String serverName, AccessControlContext acc,
            ProtocolVersion protocolVersion, SecureRandom rand) throws IOException;

    /**
     * Create the ClientKeyExchange on the server side.
     * @param protocolVersion the protocol version
     * @param clientVersion the input protocol version
     * @param rand a SecureRandom object used to generate premaster
     *             (if the server has to create one)
     * @param encodedTicket the ticket from client
     * @param encrypted the encrypted premaster secret from client
     * @param acc the AccessControlContext of the SSL session
     * @param ServiceCreds the service side credentials object as retrived from
     *                     {@link #getServiceCreds}
     * @return the new Exchanger object
     * @throws IOException if there is an error
     */
    ClientKeyExchange createServerExchange(
            ProtocolVersion protocolVersion, ProtocolVersion clientVersion,
            SecureRandom rand, byte[] encodedTicket, byte[] encrypted,
            AccessControlContext acc, Object ServiceCreds) throws IOException;
}
