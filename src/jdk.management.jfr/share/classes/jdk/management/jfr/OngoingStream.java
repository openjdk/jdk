package jdk.management.jfr;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;

import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.internal.PlatformRecording;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.SecuritySupport.SafePath;
import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.RecordingInput;
import jdk.jfr.internal.consumer.RepositoryFiles;
import jdk.jfr.internal.management.ManagementSupport;

final class OngoingStream extends Stream {
    private static final byte[] EMPTY_ARRAY = new byte[0];

    private static final int HEADER_SIZE = DiskRepository.HEADER_SIZE;
    private static final int HEADER_FILE_STATE_POSITION = DiskRepository.HEADER_FILE_STATE_POSITION;
    private static final byte MODIFYING_STATE = DiskRepository.MODIFYING_STATE;

    private final RepositoryFiles repositoryFiles;
    private final Recording recording;
    private final int blockSize;
    private final long endTimeNanos;
    private final byte[] headerBytes = new byte[HEADER_SIZE];
    private RecordingInput input;
    private ChunkHeader header;
    private long position;
    private long startTimeNanos;
    private Path path;

        private boolean first = true;

    public OngoingStream(Recording recording, int blockSize, long startTimeNanos, long endTimeNanos) {
        super();
        this.recording = recording;
        this.blockSize = blockSize;
        this.startTimeNanos = startTimeNanos;
        this.endTimeNanos = endTimeNanos;
        this.repositoryFiles = new RepositoryFiles(SecuritySupport.PRIVILEGED, null);
    }

	public synchronized byte[] read() throws IOException {
		try {
			return readBytes();
		} catch (IOException ioe) {
			if (recording.getState() == RecordingState.CLOSED) {
				// Recording closed, return null;
				return null;
			}
			// Something unexpected has happened.
			throw ioe;
		}
	}

	public synchronized byte[] readBytes() throws IOException {
		touch();
		while (true) {
			if (recording.getState() == RecordingState.NEW) {
				return EMPTY_ARRAY;
			}

			if (recording.getState() == RecordingState.DELAYED) {
				return EMPTY_ARRAY;
			}

			if (first) {
				// In case stream starts before recording
				long s = ManagementSupport.getStartTimeNanos(recording);
				startTimeNanos = Math.max(s, startTimeNanos);
				first = false;
			}

			if (startTimeNanos > endTimeNanos) {
				return null;
			}
			if (isRecordingClosed()) {
				closeInput();
				return null;
			}
			if (!ensurePath()) {
				return EMPTY_ARRAY;
			}
			if (!ensureInput()) {
				return EMPTY_ARRAY;
			}
			if (position < header.getChunkSize()) {
				long size = Math.min(header.getChunkSize() - position, blockSize);
				return readBytes((int) size);
			}
			if (header.isFinished()) {
				if (header.getDurationNanos() < 1) {
					throw new IOException("No progress");
				}
				startTimeNanos += header.getDurationNanos();
				Instant timestamp = MBeanUtils.epochNanosToInstant(startTimeNanos);
				ManagementSupport.removeBefore(recording, timestamp);
				closeInput();
			} else {
				header.refresh();
				if (position >= header.getChunkSize()) {
					return EMPTY_ARRAY;
				}
			}
		}
	}

    private boolean isRecordingClosed() {
        return recording != null && recording.getState() == RecordingState.CLOSED;
    }

    private void closeInput() {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ioe) {
                // ignore
            }
            input = null;
            position = 0;
            path = null;
        }
    }

    private byte[] readBytes(int size) throws IOException {
        if (position == 0) {
            return readWithHeader(size);
        } else {
            return readNonHeader(size);
        }
    }

    private byte[] readNonHeader(int size) throws IOException {
        byte[] result = new byte[size];
        input.readFully(result);
        position += size;
        return result;
    }

    private byte[] readWithHeader(int size) throws IOException {
        byte[] bytes = new byte[Math.max(HEADER_SIZE, size)];
        for (int attempts = 0; attempts < 25; attempts++) {
            // read twice and check files state to avoid simultaneous change by JVM
            input.position(0);
            input.readFully(bytes, 0, HEADER_SIZE);
            input.position(0);
            input.readFully(headerBytes);
            if (headerBytes[HEADER_FILE_STATE_POSITION] != MODIFYING_STATE) {
                if (equalBytes(bytes, headerBytes, HEADER_SIZE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    // 0-3: magic
                    // 4-5: major
                    // 6-7: minor
                    // 8-15: chunk size
                    buffer.putLong(8, HEADER_SIZE);
                    // 16-23: constant pool offset
                    buffer.putLong(16, 0);
                    // 24-31: metadata offset
                    buffer.putLong(24, 0);
                    // 32-39: chunk start nanos
                    // 40-47 duration
                    buffer.putLong(40, 0);
                    // 48-55: chunk start ticks
                    // 56-63: ticks per second
                    // 64: file state
                    buffer.put(64,(byte)1);
                    // 65-67: extension bit
                    int left = bytes.length - HEADER_SIZE;
                    input.readFully(bytes, HEADER_SIZE, left);
                    position += bytes.length;
                    startTimeNanos = buffer.getLong(32);
                    return bytes;
                }
            }
            takeNap();
        }
        return EMPTY_ARRAY;
    }

    private void takeNap() throws IOException {
        try {
            Thread.sleep(10);
        } catch (InterruptedException ie) {
            throw new IOException("Read operation interrupted", ie);
        }
    }

    boolean equalBytes(byte[] a, byte[] b, int size) {
        for (int i = 0; i < size; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean ensureInput() throws IOException {
        if (input == null) {
            if (SecuritySupport.getFileSize(new SafePath(path)) < HEADER_SIZE) {
                return false;
            }
            input = new RecordingInput(path.toFile(), SecuritySupport.PRIVILEGED);
            header = new ChunkHeader(input);
        }
        return true;
    }

    private boolean ensurePath() {
        if (path == null) {
            boolean validStartTime = recording != null || startTimeNanos != Long.MIN_VALUE;
            if (validStartTime) {
                path = repositoryFiles.firstPath(startTimeNanos);
            } else {
                path = repositoryFiles.lastPath();
            }
        }
        return path != null;
    }

    @Override
    public void close() throws IOException {
        closeInput();
    }
}
