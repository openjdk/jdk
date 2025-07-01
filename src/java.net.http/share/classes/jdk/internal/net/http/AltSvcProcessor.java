/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.AltServicesRegistry.AltService;
import jdk.internal.net.http.AltServicesRegistry.Origin;
import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.frame.AltSvcFrame;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;

import static jdk.internal.net.http.Http3ClientProperties.ALTSVC_ALLOW_LOCAL_HOST_ORIGIN;
import static jdk.internal.net.http.common.Alpns.isSecureALPNName;


/**
 * Responsible for parsing the Alt-Svc values from an Alt-Svc header and/or AltSvc HTTP/2 frame.
 */
final class AltSvcProcessor {

    private static final String HEADER = "alt-svc";
    private static final Logger debug = Utils.getDebugLogger(() -> "AltSvc");
    // a special value that we return back while parsing the header values,
    // indicate that all existing alternate services for a origin need to be cleared
    private static final List<AltService> CLEAR_ALL_ALT_SVCS = List.of();
    // whether or not alt service can be created from "localhost" origin host
    private static final boolean allowLocalHostOrigin = ALTSVC_ALLOW_LOCAL_HOST_ORIGIN;

    private static final SNIHostName LOCALHOST_SNI = new SNIHostName("localhost");

    private record ParsedHeaderValue(String rawValue, String alpnName, String host, int port,
                                     Map<String, String> parameters) {
    }

    private AltSvcProcessor() {
        throw new UnsupportedOperationException("Instantiation not supported");
    }


    /**
     * Parses the alt-svc header received from origin and update
     * registry with the processed values.
     *
     * @param response response passed on by the server
     * @param client   client that holds alt-svc registry
     * @param request  request that holds the origin details
     */
    static void processAltSvcHeader(Response response, HttpClientImpl client,
                                    HttpRequestImpl request) {

        // we don't support AltSvc from unsecure origins
        if (!request.secure()) {
            return;
        }
        if (response.statusCode == 421) {
            // As per AltSvc spec (RFC-7838), section 6:
            // An Alt-Svc header field in a 421 (Misdirected Request) response MUST be ignored.
            return;
        }
        final var altSvcHeaderVal = response.headers().firstValue(HEADER);
        if (altSvcHeaderVal.isEmpty()) {
            return;
        }
        if (debug.on()) {
            debug.log("Processing alt-svc header");
        }
        final HttpConnection conn = response.exchange.exchImpl.connection();
        final List<SNIServerName> sniServerNames = getSNIServerNames(conn);
        if (sniServerNames.isEmpty()) {
            // we don't trust the alt-svc advertisement if the connection over which it
            // was advertised didn't use SNI during TLS handshake while establishing the connection
            if (debug.on()) {
                debug.log("ignoring alt-svc header because connection %s didn't use SNI during" +
                        " connection establishment", conn);
            }
            return;
        }
        final Origin origin;
        try {
            origin = Origin.from(request.uri());
        } catch (IllegalArgumentException iae) {
            if (debug.on()) {
                debug.log("ignoring alt-svc header due to: " + iae);
            }
            // ignore the alt-svc
            return;
        }
        String altSvcValue = altSvcHeaderVal.get();
        processValueAndUpdateRegistry(client, origin, altSvcValue);
    }

    static void processAltSvcFrame(final int streamId,
                                   final AltSvcFrame frame,
                                   final HttpConnection conn,
                                   final HttpClientImpl client) {
        final String value = frame.getAltSvcValue();
        if (value == null || value.isBlank()) {
            return;
        }
        if (!conn.isSecure()) {
            // don't support alt svc from unsecure origins
            return;
        }
        final List<SNIServerName> sniServerNames = getSNIServerNames(conn);
        if (sniServerNames.isEmpty()) {
            // we don't trust the alt-svc advertisement if the connection over which it
            // was advertised didn't use SNI during TLS handshake while establishing the connection
            if (debug.on()) {
                debug.log("ignoring altSvc frame because connection %s didn't use SNI during" +
                        " connection establishment", conn);
            }
            return;
        }
        debug.log("processing AltSvcFrame %s", value);
        final Origin origin;
        if (streamId == 0) {
            // section 4, RFC-7838 - alt-svc frame on stream 0 with empty (zero length) origin
            // is invalid and MUST be ignored
            if (frame.getOrigin().isBlank()) {
                // invalid frame, ignore it
                debug.log("Ignoring invalid alt-svc frame on stream 0 of " + conn);
                return;
            }
            // parse origin from frame.getOrigin() string which is in ASCII
            // serialized form of an origin (defined in section 6.2 of RFC-6454)
            try {
                origin = Origin.fromASCIISerializedForm(frame.getOrigin());
            } catch (IllegalArgumentException iae) {
                // invalid origin value, ignore the frame
                debug.log("origin couldn't be parsed, ignoring invalid alt-svc frame" +
                        " on stream " + streamId + " of " + conn);
                return;
            }
        } else {
            // (section 4, RFC-7838) - for non-zero stream id, the alt-svc is for the origin of
            // the stream. Additionally, an ALTSVC frame on a stream other than stream 0 containing
            // non-empty "Origin" information is invalid and MUST be ignored.
            if (!frame.getOrigin().isEmpty()) {
                // invalid frame, ignore it
                debug.log("non-empty origin in alt-svc frame on stream " + streamId + " of "
                        + conn + ", ignoring alt-svc frame");
                return;
            }
            // We are processing a AltSvcFrame that arrived on a HttpConnection
            // which we have verified as secure. We thus use "https" as the
            // scheme for the origin
            final String scheme = "https";
            try {
                origin = Origin.of(scheme, conn.address);
            } catch (IllegalArgumentException iae) {
                debug.log("origin couldn't be parsed: " + iae
                        + ", ignoring invalid alt-svc frame on stream "
                        + streamId + " of " + conn);
                return;
            }
        }
        processValueAndUpdateRegistry(client, origin, value);
    }

