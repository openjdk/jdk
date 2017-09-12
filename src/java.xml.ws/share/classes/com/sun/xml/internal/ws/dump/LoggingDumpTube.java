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

import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.commons.xmlutil.Converter;
import com.sun.xml.internal.ws.dump.MessageDumper.ProcessingState;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Marek Potociar <marek.potociar at sun.com>
 */
public class LoggingDumpTube extends AbstractFilterTubeImpl {
    public static enum Position {
        Before(MessageDumper.ProcessingState.Received, MessageDumper.ProcessingState.Processed),
        After(MessageDumper.ProcessingState.Processed, MessageDumper.ProcessingState.Received);

        private final MessageDumper.ProcessingState requestState;
        private final MessageDumper.ProcessingState responseState;

        private Position(ProcessingState requestState, ProcessingState responseState) {
            this.requestState = requestState;
            this.responseState = responseState;
        }
    }

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
    //
    private MessageDumper messageDumper;
    private final Level loggingLevel;
    private final Position position;
    private final int tubeId;

    public LoggingDumpTube(Level loggingLevel, Position position, Tube tubelineHead) {
        super(tubelineHead);

        this.position = position;
        this.loggingLevel = loggingLevel;

        this.tubeId = ID_GENERATOR.incrementAndGet();
    }

    public void setLoggedTubeName(String loggedTubeName) {
        assert messageDumper == null; // must not set a new message dumper once already set
        this.messageDumper = new MessageDumper(loggedTubeName, Logger.getLogger(loggedTubeName), loggingLevel);
    }

    /**
     * Copy constructor.
     */
    private LoggingDumpTube(LoggingDumpTube original, TubeCloner cloner) {
        super(original, cloner);

        this.messageDumper = original.messageDumper;
        this.loggingLevel = original.loggingLevel;
        this.position = original.position;

        this.tubeId = ID_GENERATOR.incrementAndGet();
    }

    public LoggingDumpTube copy(TubeCloner cloner) {
        return new LoggingDumpTube(this, cloner);
    }


    @Override
    public NextAction processRequest(Packet request) {
        if (messageDumper.isLoggable()) {
            Packet dumpPacket = (request != null) ? request.copy(true) : null;
            messageDumper.dump(MessageDumper.MessageType.Request, position.requestState, Converter.toString(dumpPacket), tubeId, Fiber.current().owner.id);
        }

        return super.processRequest(request);
    }

    @Override
    public NextAction processResponse(Packet response) {
        if (messageDumper.isLoggable()) {
            Packet dumpPacket = (response != null) ? response.copy(true) : null;
            messageDumper.dump(MessageDumper.MessageType.Response, position.responseState, Converter.toString(dumpPacket), tubeId, Fiber.current().owner.id);
        }

        return super.processResponse(response);
    }

    @Override
    public NextAction processException(Throwable t) {
        if (messageDumper.isLoggable()) {
            messageDumper.dump(MessageDumper.MessageType.Exception, position.responseState, Converter.toString(t), tubeId, Fiber.current().owner.id);
        }

        return super.processException(t);
    }

    @Override
    public void preDestroy() {
        super.preDestroy();
    }
}
