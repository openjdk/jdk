/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.httpserver;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.security.PrivilegedAction;

/**
 * Parameters that users will not likely need to set
 * but are useful for debugging
 */

@SuppressWarnings("removal")
class ServerConfig {

    private static final int DEFAULT_IDLE_TIMER_SCHEDULE_MILLIS = 10000 ; // 10 sec.

    private static final long DEFAULT_IDLE_INTERVAL_IN_SECS = 30;
    private static final int DEFAULT_MAX_CONNECTIONS = -1 ; // no limit on maximum connections
    private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 200 ;

    private static final long DEFAULT_MAX_REQ_TIME = -1; // default: forever
    private static final long DEFAULT_MAX_RSP_TIME = -1; // default: forever
    // default timer schedule, in milli seconds, for the timer task that's responsible for
    // timing out request/response if max request/response time is configured
    private static final long DEFAULT_REQ_RSP_TIMER_TASK_SCHEDULE_MILLIS = 1000;
    private static final int  DEFAULT_MAX_REQ_HEADERS = 200;
    private static final int  DEFAULT_MAX_REQ_HEADER_SIZE = 380 * 1024;
    private static final long DEFAULT_DRAIN_AMOUNT = 64 * 1024;

    private static long idleTimerScheduleMillis;
    private static long idleIntervalMillis;
    // The maximum number of bytes to drain from an inputstream
    private static long drainAmount;
    // the maximum number of connections that the server will allow to be open
    // after which it will no longer "accept()" any new connections, till the
    // current connection count goes down due to completion of processing the requests
    private static int maxConnections;
    private static int maxIdleConnections;
    // The maximum number of request headers allowable
    private static int maxReqHeaders;
    // a maximum value for the header list size. This is the
    // names size + values size + 32 bytes per field line
    private static int maxReqHeadersSize;
    // max time a request or response is allowed to take
    private static long maxReqTime;
    private static long maxRspTime;
    private static long reqRspTimerScheduleMillis;
    private static boolean debug;

    // the value of the TCP_NODELAY socket-level option
    private static boolean noDelay;

    static {
        java.security.AccessController.doPrivileged(
            new PrivilegedAction<Void>() {
                @Override
                public Void run () {
                    idleIntervalMillis = Long.getLong("sun.net.httpserver.idleInterval",
                            DEFAULT_IDLE_INTERVAL_IN_SECS) * 1000;
                    if (idleIntervalMillis <= 0) {
                        idleIntervalMillis = DEFAULT_IDLE_INTERVAL_IN_SECS * 1000;
                    }

                    idleTimerScheduleMillis = Long.getLong("sun.net.httpserver.clockTick",
                            DEFAULT_IDLE_TIMER_SCHEDULE_MILLIS);
                    if (idleTimerScheduleMillis <= 0) {
                        // ignore zero or negative value and use the default schedule
                        idleTimerScheduleMillis = DEFAULT_IDLE_TIMER_SCHEDULE_MILLIS;
                    }

                    maxConnections = Integer.getInteger(
                            "jdk.httpserver.maxConnections",
                            DEFAULT_MAX_CONNECTIONS);

                    maxIdleConnections = Integer.getInteger(
                            "sun.net.httpserver.maxIdleConnections",
                            DEFAULT_MAX_IDLE_CONNECTIONS);

                    drainAmount = Long.getLong("sun.net.httpserver.drainAmount",
                            DEFAULT_DRAIN_AMOUNT);

                    maxReqHeaders = Integer.getInteger(
                            "sun.net.httpserver.maxReqHeaders",
                            DEFAULT_MAX_REQ_HEADERS);
                    if (maxReqHeaders <= 0) {
                        maxReqHeaders = DEFAULT_MAX_REQ_HEADERS;
                    }

                    // a value <= 0 means unlimited
                    maxReqHeadersSize = Integer.getInteger(
                            "sun.net.httpserver.maxReqHeaderSize",
                            DEFAULT_MAX_REQ_HEADER_SIZE);
                    if (maxReqHeadersSize <= 0) {
                        maxReqHeadersSize = 0;
                    }

                    maxReqTime = Long.getLong("sun.net.httpserver.maxReqTime",
                            DEFAULT_MAX_REQ_TIME);

                    maxRspTime = Long.getLong("sun.net.httpserver.maxRspTime",
                            DEFAULT_MAX_RSP_TIME);

                    reqRspTimerScheduleMillis = Long.getLong("sun.net.httpserver.timerMillis",
                            DEFAULT_REQ_RSP_TIMER_TASK_SCHEDULE_MILLIS);
                    if (reqRspTimerScheduleMillis <= 0) {
                        // ignore any negative or zero value for this configuration and reset
                        // to default schedule
                        reqRspTimerScheduleMillis = DEFAULT_REQ_RSP_TIMER_TASK_SCHEDULE_MILLIS;
                    }

                    debug = Boolean.getBoolean("sun.net.httpserver.debug");

                    noDelay = Boolean.getBoolean("sun.net.httpserver.nodelay");

                    return null;
                }
            });

    }

