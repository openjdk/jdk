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

package com.sun.xml.internal.ws.dump;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Marek Potociar <marek.potociar at sun.com>
 */
final class MessageDumper {

    static enum MessageType {
        Request("Request message"),
        Response("Response message"),
        Exception("Response exception");

        private final String name;

        private MessageType(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static enum ProcessingState {
        Received("received"),
        Processed("processed");

        private final String name;

        private ProcessingState(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }


    private final String tubeName;
    private final Logger logger;
    private Level loggingLevel;


    public MessageDumper(String tubeName, Logger logger, Level loggingLevel) {
        this.tubeName = tubeName;
        this.logger = logger;
        this.loggingLevel = loggingLevel;
    }

    final boolean isLoggable() {
        return logger.isLoggable(loggingLevel);
    }

    final void setLoggingLevel(Level level) {
        this.loggingLevel = level;
    }

    final String createLogMessage(MessageType messageType, ProcessingState processingState, int tubeId, String engineId, String message) {
        return String.format("%s %s in Tube [ %s ] Instance [ %d ] Engine [ %s ] Thread [ %s ]:%n%s",
                messageType,
                processingState,
                tubeName,
                tubeId,
                engineId,
                Thread.currentThread().getName(),
                message);
    }

    final String dump(MessageType messageType, ProcessingState processingState, String message, int tubeId, String engineId) {
        String logMessage = createLogMessage(messageType, processingState, tubeId, engineId, message);
        logger.log(loggingLevel, logMessage);

        return logMessage;
    }
}
