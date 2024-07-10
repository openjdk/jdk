/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.net.ssl.SNIServerName;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.common.Utils;

/**
 * A registry for Alternate Services advertised by server endpoints.
 * There is one registry per HttpClient.
 */
public final class AltServicesRegistry {

    static final Logger DEBUG = Utils.getDebugLogger(AltServicesRegistry.class::getSimpleName);

    // id and logger for debugging purposes: the id is the same for the HttpClientImpl.
    private final long id;
    private final Logger debug = Utils.getDebugLogger(this::dbgString);

    // The key is the origin of the alternate service
    // The value is a list of AltService records declared by the origin.
    private final Map<Origin, List<AltService>> altServices = new HashMap<>();
    // alt services which were marked invalid in context of an origin. the reason for
    // them being invalid can be connection issues (for example: the alt service didn't present the
    // certificate of the origin)
    private final Set<InvalidAltSvc> invalidAltServices = new HashSet<>();

    // used while dealing with both altServices Map and the invalidAltServices Set
    private final ReentrantLock registryLock = new ReentrantLock();

    public AltServicesRegistry(long id) {
        this.id = id;
    }

    String dbgString() {
        return "AltServicesRegistry(" + id + ")";
    }

    /**
     * Represents an origin of an alternative service.
     *
     * @param scheme The scheme of the origin (for example: https). Unlike the protocol part of the
     *               alternative service (which can be a finer grained protocol like h2, h3 etc...),
     *               this is actually a scheme.
     * @param host   The host of the origin
     * @param port   The port of the origin
     */
    public static final record Origin(String scheme, String host, int port) {
        public Origin {
            Objects.requireNonNull(scheme);
            Objects.requireNonNull(host);
            if (port <= 0) {
                throw new IllegalArgumentException("Invalid port");
            }
        }

        @Override
        public String toString() {
            return scheme + "://" + toAuthority(host, port);
        }

        /**
         * {@return Creates and returns an Origin from an URI}
         *
         * @param uri The URI of the origin
         */
        public static Origin from(final URI uri) throws IllegalArgumentException {
            Objects.requireNonNull(uri);
            final String scheme = uri.getScheme();
            final String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1 && scheme != null) {
                port = switch (scheme.toLowerCase(Locale.ROOT)) {
                    case "http" -> 80;
                    case "https" -> 443;
                    default -> throw new IllegalArgumentException("Unsupported scheme: " + scheme);
                };
            }
            return new Origin(scheme, host, port);
        }

        /**
         * {@return Creates and returns an Origin parsed from the ASCII serialized form as defined
         * in section 6.2 of RFC-6454}
         *
         * @param value The value to be parsed
         */
        static Origin fromASCIISerializedForm(final String value) throws IllegalArgumentException {
            Objects.requireNonNull(value);
            try {
                URI uri = new URI(value);
                if (!uri.isAbsolute()) {
                    throw new IllegalArgumentException("Invalid ASCII serialized form of origin");
                }
                if (uri.isOpaque()) {
                    throw new IllegalArgumentException("Invalid ASCII serialized form of origin");
                }
                if (uri.getHost() == null) {
                    throw new IllegalArgumentException("Invalid ASCII serialized form of origin");
                }
                try {
                    return Origin.from(uri);
                } catch (IllegalArgumentException iae) {
                    throw new IllegalArgumentException("Invalid ASCII serialized form of origin", iae);
                }
            } catch (URISyntaxException use) {
                throw new IllegalArgumentException("Invalid ASCII serialized form of origin", use);
            }
        }

        static Origin of(final String scheme, final InetSocketAddress addr) {
            // we use getHostString(), since the address could be unresolved in the case where
            // proxy is configured
            final String originHost = addr.getHostString();
            return new Origin(scheme, originHost, addr.getPort());
        }