    static void checkLegacyProperties(final Logger logger) {

        // legacy properties that are no longer used
        // print a warning to logger if they are set.

        java.security.AccessController.doPrivileged(
            new PrivilegedAction<Void>() {
                public Void run () {
                    if (System.getProperty("sun.net.httpserver.readTimeout")
                                                !=null)
                    {
                        logger.log (Level.WARNING,
                            "sun.net.httpserver.readTimeout "+
                            "property is no longer used. "+
                            "Use sun.net.httpserver.maxReqTime instead."
                        );
                    }
                    if (System.getProperty("sun.net.httpserver.writeTimeout")
                                                !=null)
                    {
                        logger.log (Level.WARNING,
                            "sun.net.httpserver.writeTimeout "+
                            "property is no longer used. Use "+
                            "sun.net.httpserver.maxRspTime instead."
                        );
                    }
                    if (System.getProperty("sun.net.httpserver.selCacheTimeout")
                                                !=null)
                    {
                        logger.log (Level.WARNING,
                            "sun.net.httpserver.selCacheTimeout "+
                            "property is no longer used."
                        );
                    }
                    return null;
                }
            }
        );
    }

    static boolean debugEnabled() {
        return debug;
    }

    /**
     * {@return Returns the maximum duration, in milli seconds, a connection can be idle}
     */
    static long getIdleIntervalMillis() {
        return idleIntervalMillis;
    }

    /**
     * {@return Returns the schedule, in milli seconds, for the timer task that is responsible
     * for managing the idle connections}
     */
    static long getIdleTimerScheduleMillis() {
        return idleTimerScheduleMillis;
    }

    /**
     * @return Returns the maximum number of connections that can be open at any given time.
     * This method can return a value of 0 or negative to represent that the limit hasn't
     * been configured.
     */
    static int getMaxConnections() {
        return maxConnections;
    }

    /**
     * @return Returns the maximum number of connections that can be idle. This method
     * can return a value of 0 or negative.
     */
    static int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    static long getDrainAmount() {
        return drainAmount;
    }

    static int getMaxReqHeaders() {
        return maxReqHeaders;
    }

    static int getMaxReqHeaderSize() {
        return maxReqHeadersSize;
    }

    /**
     * @return Returns the maximum amount of time the server will wait for the request to be read
     * completely. This method can return a value of 0 or negative to imply no maximum limit has
     * been configured.
     */
    static long getMaxReqTime() {
        return maxReqTime;
    }

    /**
     * @return Returns the maximum amount of time the server will wait for the response to be generated
     * for a request that is being processed. This method can return a value of 0 or negative to
     * imply no maximum limit has been configured.
     */
    static long getMaxRspTime() {
        return maxRspTime;
    }

    /**
     * {@return Returns the timer schedule of the task that's responsible for timing out
     * request/response that have been running longer than any configured timeout}
     */
    static long getReqRspTimerScheduleMillis() {
        return reqRspTimerScheduleMillis;
    }

    static boolean noDelay() {
        return noDelay;
    }
}
