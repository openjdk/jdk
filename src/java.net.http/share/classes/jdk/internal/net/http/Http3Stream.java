/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ProtocolException;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.common.ValidatingHeadersConsumer;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.DataFrame;
import jdk.internal.net.http.http3.frames.FramesDecoder;
import jdk.internal.net.http.http3.frames.HeadersFrame;
import jdk.internal.net.http.http3.frames.Http3Frame;
import jdk.internal.net.http.http3.frames.Http3FrameType;
import jdk.internal.net.http.http3.frames.MalformedFrame;
import jdk.internal.net.http.http3.frames.PartialFrame;
import jdk.internal.net.http.http3.frames.PushPromiseFrame;
import jdk.internal.net.http.http3.frames.UnknownFrame;
import jdk.internal.net.http.qpack.Decoder;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.quic.streams.QuicStreamReader;

import static jdk.internal.net.http.Exchange.MAX_NON_FINAL_RESPONSES;
import static jdk.internal.net.http.RedirectFilter.HTTP_NOT_MODIFIED;
import static jdk.internal.net.http.common.Utils.readContentLength;
import static jdk.internal.net.http.common.Utils.readStatusCode;

/**
 * A common super class for the HTTP/3 request/response stream ({@link Http3ExchangeImpl}
 * and the HTTP/3 push promises stream ({@link Http3PushPromiseStream}.
 * @param <T> the expected type of the response body
 */
sealed abstract class Http3Stream<T> extends ExchangeImpl<T> permits Http3ExchangeImpl, Http3PushPromiseStream {
    enum ResponseState { PERMIT_HEADER, PERMIT_TRAILER, PERMIT_NONE }

    // count of bytes read from the Quic stream. This is weakly consistent and
    // used for debug only. Must not be updated outside of processQuicData
    private volatile long receivedQuicBytes;
    // keep track of which HTTP/3 frames have been parsed and whether more header
    // frames are permitted
    private ResponseState responseState = ResponseState.PERMIT_HEADER;
    // value of content-length header in the response header, or null
    private Long contentLength;
    // number of data bytes delivered to user subscriber
    private long consumedDataBytes;
    // switched to true if reading from the quic stream should be temporarily
    // paused. After switching back to false, readScheduler.runOrSchedule() should
    // called.
    private volatile boolean readingPaused;

    // A temporary buffer for response body bytes
    final ConcurrentLinkedQueue<List<ByteBuffer>> responseData = new ConcurrentLinkedQueue<>();

    private final AtomicInteger nonFinalResponseCount = new AtomicInteger();


    Http3Stream(Exchange<T> exchange) {
        super(exchange);
    }

    /**
     * Cancel the stream exchange on error
     * @param throwable an exception to be relayed to the multi exchange
     *                  through the completable future chain
     * @param error an HTTP/3 error
     */
    abstract void cancelImpl(Throwable throwable, Http3Error error);

    /**
     * {@return the Quic stream id for this exchange (request/response or push response)}
     */
    abstract long streamId();