    private static void processValueAndUpdateRegistry(HttpClientImpl client,
                                                      Origin origin,
                                                      String altSvcValue) {
        final List<AltService> altServices = processHeaderValue(origin, altSvcValue);
        // intentional identity check
        if (altServices == CLEAR_ALL_ALT_SVCS) {
            // clear all existing alt services for this origin
            debug.log("clearing AltServiceRegistry for " + origin);
            client.registry().clear(origin);
            return;
        }
        debug.log("AltServices: %s", altServices);
        if (altServices.isEmpty()) {
            return;
        }
        // AltService RFC-7838, section 3.1 states:
        //
        // When an Alt-Svc response header field is received from an origin, its
        // value invalidates and replaces all cached alternative services for
        // that origin.
        client.registry().replace(origin, altServices);
    }

    static List<SNIServerName> getSNIServerNames(final HttpConnection conn) {
        final List<SNIServerName> sniServerNames = conn.getSNIServerNames();
        if (sniServerNames != null && !sniServerNames.isEmpty()) {
            return sniServerNames;
        }
        // no SNI server name(s) were used when establishing this connection. check if
        // this connection is to a loopback address and if it is then see if a configuration
        // has been set to allow alt services advertised by loopback addresses to be trusted/accepted.
        // if such a configuration has been set, then we return a SNIHostName for "localhost"
        final InetSocketAddress addr = conn.address();
        final boolean isLoopbackAddr = addr.isUnresolved()
                ? false
                : conn.address.getAddress().isLoopbackAddress();
        if (!isLoopbackAddr) {
            return List.of(); // no SNI server name(s) used for this connection
        }
        if (!allowLocalHostOrigin) {
            // this is a connection to a loopback address, with no SNI server name(s) used
            // during TLS handshake and the configuration doesn't allow accepting/trusting
            // alt services from loopback address, so we return no SNI server name(s) for this
            // connection
            return List.of();
        }
        // at this point, we have identified this as a loopback address and the configuration
        // has been set to accept/trust alt services from loopback address, so we return a
        // SNIHostname corresponding to "localhost"
        return List.of(LOCALHOST_SNI);
    }

