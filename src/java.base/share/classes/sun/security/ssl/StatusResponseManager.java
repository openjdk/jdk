/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.cert.X509Certificate;
import java.security.cert.Extension;
import java.util.*;
import java.util.concurrent.*;

import sun.security.provider.certpath.CertId;
import sun.security.provider.certpath.OCSP;
import sun.security.provider.certpath.OCSPResponse;
import sun.security.provider.certpath.ResponderId;
import sun.security.util.Cache;
import sun.security.x509.PKIXExtensions;
import sun.security.x509.SerialNumber;
import sun.security.action.GetBooleanAction;
import sun.security.action.GetIntegerAction;
import sun.security.action.GetPropertyAction;

final class StatusResponseManager {
    private static final int DEFAULT_CORE_THREADS = 8;
    private static final int DEFAULT_CACHE_SIZE = 256;
    private static final int DEFAULT_CACHE_LIFETIME = 3600;         // seconds
    private static final Debug debug = Debug.getInstance("ssl");

    private final ScheduledThreadPoolExecutor threadMgr;
    private final Cache<CertId, ResponseCacheEntry> responseCache;
    private final URI defaultResponder;
    private final boolean respOverride;
    private final int cacheCapacity;
    private final int cacheLifetime;
    private final boolean ignoreExtensions;

