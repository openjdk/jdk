/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.jgss.krb5;

import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.Subject;
import javax.security.auth.DestroyFailedException;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This utility looks through the current Subject and retrieves a ticket or key
 * for the desired client/server principals.
 *
 * @author Ram Marti
 * @since 1.4.2
 */

class SubjectComber {

    private static final boolean DEBUG = Krb5Util.DEBUG;

    /**
     * Default constructor
     */
    private SubjectComber() {  // Cannot create one of these
    }

    static Object find(Subject subject, String serverPrincipal,
        String clientPrincipal, Class credClass) {

        return findAux(subject, serverPrincipal, clientPrincipal, credClass,
            true);
    }

    static Object findMany(Subject subject, String serverPrincipal,
        String clientPrincipal, Class credClass) {

        return findAux(subject, serverPrincipal, clientPrincipal, credClass,
            false);
    }

    /**
     * Find the ticket or key for the specified client/server principals
     * in the subject. Returns null if the subject is null.
     *
     * @return the ticket or key
     */
    private static Object findAux(Subject subject, String serverPrincipal,
        String clientPrincipal, Class credClass, boolean oneOnly) {

        if (subject == null) {
            return null;
        } else {
            List<Object> answer = (oneOnly ? null : new ArrayList<Object>());

            if (credClass == KerberosKey.class) {
                // We are looking for a KerberosKey credentials for the
                // serverPrincipal
                Iterator<KerberosKey> iterator =
                    subject.getPrivateCredentials(KerberosKey.class).iterator();
                while (iterator.hasNext()) {
                    KerberosKey key = iterator.next();
                    if (serverPrincipal == null ||
                        serverPrincipal.equals(key.getPrincipal().getName())) {
                         if (DEBUG) {
                             System.out.println("Found key for "
                                 + key.getPrincipal() + "(" +
                                 key.getKeyType() + ")");
                         }
                         if (oneOnly) {
                             return key;
                         } else {
                             if (serverPrincipal == null) {
                                 // Record name so that keys returned will all
                                 // belong to the same principal
                                 serverPrincipal =
                                     key.getPrincipal().getName();
                             }
                             answer.add(key);
                         }
                    }
                }
            } else if (credClass == KerberosTicket.class) {
                // we are looking for a KerberosTicket credentials
                // for client-service principal pair
                Set<Object> pcs = subject.getPrivateCredentials();
                synchronized (pcs) {
                    Iterator<Object> iterator = pcs.iterator();
                    while (iterator.hasNext()) {
                        Object obj = iterator.next();
                        if (obj instanceof KerberosTicket) {
                            KerberosTicket ticket = (KerberosTicket)obj;
                            if (DEBUG) {
                                System.out.println("Found ticket for "
                                                    + ticket.getClient()
                                                    + " to go to "
                                                    + ticket.getServer()
                                                    + " expiring on "
                                                    + ticket.getEndTime());
                            }
                            if (!ticket.isCurrent()) {
                                // let us remove the ticket from the Subject
                                // Note that both TGT and service ticket will be
                                // removed  upon expiration
                                if (!subject.isReadOnly()) {
                                    iterator.remove();
                                    try {
                                        ticket.destroy();
                                        if (DEBUG) {
                                            System.out.println("Removed and destroyed "
                                                        + "the expired Ticket \n"
                                                        + ticket);

                                        }
                                    } catch (DestroyFailedException dfe) {
                                        if (DEBUG) {
                                            System.out.println("Expired ticket not" +
                                                    " detroyed successfully. " + dfe);
                                        }
                                    }

                                }
                            } else {
                                if (serverPrincipal == null ||
                                    ticket.getServer().getName().equals(serverPrincipal))  {

                                    if (clientPrincipal == null ||
                                        clientPrincipal.equals(
                                            ticket.getClient().getName())) {
                                        if (oneOnly) {
                                            return ticket;
                                        } else {
                                            // Record names so that tickets will
                                            // all belong to same principals
                                            if (clientPrincipal == null) {
                                                clientPrincipal =
                                                ticket.getClient().getName();
                                            }
                                            if (serverPrincipal == null) {
                                                serverPrincipal =
                                                ticket.getServer().getName();
                                            }
                                            answer.add(ticket);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return answer;
        }
    }
}
