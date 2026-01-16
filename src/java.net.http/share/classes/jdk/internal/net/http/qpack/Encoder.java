/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.qpack;

import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.qpack.QPACK.Logger;
import jdk.internal.net.http.qpack.QPACK.QPACKErrorHandler;
import jdk.internal.net.http.qpack.QPACK.StreamPairSupplier;
import jdk.internal.net.http.qpack.TableEntry.EntryType;
import jdk.internal.net.http.qpack.readers.DecoderInstructionsReader;
import jdk.internal.net.http.qpack.writers.EncoderInstructionsWriter;
import jdk.internal.net.http.qpack.writers.FieldLineSectionPrefixWriter;
import jdk.internal.net.http.qpack.writers.HeaderFrameWriter;
import jdk.internal.net.http.quic.streams.QuicStreamReader;

import java.net.ProtocolException;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static jdk.internal.net.http.http3.Http3Error.H3_CLOSED_CRITICAL_STREAM;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;

/**
 * Encodes headers to their binary representation.
 */
public class Encoder {
    private static final AtomicLong ENCODERS_IDS = new AtomicLong();

    // RFC 9204 7.1.3. Never-Indexed Literals:
    // "Implementations can also choose to protect sensitive fields by not
    // compressing them and instead encoding their value as literals"
    private static final Set<String> SENSITIVE_HEADER_NAMES =
            Set.of("cookie", "authorization", "proxy-authorization");

    private final Logger logger;
    private final InsertionPolicy policy;
    private final TablesIndexer tablesIndexer;
    private final DynamicTable dynamicTable;
    private final QueuingStreamPair encoderStreams;
    private final DecoderInstructionsReader decoderInstructionsReader;
    // RFC-9204: 2.1.4. Known Received Count
    private long knownReceivedCount;

    // Lock for Known Received Count variable
    private final ReentrantReadWriteLock krcLock = new ReentrantReadWriteLock();

    // Max blocked streams setting value received from the peer decoder
    // can be set only once
    private long maxBlockedStreams = -1L;

    // Number of streams in process of headers encoding that expected to be blocked
    // but their unacknowledged section is not registered yet
    private long blockedStreamsInFlight;

    private final ReentrantLock blockedStreamsCounterLock = new ReentrantLock();

    // stream id -> fifo list of max and min ids referenced from field sections for each stream id
    private final ConcurrentMap<Long, Queue<SectionReference>> unacknowledgedSections =
            new ConcurrentHashMap<>();

    // stream id -> set of referenced entry absolute indexes from a field line section that currently
    // are in process of encoding and not added to the unacknowledged field sections map yet.
    private final ConcurrentMap<Long, ConcurrentSkipListSet<Long>> liveContextReferences =
            new ConcurrentHashMap<>();

    private final QPACKErrorHandler qpackErrorHandler;

    public HeaderFrameWriter newHeaderFrameWriter() {
        return new HeaderFrameWriter(logger);
    }