    // Here are five examples of values for the Alt-Svc header:
    //  String svc1 = """foo=":443"; ma=2592000; persist=1"""
    //  String svc2 = """h3="localhost:5678"""";
    //  String svc3 = """bar3=":446"; ma=2592000; persist=1""";
    //  String svc4 = """h3-34=":5678"; ma=2592000; persist=1""";
    //  String svc5 = "%s, %s, %s, %s".formatted(svc1, svc2, svc3, svc4);
    // The last one (svc5) should result in two services being registered:
    // AltService[origin=https://localhost:64077/, alpn=h3, endpoint=localhost/127.0.0.1:5678,
    //            deadline=2021-03-13T01:41:01.369488Z, persist=false]
    // AltService[origin=https://localhost:64077/, alpn=h3-34, endpoint=localhost/127.0.0.1:5678,
    //            deadline=2021-04-11T01:41:01.369912Z, persist=true]
    private static List<AltService> processHeaderValue(final Origin origin,
                                                       final String headerValue) {
        final List<AltService> altServices = new ArrayList<>();
        // multiple alternate services can be specified with comma as a delimiter
        final var altSvcs = headerValue.split(",");
        for (var altSvc : altSvcs) {
            altSvc = altSvc.trim();

            // each value is expected to be of the following form, as noted in RFC-7838, section 3
            //   Alt-Svc       = clear / 1#alt-value
            //   clear         = %s"clear"; "clear", case-sensitive
            //   alt-value     = alternative *( OWS ";" OWS parameter )
            //   alternative   = protocol-id "=" alt-authority
            //   protocol-id   = token ; percent-encoded ALPN protocol name
            //   alt-authority = quoted-string ; containing [ uri-host ] ":" port
            //   parameter     = token "=" ( token / quoted-string )

            // As per the spec, the value "clear" is expected to be case-sensitive
            if (altSvc.equals("clear")) {
                return CLEAR_ALL_ALT_SVCS;
            }
            final ParsedHeaderValue parsed = parseAltValue(origin, altSvc);
            if (parsed == null) {
                // this implies the alt-svc header value couldn't be parsed and thus is malformed.
                // we skip such header values.
                debug.log("skipping %s", altSvc);
                continue;
            }
            final var deadline = getValidTill(parsed.parameters().get("ma"));
            final var persist = getPersist(parsed.parameters().get("persist"));
            final AltService.Identity altSvcId = new AltService.Identity(parsed.alpnName(),
                    parsed.host(), parsed.port());
            AltService.create(altSvcId, origin, deadline, persist)
                    .ifPresent((altsvc) -> {
                        altServices.add(altsvc);
                        if (Log.altsvc()) {
                            final var s = altsvc;
                            Log.logAltSvc("Created AltService: {0}", s);
                        } else if (debug.on()) {
                            debug.log("Created AltService for id=%s, origin=%s%n", altSvcId, origin);
                        }
                    });
        }
        return altServices;
    }

    private static ParsedHeaderValue parseAltValue(final Origin origin, final String altValue) {
        // header value is expected to be of the following form, as noted in RFC-7838, section 3
        //   Alt-Svc       = clear / 1#alt-value
        //   clear         = %s"clear"; "clear", case-sensitive
        //   alt-value     = alternative *( OWS ";" OWS parameter )
        //   alternative   = protocol-id "=" alt-authority
        //   protocol-id   = token ; percent-encoded ALPN protocol name
        //   alt-authority = quoted-string ; containing [ uri-host ] ":" port
        //   parameter     = token "=" ( token / quoted-string )

        // find the = sign that separates the protocol-id and alt-authority
        debug.log("parsing %s", altValue);
        final int alternativeDelimIndex = altValue.indexOf("=");
        if (alternativeDelimIndex == -1 || alternativeDelimIndex == altValue.length() - 1) {
            // not a valid alt value
            debug.log("no \"=\" character in %s", altValue);
            return null;
        }
        // key is always the protocol-id. example, in 'h3="localhost:5678"; ma=23232; persist=1'
        // "h3" acts as the key with '"localhost:5678"; ma=23232; persist=1' as the value
        final String protocolId = altValue.substring(0, alternativeDelimIndex);
        // the protocol-id can be percent encoded as per the spec, so we decode it to get the alpn name
        final var alpnName = decodePotentialPercentEncoded(protocolId);
        debug.log("alpn is %s in %s", alpnName, altValue);
        if (!isSecureALPNName(alpnName)) {
            // no reasonable assurance that the alternate service will be under the control
            // of the origin (section 2.1, RFC-7838)
            debug.log("alpn %s is not secure, skipping", alpnName);
            return null;
        }
        String remaining = altValue.substring(alternativeDelimIndex + 1);
        // now parse alt-authority
        if (!remaining.startsWith("\"") || remaining.length() == 1) {
            // we expect a quoted string for alt-authority
            debug.log("no quoted authority in %s", altValue);
            return null;
        }
        remaining = remaining.substring(1); // skip the starting double quote
        final int nextDoubleQuoteIndex = remaining.indexOf("\"");
        if (nextDoubleQuoteIndex == -1) {
            // malformed value
            debug.log("missing closing quote in %s", altValue);
            return null;
        }
        final String altAuthority = remaining.substring(0, nextDoubleQuoteIndex);
        final HostPort hostPort = getHostPort(origin, altAuthority);
        if (hostPort == null) return null; // host port could not be parsed
        if (nextDoubleQuoteIndex == remaining.length() - 1) {
            // there's nothing more left to parse
            return new ParsedHeaderValue(altValue, alpnName, hostPort.host(), hostPort.port(), Map.of());
        }
        // parse the semi-colon delimited parameters out of the rest of the remaining string
        remaining = remaining.substring(nextDoubleQuoteIndex + 1);
        final Map<String, String> parameters = extractParameters(remaining);
        return new ParsedHeaderValue(altValue, alpnName, hostPort.host(), hostPort.port(), parameters);
    }

