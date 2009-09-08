/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;

/**
 * Represents MIME message. MIME message parsing is done lazily using a
 * pull parser.
 *
 * @author Jitendra Kotamraju
 */
public class MIMEMessage {
    private static final Logger LOGGER = Logger.getLogger(MIMEMessage.class.getName());

    MIMEConfig config;

    private final InputStream in;
    private final List<MIMEPart> partsList;
    private final Map<String, MIMEPart> partsMap;
    private final Iterator<MIMEEvent> it;
    private boolean parsed;     // true when entire message is parsed
    private MIMEPart currentPart;
    private int currentIndex;

    /**
     * @see MIMEMessage(InputStream, String, MIMEConfig)
     */
    public MIMEMessage(InputStream in, String boundary) {
        this(in, boundary, new MIMEConfig());
    }

    /**
     * Creates a MIME message from the content's stream. The content stream
     * is closed when EOF is reached.
     *
     * @param in MIME message stream
     * @param boundary the separator for parts(pass it without --)
     * @param config various configuration parameters
     */
    public MIMEMessage(InputStream in, String boundary, MIMEConfig config) {
        this.in = in;
        this.config = config;
        MIMEParser parser = new MIMEParser(in, boundary, config);
        it = parser.iterator();

        partsList = new ArrayList<MIMEPart>();
        partsMap = new HashMap<String, MIMEPart>();
        if (config.isParseEagerly()) {
            parseAll();
        }
    }

    /**
     * Gets all the attachments by parsing the entire MIME message. Avoid
     * this if possible since it is an expensive operation.
     *
     * @return list of attachments.
     */
    public List<MIMEPart> getAttachments() {
        if (!parsed) {
            parseAll();
        }
        return partsList;
    }

    /**
     * Creates nth attachment lazily. It doesn't validate
     * if the message has so many attachments. To
     * do the validation, the message needs to be parsed.
     * The parsing of the message is done lazily and is done
     * while reading the bytes of the part.
     *
     * @param index sequential order of the part. starts with zero.
     * @return attachemnt part
     */
    public MIMEPart getPart(int index) {
        LOGGER.fine("index="+index);
        MIMEPart part = (index < partsList.size()) ? partsList.get(index) : null;
        if (parsed && part == null) {
            throw new MIMEParsingException("There is no "+index+" attachment part ");
        }
        if (part == null) {
            // Parsing will done lazily and will be driven by reading the part
            part = new MIMEPart(this);
            partsList.add(index, part);
        }
        LOGGER.fine("Got attachment at index="+index+" attachment="+part);
        return part;
    }

    /**
     * Creates a lazy attachment for a given Content-ID. It doesn't validate
     * if the message contains an attachment with the given Content-ID. To
     * do the validation, the message needs to be parsed. The parsing of the
     * message is done lazily and is done while reading the bytes of the part.
     *
     * @param contentId Content-ID of the part, expects Content-ID without <, >
     * @return attachemnt part
     */
    public MIMEPart getPart(String contentId) {
        LOGGER.fine("Content-ID="+contentId);
        MIMEPart part = getDecodedCidPart(contentId);
        if (parsed && part == null) {
            throw new MIMEParsingException("There is no attachment part with Content-ID = "+contentId);
        }
        if (part == null) {
            // Parsing is done lazily and is driven by reading the part
            part = new MIMEPart(this, contentId);
            partsMap.put(contentId, part);
        }
        LOGGER.fine("Got attachment for Content-ID="+contentId+" attachment="+part);
        return part;
    }

    // this is required for Indigo interop, it writes content-id without escaping
    private MIMEPart getDecodedCidPart(String cid) {
        MIMEPart part = partsMap.get(cid);
        if (part == null) {
            if (cid.indexOf('%') != -1) {
                try {
                    String tempCid = URLDecoder.decode(cid, "utf-8");
                    part = partsMap.get(tempCid);
                } catch(UnsupportedEncodingException ue) {
                    // Ignore it
                }
            }
        }
        return part;
    }


    /**
     * Parses the whole MIME message eagerly
     */
    public void parseAll() {
        while(makeProgress()) {
            // Nothing to do
        }
    }


    /**
     * Parses the MIME message in a pull fashion.
     *
     * @return
     *      false if the parsing is completed.
     */
    public synchronized boolean makeProgress() {
        if (!it.hasNext()) {
            return false;
        }

        MIMEEvent event = it.next();

        switch(event.getEventType()) {
            case START_MESSAGE :
                LOGGER.fine("MIMEEvent="+MIMEEvent.EVENT_TYPE.START_MESSAGE);
                break;

            case START_PART :
                LOGGER.fine("MIMEEvent="+MIMEEvent.EVENT_TYPE.START_PART);
                break;

            case HEADERS :
                LOGGER.fine("MIMEEvent="+MIMEEvent.EVENT_TYPE.HEADERS);
                MIMEEvent.Headers headers = (MIMEEvent.Headers)event;
                InternetHeaders ih = headers.getHeaders();
                List<String> cids = ih.getHeader("content-id");
                String cid = (cids != null) ? cids.get(0) : currentIndex+"";
                if (cid.length() > 2 && cid.charAt(0)=='<') {
                    cid = cid.substring(1,cid.length()-1);
                }
                MIMEPart listPart = (currentIndex < partsList.size()) ? partsList.get(currentIndex) : null;
                MIMEPart mapPart = getDecodedCidPart(cid);
                if (listPart == null && mapPart == null) {
                    currentPart = getPart(cid);
                    partsList.add(currentIndex, currentPart);
                } else if (listPart == null) {
                    currentPart = mapPart;
                    partsList.add(currentIndex, mapPart);
                } else if (mapPart == null) {
                    currentPart = listPart;
                    currentPart.setContentId(cid);
                    partsMap.put(cid, currentPart);
                } else if (listPart != mapPart) {
                    throw new MIMEParsingException("Created two different attachments using Content-ID and index");
                }
                currentPart.setHeaders(ih);
                break;

            case CONTENT :
                LOGGER.finer("MIMEEvent="+MIMEEvent.EVENT_TYPE.CONTENT);
                MIMEEvent.Content content = (MIMEEvent.Content)event;
                ByteBuffer buf = content.getData();
                currentPart.addBody(buf);
                break;

            case END_PART :
                LOGGER.fine("MIMEEvent="+MIMEEvent.EVENT_TYPE.END_PART);
                currentPart.doneParsing();
                ++currentIndex;
                break;

            case END_MESSAGE :
                LOGGER.fine("MIMEEvent="+MIMEEvent.EVENT_TYPE.END_MESSAGE);
                parsed = true;
                try {
                    in.close();
                } catch(IOException ioe) {
                    throw new MIMEParsingException(ioe);
                }
                break;

            default :
                throw new MIMEParsingException("Unknown Parser state = "+event.getEventType());
        }
        return true;
    }
}
