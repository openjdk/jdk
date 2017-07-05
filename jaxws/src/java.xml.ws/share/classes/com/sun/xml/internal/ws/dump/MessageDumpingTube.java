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
import com.sun.xml.internal.ws.dump.MessageDumper.MessageType;
import com.sun.xml.internal.ws.dump.MessageDumper.ProcessingState;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
final class MessageDumpingTube extends AbstractFilterTubeImpl {
    static final String DEFAULT_MSGDUMP_LOGGING_ROOT = com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".messagedump";
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
    //
    private final MessageDumper messageDumper;
    private final int tubeId;
    //
    private final MessageDumpingFeature messageDumpingFeature;
    /**
     * @param name
     *      Specify the name that identifies this {@link MessageDumpingTube}
     *      instance. This string will be printed when this pipe
     *      dumps messages, and allows people to distinguish which
     *      pipe instance is dumping a message when multiple
     *      {@link com.sun.xml.internal.ws.util.pipe.DumpTube}s print messages out.
     * @param out
     *      The output to send dumps to.
     * @param next
     *      The next {@link com.sun.xml.internal.ws.api.pipe.Tube} in the pipeline.
     */
    MessageDumpingTube(Tube next, MessageDumpingFeature feature) {
        super(next);

        this.messageDumpingFeature = feature;
        this.tubeId = ID_GENERATOR.incrementAndGet();
        this.messageDumper = new MessageDumper(
                "MesageDumpingTube",
                java.util.logging.Logger.getLogger(feature.getMessageLoggingRoot()),
                feature.getMessageLoggingLevel());
    }

    /**
     * Copy constructor.
     */
    MessageDumpingTube(MessageDumpingTube that, TubeCloner cloner) {
        super(that, cloner);


        this.messageDumpingFeature = that.messageDumpingFeature;
        this.tubeId = ID_GENERATOR.incrementAndGet();
        this.messageDumper = that.messageDumper;
    }

    public MessageDumpingTube copy(TubeCloner cloner) {
        return new MessageDumpingTube(this, cloner);
    }

    @Override
    public NextAction processRequest(Packet request) {
        dump(MessageType.Request, Converter.toString(request), Fiber.current().owner.id);
        return super.processRequest(request);
    }

    @Override
    public NextAction processResponse(Packet response) {
        dump(MessageType.Response, Converter.toString(response), Fiber.current().owner.id);
        return super.processResponse(response);
    }

    @Override
    public NextAction processException(Throwable t) {
        dump(MessageType.Exception, Converter.toString(t), Fiber.current().owner.id);

        return super.processException(t);
    }

    protected final void dump(MessageType messageType, String message, String engineId) {
        String logMessage;
        if (messageDumpingFeature.getMessageLoggingStatus()) {
            messageDumper.setLoggingLevel(messageDumpingFeature.getMessageLoggingLevel());
            logMessage = messageDumper.dump(messageType, ProcessingState.Received, message, tubeId, engineId);
        } else {
            logMessage = messageDumper.createLogMessage(messageType, ProcessingState.Received, tubeId, engineId, message);
        }
        messageDumpingFeature.offerMessage(logMessage);
    }
}
