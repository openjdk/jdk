/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.dump;

import com.sun.xml.internal.ws.api.FeatureConstructor;
import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedData;

import javax.xml.ws.WebServiceFeature;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
@ManagedData
public final class MessageDumpingFeature extends WebServiceFeature {

    public static final String ID = "com.sun.xml.internal.ws.messagedump.MessageDumpingFeature";
    //
    private static final Level DEFAULT_MSG_LOG_LEVEL = Level.FINE;
    //
    private final Queue<String> messageQueue;
    private final AtomicBoolean messageLoggingStatus;
    private final String messageLoggingRoot;
    private final Level messageLoggingLevel;

    public MessageDumpingFeature() {
        this(null, null, true);
    }

    public MessageDumpingFeature(String msgLogRoot, Level msgLogLevel, boolean storeMessages) {
        this.messageQueue =  (storeMessages) ? new java.util.concurrent.ConcurrentLinkedQueue<String>() : null;
        this.messageLoggingStatus = new AtomicBoolean(true);
        this.messageLoggingRoot = (msgLogRoot != null && msgLogRoot.length() > 0) ? msgLogRoot : MessageDumpingTube.DEFAULT_MSGDUMP_LOGGING_ROOT;
        this.messageLoggingLevel = (msgLogLevel != null) ? msgLogLevel : DEFAULT_MSG_LOG_LEVEL;

        super.enabled = true;
    }

    public MessageDumpingFeature(boolean enabled) {
        // this constructor is here just to satisfy JAX-WS specification requirements
        this();
        super.enabled = enabled;
    }

    @FeatureConstructor({"enabled", "messageLoggingRoot", "messageLoggingLevel", "storeMessages"})
    public MessageDumpingFeature(boolean enabled, String msgLogRoot, String msgLogLevel, boolean storeMessages) {
        // this constructor is here just to satisfy JAX-WS specification requirements
        this(msgLogRoot, Level.parse(msgLogLevel), storeMessages);

        super.enabled = enabled;
    }

    @Override
    @ManagedAttribute
    public String getID() {
        return ID;
    }

    public String nextMessage() {
        return (messageQueue != null) ? messageQueue.poll() : null;
    }

    public void enableMessageLogging() {
        messageLoggingStatus.set(true);
    }

    public void disableMessageLogging() {
        messageLoggingStatus.set(false);
    }

    @ManagedAttribute
    public boolean getMessageLoggingStatus() {
        return messageLoggingStatus.get();
    }

    @ManagedAttribute
    public String getMessageLoggingRoot() {
        return messageLoggingRoot;
    }

    @ManagedAttribute
    public Level getMessageLoggingLevel() {
        return messageLoggingLevel;
    }

    boolean offerMessage(String message) {
        return (messageQueue != null) ? messageQueue.offer(message) : false;
    }
}
