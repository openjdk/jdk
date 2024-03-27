package jdk.internal.natives;

public interface HasCopyFrom<T extends HasCopyFrom<T>> extends HasSegment {

    default void copyFrom(T other) {
        if (segment().byteSize() != other.segment().byteSize()) {
            throw new IllegalArgumentException("Size mismatch:" + segment().byteSize() + " != " + other.segment().byteSize());
        }
        segment().copyFrom(other.segment());
    }

}