    private static String decodePotentialPercentEncoded(final String val) {
        if (!val.contains("%")) {
            return val;
        }
        // TODO: impl this
        // In practice this method is only used for the ALPN.
        // We only support h3 for now, so we do not need to
        // decode percents: anything else but h3 will eventually be ignored.
        return val;
    }

    private static Map<String, String> extractParameters(final String val) {
        // As per the spec, parameters take the form of:
        //   *( OWS ";" OWS parameter )
        // ...
        //   parameter     = token "=" ( token / quoted-string )
        //
        // where * represents "any number of" and OWS means "optional whitespace"

        final var tokenizer = new StringTokenizer(val, ";");
        if (!tokenizer.hasMoreTokens()) {
            return Map.of();
        }
        Map<String, String> parameters = null;
        while (tokenizer.hasMoreTokens()) {
            final var parameter = tokenizer.nextToken().trim();
            if (parameter.isEmpty()) {
                continue;
            }
            final var equalSignIndex = parameter.indexOf('=');
            if (equalSignIndex == -1 || equalSignIndex == parameter.length() - 1) {
                // a parameter is expected to have a "=" delimiter which separates a key and a value.
                // we skip parameters which don't conform to that rule
                continue;
            }
            final var paramKey = parameter.substring(0, equalSignIndex);
            final var paramValue = parameter.substring(equalSignIndex + 1);
            if (parameters == null) {
                parameters = new HashMap<>();
            }
            parameters.put(paramKey, paramValue);
        }
        if (parameters == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(parameters);
    }

    private record HostPort(String host, int port) {}

    private static HostPort getHostPort(Origin origin, String altAuthority) {
        // The AltService spec defines an alt-authority as follows:
        //
        //   alt-authority = quoted-string ; containing [ uri-host ] ":" port
        //
        // When this method is called the passed altAuthority is already stripped of the leading and trailing
        // double-quotes. The value will this be of the form [uri-host]:port where uri-host is optional.
        String host; int port;
        try {
            // Use URI to do the parsing, with a special case for optional host
            URI uri = new URI("http://" + altAuthority + "/");
            host = uri.getHost();
            port = uri.getPort();
            if (host == null && port == -1) {
                var auth = uri.getRawAuthority();
                if (auth.isEmpty()) return null;
                if (auth.charAt(0) == ':') {
                    uri = new URI("http://x" + altAuthority + "/");
                    if ("x".equals(uri.getHost())) {
                        port = uri.getPort();
                    }
                }
            }
            if (port == -1) {
                debug.log("Can't parse authority: " + altAuthority);
                return null;
            }
            String hostport;
            if (host == null || host.isEmpty()) {
                hostport = ":" + port;
                host = origin.host();
            } else {
                hostport = host + ":" + port;
            }
            // reject anything unexpected. altAuthority should match hostport
            if (!hostport.equals(altAuthority)) {
                debug.log("Authority \"%s\" doesn't match host:port \"%s\"",
                        altAuthority, hostport);
                return null;
            }
        } catch (URISyntaxException x) {
            debug.log("Failed to parse authority: %s - %s",
                    altAuthority, x);
            return null;
        }
        return new HostPort(host, port);
    }

    private static Deadline getValidTill(final String maxAge) {
        // There's a detailed algorithm in RFC-7234 section 4.2.3, for calculating the age. This
        // RFC section is referenced from the alternate service RFC-7838 section 3.1.
        // For now though, we use "now" as the instant against which the age will be applied.
        final Deadline responseGenerationInstant = TimeSource.now();
        // default max age as per AltService RFC-7838, section 3.1 is 24 hours
        final long defaultMaxAgeInSecs = 3600 * 24;
        if (maxAge == null) {
            return responseGenerationInstant.plusSeconds(defaultMaxAgeInSecs);
        }
        try {
            final long seconds = Long.parseLong(maxAge);
            // negative values aren't allowed for max-age as per RFC-7234, section 1.2.1
            return seconds < 0 ? responseGenerationInstant.plusSeconds(defaultMaxAgeInSecs)
                    : responseGenerationInstant.plusSeconds(seconds);
        } catch (NumberFormatException nfe) {
            return responseGenerationInstant.plusSeconds(defaultMaxAgeInSecs);
        }
    }

    private static boolean getPersist(final String persist) {
        // AltService RFC-7838, section 3.1, states:
        //
        // This specification only defines a single value for "persist".
        // Clients MUST ignore "persist" parameters with values other than "1".
        //
        return "1".equals(persist);
    }
}
