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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.nio.ByteBuffer;

/**
 * @author Jitendra Kotamraju
 */
abstract class MIMEEvent {

    enum EVENT_TYPE {START_MESSAGE, START_PART, HEADERS, CONTENT, END_PART, END_MESSAGE}

    /**
     * Returns a event for parser's current cursor location in the MIME message.
     *
     * <p>
     * {@link EVENT_TYPE#START_MESSAGE} and {@link EVENT_TYPE#START_MESSAGE} events
     * are generated only once.
     *
     * <p>
     * {@link EVENT_TYPE#START_PART}, {@link EVENT_TYPE#END_PART}, {@link EVENT_TYPE#HEADERS}
     * events are generated only once for each attachment part.
     *
     * <p>
     * {@link EVENT_TYPE#CONTENT} event may be generated more than once for an attachment
     * part.
     *
     * @return event type
     */
    abstract EVENT_TYPE getEventType();

    static final StartMessage START_MESSAGE = new StartMessage();
    static final StartPart START_PART = new StartPart();
    static final EndPart END_PART = new EndPart();
    static final EndMessage END_MESSAGE = new EndMessage();

    static final class StartMessage extends MIMEEvent {
        EVENT_TYPE getEventType() {
            return EVENT_TYPE.START_MESSAGE;
        }
    }

    static final class StartPart extends MIMEEvent {
        EVENT_TYPE getEventType() {
            return EVENT_TYPE.START_PART;
        }
    }

    static final class EndPart extends MIMEEvent {
        EVENT_TYPE getEventType () {
            return EVENT_TYPE.END_PART;
        }
    }

    static final class Headers extends MIMEEvent {
        InternetHeaders ih;

        Headers(InternetHeaders ih) {
            this.ih = ih;
        }

        EVENT_TYPE getEventType() {
            return EVENT_TYPE.HEADERS;
        }

        InternetHeaders getHeaders() {
            return ih;
        }
    }

    static final class Content extends MIMEEvent {
        private final ByteBuffer buf;

        Content(ByteBuffer buf) {
            this.buf = buf;
        }

        EVENT_TYPE getEventType() {
            return EVENT_TYPE.CONTENT;
        }

        ByteBuffer getData() {
            return buf;
        }
    }

    static final class EndMessage extends MIMEEvent {
        EVENT_TYPE getEventType() {
            return EVENT_TYPE.END_MESSAGE;
        }
    }

}
