package jdk.jfr.internal.event;

import jdk.jfr.internal.JVM;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.StringPool;
import jdk.jfr.internal.consumer.StringParser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BufferedEventWriter {
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private DataOutputStream writer = new DataOutputStream(buffer);

    private PlatformEventType eventType;

    private long eventId;
    private Thread targetThread;
    private boolean hasDuration;
    private boolean hasEventThread;
    private boolean hasStackTrace;

    private BufferedEventWriter() {
    }

    public static BufferedEventWriter getEventWriter() {
        return new BufferedEventWriter();
    }

    public boolean beginEvent(EventConfiguration configuration, long typeId) {
        // This check makes sure the event type matches what was added by instrumentation.
        if (configuration.id() != typeId) {
            throw new InternalError("Unexpected type id " + typeId);
        }
        this.eventType = configuration.platformEventType();
        this.eventId = eventType.getId();
        return true;
    }

    public void putTargetThread(Thread t) {
        this.targetThread = t;
    }

    public void putHasDuration() {
        this.hasDuration = true;
    }

    public void putHasEventThread() {
        this.hasEventThread = true;
    }

    public void putHasStackTrace() {
        this.hasStackTrace = true;
    }

    public boolean endEvent() {
        byte[] payload = buffer.toByteArray();
        JVM.sendAsyncEvent(targetThread, eventId, hasDuration, hasEventThread, hasStackTrace, payload);
        return true;
    }

    public void putBoolean(boolean i) {
        try {
            writer.writeBoolean(i);
        } catch (IOException e) {
            // Should never happen
        }
    }

    public void putByte(byte i) {
        try {
            writer.writeByte(i);
        } catch (IOException e) {
            // Should never happen
        }
    }

    public void putChar(char v) {
        try {
            writer.writeChar(v);
        } catch (IOException e) {
            // Should never happen
        }
    }


    public void putShort(short v) {
        try {
            writer.writeShort(v);
        } catch (IOException e) {
            // Should never happen
        }
    }

    public void putInt(int v) {
        try {
            writer.write(v);
        } catch (IOException e) {
            // Should never happen
        }
    }
    public void putFloat(float i) {
        try {
            writer.writeFloat(i);
        } catch (IOException e) {
            // Should never happen
        }
    }

    public void putLong(long v) {
        try {
            writer.writeLong(v);
        } catch (IOException e) {
            // Should never happen
        }
    }

    public void putDouble(double i) {
        try {
            writer.writeDouble(i);
        } catch (IOException e) {
            // Should never happen
        }
    }

    public void putString(String s) {
        if (s == null) {
            putByte(StringParser.Encoding.NULL.byteValue());
            return;
        }
        int length = s.length();
        if (length == 0) {
            putByte(StringParser.Encoding.EMPTY_STRING.byteValue());
            return;
        }
        if (length > StringPool.MIN_LIMIT && length < StringPool.MAX_LIMIT) {
            long l = StringPool.addString(s, Thread.currentThread().isVirtual());
            if (l > 0) {
                putByte(StringParser.Encoding.CONSTANT_POOL.byteValue());
                putLong(l);
                return;
            }
        }
        putStringValue(s);
        return;
    }

    private void putStringValue(String s) {
        int length = s.length();
        putByte(StringParser.Encoding.CHAR_ARRAY.byteValue()); // 1 byte
        putUncheckedInt(length); // max 5 bytes
        for (int i = 0; i < length; i++) {
            putUncheckedChar(s.charAt(i)); // max 3 bytes
        }
    }

    private void putUncheckedInt(int v) {
        putUncheckedLong(v & 0x00000000ffffffffL);
    }

    private void putUncheckedChar(char v) {
        putUncheckedLong(v);
    }

    private void putUncheckedLong(long v) {
        if ((v & ~0x7FL) == 0L) {
            putByte((byte) v); // 0-6
            return;
        }
        putByte((byte) (v | 0x80L)); // 0-6
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putByte((byte) v); // 7-13
            return;
        }
        putByte((byte) (v | 0x80L)); // 7-13
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putByte((byte) v); // 14-20
            return;
        }
        putByte((byte) (v | 0x80L)); // 14-20
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putByte((byte) v); // 21-27
            return;
        }
        putByte((byte) (v | 0x80L)); // 21-27
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putByte((byte) v); // 28-34
            return;
        }
        putByte((byte) (v | 0x80L)); // 28-34
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putByte((byte) v); // 35-41
            return;
        }
        putByte((byte) (v | 0x80L)); // 35-41
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putByte((byte) v); // 42-48
            return;
        }
        putByte((byte) (v | 0x80L)); // 42-48
        v >>>= 7;

        if ((v & ~0x7FL) == 0L) {
            putByte((byte) v); // 49-55
            return;
        }
        putByte((byte) (v | 0x80L)); // 49-55
        putByte((byte) (v >>> 7)); // 56-63, last byte as is.
    }


    public void putThread(Thread athread) {
        if (athread == null) {
            putLong(0L);
        } else {
            putLong(JVM.getThreadId(athread));
        }
    }

    public void putClass(Class<?> aClass) {
        if (aClass == null) {
            putLong(0L);
        } else {
            putLong(JVM.getClassId(aClass));
        }
    }
}
