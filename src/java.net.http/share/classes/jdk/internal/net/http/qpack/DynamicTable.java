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

import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.qpack.Encoder.EncodingContext;
import jdk.internal.net.http.qpack.Encoder.SectionReference;
import jdk.internal.net.http.qpack.QPACK.Logger;
import jdk.internal.net.http.qpack.writers.EncoderInstructionsWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import static java.lang.String.format;
import static jdk.internal.net.http.http3.Http3Error.H3_CLOSED_CRITICAL_STREAM;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;
import static jdk.internal.net.http.qpack.TableEntry.EntryType.NAME;

/*
 * The dynamic table to store header fields. Implements dynamic table described
 *  in "QPACK: Header Compression for HTTP/3" RFC.
 * The size of the table is the sum of the sizes of its entries.
 */
public final class DynamicTable implements HeadersTable {

    // QPACK Section 3.2.1:
    // The size of an entry is the sum of its name's length in bytes,
    // its value's length in bytes, and 32 additional bytes.
    public static final long ENTRY_SIZE = 32L;

    // Initial length of the elements array
    // It is required for this value to be a power of 2 integer
    private static final int INITIAL_HOLDER_ARRAY_LENGTH = 64;

    final Logger logger;

    // Capacity (Maximum size) in bytes (or capacity in RFC 9204) of the dynamic table
    private long capacity;

    // RFC-9204: 3.2.3. Maximum Dynamic Table Capacity
    private long maxCapacity;

    // Max entries is required to implement encoding of Required Insert Count
    // in Field Lines Prefix:
    //    RFC-9204: 4.5.1.1. Required Insert Count:
    //         "This encoding limits the length of the prefix on long-lived connections."
    private long maxEntries;

    // Size of the dynamic table in bytes - calculated as the sum of the sizes of its entries.
    private long size;

    // Table elements holder and its state variables
    // Absolute ID of tail and head elements.
    // tail id - is an id of the oldest element in the table
    // head id - is an id of the next element that will be added to the table.
    //           head element id is head - 1.
    // drain id - is the lowest element id that encoder can reference
    private long tail, head, drain = -1;

    // Used space percentage threshold when to start increasing the drain index
    private final int drainUsedSpaceThreshold = QPACK.ENCODER_DRAINING_THRESHOLD;

    // true - table is used by the QPack encoder, otherwise used by the
    // QPack decoder
    private final boolean encoderTable;

    // Array that holds dynamic table entries
    private HeaderField[] elements;

    //                name  ->    (value ->    [index])
    private final Map<String, Map<String, Deque<Long>>> indicesMap;

    private record TableInsertCountNotification(long streamId, long minimumRIC,
                                                CompletableFuture<Void> completion) {
        public boolean isStreamId(long streamId) {
            return this.streamId == streamId;
        }
        public boolean isFulfilled(long insertionCount) {
            return insertionCount >= minimumRIC;
        }
    }

    private final Queue<TableInsertCountNotification> insertCountNotifications =
            new PriorityQueue<>(
                    Comparator.comparingLong(TableInsertCountNotification::minimumRIC)
            );