        static Origin fromRequest(final HttpRequestImpl request) {
            final String scheme = request.uri().getScheme();
            final InetSocketAddress requestAddr = request.getAddress();
            return Origin.of(scheme, requestAddr);
        }
    }

    public static final class AltService {
        // As defined in RFC-7838, section 2, formally an alternate service is a combination of
        // ALPN, host and port
        public static record Identity(String alpn, String host, int port) {
            public Identity {
                Objects.requireNonNull(alpn);
                Objects.requireNonNull(host);
                if (port <= 0) {
                    throw new IllegalArgumentException("Invalid port: " + port);
                }
            }

            public boolean matches(AltService service) {
                return equals(service.identity());
            }

            @Override
            public String toString() {
                return alpn + "=\"" + toAuthority(host, port) +"\"";
            }
        }

        private static record AltServiceData(Identity id,  Origin origin, Deadline deadline,
                                             boolean persist, boolean advertised,
                                             List<SNIServerName> originSNIServerNames,
                                             String authority,
                                             boolean sameAuthorityAsOrigin) {
            public String pretty() {
                return  "AltSvc: " + id +
                        "; origin=\"" + origin + "\"" +
                        "; deadline=" + deadline +
                        "; persist=" + persist +
                        "; advertised=" + advertised +
                        "; originSNIServerNames=" + originSNIServerNames +
                        "; sameAuthorityAsOrigin=" + sameAuthorityAsOrigin +
                        ';';
            }
        }
        private final AltServiceData svc;

        /**
         * @param id             the alpn, host and port of this alternate service
         * @param origin         the {@link Origin} for this alternate service
         * @param deadline       the deadline until which this endpoint is valid
         * @param persist        whether that information can be persisted (we don't use this)
         * @param sniServerNames The list of server names to use in the SNI extension, when this alt
         *                       service is used
         * @param advertised     Whether or not this alt service was advertised as an alt service.
         *                       In certain cases, an alt service is created when no origin server
         *                       has advertised it. In those cases, this param is {@code false}
         */
        private AltService(final Identity id, final Origin origin, Deadline deadline,
                           final boolean persist, final List<SNIServerName> sniServerNames,
                           final boolean advertised) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(origin);
            deadline = deadline == null ? Deadline.MAX : deadline;
            final String authority = toAuthority(id.host, id.port);
            final String originAuthority = toAuthority(origin.host, origin.port);
            // keep track of whether the authority of this alt service is same as that
            // of the origin
            final boolean sameAuthorityAsOrigin = authority.equals(originAuthority);
            svc = new AltServiceData(id, origin, deadline, persist, advertised,
                    List.copyOf(sniServerNames), authority, sameAuthorityAsOrigin);
        }

        public Identity identity() {
            return svc.id;
        }

        /**
         * Returns the SNI names of the origin. These are the SNI names that must be used
         * in the TLS handshake when establishing a connection with this alt service.
         *
         * @return the SNI names of the origin.
         */
        public List<SNIServerName> originSNIServerNames() {
            return svc.originSNIServerNames;
        }

        /**
         * @return {@code host:port} of the alternate service
         */
        public String authority() {
            return svc.authority;
        }

        /**
         * @return {@code identity().host()}
         */
        public String host() {
            return svc.id.host;
        }

        /**
         * @return {@code identity().port()}
         */
        public int port() {
            return svc.id.port;
        }

        public boolean isPersist() {
            return svc.persist;
        }

        public boolean wasAdvertised() {
            return svc.advertised;
        }

        public String alpn() {
            return svc.id.alpn;
        }

        public Origin origin() {
            return svc.origin;
        }

        public Deadline deadline() {
            return svc.deadline;
        }

        /**
         * {@return true if the origin, for which this is an alternate service, has the
         * same authority as this alternate service. false otherwise.}
         */
        public boolean originHasSameAuthority() {
            return svc.sameAuthorityAsOrigin;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AltService service)) return false;
            return svc.equals(service.svc);
        }

        @Override
        public int hashCode() {
            return svc.hashCode();
        }

        @Override
        public String toString() {
            return svc.pretty();
        }

        public static Optional<AltService> create(final Identity id, final Origin origin,
                                                  final List<SNIServerName> sniServerNames,
                                                  final Deadline deadline, final boolean persist) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(origin);
            if (sniServerNames == null || sniServerNames.isEmpty()) {
                if (DEBUG.on()) {
                    DEBUG.log("Skipping altsvc creation for %s due to no SNI", id);
                }
                return Optional.empty();
            }
            if (DEBUG.on()) {
                DEBUG.log("Creating AltService for id=%s, origin=%s, SNI=%s%n",
                        id, origin, sniServerNames);
            }
            var service = new AltService(id, origin, deadline, persist, sniServerNames, true);
            if (Log.altsvc()) {
                Log.logAltSvc("Creating AltService: {0}", service);
            }
            return Optional.of(service);
        }

        private static Optional<AltService> createUnadvertised(final Identity id, final Origin origin,
                                                               final HttpConnection conn,
                                                               final Deadline deadline, final boolean persist) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(origin);
            final List<SNIServerName> sniServerNames = AltSvcProcessor.getSNIServerNames(conn);
            if (sniServerNames == null || sniServerNames.isEmpty()) {
                if (DEBUG.on()) {
                    DEBUG.log("Skipping unadvertised altsvc creation of %s because connection %s" +
                                    " didn't use SNI during connection establishment", id, conn);
                }
                return Optional.empty();
            }
            return Optional.of(new AltService(id, origin, deadline, persist, sniServerNames, false));
        }

    }

    // An alt-service is invalid for a particular origin
    private final record InvalidAltSvc(Origin origin, AltService.Identity id) {
    }

    private static String toAuthority(String host, int port) {
        assert port > 0 : "invalid port: " + port;
        // borrowed from code in java.net.URI
        final boolean needBrackets = host.indexOf(':') >= 0
                && !host.startsWith("[")
                && !host.endsWith("]");
        if (needBrackets) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    private boolean keepAltServiceFor(Origin origin, AltService svc) {
        // skip invalid alt services
        if (isMarkedInvalid(origin, svc.identity())) {
            if (debug.on()) {
                debug.log("Not registering alt-service which was previously" +
                        " marked invalid: " + svc);
            }
            if (Log.altsvc()) {
                Log.logAltSvc("AltService skipped (was previously marked invalid): " + svc);
            }
            return false;
        }
        return true;
    }

    /**
     * Declare a new Alternate Service endpoint for the given origin.
     *
     * @param origin   the origin
     * @param services a set of alt services for the origin
     */
    public void replace(final Origin origin, final List<AltService> services) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(services);
        List<AltService> added;
        registryLock.lock();
        try {
            // the list needs to be thread safe to ensure that we won't
            // get a ConcurrentModificationException when iterating
            // through the elements in list::stream();
            added = altServices.compute(origin, (key, list) -> {
                Stream<AltService> svcs = services.stream()
                        .filter(AltService.class::isInstance) // filter null
                        .filter((s) -> keepAltServiceFor(origin, s));
                List<AltService> newList = svcs.toList();
                return newList.isEmpty() ? null : newList;
            });
        } finally {
            registryLock.unlock();
        }
        if (debug.on()) {
            debug.log("parsed services: %s", services);
            debug.log("resulting services: %s", added);
        }
        if (Log.altsvc()) {
            if (added != null) {
                added.forEach((svc) -> Log.logAltSvc("AltService registry updated: {0}", svc));
            }
        }
    }

    // should be invoked while holding registryLock
    private boolean isMarkedInvalid(final Origin origin, final AltService.Identity id) {
        assert registryLock.isHeldByCurrentThread() : "Thread isn't holding registry lock";
        return this.invalidAltServices.contains(new InvalidAltSvc(origin, id));
    }

    /**
     * Registers an unadvertised alt service for the given origin and the alpn.
     *
     * @param id         The alt service identity
     * @param origin     The origin
     * @return true if the alt service was registered with the registry. False otherwise.
     */
    public Optional<AltService> registerUnadvertised(final AltService.Identity id,
                                                     final Origin origin,
                                                     final HttpConnection conn) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(origin);
        registryLock.lock();
        try {
            // an unadvertised alt service is registered by an origin only after a
            // successful connection has completed with that alt service. This effectively means
            // that we shouldn't check our "invalid alt services" collection, since a successful
            // connection against the alt service implies a valid alt service.
            // Additionally, we remove it from the "invalid alt services" collection for this
            // origin, if at all it was part of that collection
            this.invalidAltServices.remove(new InvalidAltSvc(origin, id));
            // default max age as per AltService RFC-7838, section 3.1 is 24 hours. we use
            // that same value for unadvertised alt-service(s) for an origin.
            final long defaultMaxAgeInSecs = 3600 * 24;
            final Deadline deadline = TimeSource.now().plusSeconds(defaultMaxAgeInSecs);
            final Optional<AltService> created = AltService.createUnadvertised(id, origin, conn,
                    deadline, true);
            if (created.isEmpty()) {
                return Optional.empty();
            }
            final AltService altSvc = created.get();
            altServices.compute(origin, (key, list) -> {
                    Stream<AltService> old = list == null ? Stream.empty() : list.stream();
                    List<AltService> newList = Stream.concat(old, Stream.of(altSvc)).toList();
                    return newList.isEmpty() ? null : newList;
            });
            if (debug.on()) {
                debug.log("Added unadvertised AltService: %s", created);
            }
            if (Log.altsvc()) {
                Log.logAltSvc("Added unadvertised AltService: {0}", created);
            }
            return created;
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * Clear the alternate services of the specified origin from the registry
     *
     * @param origin The origin whose alternate services need to be cleared
     */
    public void clear(final Origin origin) {
        Objects.requireNonNull(origin);
        registryLock.lock();
        try {
            if (Log.altsvc()) {
                Log.logAltSvc("Clearing AltServices for: " + origin);
            }
            altServices.remove(origin);
        } finally {
            registryLock.unlock();
        }
    }

    public void markInvalid(final AltService altService) {
        Objects.requireNonNull(altService);
        markInvalid(altService.origin(), altService.identity());
    }

    private void markInvalid(final Origin origin, final AltService.Identity id) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(id);
        registryLock.lock();
        try {
            // remove this alt service from the current active set of the origin
            this.altServices.computeIfPresent(origin,
                    (key, currentActive) -> {
                        if (currentActive == null) return null;
                        List<AltService> newList = currentActive.stream()
                                .filter(Predicate.not(id::matches)).toList();
                        return newList.isEmpty() ? null : newList;

                    });
            // additionally keep track of this as an invalid alt service, so that it cannot be
            // registered again in future.
            // we currently ban the alt-service for the origin permanently. In future,
            // if necessary, we can decide if this needs to be banned for only some
            // duration of time.
            this.invalidAltServices.add(new InvalidAltSvc(origin, id));
            if (debug.on()) {
                debug.log("AltService marked invalid: " + id + " for origin " + origin);
            }
            if (Log.altsvc()) {
                Log.logAltSvc("AltService marked invalid: " + id + " for origin " + origin);
            }
        } finally {
            registryLock.unlock();
        }

    }

    public Stream<AltService> lookup(final URI uri, final String alpn) {
        return lookup(Origin.from(uri), alpn);
    }

    /**
     * A stream of {@code AlternateService} that are available for the
     * given origin and the given ALPN.
     *
     * @param origin the URI of the origin server
     * @param alpn   the ALPN of the alternate service
     * @return a stream of {@code AlternateService} that are available for the
     * given origin and that support the given ALPN
     */
    public Stream<AltService> lookup(final Origin origin, final String alpn) {
        return lookup(origin, Predicate.isEqual(alpn));
    }

    public Stream<AltService> lookup(final URI uri,
                                     final Predicate<? super String> alpnMatcher) {
        return lookup(Origin.from(uri), alpnMatcher);
    }

    private boolean isExpired(AltService service, Deadline now) {
        var deadline = service.deadline();
        if (now.equals(deadline) || now.isAfter(deadline)) {
            // expired, remove from the list
            if (debug.on()) {
                debug.log("Removing expired alt-service " + service);
            }
            if (Log.altsvc()) {
                Log.logAltSvc("AltService has expired: {0}", service);
            }
            return true;
        }
        return false;
    }

    /**
     * A stream of {@code AlternateService} that are available for the
     * given origin and the given ALPN.
     *
     * @param origin      the URI of the origin server
     * @param alpnMatcher a predicate to select particular AltService(s) based on the alpn
     *                    of the alternate service
     * @return a stream of {@code AlternateService} that are available for the
     * given origin and whose ALPN satisfies the {@code alpn} predicate.
     */
    private Stream<AltService> lookup(final Origin origin,
                                      final Predicate<? super String> alpnMatcher) {
        if (debug.on()) debug.log("looking up service for: %s", origin);
        final List<AltService> services;
        registryLock.lock();
        try {
            // we first drop any expired services
            final Deadline now = TimeSource.now();
            services = altServices.compute(origin, (key, list) -> {
                if (list == null) return null;
                List<AltService> newList = list.stream()
                        .filter((s) -> !isExpired(s, now))
                        .toList();
                return newList.isEmpty() ? null : newList;
            });
        } finally {
            registryLock.unlock();
        }
        // the order is important - since preferred service are at the head
        return services == null
                ? Stream.empty()
                : services.stream().sequential().filter(s -> alpnMatcher.test(s.identity().alpn()));
    }

    /**
     * @param altService The alternate service
     *                {@return true if the {@code service} is known to this registry and the
     *                service isn't past its max age. false otherwise}
     * @throws NullPointerException if {@code service} is null
     */
    public boolean isActive(final AltService altService) {
        Objects.requireNonNull(altService);
        return isActive(altService.origin(), altService.identity());
    }

    private boolean isActive(final Origin origin, final AltService.Identity id) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(id);
        registryLock.lock();
        try {
            final List<AltService> currentActive = this.altServices.get(origin);
            if (currentActive == null) {
                return false;
            }
            AltService svc = null;
            for (AltService s : currentActive) {
                if (s.identity().equals(id)) {
                    svc = s;
                    break;
                }
            }
            if (svc == null) {
                return false;
            }
            // verify that the service hasn't expired
            final Deadline now = TimeSource.now();
            final Deadline deadline = svc.deadline();
            final boolean expired = now.equals(deadline) || now.isAfter(deadline);
            if (expired) {
                // remove from the registry
                altServices.put(origin, currentActive.stream()
                        .filter(Predicate.not(svc::equals)).toList());
                if (debug.on()) {
                    debug.log("Removed expired alt-service " + svc + " for origin " + origin);
                }
                if (Log.altsvc()) {
                    Log.logAltSvc("Removed AltService: {0}", svc);
                }
                return false;
            }
            return true;
        } finally {
            registryLock.unlock();
        }
    }
}