    /**
     * A base class implementing {@link DecodingCallback} used for receiving
     * and building HttpHeaders. Can be used for request headers, response headers,
     * push response headers, or trailers.
     */
    abstract class StreamHeadersConsumer extends ValidatingHeadersConsumer
            implements DecodingCallback {

        private volatile boolean hasError;

        StreamHeadersConsumer(Context context) {
            super(context);
        }

        abstract Decoder qpackDecoder();

        abstract HeaderFrameReader headerFrameReader();

        abstract HttpHeadersBuilder headersBuilder();

        abstract void resetDone();

        @Override
        public void reset() {
            super.reset();
            headerFrameReader().reset();
            headersBuilder().clear();
            hasError = false;
            resetDone();
        }

        String headerFieldType() {return "HEADER FIELD";}

        @Override
        public void onDecoded(CharSequence name, CharSequence value) {
            try {
                String n = name.toString();
                String v = value.toString();
                super.onDecoded(n, v);
                headersBuilder().addHeader(n, v);
                if (Log.headers() && Log.trace()) {
                    Log.logTrace("RECEIVED {0} (streamid={1}): {2}: {3}",
                            headerFieldType(), streamId(), n, v);
                }
            } catch (Throwable throwable) {
                if (throwable instanceof UncheckedIOException uio) {
                    // UncheckedIOException is thrown by ValidatingHeadersConsumer.onDecoded
                    // for cases with invalid headers or unknown/unsupported pseudo-headers.
                    // It should be treated as a malformed request.
                    // RFC-9114 4.1.2.  Malformed Requests and Responses:
                    //  Malformed requests or responses that are
                    //  detected MUST be treated as a stream error of
                    //  type H3_MESSAGE_ERROR.
                    onStreamError(uio.getCause(), Http3Error.H3_MESSAGE_ERROR);
                } else {
                    onConnectionError(throwable, Http3Error.H3_INTERNAL_ERROR);
                }
            }
        }

        @Override
        public void onComplete() {
            // RFC-9204 2.2.2.1: After the decoder finishes decoding a field
            // section encoded using representations containing dynamic table
            // references, it MUST emit a Section Acknowledgment instruction
            qpackDecoder().ackSection(streamId(), headerFrameReader());
            qpackDecoder().resetInsertionsCounter();
            headersCompleted();
        }

        abstract void headersCompleted();

        @Override
        public void onStreamError(Throwable throwable, Http3Error http3Error) {
            hasError = true;
            qpackDecoder().resetInsertionsCounter();
            // Stream error
            cancelImpl(throwable, http3Error);
        }

        @Override
        public void onConnectionError(Throwable throwable, Http3Error http3Error) {
            hasError = true;
            // Connection error
            connectionError(throwable, http3Error);
        }

        @Override
        public boolean hasError() {
            return hasError;
        }

    }

    /**
     * {@return count of bytes read from the QUIC stream so far}
     */
    public long receivedQuicBytes() {
        return receivedQuicBytes;
    }

    /**
     * Notify of a connection error.
     *
     * The implementation of this method is supposed to close all
     * exchanges, cancel all push promises, and close the connection.
     *
     * @implSpec
     *    The implementation of this method calls
     *    {@snippet lang=java :
     *    connectionError(throwable, error.code(), throwable.getMessage());
     *    }
     *
     * @param throwable an exception to be relayed to the multi exchange
     *                  through the completable future chain
     * @param error an HTTP/3 error
     */
    void connectionError(Throwable throwable, Http3Error error) {
        connectionError(throwable, error.code(), throwable.getMessage());
    }


    /**
     * Notify of a connection error.
     *
     * The implementation of this method is supposed to close all
     * exchanges, cancel all push promises, and close the connection.
     *
     * @param throwable an exception to be relayed to the multi exchange
     *                  through the completable future chain
     * @param errorCode an HTTP/3 error code
     * @param errMsg an error message to be logged when closing the connection
     */
    abstract void connectionError(Throwable throwable, long errorCode, String errMsg);


    /**
     * Push response data to the {@linkplain java.net.http.HttpResponse.BodySubscriber
     * response body subscriber} if allowed by the subscription state.
     * @param responseData a queue of available data to be pushed to the subscriber
     */
    abstract void pushResponseData(ConcurrentLinkedQueue<List<ByteBuffer>> responseData);

    /**
     * Called when an exception is thrown by {@link QuicStreamReader#poll() reader::poll}
     * when called from {@link #processQuicData(QuicStreamReader, FramesDecoder,
     * H3FrameOrderVerifier, SequentialScheduler, Logger) processQuicData}.
     * This is typically only used for logging purposes.
     * @param reader the stream reader
     * @param io the exception caught
     */
    abstract void onPollException(QuicStreamReader reader, IOException io);

    /**
     * Called when new payload data is received by {@link #processQuicData(QuicStreamReader,
     * FramesDecoder, H3FrameOrderVerifier, SequentialScheduler, Logger) processQuicData}
     * for a given header frame.
     * <p>
     * Any exception thrown here will be rethrown by {@code processQuicData}
     *
     * @param headers a partially received header frame
     * @param payload the payload bytes available for that frame
     * @throws IOException if an error is detected
     */
    abstract void receiveHeaders(HeadersFrame headers, List<ByteBuffer> payload) throws IOException;