    /**
     * Create a StatusResponseManager with default parameters.
     */
    StatusResponseManager() {
        int cap = AccessController.doPrivileged(
                new GetIntegerAction("jdk.tls.stapling.cacheSize",
                    DEFAULT_CACHE_SIZE));
        cacheCapacity = cap > 0 ? cap : 0;

        int life = AccessController.doPrivileged(
                new GetIntegerAction("jdk.tls.stapling.cacheLifetime",
                    DEFAULT_CACHE_LIFETIME));
        cacheLifetime = life > 0 ? life : 0;

        String uriStr = GetPropertyAction
                .privilegedGetProperty("jdk.tls.stapling.responderURI");
        URI tmpURI;
        try {
            tmpURI = ((uriStr != null && !uriStr.isEmpty()) ?
                    new URI(uriStr) : null);
        } catch (URISyntaxException urise) {
            tmpURI = null;
        }
        defaultResponder = tmpURI;

        respOverride = AccessController.doPrivileged(
                new GetBooleanAction("jdk.tls.stapling.responderOverride"));
        ignoreExtensions = AccessController.doPrivileged(
                new GetBooleanAction("jdk.tls.stapling.ignoreExtensions"));

        threadMgr = new ScheduledThreadPoolExecutor(DEFAULT_CORE_THREADS,
                new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            }
        }, new ThreadPoolExecutor.DiscardPolicy());
        threadMgr.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        threadMgr.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        threadMgr.setKeepAliveTime(5000, TimeUnit.MILLISECONDS);
        threadMgr.allowCoreThreadTimeOut(true);
        responseCache = Cache.newSoftMemoryCache(cacheCapacity, cacheLifetime);
    }

    /**
     * Get the current cache lifetime setting
     *
     * @return the current cache lifetime value
     */
    int getCacheLifetime() {
        return cacheLifetime;
    }

    /**
     * Get the current maximum cache size.
     *
     * @return the current maximum cache size
     */
    int getCacheCapacity() {
        return cacheCapacity;
    }

    /**
     * Get the default OCSP responder URI, if previously set.
     *
     * @return the current default OCSP responder URI, or {@code null} if
     *      it has not been set.
     */
    URI getDefaultResponder() {
        return defaultResponder;
    }

    /**
     * Get the URI override setting
     *
     * @return {@code true} if URI override has been set, {@code false}
     * otherwise.
     */
    boolean getURIOverride() {
        return respOverride;
    }

    /**
     * Get the ignore extensions setting.
     *
     * @return {@code true} if the {@code StatusResponseManager} will not
     * pass OCSP Extensions in the TLS {@code status_request[_v2]} extensions,
     * {@code false} if extensions will be passed (the default).
     */
    boolean getIgnoreExtensions() {
        return ignoreExtensions;
    }

    /**
     * Clear the status response cache
     */
    void clear() {
        debugLog("Clearing response cache");
        responseCache.clear();
    }

    /**
     * Returns the number of currently valid objects in the response cache.
     *
     * @return the number of valid objects in the response cache.
     */
    int size() {
        return responseCache.size();
    }

    /**
     * Obtain the URI use by the {@code StatusResponseManager} during lookups.
     * This method takes into account not only the AIA extension from a
     * certificate to be checked, but also any default URI and possible
     * override settings for the response manager.
     *
     * @param cert the subject to get the responder URI from
     *
     * @return a {@code URI} containing the address to the OCSP responder, or
     *      {@code null} if no AIA extension exists in the certificate and no
     *      default responder has been configured.
     *
     * @throws NullPointerException if {@code cert} is {@code null}.
     */
    URI getURI(X509Certificate cert) {
        Objects.requireNonNull(cert);

        if (cert.getExtensionValue(
                PKIXExtensions.OCSPNoCheck_Id.toString()) != null) {
            debugLog("OCSP NoCheck extension found.  OCSP will be skipped");
            return null;
        } else if (defaultResponder != null && respOverride) {
            debugLog("Responder override: URI is " + defaultResponder);
            return defaultResponder;
        } else {
            URI certURI = OCSP.getResponderURI(cert);
            return (certURI != null ? certURI : defaultResponder);
        }
    }

    /**
     * Shutdown the thread pool
     */
    void shutdown() {
        debugLog("Shutting down " + threadMgr.getActiveCount() +
                " active threads");
        threadMgr.shutdown();
    }

    /**
     * Get a list of responses for a chain of certificates.
     * This will find OCSP responses from the cache, or failing that, directly
     * contact the OCSP responder.  It is assumed that the certificates in
     * the provided chain are in their proper order (from end-entity to
     * trust anchor).
     *
     * @param type the type of request being made of the
     *      {@code StatusResponseManager}
     * @param request the {@code StatusRequest} from the status_request or
     *      status_request_v2 ClientHello extension.  A value of {@code null}
     *      is interpreted as providing no responder IDs or extensions.
     * @param chain an array of 2 or more certificates.  Each certificate must
     *      be issued by the next certificate in the chain.
     * @param delay the number of time units to delay before returning
     *      responses.
     * @param unit the unit of time applied to the {@code delay} parameter
     *
     * @return an unmodifiable {@code Map} containing the certificate and
     *      its usually
     *
     * @throws SSLHandshakeException if an unsupported {@code StatusRequest}
     *      is provided.
     */
    Map<X509Certificate, byte[]> get(StatusRequestType type,
            StatusRequest request, X509Certificate[] chain, long delay,
            TimeUnit unit) {
        Map<X509Certificate, byte[]> responseMap = new HashMap<>();
        List<OCSPFetchCall> requestList = new ArrayList<>();

        debugLog("Beginning check: Type = " + type + ", Chain length = " +
                chain.length);

        // It is assumed that the caller has ordered the certs in the chain
        // in the proper order (each certificate is issued by the next entry
        // in the provided chain).
        if (chain.length < 2) {
            return Collections.emptyMap();
        }

        if (type == StatusRequestType.OCSP) {
            try {
                // For type OCSP, we only check the end-entity certificate
                OCSPStatusRequest ocspReq = (OCSPStatusRequest)request;
                CertId cid = new CertId(chain[1],
                        new SerialNumber(chain[0].getSerialNumber()));
                ResponseCacheEntry cacheEntry = getFromCache(cid, ocspReq);
                if (cacheEntry != null) {
                    responseMap.put(chain[0], cacheEntry.ocspBytes);
                } else {
                    StatusInfo sInfo = new StatusInfo(chain[0], cid);
                    requestList.add(new OCSPFetchCall(sInfo, ocspReq));
                }
            } catch (IOException exc) {
                debugLog("Exception during CertId creation: " + exc);
            }
        } else if (type == StatusRequestType.OCSP_MULTI) {
            // For type OCSP_MULTI, we check every cert in the chain that
            // has a direct issuer at the next index.  We won't have an issuer
            // certificate for the last certificate in the chain and will
            // not be able to create a CertId because of that.
            OCSPStatusRequest ocspReq = (OCSPStatusRequest)request;
            int ctr;
            for (ctr = 0; ctr < chain.length - 1; ctr++) {
                try {
                    // The cert at "ctr" is the subject cert, "ctr + 1" is the
                    // issuer certificate.
                    CertId cid = new CertId(chain[ctr + 1],
                            new SerialNumber(chain[ctr].getSerialNumber()));
                    ResponseCacheEntry cacheEntry = getFromCache(cid, ocspReq);
                    if (cacheEntry != null) {
                        responseMap.put(chain[ctr], cacheEntry.ocspBytes);
                    } else {
                        StatusInfo sInfo = new StatusInfo(chain[ctr], cid);
                        requestList.add(new OCSPFetchCall(sInfo, ocspReq));
                    }
                } catch (IOException exc) {
                    debugLog("Exception during CertId creation: " + exc);
                }
            }
        } else {
            debugLog("Unsupported status request type: " + type);
        }

        // If we were able to create one or more Fetches, go and run all
        // of them in separate threads.  For all the threads that completed
        // in the allotted time, put those status responses into the returned
        // Map.
        if (!requestList.isEmpty()) {
            try {
                // Set a bunch of threads to go do the fetching
                List<Future<StatusInfo>> resultList =
                        threadMgr.invokeAll(requestList, delay, unit);

                // Go through the Futures and from any non-cancelled task,
                // get the bytes and attach them to the responseMap.
                for (Future<StatusInfo> task : resultList) {
                    if (task.isDone()) {
                        if (!task.isCancelled()) {
                            StatusInfo info = task.get();
                            if (info != null && info.responseData != null) {
                                responseMap.put(info.cert,
                                        info.responseData.ocspBytes);
                            } else {
                                debugLog("Completed task had no response data");
                            }
                        } else {
                            debugLog("Found cancelled task");
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException exc) {
                // Not sure what else to do here
                debugLog("Exception when getting data: " + exc);
            }
        }

        return Collections.unmodifiableMap(responseMap);
    }

    /**
     * Check the cache for a given {@code CertId}.
     *
     * @param cid the CertId of the response to look up
     * @param ocspRequest the OCSP request structure sent by the client
     *      in the TLS status_request[_v2] hello extension.
     *
     * @return the {@code ResponseCacheEntry} for a specific CertId, or
     *      {@code null} if it is not found or a nonce extension has been
     *      requested by the caller.
     */
    private ResponseCacheEntry getFromCache(CertId cid,
            OCSPStatusRequest ocspRequest) {
        // Determine if the nonce extension is present in the request.  If
        // so, then do not attempt to retrieve the response from the cache.
        for (Extension ext : ocspRequest.getExtensions()) {
            if (ext.getId().equals(PKIXExtensions.OCSPNonce_Id.toString())) {
                debugLog("Nonce extension found, skipping cache check");
                return null;
            }
        }

        ResponseCacheEntry respEntry = responseCache.get(cid);

        // If the response entry has a nextUpdate and it has expired
        // before the cache expiration, purge it from the cache
        // and do not return it as a cache hit.
        if (respEntry != null && respEntry.nextUpdate != null &&
                respEntry.nextUpdate.before(new Date())) {
            debugLog("nextUpdate threshold exceeded, purging from cache");
            respEntry = null;
        }

        debugLog("Check cache for SN" + cid.getSerialNumber() + ": " +
                (respEntry != null ? "HIT" : "MISS"));
        return respEntry;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StatusResponseManager: ");

        sb.append("Core threads: ").append(threadMgr.getCorePoolSize());
        sb.append(", Cache timeout: ");
        if (cacheLifetime > 0) {
            sb.append(cacheLifetime).append(" seconds");
        } else {
            sb.append(" indefinite");
        }

        sb.append(", Cache MaxSize: ");
        if (cacheCapacity > 0) {
            sb.append(cacheCapacity).append(" items");
        } else {
            sb.append(" unbounded");
        }

        sb.append(", Default URI: ");
        if (defaultResponder != null) {
            sb.append(defaultResponder);
        } else {
            sb.append("NONE");
        }

        return sb.toString();
    }

    /**
     * Log messages through the SSL Debug facility.
     *
     * @param message the message to be displayed
     */
    static void debugLog(String message) {
        if (debug != null && Debug.isOn("respmgr")) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(Thread.currentThread().getName());
            sb.append("] ").append(message);
            System.out.println(sb.toString());
        }
    }

    /**
     * Inner class used to group request and response data.
     */
    class StatusInfo {
        final X509Certificate cert;
        final CertId cid;
        final URI responder;
        ResponseCacheEntry responseData;

        /**
         * Create a StatusInfo object from certificate data.
         *
         * @param subjectCert the certificate to be checked for revocation
         * @param issuerCert the issuer of the {@code subjectCert}
         *
         * @throws IOException if CertId creation from the certificates fails
         */
        StatusInfo(X509Certificate subjectCert, X509Certificate issuerCert)
                throws IOException {
            this(subjectCert, new CertId(issuerCert,
                    new SerialNumber(subjectCert.getSerialNumber())));
        }

        /**
         * Create a StatusInfo object from an existing subject certificate
         * and its corresponding CertId.
         *
         * @param subjectCert the certificate to be checked for revocation
         * @param cid the CertId for {@code subjectCert}
         */
        StatusInfo(X509Certificate subjectCert, CertId certId) {
            cert = subjectCert;
            cid = certId;
            responder = getURI(cert);
            responseData = null;
        }

        /**
         * Copy constructor (used primarily for rescheduling).
         * This will do a member-wise copy with the exception of the
         * responseData and extensions fields, which should not persist
         * in a rescheduled fetch.
         *
         * @param orig the original {@code StatusInfo}
         */
        StatusInfo(StatusInfo orig) {
            this.cert = orig.cert;
            this.cid = orig.cid;
            this.responder = orig.responder;
            this.responseData = null;
        }

        /**
         * Return a String representation of the {@code StatusInfo}
         *
         * @return a {@code String} representation of this object
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("StatusInfo:");
            sb.append("\n\tCert: ").append(this.cert.getSubjectX500Principal());
            sb.append("\n\tSerial: ").append(this.cert.getSerialNumber());
            sb.append("\n\tResponder: ").append(this.responder);
            sb.append("\n\tResponse data: ").append(this.responseData != null ?
                    (this.responseData.ocspBytes.length + " bytes") : "<NULL>");
            return sb.toString();
        }
    }

    /**
     * Static nested class used as the data kept in the response cache.
     */
    static class ResponseCacheEntry {
        final OCSPResponse.ResponseStatus status;
        final byte[] ocspBytes;
        final Date nextUpdate;
        final OCSPResponse.SingleResponse singleResp;
        final ResponderId respId;

        /**
         * Create a new cache entry from the raw bytes of the response
         *
         * @param responseBytes the DER encoding for the OCSP response
         *
         * @throws IOException if an {@code OCSPResponse} cannot be created from
         *      the encoded bytes.
         */
        ResponseCacheEntry(byte[] responseBytes, CertId cid)
                throws IOException {
            Objects.requireNonNull(responseBytes,
                    "Non-null responseBytes required");
            Objects.requireNonNull(cid, "Non-null Cert ID required");

            ocspBytes = responseBytes.clone();
            OCSPResponse oResp = new OCSPResponse(ocspBytes);
            status = oResp.getResponseStatus();
            respId = oResp.getResponderId();
            singleResp = oResp.getSingleResponse(cid);
            if (status == OCSPResponse.ResponseStatus.SUCCESSFUL) {
                if (singleResp != null) {
                    // Pull out the nextUpdate field in advance because the
                    // Date is cloned.
                    nextUpdate = singleResp.getNextUpdate();
                } else {
                    throw new IOException("Unable to find SingleResponse for " +
                            "SN " + cid.getSerialNumber());
                }
            } else {
                nextUpdate = null;
            }
        }
    }

    /**
     * Inner Callable class that does the actual work of looking up OCSP
     * responses, first looking at the cache and doing OCSP requests if
     * a cache miss occurs.
     */
    class OCSPFetchCall implements Callable<StatusInfo> {
        StatusInfo statInfo;
        OCSPStatusRequest ocspRequest;
        List<Extension> extensions;
        List<ResponderId> responderIds;

        /**
         * A constructor that builds the OCSPFetchCall from the provided
         * StatusInfo and information from the status_request[_v2] extension.
         *
         * @param info the {@code StatusInfo} containing the subject
         * certificate, CertId, and other supplemental info.
         * @param request the {@code OCSPStatusRequest} containing any
         * responder IDs and extensions.
         */
        public OCSPFetchCall(StatusInfo info, OCSPStatusRequest request) {
            statInfo = Objects.requireNonNull(info,
                    "Null StatusInfo not allowed");
            ocspRequest = Objects.requireNonNull(request,
                    "Null OCSPStatusRequest not allowed");
            extensions = ocspRequest.getExtensions();
            responderIds = ocspRequest.getResponderIds();
        }

        /**
         * Get an OCSP response, either from the cache or from a responder.
         *
         * @return The StatusInfo object passed into the {@code OCSPFetchCall}
         * constructor, with the {@code responseData} field filled in with the
         * response or {@code null} if no response can be obtained.
         */
        @Override
        public StatusInfo call() {
            debugLog("Starting fetch for SN " + statInfo.cid.getSerialNumber());
            try {
                ResponseCacheEntry cacheEntry;
                List<Extension> extsToSend;

                if (statInfo.responder == null) {
                    // If we have no URI then there's nothing to do but return
                    debugLog("Null URI detected, OCSP fetch aborted.");
                    return statInfo;
                } else {
                    debugLog("Attempting fetch from " + statInfo.responder);
                }

                // If the StatusResponseManager has been configured to not
                // forward extensions, then set extensions to an empty list.
                // We will forward the extensions unless one of two conditions
                // occur: (1) The jdk.tls.stapling.ignoreExtensions property is
                // true or (2) There is a non-empty ResponderId list.
                // ResponderId selection is a feature that will be
                // supported in the future.
                extsToSend = (ignoreExtensions || !responderIds.isEmpty()) ?
                        Collections.emptyList() : extensions;

                byte[] respBytes = OCSP.getOCSPBytes(
                        Collections.singletonList(statInfo.cid),
                        statInfo.responder, extsToSend);

                if (respBytes != null) {
                    // Place the data into the response cache
                    cacheEntry = new ResponseCacheEntry(respBytes,
                            statInfo.cid);

                    // Get the response status and act on it appropriately
                    debugLog("OCSP Status: " + cacheEntry.status +
                            " (" + respBytes.length + " bytes)");
                    if (cacheEntry.status ==
                            OCSPResponse.ResponseStatus.SUCCESSFUL) {
                        // Set the response in the returned StatusInfo
                        statInfo.responseData = cacheEntry;

                        // Add the response to the cache (if applicable)
                        addToCache(statInfo.cid, cacheEntry);
                    }
                } else {
                    debugLog("No data returned from OCSP Responder");
                }
            } catch (IOException ioe) {
                debugLog("Caught exception: " + ioe);
            }

            return statInfo;
        }

        /**
         * Add a response to the cache.
         *
         * @param certId The {@code CertId} for the OCSP response
         * @param entry A cache entry containing the response bytes and
         *      the {@code OCSPResponse} built from those bytes.
         */
        private void addToCache(CertId certId, ResponseCacheEntry entry) {
            // If no cache lifetime has been set on entries then
            // don't cache this response if there is no nextUpdate field
            if (entry.nextUpdate == null && cacheLifetime == 0) {
                debugLog("Not caching this OCSP response");
            } else {
                responseCache.put(certId, entry);
                debugLog("Added response for SN " + certId.getSerialNumber() +
                        " to cache");
            }
        }

        /**
         * Determine the delay to use when scheduling the task that will
         * update the OCSP response.  This is the shorter time between the
         * cache lifetime and the nextUpdate.  If no nextUpdate is present in
         * the response, then only the cache lifetime is used.
         * If cache timeouts are disabled (a zero value) and there's no
         * nextUpdate, then the entry is not cached and no rescheduling will
         * take place.
         *
         * @param nextUpdate a {@code Date} object corresponding to the
         *      next update time from a SingleResponse.
         *
         * @return the number of seconds of delay before the next fetch
         *      should be executed.  A zero value means that the fetch
         *      should happen immediately, while a value less than zero
         *      indicates no rescheduling should be done.
         */
        private long getNextTaskDelay(Date nextUpdate) {
            long delaySec;
            int lifetime = getCacheLifetime();

            if (nextUpdate != null) {
                long nuDiffSec = (nextUpdate.getTime() -
                        System.currentTimeMillis()) / 1000;
                delaySec = lifetime > 0 ? Long.min(nuDiffSec, lifetime) :
                        nuDiffSec;
            } else {
                delaySec = lifetime > 0 ? lifetime : -1;
            }

            return delaySec;
        }
    }
}
