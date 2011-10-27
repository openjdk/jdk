/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.spi.nameservice.dns;

import java.lang.ref.SoftReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.spi.NamingManager;
import java.util.*;
import sun.net.util.IPAddressUtil;
import sun.net.dns.ResolverConfiguration;
import sun.net.spi.nameservice.*;
import java.security.AccessController;
import sun.security.action.*;

/*
 * A name service provider based on JNDI-DNS.
 */

public final class DNSNameService implements NameService {

    // List of domains specified by property
    private LinkedList<String> domainList = null;

    // JNDI-DNS URL for name servers specified via property
    private String nameProviderUrl = null;

    // Per-thread soft cache of the last temporary context
    private static ThreadLocal<SoftReference<ThreadContext>> contextRef =
            new ThreadLocal<>();

    // Simple class to encapsulate the temporary context
    private static class ThreadContext {
        private DirContext dirCtxt;
        private List<String> nsList;

        public ThreadContext(DirContext dirCtxt, List<String> nsList) {
            this.dirCtxt = dirCtxt;
            this.nsList = nsList;
        }

        public DirContext dirContext() {
            return dirCtxt;
        }

        public List<String> nameservers() {
            return nsList;
        }
    }

    // Returns a per-thread DirContext
    private DirContext getTemporaryContext() throws NamingException {
        SoftReference<ThreadContext> ref = contextRef.get();
        ThreadContext thrCtxt = null;
        List<String> nsList = null;

        // if no property specified we need to obtain the list of servers
        //
        if (nameProviderUrl == null)
            nsList = ResolverConfiguration.open().nameservers();

        // if soft reference hasn't been gc'ed no property has been
        // specified then we need to check if the DNS configuration
        // has changed.
        //
        if ((ref != null) && ((thrCtxt = ref.get()) != null)) {
            if (nameProviderUrl == null) {
                if (!thrCtxt.nameservers().equals(nsList)) {
                    // DNS configuration has changed
                    thrCtxt = null;
                }
            }
        }

        // new thread context needs to be created
        if (thrCtxt == null) {
            final Hashtable<String,Object> env = new Hashtable<>();
            env.put("java.naming.factory.initial",
                    "com.sun.jndi.dns.DnsContextFactory");

            // If no nameservers property specified we create provider URL
            // based on system configured name servers
            //
            String provUrl = nameProviderUrl;
            if (provUrl == null) {
                provUrl = createProviderURL(nsList);
                if (provUrl.length() == 0) {
                    throw new RuntimeException("bad nameserver configuration");
                }
            }
            env.put("java.naming.provider.url", provUrl);

            // Need to create directory context in privileged block
            // as JNDI-DNS needs to resolve the name servers.
            //
            DirContext dirCtxt;
            try {
                dirCtxt = java.security.AccessController.doPrivileged(
                        new java.security.PrivilegedExceptionAction<DirContext>() {
                            public DirContext run() throws NamingException {
                                // Create the DNS context using NamingManager rather than using
                                // the initial context constructor. This avoids having the initial
                                // context constructor call itself.
                                Context ctx = NamingManager.getInitialContext(env);
                                if (!(ctx instanceof DirContext)) {
                                    return null; // cannot create a DNS context
                                }
                                return (DirContext)ctx;
                            }
                    });
            } catch (java.security.PrivilegedActionException pae) {
                throw (NamingException)pae.getException();
            }

            // create new soft reference to our thread context
            //
            thrCtxt = new ThreadContext(dirCtxt, nsList);
            contextRef.set(new SoftReference<ThreadContext>(thrCtxt));
        }

        return thrCtxt.dirContext();
    }

    /**
     * Resolves the specified entry in DNS.
     *
     * Canonical name records are recursively resolved (to a maximum
     * of 5 to avoid performance hit and potential CNAME loops).
     *
     * @param   ctx     JNDI directory context
     * @param   name    name to resolve
     * @param   ids     record types to search
     * @param   depth   call depth - pass as 0.
     *
     * @return  array list with results (will have at least on entry)
     *
     * @throws  UnknownHostException if lookup fails or other error.
     */
    private ArrayList<String> resolve(final DirContext ctx, final String name,
                                      final String[] ids, int depth)
            throws UnknownHostException
    {
        ArrayList<String> results = new ArrayList<>();
        Attributes attrs;

        // do the query
        try {
            attrs = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction<Attributes>() {
                        public Attributes run() throws NamingException {
                            return ctx.getAttributes(name, ids);
                        }
                });
        } catch (java.security.PrivilegedActionException pae) {
            throw new UnknownHostException(pae.getException().getMessage());
        }