    /**
     * Called when new payload data is received by {@link #processQuicData(QuicStreamReader,
     * FramesDecoder, H3FrameOrderVerifier, SequentialScheduler, Logger) processQuicData}
     * for a given push promise frame.
     * <p>
     * Any exception thrown here will be rethrown by {@code processQuicData}
     *
     * @param ppf a partially received push promise frame
     * @param payload the payload bytes available for that frame
     * @throws IOException if an error is detected
     */
    abstract void receivePushPromiseFrame(PushPromiseFrame ppf, List<ByteBuffer> payload) throws IOException;

    /**
     * {@return whether reading from the quic stream is currently paused}
     * Typically reading is paused when waiting for headers to be decoded by QPack.
     */
    boolean readingPaused() {return readingPaused;}

    /**
     * Switches the value of the {@link #readingPaused() readingPaused}
     * flag
     * <p>
     * Subclasses of {@code Http3Stream} can call this method to switch
     * the value of this flag if needed, typically in their
     * concrete implementation of {@link #receiveHeaders(HeadersFrame, List)}.
     * @param value the new value
     */
    void switchReadingPaused(boolean value) {
        readingPaused = value;
    }

    // invoked when ByteBuffers containing the next payload bytes for the
    // given partial data frame are received.
    private void receiveData(DataFrame data, List<ByteBuffer> payload, Logger debug) {
        if (debug.on()) {
            debug.log("receiveData: adding %s payload byte", Utils.remaining(payload));
        }
        responseData.add(payload);
        pushResponseData(responseData);
    }

    private ByteBuffer pollIfNotReset(QuicStreamReader reader) throws IOException {
        ByteBuffer buffer;
        try {
            if (reader.isReset()) return null;
            buffer = reader.poll();
        } catch (IOException io) {
            if (reader.isReset()) return null;
            onPollException(reader, io);
            throw io;
        }
        return buffer;
    }

    private Throwable toThrowable(MalformedFrame malformedFrame) {
        Throwable cause = malformedFrame.getCause();
        if (cause != null) return cause;
        return new ProtocolException(malformedFrame.toString());
    }

    /**
     * Called when {@code processQuicData} detects that the {@linkplain
     * QuicStreamReader reader} has been reset.
     * This method should do the appropriate garbage collection,
     * possibly closing the exchange or the connection if needed, and
     * closing the read scheduler.
     */
    abstract void onReaderReset();

