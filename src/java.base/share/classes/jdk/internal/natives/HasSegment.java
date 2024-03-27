package jdk.internal.natives;

import java.lang.foreign.MemorySegment;

/**
 * Trait indicating a class has a backing MemorySegment
 */
public interface HasSegment {

    /**
     * {@return the segment that backs this instance}
     */
    MemorySegment segment();

}
