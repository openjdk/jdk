package jdk.internal.access.foreign;

import java.io.FileDescriptor;

/**
 * This proxy interface is required to allow access to @{code MappedMemoryUtils} methods from {@code ScopedMemoryAccess}.
 * This allows to avoid pesky initialization issues in the middle of memory mapped scoped methods.
 */
public interface MappedMemoryUtilsProxy {
    boolean isLoaded(long address, boolean isSync, long size);
    void load(long address, boolean isSync, long size);
    void unload(long address, boolean isSync, long size);
    void force(FileDescriptor fd, long address, boolean isSync, long index, long length);
}