    /**
     * Invoked when some data is received from the underlying quic stream.
     * This implements the read loop for a request-response stream or a
     * push response stream.
     */
     void processQuicData(QuicStreamReader reader,
                          FramesDecoder framesDecoder,
                          H3FrameOrderVerifier frameOrderVerifier,
                          SequentialScheduler readScheduler,
                          Logger debug) throws IOException {


         // Poll bytes from the request-response stream
         // and parses the data to read HTTP/3 frames.
         //
         // If the frame being read is a header frame, send the
         // compacted header field data to QPack.
         //
         // Otherwise, if it's a data frame, send the bytes
         // to the response body subscriber.
         //
         // Finally, if the frame being read is a PushPromiseFrame,
         // sends the compressed field data to the QPack decoder to
         // decode the push promise request headers.
         //

         // the reader might be null if the loop is triggered before
         // the field is assigned
         if (reader == null) return;

         // check whether we need to wait until response headers
         // have been decoded: in that case readingPaused will be true
         if (readingPaused) return;

         if (debug.on()) debug.log("processQuicData");
         ByteBuffer buffer;
         Http3Frame frame;
         pushResponseData(responseData);
         boolean readmore = responseData.isEmpty();
         // do not read more until data has been pulled
         while (readmore && (buffer = pollIfNotReset(reader)) != null) {
             if (debug.on())
                 debug.log("processQuicData - submitting buffer: %s bytes (ByteBuffer@%s)",
                         buffer.remaining(), System.identityHashCode(buffer));
             // only updated here
             var received = receivedQuicBytes;
             receivedQuicBytes = received + buffer.remaining();
             framesDecoder.submit(buffer);
             while ((frame = framesDecoder.poll()) != null) {
                 if (debug.on()) debug.log("processQuicData - frame: " + frame);
                 final long frameType = frame.type();
                 // before we start processing, verify that this frame *type* has arrived in the
                 // allowed order
                 if (!frameOrderVerifier.allowsProcessing(frame)) {
                     final String unexpectedFrameType = Http3FrameType.asString(frameType);
                     // not expected to be arriving now
                     // RFC-9114, section 4.1 - Receipt of an invalid sequence of frames MUST be
                     // treated as a connection error of type H3_FRAME_UNEXPECTED.
                     if (debug.on()) {
                         debug.log("unexpected (order of) frame type: "
                                 + unexpectedFrameType + " on stream");
                     }
                     Log.logError("Connection error due to unexpected (order of) frame type" +
                             " {0} on stream", unexpectedFrameType);
                     readScheduler.stop();
                     final String errMsg = "Unexpected frame " + unexpectedFrameType;
                     connectionError(new ProtocolException(errMsg), Http3Error.H3_FRAME_UNEXPECTED);
                     return;
                 }
                 if (frame instanceof PartialFrame partialFrame) {
                     final List<ByteBuffer> payload = framesDecoder.readPayloadBytes();
                     if (debug.on()) {
                         debug.log("processQuicData - payload: %s",
                                 payload == null ? null : Utils.remaining(payload));
                     }
                     if (framesDecoder.eof() && !framesDecoder.clean()) {
                         String msg = "Frame truncated: " + partialFrame;
                         connectionError(new ProtocolException(msg),
                                 Http3Error.H3_FRAME_ERROR.code(),
                                 msg);
                         break;
                     }
                     if ((payload == null || payload.isEmpty()) && partialFrame.remaining() != 0) {
                         break;
                     }
                     if (partialFrame instanceof HeadersFrame headers) {
                         receiveHeaders(headers, payload);
                         // check if we need to wait for the status code to be decoded
                         // before reading more
                         readmore = !readingPaused;
                     } else if (partialFrame instanceof DataFrame data) {
                         if (responseState != ResponseState.PERMIT_TRAILER) {
                             cancelImpl(new IOException("DATA frame not expected here"), Http3Error.H3_MESSAGE_ERROR);
                             return;
                         }
                         if (payload != null) {
                             consumedDataBytes += Utils.remaining(payload);
                             if (contentLength != null &&
                                     consumedDataBytes + data.remaining() > contentLength) {
                                 cancelImpl(new IOException(
                                         String.format("DATA frame (length %d) exceeds content-length (%d) by %d",
                                                 data.streamingLength(), contentLength,
                                                 consumedDataBytes + data.remaining() - contentLength)),
                                         Http3Error.H3_MESSAGE_ERROR);
                                 return;
                             }
                             // don't read more if there is pending data waiting
                             // to be read from downstream
                             readmore = responseData.isEmpty();
                             receiveData(data, payload, debug);
                         }
                     } else if (partialFrame instanceof PushPromiseFrame ppf) {
                         receivePushPromiseFrame(ppf, payload);
                     } else if (partialFrame instanceof UnknownFrame) {
                         if (debug.on()) {
                             debug.log("ignoring %s bytes for unknown frame type: %s",
                                     Utils.remaining(payload),
                                     Http3FrameType.asString(frameType));
                         }
                     } else {
                         // should never come here: the only frame that we can
                         //   receive on a request-response stream are
                         //   HEADERS, DATA, PUSH_PROMISE, and RESERVED/UNKNOWN
                         // All have already been taken care above.
                         // So this here should be dead-code.
                         String msg = "unhandled frame type: " +
                                 Http3FrameType.asString(frameType);
                         if (debug.on()) debug.log("Warning: %s", msg);
                         throw new AssertionError(msg);
                     }
                     // mark as complete, if all expected data has been read for a frame
                     if (partialFrame.remaining() == 0) {
                         frameOrderVerifier.completed(frame);
                     }
                 } else if (frame instanceof MalformedFrame malformed) {
                     var cause = malformed.getCause();
                     if (cause != null && debug.on()) {
                         debug.log(malformed.toString(), cause);
                     }
                     readScheduler.stop();
                     connectionError(toThrowable(malformed),
                             malformed.getErrorCode(),
                             malformed.getMessage());
                     return;
                 } else {
                     // should never come here: the only frame that we can
                     //   receive on a request-response stream are
                     //   HEADERS, DATA, PUSH_PROMISE, and RESERVED/UNKNOWN
                     //   All should have already been taken care above,
                     //   including malformed frames. So this here should be
                     //   dead-code.
                     String msg = "unhandled frame type: " +
                             Http3FrameType.asString(frameType);
                     if (debug.on()) debug.log("Warning: %s", msg);
                     throw new AssertionError(msg);
                 }
                 if (framesDecoder.eof()) break;
             }
             if (framesDecoder.eof()) break;
         }
         if (framesDecoder.eof()) {
             if (!framesDecoder.clean()) {
                 String msg = "EOF reading frame type and length";
                 connectionError(new ProtocolException(msg),
                         Http3Error.H3_FRAME_ERROR.code(),
                         msg);
             }
             if (debug.on()) debug.log("processQuicData - EOF");
             if (responseState == ResponseState.PERMIT_HEADER) {
                 cancelImpl(new EOFException("EOF reached: no header bytes received"), Http3Error.H3_MESSAGE_ERROR);
             } else {
                 if (contentLength != null &&
                         consumedDataBytes != contentLength) {
                     cancelImpl(new IOException(
                             String.format("fixed content-length: %d, bytes received: %d", contentLength, consumedDataBytes)),
                             Http3Error.H3_MESSAGE_ERROR);
                     return;
                 }
                 receiveData(new DataFrame(0),
                         List.of(QuicStreamReader.EOF), debug);
             }
         }
         if (framesDecoder.eof() && responseData.isEmpty()) {
             if (debug.on()) debug.log("EOF: Stopping scheduler");
             readScheduler.stop();
         }
         if (reader.isReset() && responseData.isEmpty()) {
             onReaderReset();
         }
     }