        // non-requested type returned so enumeration is empty
        NamingEnumeration<? extends Attribute> ne = attrs.getAll();
        if (!ne.hasMoreElements()) {
            throw new UnknownHostException("DNS record not found");
        }

        // iterate through the returned attributes
        UnknownHostException uhe = null;
        try {
            while (ne.hasMoreElements()) {
                Attribute attr = ne.next();
                String attrID = attr.getID();

                for (NamingEnumeration<?> e = attr.getAll(); e.hasMoreElements();) {
                    String addr = (String)e.next();

                    // for canoncical name records do recursive lookup
                    // - also check for CNAME loops to avoid stack overflow

                    if (attrID.equals("CNAME")) {
                        if (depth > 4) {
                            throw new UnknownHostException(name + ": possible CNAME loop");
                        }
                        try {
                            results.addAll(resolve(ctx, addr, ids, depth+1));
                        } catch (UnknownHostException x) {
                            // canonical name can't be resolved.
                            if (uhe == null)
                                uhe = x;
                        }
                    } else {
                        results.add(addr);
                    }
                }
            }
        } catch (NamingException nx) {
            throw new UnknownHostException(nx.getMessage());
        }

        // pending exception as canonical name could not be resolved.
        if (results.isEmpty() && uhe != null) {
            throw uhe;
        }