    public CompletableFuture<Void> awaitFutureInsertCount(long streamId,
                                                          long valueToAwait) {
        if (encoderTable) {
            throw new IllegalStateException("Misconfigured table");
        }
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            var completion = new CompletableFuture<Void>();
            long insertCount = insertCount();
            if (insertCount >= valueToAwait) {
                completion.complete(null);
            } else {
                insertCountNotifications
                        .add(new TableInsertCountNotification(
                                streamId, valueToAwait, completion));
            }
            return completion;
        } finally {
            writeLock.unlock();
        }
    }

    private void notifyInsertCountChange() {
        assert lock.isWriteLockedByCurrentThread();
        if (insertCountNotifications.isEmpty()) {
            return;
        }
        long insertCount = insertCount();
        Predicate<TableInsertCountNotification> isFulfilled =
                icn -> icn.isFulfilled(insertCount);
        insertCountNotifications.removeIf(icn -> completeIf(isFulfilled, icn));
    }

    public boolean cleanupStreamInsertCountNotifications(long streamId) {
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            Predicate<TableInsertCountNotification> isSameStreamId =
                    icn -> icn.isStreamId(streamId);
            return insertCountNotifications.removeIf(icn -> completeIf(isSameStreamId, icn));
        } finally {
            writeLock.unlock();
        }
    }

    private static boolean completeIf(Predicate<TableInsertCountNotification> predicate,
                                      TableInsertCountNotification insertCountNotification) {
        if (predicate.test(insertCountNotification)) {
            insertCountNotification.completion.complete(null);
            return true;
        }
        return false;
    }

    // Read-Write lock to manage access to table entries
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public DynamicTable(Logger logger) {
        this(logger, true);
    }

    public DynamicTable(Logger logger, boolean encoderTable) {
        this.logger = logger;
        this.encoderTable = encoderTable;
        elements = new HeaderField[INITIAL_HOLDER_ARRAY_LENGTH];
        indicesMap = new HashMap<>();
        // -1 signifies that max table capacity was not yet initialized
        maxCapacity = -1L;
        maxEntries = 0L;
    }

    /**
     * Returns size of the dynamic table in bytes
     * @return size of the dynamic table
     */
    public long size() {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            return size;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns current capacity of the dynamic table
     * @return current capacity
     */
    public long capacity() {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            return capacity;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns a maximum capacity in bytes of the dynamic table.
     * @return maximum capacity
     */
    public long maxCapacity() {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            return maxCapacity;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Sets a maximum capacity in bytes of the dynamic table.
     *
     * <p> The value has to be agreed between decoder and encoder out-of-band,
     * e.g. by a protocol that uses QPACK
     * (see <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-3.2.3">
     *     3.2.3 Maximum Dynamic Table Capacity</a>).
     *
     * <p> May be called only once to set maximum dynamic table capacity.
     * <p> This method doesn't change the actual capacity of the dynamic table.
     *
     * @see #setCapacity(long)
     * @param maxCapacity a non-negative long
     * @throws IllegalArgumentException if max capacity is negative
     * @throws IllegalStateException if max capacity was already set
     */
    public void setMaxTableCapacity(long maxCapacity) {
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (maxCapacity < 0) {
                throw new IllegalArgumentException("maxCapacity >= 0: " + maxCapacity);
            }
            if (this.maxCapacity != -1L) {
                // Max table capacity is initialized from SETTINGS frame which can be only received once:
                //  "If an endpoint receives a second SETTINGS frame on the control stream,
                //   the endpoint MUST respond with a connection error of type H3_FRAME_UNEXPECTED"
                //   [RFC 9114 https://www.rfc-editor.org/rfc/rfc9114.html#name-settings]
                throw new IllegalStateException("Max Table Capacity can only be set once");
            }
            if (logger.isLoggable(NORMAL)) {
                logger.log(NORMAL, () -> format("setting maximum allowed dynamic table capacity to %s",
                        maxCapacity));
            }
            this.maxCapacity = maxCapacity;
            this.maxEntries = maxCapacity / ENTRY_SIZE;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns maximum possible number of entries that could be stored in the dynamic table
     * with respect to MAX_CAPACITY setting.
     * @return max entries
     */
    public long maxEntries() {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            return maxEntries;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Retrieves a header field by its absolute index. Entry referenced by an absolute
     * index does not depend on the state of the dynamic table.
     * @param uniqueID an entry unique index
     * @return retrieved header field
     * @throws IllegalArgumentException if entry is not received yet,
     *                                  already evicted or invalid entry index is specified.
     */
    @Override
    public HeaderField get(long uniqueID) {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            if (uniqueID < 0) {
                throw new IllegalArgumentException("Entry index invalid");
            }
            // Not yet received entry
            if (uniqueID >= head) {
                throw new IllegalArgumentException("Entry not received yet");
            }
            // Already evicted entry
            if (uniqueID < tail) {
                throw new IllegalArgumentException("Entry already evicted");
            }
            return elements[(int) (uniqueID & (elements.length - 1))];
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Retrieves a header field by its relative index. Entry referenced by a relative index depends
     * on the state of the dynamic table.
     * @param relativeId index relative to the most recently inserted entry
     * @return retrieved header field
     */
    public HeaderField getRelative(long relativeId) {
        // RFC 9204: 3.2.5. Relative Indexing
        // "Relative indices begin at zero and increase in the opposite direction from the absolute index.
        // Determining which entry has a relative index of 0 depends on the context of the reference.
        // In encoder instructions (Section 4.3), a relative index of 0 refers to the most recently inserted
        // value in the dynamic table."
        var readLock = lock.readLock();
        readLock.lock();
        try {
            return get(insertCount() - 1 - relativeId);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Converts absolute entry index to relative index that can be used
     * in the encoder instructions.
     * Relative index of 0 refers to the most recently inserted entry.
     *
     * @param absoluteId absolute index of an entry
     * @return relative entry index
     */
    public long toRelative(long absoluteId) {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            assert absoluteId < head;
            return head - 1 - absoluteId;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Search an absolute id of a name:value pair in the dynamic table.
     * @param name  a name to search for
     * @param value a value to search for
     * @return positive index if name:value match found,
     *         negative index if only name match found,
     *         0 if no match found
     */
    @Override
    public long search(String name, String value) {
        // This method is only designated for encoder use
        if (!encoderTable) {
            return 0;
        }
        var readLock = lock.readLock();
        readLock.lock();
        try {
            Map<String, Deque<Long>> values = indicesMap.get(name);
            if (values == null) {
                return 0;
            }
            Deque<Long> indexes = values.get(value);
            if (indexes != null) {
                // "+1" since the index range [0..id] is mapped to [1..id+1]
                return indexes.peekLast() + 1;
            } else {
                assert !values.isEmpty();
                Long any = values.values().iterator().next().peekLast(); // Iterator allocation
                // Use last entry in found values with matching name, and use its index for
                // encoding with name reference.
                // Negation and "-1" since name-only matches are mapped from [0..id] to
                // [-1..-id-1] region
                return -any - 1;
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Add an entry to the dynamic table.
     * Entries could be evicted from the dynamic table.
     * Unacknowledged section references are not checked by this method, therefore
     * this method is intended to be used by the decoder only. The encoder should use
     * overloaded method that takes global unacknowledged section reference.
     *
     * @param name  header name
     * @param value header value
     * @return unique index of an entry added to the table.
     * If element cannot be added {@code -1} is returned.
     */
    @Override
    public long insert(String name, String value) {
        // Invoking toString() will possibly allocate Strings. But that's
        // unavoidable at this stage. If a CharSequence is going to be stored in
        // the table, it must not be mutable (e.g. for the sake of hashing).
        return insert(new HeaderField(name, value), SectionReference.noReferences());
    }


    /**
     * Add entry to the dynamic table with name specified as index in static
     * or dynamic table.
     * Entries could be evicted from the dynamic table.
     * Unacknowledged section references are not checked by this method, therefore
     * this method is intended to be used by the decoder only. The encoder should use
     * overloaded method that takes global unacknowledged section reference.
     *
     * @param nameIndex     index of the header name to add
     * @param isStaticIndex if name index references static table header name
     * @param value         header value
     * @return unique index of an entry added to the table.
     * If element cannot be added {@code -1} is returned.
     * @throws IllegalStateException if table memory reclamation error observed
     */
    public long insert(long nameIndex, boolean isStaticIndex, String value) {
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("Inserting with name index (nameIndex='%s' isStaticIndex='%s' value=%s)",
                        nameIndex, isStaticIndex, value));
            }
            String name = isStaticIndex ?
                    StaticTable.HTTP3.get(nameIndex).name() :
                    getRelative(nameIndex).name();
            return insert(name, value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Add an entry to the dynamic table.
     * Entries could be evicted from the dynamic table.
     * The supplied unacknowledged section references are checked by this method to check
     * if entries are evictable.
     * Such checks are performed when there is not enough space in the dynamic table to insert
     * the requested header.
     * This method is intended to be used by the encoder only.
     *
     * @param name             header name
     * @param value            header value
     * @param sectionReference unacknowledged section references
     * @return unique index of an entry added to the table.
     * If element cannot be added {@code -1} is returned.
     * @throws IllegalStateException if table memory reclamation error observed
     */
    public long insert(String name, String value, SectionReference sectionReference) {
        return insert(new HeaderField(name, value), sectionReference);
    }

    /**
     * Inserts an entry to the dynamic table and sends encoder insert instruction bytes
     * to the peer decoder.
     * This method is designated to be used by the {@link Encoder} class only.
     * If an entry with matching name:value is available, its index is returned
     * and no insert instruction is generated on encoder stream. If duplicate entry is required
     * due to entry being non-referencable then {@link DynamicTable#duplicateWithEncoderStreamUpdate(
     * EncoderInstructionsWriter, long, QueuingStreamPair, EncodingContext)} is used.
     *
     * @param entry             table entry to add
     * @param writer            non-configured encoder instruction writer for generating encoder
     *                          instruction
     * @param encoderStreams    encoder stream pair
     * @param encodingContext   encoder encoding context
     * @return absolute id of inserted entry OR already available entry, -1L if entry cannot
     *  be added
     */
    public long insertWithEncoderStreamUpdate(TableEntry entry,
                                              EncoderInstructionsWriter writer,
                                              QueuingStreamPair encoderStreams,
                                              EncodingContext encodingContext) {
        if (!encoderTable) {
            throw new IllegalStateException("Misconfigured table");
        }
        String name = entry.name().toString();
        String value = entry.value().toString();
        // Entry with name only match in dynamic table
        boolean nameOnlyDynamicEntry = !entry.isStaticTable() && entry.type() == NAME;
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            // First, check if entry is in the table already -
            // no need to add a new one.
            long index = search(name, value);
            if (index > 0) {
                long absIndex = index - 1;
                // Check if found entry can be referenced,
                // if not issue duplicate instruction
                if (!canReferenceEntry(absIndex)) {
                    return duplicateWithEncoderStreamUpdate(writer,
                            absIndex, encoderStreams, encodingContext);
                }
                return absIndex;
            }
            SectionReference evictionLimitSR = encodingContext.evictionLimit();
            if (nameOnlyDynamicEntry) {
                long nameIndex = entry.index();
                if (!canReferenceEntry(nameIndex)) {
                    return ENTRY_NOT_INSERTED;
                }
                evictionLimitSR = evictionLimitSR.reduce(nameIndex);
                encodingContext.registerSessionReference(nameIndex);
            }
            // Relative index calculation should precede the insertion
            // due to dependency on insert count value
            long relativeNameIndex =
                    nameOnlyDynamicEntry ? toRelative(entry.index()) : -1;

            // Insert new entry to the table with respect to entry
            // references range provided by the encoding context
            long idx = insert(name, value, evictionLimitSR);
            if (idx == ENTRY_NOT_INSERTED) {
                // Insertion requires eviction of entries from unacknowledged
                // sections therefore entry is not added
                return ENTRY_NOT_INSERTED;
            }
            // Entry was successfully inserted
            if (nameOnlyDynamicEntry) {
                // Absolute index only needs to be replaced with the relative one
                // when it references a name in the dynamic table.
                entry = entry.relativizeDynamicTableEntry(relativeNameIndex);
            }
            int instructionSize = writer.configureForEntryInsertion(entry);
            writeEncoderInstruction(writer, instructionSize, encoderStreams);
            return idx;
        } finally {
            writeLock.unlock();
        }
    }

    private long insert(HeaderField h, SectionReference sectionReference) {
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("adding ('%s', '%s')", h.name(), h.value()));
            }
            long entrySize = headerSize(h);
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("size of ('%s', '%s') is %s", h.name(), h.value(), entrySize));
            }

            long availableEvictableSpace = availableEvictableSpace(sectionReference);
            if (availableEvictableSpace < entrySize) {
                if (logger.isLoggable(EXTRA)) {
                    logger.log(EXTRA, () -> format("Header size exceeds available evictable space=%s." +
                                    " Combined section reference=%s",
                            availableEvictableSpace, sectionReference));
                }
                // Evicting entries won't help to gather enough space to insert the requested one
                return ENTRY_NOT_INSERTED;
            }
            while (entrySize > capacity - size && size != 0) {
                if (logger.isLoggable(EXTRA)) {
                    logger.log(EXTRA, () -> format("insufficient space %s, must evict entry", (capacity - size)));
                }
                // Only Encoder will supply section with referenced
                // entries
                if (sectionReference.referencesEntries()) {
                    // Check if tail element is evictable
                    if (tail < sectionReference.min()) {
                        if (!evictEntry()) {
                            return ENTRY_NOT_INSERTED;
                        }
                    } else {
                        if (logger.isLoggable(EXTRA)) {
                            logger.log(EXTRA, () -> format("Cannot evict entry: sectionRef=%s tail=%s",
                                    sectionReference, tail));
                        }
                        // For now -1 is returned to notify the Encoder that entry
                        // cannot be inserted to the dynamic table
                        return ENTRY_NOT_INSERTED;
                    }
                } else {
                    // This call can be called by both Encoder and Decoder:
                    //     - Encoder when add new entry with no unacked section references
                    //     - Decoder when processing insert entry instructions.
                    //       Entries are evicted until there is enough space OR until table
                    //       is empty.
                    if (!evictEntry()) {
                        return ENTRY_NOT_INSERTED;
                    }
                }
            }
            size += entrySize;
            // At this stage it is clear that there are enough bytes (max capacity is not exceeded) in the dynamic
            // table to add new header field
            addWithInverseMapping(h);
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("('%s, '%s') added", h.name(), h.value()));
                logger.log(EXTRA, this::toString);
            }
            notifyInsertCountChange();
            return head - 1;
        } finally {
            writeLock.unlock();
        }
    }

    public long duplicate(long relativeId) {
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            var entry = getRelative(relativeId);
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("Duplicate entry with absId=%s" +
                                " insertCount=%s ('%s', '%s')",
                        insertCount() - 1 - relativeId, insertCount(),
                        entry.name(), entry.value()));
            }
            return insert(entry.name(), entry.value());
        } finally {
            writeLock.unlock();
        }
    }

    public long duplicateWithEncoderStreamUpdate(EncoderInstructionsWriter writer,
                                                 long absoluteEntryId,
                                                 QueuingStreamPair encoderStreams,
                                                 EncodingContext encodingContext) {
        if (!encoderTable) {
            throw new IllegalStateException("Misconfigured table");
        }
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            var entry = get(absoluteEntryId);
            // Relative index calculation should precede the insertion
            // due to dependency on insert count value
            long relativeEntryId = toRelative(absoluteEntryId);

            // Make entry id that needs to be duplicated non-evictable
            SectionReference evictionLimit = encodingContext.evictionLimit()
                    .reduce(absoluteEntryId);

            // Put duplicated entry to our dynamic table first
            long idx = insert(entry.name(), entry.value(),
                    evictionLimit);
            if (idx == ENTRY_NOT_INSERTED) {
                return ENTRY_NOT_INSERTED;
            }
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("Issuing entry duplication instruction" +
                                " for absId=%s relId=%s ('%s', '%s')",
                        absoluteEntryId, relativeEntryId, entry.name(), entry.value()));
            }

            // Configure writer for entry duplication
            int instructionSize =
                    writer.configureForEntryDuplication(relativeEntryId);

            // Write instruction to the encoder stream
            writeEncoderInstruction(writer, instructionSize, encoderStreams);
            return idx;
        } finally {
            writeLock.unlock();
        }
    }

    private HeaderField remove() {
        assert lock.isWriteLockedByCurrentThread();
        // Remove element from the holder array first
        if (getElementsCount() == 0) {
            throw new IllegalStateException("Empty table");
        }

        int tailIdx = (int) (tail++ & (elements.length - 1));
        HeaderField f = elements[tailIdx];
        elements[tailIdx] = null;

        // Log the removal event
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("removing ('%s', '%s')", f.name(), f.value()));
        }

        // Update indices map on the encoder table only
        if (encoderTable) {
            Map<String, Deque<Long>> values = indicesMap.get(f.name());
            Deque<Long> indexes = values.get(f.value());
            // Remove the oldest index of the name:value pair
            Long index = indexes.pollFirst();
            // Clean-up indexes associated with a value from values map
            if (indexes.isEmpty()) {
                values.remove(f.value());
            }
            assert index != null;
            // If indexes map associated with name is empty remove name
            // entry from indices map
            if (values.isEmpty()) {
                indicesMap.remove(f.name());
            }
        }
        return f;
    }

    /**
     * Sets the dynamic table capacity in bytes.
     * The new capacity must be lower than or equal to the limit defined by
     * SETTINGS_QPACK_MAX_TABLE_CAPACITY HTTP/3 settings parameter. This limit is
     * enforced by {@linkplain DynamicTable#setMaxTableCapacity(long)}.
     *
     * @param capacity dynamic table capacity to set
     */
    public void setCapacity(long capacity) {
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (capacity > maxCapacity) {
                // Calling code catches IllegalArgumentException and generates the connection error:
                //       4.3.1. Set Dynamic Table Capacity:
                //              "The decoder MUST treat a new dynamic table capacity value that exceeds
                //               this limit as a connection error of type QPACK_ENCODER_STREAM_ERROR."
                throw new IllegalArgumentException("Illegal dynamic table capacity");
            }
            if (capacity < 0) {
                throw new IllegalArgumentException("capacity >= 0: capacity=" + capacity);
            }
            while (capacity < size && size != 0) {
                // Evict entries until existing elements fit into
                // new table capacity
                boolean entryEvicted = evictEntry();
                assert entryEvicted;
            }
            this.capacity = capacity;
            if (usedSpace() < drainUsedSpaceThreshold) {
                if (drain != -1) {
                    drain = -1;
                }
            } else if (drain == -1 || tail > drain) {
                drain = tail;
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Updates the capacity of the dynamic table and sends encoder capacity update instruction
     * bytes to the peer decoder.
     * This method is designated to be used by the {@link Encoder} class only.
     * @param writer non-configured encoder instruction writer for generating encoder instruction
     * @param capacity new capacity value
     * @param encoderStreams encoder stream pair
     */
    public void setCapacityWithEncoderStreamUpdate(EncoderInstructionsWriter writer, long capacity,
                                                   QueuingStreamPair encoderStreams) {
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            // Configure writer for capacity update
            int instructionSize = writer.configureForTableCapacityUpdate(capacity);
            // Check and set our capacity
            setCapacity(capacity);
            // Write instruction
            writeEncoderInstruction(writer, instructionSize, encoderStreams);
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * Evicts one entry from the table tail.
     * @return {@code true} if entry was evicted,
     *         {@code false} if nothing to remove
     */
    private boolean evictEntry() {
        assert lock.isWriteLockedByCurrentThread();
        try {
            HeaderField f = remove();
            long s = headerSize(f);
            this.size -= s;
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA,
                        () -> format("evicted entry ('%s', '%s') of size %s with absId=%s",
                                f.name(), f.value(), s, tail - 1));
            }
        } catch (IllegalStateException ise) {
            // Entry cannot be evicted from empty table
            return false;
        }
        return true;
    }

    public long availableEvictableSpace(SectionReference sectionReference) {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            if (!sectionReference.referencesEntries()) {
                return capacity;
            }
            // Size that can be reclaimed in the dynamic table by evicting
            // non-referenced entries
            long availableEvictableCapacity = 0;
            for (long absId = tail; absId < sectionReference.min(); absId++) {
                HeaderField field = get(absId);
                availableEvictableCapacity += headerSize(field);
            }
            // (capacity - size) - free space in the dynamic table
            return availableEvictableCapacity + (capacity - size);
        } finally {
            readLock.unlock();
        }
    }

    public boolean tryReferenceEntry(TableEntry tableEntry, EncodingContext context) {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            long absId = tableEntry.index();
            if (canReferenceEntry(absId)) {
                context.registerSessionReference(absId);
                context.referenceEntry(tableEntry);
                return true;
            } else {
                return false;
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public String toString() {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            double used = usedSpace();
            return format("full length: %s, used space: %s/%s (%.1f%%)",
                    getElementsCount(), size, capacity, used);
        } finally {
            readLock.unlock();
        }
    }

    private boolean canReferenceEntry(long absId) {
        // The dynamic table lock is acquired by the calling methods
        return absId > drain;
    }

    private double usedSpace() {
        return capacity == 0 ? 0 : 100 * (((double) size) / capacity);
    }

    public static long headerSize(HeaderField f) {
        return headerSize(f.name(), f.value());
    }

    public static long headerSize(String name, String value) {
        return name.length() + value.length() + ENTRY_SIZE;
    }

    // To quickly find an index of an entry in the dynamic table with the
    // given contents an effective inverse mapping is needed.
    private void addWithInverseMapping(HeaderField field) {
        assert lock.isWriteLockedByCurrentThread();
        // Check if holder array has at least one free slot to add header field
        // The method below can increase elements.length if no free slot found
        ensureElementsArrayLength();
        long counterSnapshot = head++;
        elements[(int) (counterSnapshot & (elements.length - 1))] = field;
        if (encoderTable) {
            // Allocate unique index and use it to store in indicesMap
            Map<String, Deque<Long>> values = indicesMap.computeIfAbsent(
                    field.name(), _ -> new HashMap<>());
            Deque<Long> indexes = values.computeIfAbsent(
                    field.value(), _ -> new LinkedList<>());
            indexes.add(counterSnapshot);
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA,
                        () -> format("added '%s' header field with '%s' unique id",
                                field, counterSnapshot));
            }
            assert indexesUniqueAndOrdered(indexes);
            // Draining index is only used by the Encoder
            updateDrainIndex();
        }
    }

    private void updateDrainIndex() {
        if (!encoderTable) {
            return;
        }
        assert lock.isWriteLockedByCurrentThread();
        if (usedSpace() > drainUsedSpaceThreshold) {
            if (drain == -1L) {
                drain = tail;
            } else {
                drain++;
            }
            if (logger.isLoggable(EXTRA)) {
                logger.log(EXTRA, () -> format("Draining index changed: %d", drain));
            }
        }
    }

    private void ensureElementsArrayLength() {
        assert lock.isWriteLockedByCurrentThread();

        int currentArrayLength = elements.length;
        if (getElementsCount() == currentArrayLength) {
            if (currentArrayLength == (1 << 30)) {
                throw new IllegalStateException("No room for more elements");
            }
            // Increase elements array by factor of 2
            resize(currentArrayLength << 1);
        }
    }

    private boolean indexesUniqueAndOrdered(Deque<Long> indexes) {
        long maxIndexSoFar = -1L;
        for (long l : indexes) {
            if (l <= maxIndexSoFar) {
                return false;
            } else {
                maxIndexSoFar = l;
            }
        }
        return true;
    }

    private int getElementsCount() {
        // head and tail are unique and monotonic indexes - therefore we can just use their
        // difference to determine number of header:value pairs stored in the dynamic table.
        // Since head points to the next unused element head == 0 means that there is
        // no elements in the dynamic table
        return head > 0 ? (int) (head - tail) : 0;
    }

    private void resize(int newSize) {
        // newSize is always a power of 2:
        //  - its initial size is a power of 2
        //  - it is shifted 1 bit left every
        //    time when there is not enough space in the 'elements' array
        assert lock.isWriteLockedByCurrentThread();
        int elementsCnt = getElementsCount();
        final int oldSize = elements.length;

        if (newSize < elementsCnt) {
            throw new IllegalArgumentException("New size is too low to hold existing elements");
        }

        HeaderField[] newElements = new HeaderField[newSize];
        if (elementsCnt == 0) {
            elements = newElements;
            return;
        }
        long headID = head - 1;
        final int oldTailIdx = (int) (tail & (oldSize - 1));
        final int oldHeadIdx = (int) (headID & (oldSize - 1));
        final int newTailIdx = (int) (tail & (newSize - 1));
        final int newHeadIdx = (int) (headID & (newSize - 1));

        if (oldTailIdx <= oldHeadIdx) {
            // Elements in an old array are stored in a continuous segment
            if (newTailIdx <= newHeadIdx) {
                // Elements in a new array will be stored in a continuous segment
                System.arraycopy(elements, oldTailIdx, newElements, newTailIdx, elementsCnt);
            } else {
                // Elements in a new array will split in two segments due to wrapping around
                // the end of a new array.
                int sizeFromNewTailToEnd = newSize - newTailIdx;
                System.arraycopy(elements, oldTailIdx, newElements, newTailIdx, sizeFromNewTailToEnd);
                System.arraycopy(elements, oldTailIdx + sizeFromNewTailToEnd,
                        newElements, 0, newHeadIdx + 1);
            }
        } else {
            // Elements in an old array are split in two segments
            if (newTailIdx <= newHeadIdx) {
                // Elements in a new array will be stored in a continuous segment
                int firstSegmentSize = oldSize - oldTailIdx;
                System.arraycopy(elements, oldTailIdx, newElements, newTailIdx, firstSegmentSize);
                System.arraycopy(elements, 0,
                        newElements, newTailIdx + firstSegmentSize, oldHeadIdx + 1);
            } else {
                // Elements in a new array will be stored in two segments
                // Size from the tail to the end in an old array
                int oldPart1Size = oldSize - oldTailIdx;
                // Size from the tail to the end in a new array
                int newPart1Size = newSize - newTailIdx;
                if (oldPart1Size <= newPart1Size) {
                    // Segment from tail to the end of an old array
                    // fits into the corresponding segment in a new array
                    System.arraycopy(elements, oldTailIdx, newElements, newTailIdx, oldPart1Size);
                    int leftToCopyToNewPart1 = newPart1Size - oldPart1Size;
                    System.arraycopy(elements, 0, newElements,
                            newTailIdx + oldPart1Size, leftToCopyToNewPart1);
                    System.arraycopy(elements, leftToCopyToNewPart1,
                            newElements, 0, newHeadIdx + 1);
                } else { // oldPart1Size > newPart1Size
                    // Not possible given two restrictions:
                    //   - we do not allow rewriting of entries if size is not enough,
                    //     IAE is thrown above.
                    //   - the size of elements holder array can only be a power of 2
                    throw new AssertionError("Not possible dynamic table indexes configuration");
                }
            }
        }
        elements = newElements;
    }

    /**
     * Method returns number of elements inserted to the dynamic table.
     * Since element ids start from 0 the returned value is equal
     * to the id of the head element plus one.
     * @return number of elements in the dynamic table
     */
    public long insertCount() {
        var rl = lock.readLock();
        rl.lock();
        try {
            // head points to the next unallocated element
            return head;
        } finally {
            rl.unlock();
        }
    }

    // Writes an encoder instruction to the encoder stream associated with dynamic table.
    // This method is kept in DynamicTable class since most instructions depend on
    // and/or update the dynamic table state.
    // Also, we want to send encoder instruction and update the dynamic table state while
    // holding the write-lock.
    private void writeEncoderInstruction(EncoderInstructionsWriter writer, int instructionSize,
                                         QueuingStreamPair encoderStreams) {
        if (instructionSize > encoderStreams.credit()) {
            throw new QPackException(H3_CLOSED_CRITICAL_STREAM,
                    new IOException("QPACK not enough credit on an encoder stream "
                            + encoderStreams.remoteStreamType()), true);
        }
        boolean done;
        ByteBuffer buffer;
        do {
            if (instructionSize > MAX_BUFFER_SIZE) {
                buffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);
                instructionSize -= MAX_BUFFER_SIZE;
            } else {
                buffer = ByteBuffer.allocate(instructionSize);
            }
            done = writer.write(buffer);
            buffer.flip();
            encoderStreams.submitData(buffer);
        } while (!done);
    }
    private static final int MAX_BUFFER_SIZE = 1024 * 16;
    static final long ENTRY_NOT_INSERTED = -1L;
}