    final String checkInterimResponseCountExceeded() {
        // this is also checked by Exchange - but tracking it here too provides
        // a more informative message.
        int count = nonFinalResponseCount.incrementAndGet();
        if (MAX_NON_FINAL_RESPONSES > 0 && (count < 0 || count > MAX_NON_FINAL_RESPONSES)) {
            return String.format(
                    "Stream %s PROTOCOL_ERROR: too many interim responses received: %s > %s",
                    streamId(), count, MAX_NON_FINAL_RESPONSES);
        }
        return null;
    }

    /**
     * Called to create a new Response object for the newly receive response headers and
     * response status code. This method is called from {@link #handleResponse(HttpHeadersBuilder,
     * StreamHeadersConsumer, SequentialScheduler, Logger) handleResponse}, after the status code
     * and headers have been validated.
     *
     * @param responseHeaders response headers
     * @param responseCode    response code
     * @return a new {@code Response} object
     */
     abstract Response newResponse(HttpHeaders responseHeaders, int responseCode);

    /**
     * Called at the end of {@link #handleResponse(HttpHeadersBuilder,
     * StreamHeadersConsumer, SequentialScheduler, Logger) handleResponse}, to propagate
     * the response to the multi exchange.
     * @param response the {@code Response} that was received.
     */
     abstract void completeResponse(Response response);