    /**
     * Constructs an {@code Encoder} with zero initial capacity of the dynamic table.
     * Maximum dynamic table capacity is not initialized until peer (decoder) HTTP/3 settings frame is
     * received (see {@link Encoder#configure(ConnectionSettings)}).
     *
     * <p> Dynamic table capacity values has to be agreed between decoder and encoder out-of-band,
     * e.g. by a protocol that uses QPACK.
     * <p> Maximum dynamic table capacity is determined by the value of SETTINGS_QPACK_MAX_TABLE_CAPACITY
     * HTTP/3 setting sent by the decoder side (see
     * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-maximum-dynamic-table-capac">
     * 3.2.3. Maximum Dynamic Table Capacity</a>).
     * <p> An encoder informs the decoder of a change to the dynamic table capacity using the
     * "Set Dynamic Table Capacity" instruction
     * (see <a href="https://www.rfc-editor.org/rfc/rfc9204.html#set-dynamic-capacity">
     * 4.3.1. Set Dynamic Table Capacity</a>)
     *
     * @param streamPairs supplier of the encoder unidirectional stream pair
     * @throws IllegalArgumentException if maxCapacity is negative
     * @see Encoder#configure(ConnectionSettings)
     */
    public Encoder(InsertionPolicy policy, StreamPairSupplier streamPairs, QPACKErrorHandler codingError) {
        this.policy = policy;
        long id = ENCODERS_IDS.incrementAndGet();
        this.logger = QPACK.getLogger().subLogger("Encoder#" + id);
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> "New encoder");
        }
        if (logger.isLoggable(EXTRA)) {
            /* To correlate with logging outside QPACK, knowing
               hashCode/toString is important */
            logger.log(EXTRA, () -> {
                String hashCode = Integer.toHexString(
                        System.identityHashCode(this));
                /* Since Encoder can be subclassed hashCode AND identity
                   hashCode might be different. So let's print both. */
                return format("toString='%s', hashCode=%s, identityHashCode=%s",
                        this, hashCode(), hashCode);
            });
        }
        // Set maximum dynamic table to 0, postpone setting of max capacity until peer
        // settings frame is received
        dynamicTable = new DynamicTable(logger.subLogger("DynamicTable"), true);
        tablesIndexer = new TablesIndexer(StaticTable.HTTP3, dynamicTable);
        encoderStreams = streamPairs.create(this::processDecoderAcks);
        decoderInstructionsReader = new DecoderInstructionsReader(new TableUpdatesCallback(),
                logger);
        qpackErrorHandler = codingError;
    }

    /**
     * Configures encoder according to the settings received from the peer.
     *
     * @param peerSettings the peer settings
     */
    public void configure(ConnectionSettings peerSettings) {
        blockedStreamsCounterLock.lock();
        try {
            if (maxBlockedStreams == -1) {
                maxBlockedStreams = peerSettings.qpackBlockedStreams();
            } else {
                throw new IllegalStateException("Encoder already configured");
            }
        } finally {
            blockedStreamsCounterLock.unlock();
        }
        // Set max dynamic table capacity
        long maxCapacity = peerSettings.qpackMaxTableCapacity();
        dynamicTable.setMaxTableCapacity(maxCapacity);
        // Send DT capacity update instruction if the peer negotiated non-zero
        // max table capacity, and limit the value with encoder's table capacity
        // limit system property value
        if (QPACK.ENCODER_TABLE_CAPACITY_LIMIT > 0 && maxCapacity > 0) {
            long encoderCapacity = Math.min(maxCapacity, QPACK.ENCODER_TABLE_CAPACITY_LIMIT);
            setTableCapacity(encoderCapacity);
        }
    }

    public QueuingStreamPair encoderStreams() {
        return encoderStreams;
    }

    public void header(EncodingContext context, CharSequence name, CharSequence value,
                       boolean sensitive) throws IllegalStateException {
        header(context, name, value, sensitive, knownReceivedCount());
    }

    /**
     * Sets up the given header {@code (name, value)} with possibly sensitive
     * value.
     *
     * <p> If the {@code value} is sensitive (think security, secrecy, etc.)
     * this encoder will compress it using a special representation
     * (see <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-7.1.3">
     * 7.1.3. Never-Indexed Literals</a>).
     *
     * <p> Fixates {@code name} and {@code value} for the duration of encoding.
     *
     * @param context            the encoding context
     * @param name               the name
     * @param value              the value
     * @param sensitive          whether the value is sensitive
     * @param knownReceivedCount the count of received entries known to a peer decoder or
     *                           {@code -1} to skip the dynamic table entry index check during header encoding.
     * @throws NullPointerException  if any of the arguments are {@code null}
     * @throws IllegalStateException if the encoder hasn't fully encoded the previous header, or
     *                               hasn't yet started to encode it
     * @see DecodingCallback#onDecoded(CharSequence, CharSequence, boolean)
     */
    public void header(EncodingContext context, CharSequence name, CharSequence value,
                       boolean sensitive, long knownReceivedCount) throws IllegalStateException {
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format("encoding ('%s', '%s'), sensitive: %s",
                    name, value, sensitive));
        }
        requireNonNull(name, "name");
        requireNonNull(value, "value");

        // TablesIndexer.entryOf checks if the found entry is a dynamic table entry,
        // and if its insertion was already ACKed. If not - use literal or name index encoding.
        var tableEntry = tablesIndexer.entryOf(name, value, knownReceivedCount);

        // NAME_VALUE table entry type means that one of dynamic or static tables contain
        // exact name:value pair.
        if (dynamicTable.capacity() > 0L
                && tableEntry.type() != EntryType.NAME_VALUE
                && !sensitive && policy.shouldUpdateDynamicTable(tableEntry)) {
                // We should check if we have an entry in dynamic table:
                //  - If we have it - do nothing
                //  - if we do not have it - insert it and use the index straight-away
                //     when blocking encoding is allowed
                tableEntry = context.tryInsertEntry(tableEntry);
        }

        // First, check that found/newly inserted entry is in the dynamic table
        // and can be referenced
        if (!tableEntry.isStaticTable() && tableEntry.index() >= 0 &&
                tableEntry.type() != EntryType.NEITHER) {
            if (!dynamicTable.tryReferenceEntry(tableEntry, context)) {
                // If entry cannot be referenced - use literal encoding instead
                tableEntry = tableEntry.toLiteralsEntry();
            }
        }

        // Configure header frame writer to write header field to the headers frame. One of the following
        // writers is selected based on entry type, the base value and the referenced table (static or dynamic):
        //    - static table and name:value match - "Indexed Field Line"
        //    - static table and name match - "Literal Field Line with Name Reference"
        //    - dynamic table, name:value match and index < base - "Indexed Field Line"
        //    - dynamic table, name match and index < base - "Literal Field Line with Name Reference"
        //    - dynamic table, name:value match and index >= base - "Indexed Field Line with Post-Base Index"
        //    - dynamic table, name match and index >= base - "Literal Field Line with Post-Base Name Reference"
        //    - not in dynamic or static tables  - "Literal Field Line with Literal Name"
        context.writer.configure(tableEntry, sensitive, context.base);
    }

    /**
     * Sets the capacity of the encoder's dynamic table and notifies the decoder by
     * issuing "Set Dynamic Table Capacity" instruction.
     *
     * <p> The value has to be agreed between decoder and encoder out-of-band,
     * e.g. by a protocol that uses QPACK
     * (see <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-set-dynamic-table-capacity">
     * 4.3.1. Set Dynamic Table Capacity</a>).
     *
     * @param capacity a non-negative long
     * @throws IllegalArgumentException if capacity is negative or exceeds the negotiated max capacity HTTP/3 setting
     */
    public void setTableCapacity(long capacity) {
        dynamicTable.setCapacityWithEncoderStreamUpdate(new EncoderInstructionsWriter(logger),
                capacity, encoderStreams);
    }

    /**
     * This method is called when the peer decoder sends
     * data on the peer's decoder stream
     *
     * @param buffer data sent by the peer's decoder
     */
    private void processDecoderAcks(ByteBuffer buffer) {
        if (buffer == QuicStreamReader.EOF) {
            // RFC-9204, section 4.2:
            // Closure of either unidirectional stream type MUST be treated as a connection
            // error of type H3_CLOSED_CRITICAL_STREAM.
            qpackErrorHandler.closeOnError(
                    new ProtocolException("QPACK " + encoderStreams.remoteStreamType()
                            + " remote stream was unexpectedly closed"), H3_CLOSED_CRITICAL_STREAM);
            return;
        }
        try {
            decoderInstructionsReader.read(buffer);
        } catch (QPackException e) {
            qpackErrorHandler.closeOnError(e.getCause(), e.http3Error());
        }
    }

    public List<ByteBuffer> encodeHeaders(HeaderFrameWriter writer, long streamId,
                                          int bufferSize, HttpHeaders... headers) {
        List<ByteBuffer> buffers = new ArrayList<>();
        ByteBuffer buffer = getByteBuffer(bufferSize);

        try (EncodingContext encodingContext = newEncodingContext(streamId,
                dynamicTable.insertCount(), writer)) {
            for (HttpHeaders header : headers) {
                for (Map.Entry<String, List<String>> e : header.map().entrySet()) {
                    // RFC-9114, section 4.2: Field names are strings containing a subset of
                    // ASCII characters. .... Characters in field names MUST be converted to
                    // lowercase prior to their encoding.
                    final String lKey = e.getKey().toLowerCase(Locale.ROOT);
                    final List<String> values = e.getValue();
                    // An encoder might also choose not to index values for fields that are
                    // considered to be highly valuable or sensitive to recovery, such as the
                    // Cookie or Authorization header fields
                    final boolean sensitive = SENSITIVE_HEADER_NAMES.contains(lKey);
                    for (String value : values) {
                        header(encodingContext, lKey, value, sensitive);
                        while (!writer.write(buffer)) {
                            buffer.flip();
                            buffers.add(buffer);
                            buffer = getByteBuffer(bufferSize);
                        }
                    }
                }
            }
            buffer.flip();
            buffers.add(buffer);

            // Put field line section prefix as the first byte buffer
            generateFieldLineSectionPrefix(encodingContext, buffers);

            // Register field line section as unacked if it uses references to the
            // dynamic table entries
            registerUnackedFieldLineSection(streamId, SectionReference.of(encodingContext));
        }
        return buffers;
    }

    public void generateFieldLineSectionPrefix(EncodingContext encodingContext, List<ByteBuffer> buffers) {
        // Write field section prefix according to RFC 9204: "4.5.1. Encoded Field Section Prefix"
        FieldLineSectionPrefixWriter prefixWriter = new FieldLineSectionPrefixWriter();
        FieldSectionPrefix fsp = encodingContext.sectionPrefix();
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format("Encoding Field Section Prefix - required insert" +
                            " count: %d base: %d",
                    fsp.requiredInsertCount(), fsp.base()));
        }
        int requiredSize = prefixWriter.configure(fsp, dynamicTable.maxEntries());
        var fspBuffer = getByteBuffer(requiredSize);
        if (!prefixWriter.write(fspBuffer)) {
            throw new IllegalStateException("Field Line Section Prefix");
        }
        fspBuffer.flip();
        buffers.addFirst(fspBuffer);
    }

    public void registerUnackedFieldLineSection(long streamId, SectionReference sectionReference) {
        if (sectionReference.referencesEntries()) {
            unacknowledgedSections
                    .computeIfAbsent(streamId, k -> new ConcurrentLinkedQueue<>())
                    .add(sectionReference);
        }
    }

    // This one is for tracking evict-ability of dynamic table entries
    public SectionReference unackedFieldLineSectionsRange(EncodingContext context) {
        SectionReference referenceNotRegisteredYet = SectionReference.of(context);
        return unackedFieldLineSectionsRange(referenceNotRegisteredYet);
    }

    private SectionReference unackedFieldLineSectionsRange(SectionReference initial) {
        return unacknowledgedSections.values().stream()
                .flatMap(Queue::stream)
                .reduce(initial, SectionReference::reduce);
    }

    long blockedStreamsCount() {
        long blockedStreams = 0;
        long krc = knownReceivedCount();
        for (var streamSections : unacknowledgedSections.values()) {
            boolean hasBlockedSection = streamSections.stream()
                    .anyMatch(sectionReference -> !sectionReference.fullyAcked(krc));
            blockedStreams = hasBlockedSection ? blockedStreams + 1 : blockedStreams;
        }
        return blockedStreams;
    }


    public long knownReceivedCount() {
        krcLock.readLock().lock();
        try {
            return knownReceivedCount;
        } finally {
            krcLock.readLock().unlock();
        }
    }

    private void updateKrcSectionAck(long streamId) {
        krcLock.writeLock().lock();
        try {
            var queue = unacknowledgedSections.get(streamId);
            // max() + 1 - since it is "Required Insert Count" not entry ID
            SectionReference oldestSectionRef = queue != null ? queue.poll() : null;
            long oldestNonAckedRic = oldestSectionRef != null ? oldestSectionRef.max() + 1 : -1L;
            if (oldestNonAckedRic == -1L) {
                // RFC 9204 4.4.1. Section Acknowledgment:
                // If an encoder receives a Section Acknowledgment instruction referring
                // to a stream on which every encoded field section with a non-zero
                // Required Insert Count has already been acknowledged, this MUST be treated
                // as a connection error of type QPACK_DECODER_STREAM_ERROR.
                var qPackException = QPackException.decoderStreamError(
                        new IllegalStateException("No unacknowledged sections found" +
                                " for stream id = " + streamId));
                throw qPackException;
            }
            // "2.1.4. Known Received Count":
            // If the Required Insert Count of the acknowledged field section is greater
            // than the current Known Received Count, the Known Received Count is updated
            // to that Required Insert Count value.
            if (oldestNonAckedRic != -1 && knownReceivedCount < oldestNonAckedRic) {
                knownReceivedCount = oldestNonAckedRic;
            }
        } finally {
            krcLock.writeLock().unlock();
        }
    }

    private void updateKrcInsertCountIncrement(long increment) {
        long insertCount = dynamicTable.insertCount();
        krcLock.writeLock().lock();
        try {
            // An encoder that receives an Increment field equal to zero, or one that increases
            // the Known Received Count beyond what the encoder has sent, MUST treat this as
            // a connection error of type QPACK_DECODER_STREAM_ERROR.
            if (increment == 0 || knownReceivedCount > insertCount - increment) {
                var qpackException = QPackException.decoderStreamError(
                        new IllegalStateException("Invalid increment field value: " + increment));
                throw qpackException;
            }
            knownReceivedCount += increment;
        } finally {
            krcLock.writeLock().unlock();
        }
    }

    private void cleanupStreamData(long streamId) {
        liveContextReferences.remove(streamId);
        unacknowledgedSections.remove(streamId);
    }

    private class TableUpdatesCallback implements DecoderInstructionsReader.Callback {
        @Override
        public void onSectionAck(long streamId) {
            updateKrcSectionAck(streamId);
        }

        @Override
        public void onInsertCountIncrement(long increment) {
            updateKrcInsertCountIncrement(increment);
        }

        @Override
        public void onStreamCancel(long streamId) {
            cleanupStreamData(streamId);
        }
    }

    public class EncodingContext implements AutoCloseable {
        final long base;
        final long streamId;
        final ConcurrentSkipListSet<Long> referencedIndexes;
        long maxIndex;
        long minIndex;
        boolean blockedDecoderExpected;
        final HeaderFrameWriter writer;
        final EncoderInstructionsWriter encoderInstructionsWriter;

        public EncodingContext(long streamId, long base, HeaderFrameWriter writer) {
            this.base = base;
            this.encoderInstructionsWriter = new EncoderInstructionsWriter(logger);
            this.writer = writer;
            this.maxIndex = -1L;
            this.minIndex = Long.MAX_VALUE;
            this.streamId = streamId;
            this.referencedIndexes = liveContextReferences.computeIfAbsent(streamId,
                    _ -> new ConcurrentSkipListSet<>());
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("Begin encoding session with base = %s stream-id = %s", base, streamId));
            }
        }

        public void registerSessionReference(long absoluteEntryId) {
            referencedIndexes.add(absoluteEntryId);
        }

        @Override
        public void close() {
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("Closing encoding context for stream-id=%s" +
                                " session references:%s",
                        streamId, referencedIndexes));
            }
            liveContextReferences.remove(streamId);
            // Deregister if this stream was marked as in-flight blocked
            blockedStreamsCounterLock.lock();
            try {
                if (blockedDecoderExpected) {
                    blockedStreamsInFlight--;
                }
            } finally {
                blockedStreamsCounterLock.unlock();
            }
        }

        public FieldSectionPrefix sectionPrefix() {
            // RFC 9204: 2.1.2. Blocked Streams
            //       "the Required Insert Count is one larger than the largest absolute index
            //       of all referenced dynamic table entries"
            // largestAbsoluteIndex is initialized to -1, and if there is no dynamic
            // table entry references - RIC will be set to 0.
            return new FieldSectionPrefix(maxIndex + 1, base);
        }

        public SectionReference evictionLimit() {
            // In-flight references - a set with entry ids referenced from all
            // active header encoding sessions not fully encoded yet
            SectionReference inFlightReferences = SectionReference.singleReference(
                    liveContextReferences.values().stream()
                            .filter(Predicate.not(ConcurrentSkipListSet::isEmpty))
                            .map(ConcurrentSkipListSet::first)
                            .min(Long::compare)
                            .orElse(-1L));

            // Calculate the eviction limit with respect to:
            //   - in-flight references
            //   - acknowledged dynamic table insertions
            //   - range of unacknowledged sections which already fully encoded
            //     and sent as part of other request/response streams
            return inFlightReferences
                    .reduce(knownReceivedCount())
                    .reduce(unackedFieldLineSectionsRange(this));
        }

        public TableEntry tryInsertEntry(TableEntry entry) {
            long idx = dynamicTable.insertWithEncoderStreamUpdate(entry,
                                      encoderInstructionsWriter, encoderStreams,
                    this);
            if (idx == DynamicTable.ENTRY_NOT_INSERTED) {
                if (logger.isLoggable(EXTRA)) {
                    logger.log(EXTRA, () -> format("Not adding entry '%s' to the dynamic " +
                            "table - not enough space, or unacknowledged entry needs to be evicted",
                            entry));
                }
                // Return what we previously found in the dynamic or static table
                return entry;
            }

            if (QPACK.ALLOW_BLOCKING_ENCODING && canReferenceNewEntry()) {
                // Create a new TableEntry that describes newly added header field
                return entry.toNewDynamicTableEntry(idx);
            } else {
                return entry;
            }
        }

        private boolean canReferenceNewEntry() {
            blockedStreamsCounterLock.lock();
            try {
                // If current encoding context is already marked as blocked we can
                // reference new entries without analyzing number of blocked streams
                if (blockedDecoderExpected) {
                    return true;
                }
                // Number of streams with unacknowledged field line section
                long alreadyBlocked = blockedStreamsCount();
                // Other streams might be in progress of headers encoding
                boolean canReferenceNewEntry = maxBlockedStreams - alreadyBlocked - blockedStreamsInFlight > 0;
                if (logger.isLoggable(EXTRA)) {
                    logger.log(EXTRA, () -> format("%s reference to newly added header. " +
                                    "Number of blocked streams based on unAcked sections: %d " +
                                    "Number of blocked streams in progress of encoding: %d " +
                                    "Max allowed by HTTP/3 settings: %d",
                            canReferenceNewEntry ? "Allowing" : "Restricting",
                            alreadyBlocked, blockedStreamsInFlight, maxBlockedStreams));
                }
                if (canReferenceNewEntry && !blockedDecoderExpected) {
                    blockedStreamsInFlight++;
                    blockedDecoderExpected = true;
                }
                return canReferenceNewEntry;
            } finally {
                blockedStreamsCounterLock.unlock();
            }
        }

        public void referenceEntry(TableEntry tableEntry) {
            assert tableEntry.index() >= 0;
            if (!tableEntry.isStaticTable()) {
                long index = tableEntry.index();
                maxIndex = Long.max(maxIndex, index);
                minIndex = Long.min(minIndex, index);
            }
        }
    }

    /**
     * Descriptor of entries range referenced from a field lines section.
     *
     * @param min minimum entry id referenced from a field lines section
     * @param max maximum entry id referenced from a field lines section
     */
    public record SectionReference(long min, long max) {
        public static SectionReference of(EncodingContext context) {
            if (context.maxIndex == -1L) {
                return SectionReference.noReferences();
            }
            return new SectionReference(context.minIndex, context.maxIndex);
        }

        public SectionReference reduce(SectionReference other) {
            if (!referencesEntries()) {
                return other;
            } else if (!other.referencesEntries()) {
                return this;
            }
            long newMin = Long.min(this.min, other.min);
            long newMax = Long.max(this.max, other.max);
            return new SectionReference(newMin, newMax);
        }

        public SectionReference reduce(long entryId) {
            return reduce(singleReference(entryId));
        }

        public static SectionReference singleReference(long entryId) {
            return new SectionReference(entryId, entryId);
        }

        public boolean fullyAcked(long knownReceiveCount) {
            return max < knownReceiveCount;
        }

        public static SectionReference noReferences() {
            return new SectionReference(-1L, -1L);
        }

        public boolean referencesEntries() {
            return max != -1L;
        }
    }

    public EncodingContext newEncodingContext(long streamId, long base, HeaderFrameWriter writer) {
        assert streamId >= 0;
        assert base >= 0;
        return new EncodingContext(streamId, base, writer);
    }

    private ByteBuffer getByteBuffer(int size) {
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.limit(size);
        return buf;
    }
}