        return results;
    }

    public DNSNameService() throws Exception {

        // default domain
        String domain = AccessController.doPrivileged(
            new GetPropertyAction("sun.net.spi.nameservice.domain"));
        if (domain != null && domain.length() > 0) {
            domainList = new LinkedList<String>();
            domainList.add(domain);
        }

        // name servers
        String nameservers = AccessController.doPrivileged(
            new GetPropertyAction("sun.net.spi.nameservice.nameservers"));
        if (nameservers != null && nameservers.length() > 0) {
            nameProviderUrl = createProviderURL(nameservers);
            if (nameProviderUrl.length() == 0) {
                throw new RuntimeException("malformed nameservers property");
            }

        } else {

            // no property specified so check host DNS resolver configured
            // with at least one nameserver in dotted notation.
            //
            List<String> nsList = ResolverConfiguration.open().nameservers();
            if (nsList.isEmpty()) {
                throw new RuntimeException("no nameservers provided");
            }
            boolean found = false;
            for (String addr: nsList) {
                if (IPAddressUtil.isIPv4LiteralAddress(addr) ||
                    IPAddressUtil.isIPv6LiteralAddress(addr)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeException("bad nameserver configuration");
            }
        }
    }

    public InetAddress[] lookupAllHostAddr(String host) throws UnknownHostException {

        // DNS records that we search for
        String[] ids = {"A", "AAAA", "CNAME"};

        // first get directory context
        DirContext ctx;
        try {
            ctx = getTemporaryContext();
        } catch (NamingException nx) {
            throw new Error(nx);
        }

        ArrayList<String> results = null;
        UnknownHostException uhe = null;

        // If host already contains a domain name then just look it up
        if (host.indexOf('.') >= 0) {
            try {
                results = resolve(ctx, host, ids, 0);
            } catch (UnknownHostException x) {
                uhe = x;
            }
        }

        // Here we try to resolve the host using the domain suffix or
        // the domain suffix search list. If the host cannot be resolved
        // using the domain suffix then we attempt devolution of
        // the suffix - eg: if we are searching for "foo" and our
        // domain suffix is "eng.sun.com" we will try to resolve
        // "foo.eng.sun.com" and "foo.sun.com".
        // It's not normal to attempt devolation with domains on the
        // domain suffix search list - however as ResolverConfiguration
        // doesn't distinguish domain or search list in the list it
        // returns we approximate by doing devolution on the domain
        // suffix if the list has one entry.

        if (results == null) {
            List<String> searchList = null;
            Iterator<String> i;
            boolean usingSearchList = false;

            if (domainList != null) {
                i = domainList.iterator();
            } else {
                searchList = ResolverConfiguration.open().searchlist();
                if (searchList.size() > 1) {
                    usingSearchList = true;
                }
                i = searchList.iterator();
            }

            // iterator through each domain suffix
            while (i.hasNext()) {
                String parentDomain = i.next();
                int start = 0;
                while ((start = parentDomain.indexOf(".")) != -1
                       && start < parentDomain.length() -1) {
                    try {
                        results = resolve(ctx, host+"."+parentDomain, ids, 0);
                        break;
                    } catch (UnknownHostException x) {
                        uhe = x;
                        if (usingSearchList) {
                            break;
                        }

                        // devolve
                        parentDomain = parentDomain.substring(start+1);
                    }
                }
                if (results != null) {
                    break;
                }
            }
        }

        // finally try the host if it doesn't have a domain name
        if (results == null && (host.indexOf('.') < 0)) {
            results = resolve(ctx, host, ids, 0);
        }

        // if not found then throw the (last) exception thrown.
        if (results == null) {
            assert uhe != null;
            throw uhe;
        }

        /**
         * Convert the array list into a byte aray list - this
         * filters out any invalid IPv4/IPv6 addresses.
         */
        assert results.size() > 0;
        InetAddress[] addrs = new InetAddress[results.size()];
        int count = 0;
        for (int i=0; i<results.size(); i++) {
            String addrString = results.get(i);
            byte addr[] = IPAddressUtil.textToNumericFormatV4(addrString);
            if (addr == null) {
                addr = IPAddressUtil.textToNumericFormatV6(addrString);
            }
            if (addr != null) {
                addrs[count++] = InetAddress.getByAddress(host, addr);
            }
        }

        /**
         * If addresses are filtered then we need to resize the
         * array. Additionally if all addresses are filtered then
         * we throw an exception.
         */
        if (count == 0) {
            throw new UnknownHostException(host + ": no valid DNS records");
        }
        if (count < results.size()) {
            InetAddress[] tmp = new InetAddress[count];
            for (int i=0; i<count; i++) {
                tmp[i] = addrs[i];
            }
            addrs = tmp;
        }

        return addrs;
    }

    /**
     * Reverse lookup code. I.E: find a host name from an IP address.
     * IPv4 addresses are mapped in the IN-ADDR.ARPA. top domain, while
     * IPv6 addresses can be in IP6.ARPA or IP6.INT.
     * In both cases the address has to be converted into a dotted form.
     */
    public String getHostByAddr(byte[] addr) throws UnknownHostException {
        String host = null;
        try {
            String literalip = "";
            String[] ids = { "PTR" };
            DirContext ctx;
            ArrayList<String> results = null;
            try {
                ctx = getTemporaryContext();
            } catch (NamingException nx) {
                throw new Error(nx);
            }
            if (addr.length == 4) { // IPv4 Address
                for (int i = addr.length-1; i >= 0; i--) {
                    literalip += (addr[i] & 0xff) +".";
                }
                literalip += "IN-ADDR.ARPA.";

                results = resolve(ctx, literalip, ids, 0);
                host = results.get(0);
            } else if (addr.length == 16) { // IPv6 Address
                /**
                 * Because RFC 3152 changed the root domain name for reverse
                 * lookups from IP6.INT. to IP6.ARPA., we need to check
                 * both. I.E. first the new one, IP6.ARPA, then if it fails
                 * the older one, IP6.INT
                 */

                for (int i = addr.length-1; i >= 0; i--) {
                    literalip += Integer.toHexString((addr[i] & 0x0f)) +"."
                        +Integer.toHexString((addr[i] & 0xf0) >> 4) +".";
                }
                String ip6lit = literalip + "IP6.ARPA.";

                try {
                    results = resolve(ctx, ip6lit, ids, 0);
                    host = results.get(0);
                } catch (UnknownHostException e) {
                    host = null;
                }
                if (host == null) {
                    // IP6.ARPA lookup failed, let's try the older IP6.INT
                    ip6lit = literalip + "IP6.INT.";
                    results = resolve(ctx, ip6lit, ids, 0);
                    host = results.get(0);
                }
            }
        } catch (Exception e) {
            throw new UnknownHostException(e.getMessage());
        }
        // Either we couldn't find it or the address was neither IPv4 or IPv6
        if (host == null)
            throw new UnknownHostException();
        // remove trailing dot
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }


    // ---------

    private static void appendIfLiteralAddress(String addr, StringBuffer sb) {
        if (IPAddressUtil.isIPv4LiteralAddress(addr)) {
            sb.append("dns://" + addr + " ");
        } else {
            if (IPAddressUtil.isIPv6LiteralAddress(addr)) {
                sb.append("dns://[" + addr + "] ");
            }
        }
    }

    /*
     * @return String containing the JNDI-DNS provider URL
     *         corresponding to the supplied List of nameservers.
     */
    private static String createProviderURL(List<String> nsList) {
        StringBuffer sb = new StringBuffer();
        for (String s: nsList) {
            appendIfLiteralAddress(s, sb);
        }
        return sb.toString();
    }

    /*
     * @return String containing the JNDI-DNS provider URL
     *         corresponding to the list of nameservers
     *         contained in the provided str.
     */
    private static String createProviderURL(String str) {
        StringBuffer sb = new StringBuffer();
        StringTokenizer st = new StringTokenizer(str, ",");
        while (st.hasMoreTokens()) {
            appendIfLiteralAddress(st.nextToken(), sb);
        }
        return sb.toString();
    }
}