    /**
     * Validate response headers and status code based on the {@link #responseState}.
     * If validated, this method will call {@link #newResponse(HttpHeaders, int)} to
     * create a {@code Response} object, which it will then pass to
     * {@link #completeResponse(Response)}.
     *
     * @param responseHeadersBuilder the response headers builder
     * @param rspHeadersConsumer     the response headers consumer
     * @param readScheduler          the read scheduler
     * @param debug                  the debug logger
     */
     void handleResponse(HttpHeadersBuilder responseHeadersBuilder,
                            StreamHeadersConsumer rspHeadersConsumer,
                            SequentialScheduler readScheduler,
                            Logger debug) {
         if (responseState == ResponseState.PERMIT_NONE) {
             connectionError(new ProtocolException("HEADERS after trailer"),
                     Http3Error.H3_FRAME_UNEXPECTED.code(),
                     "HEADERS after trailer");
             return;
         }
         HttpHeaders responseHeaders = responseHeadersBuilder.build();
         if (responseState == ResponseState.PERMIT_TRAILER) {
             if (responseHeaders.firstValue(":status").isPresent()) {
                 cancelImpl(new IOException("Unexpected :status header in trailer"), Http3Error.H3_MESSAGE_ERROR);
                 return;
             }
             if (Log.headers()) {
                 Log.logHeaders("Ignoring trailers on stream {0}: {1}", streamId(), responseHeaders);
             } else if (debug.on()) {
                 debug.log("Ignoring trailers: %s", responseHeaders);
             }
             responseState = ResponseState.PERMIT_NONE;
             rspHeadersConsumer.reset();
             if (readingPaused) {
                 readingPaused = false;
                 readScheduler.runOrSchedule(exchange.executor());
             }
             return;
         }

         int responseCode;
         try {
             responseCode = readStatusCode(responseHeaders, "");
         } catch (ProtocolException pe) {
             // RFC-9114: 4.1.2.  Malformed Requests and Responses:
             //  "Malformed requests or responses that are
             //   detected MUST be treated as a stream error of type H3_MESSAGE_ERROR"
             cancelImpl(pe, Http3Error.H3_MESSAGE_ERROR);
             return;
         }

         boolean finalResponse = false;
         if (responseCode >= 200) {
             responseState = ResponseState.PERMIT_TRAILER;
             finalResponse = true;
         } else {
             assert responseCode >= 100 && responseCode <= 200 : "unexpected responseCode: " + responseCode;
             String protocolErrorMsg = checkInterimResponseCountExceeded();
             if (protocolErrorMsg != null) {
                 if (debug.on()) {
                     debug.log(protocolErrorMsg);
                 }
                 cancelImpl(new ProtocolException(protocolErrorMsg), Http3Error.H3_GENERAL_PROTOCOL_ERROR);
                 rspHeadersConsumer.reset();
                 return;
             }
         }

         // update readingPaused after having decoded the statusCode and
         // switched the responseState.
         if (readingPaused) {
             readingPaused = false;
             readScheduler.runOrSchedule(exchange.executor());
         }

         var response = newResponse(responseHeaders, responseCode);

         if (debug.on()) {
             debug.log("received response headers: %s",
                     responseHeaders);
         }

         if (finalResponse) {
             long cl;
             try {
                 cl = readContentLength(responseHeaders, "", -1);
             } catch (ProtocolException pe) {
                 cancelImpl(pe, Http3Error.H3_MESSAGE_ERROR);
                 return;
             }
             if (cl != -1 &&
                     !(exchange.request().method().equalsIgnoreCase("HEAD") ||
                             responseCode == HTTP_NOT_MODIFIED)) {
                 // HEAD response and 304 response might have a content-length header,
                 // but it carries no meaning
                 contentLength = cl;
             }
         }

         if (Log.headers() || debug.on()) {
             StringBuilder sb = new StringBuilder("H3 RESPONSE HEADERS (stream=");
             sb.append(streamId()).append(")\n");
             Log.dumpHeaders(sb, "    ", responseHeaders);
             if (Log.headers()) {
                 Log.logHeaders(sb.toString());
             } else if (debug.on()) {
                 debug.log(sb);
             }
         }

         // this will clear the response headers
         rspHeadersConsumer.reset();

         completeResponse(response);
     }


}
