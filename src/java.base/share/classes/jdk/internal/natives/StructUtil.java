package jdk.internal.natives;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.*;
import static jdk.internal.natives.CLayouts.C_STRING;

public final class StructUtil {

    private StructUtil() {
    }

    public static boolean equals(MemorySegment a, MemorySegment b) {
        return a.mismatch(b) == -1;
    }

    // Todo: improve when https://bugs.openjdk.org/browse/JDK-8294432 becomes available
    public static int hashCode(MemorySegment segment) {
        return Arrays.hashCode(segment.toArray(JAVA_BYTE));
    }

/*    public static String toString(String type, MemoryLayout layout, MemorySegment segment) {
        var formatter = HexFormat.ofDelimiter(" ");
        return type + "{" +
                layout.toString() + " = " + formatter.formatHex(segment.toArray(JAVA_BYTE)) +
                ", segment = " + segment +
                "}";
    }*/

    private static final HexFormat HEX_FORMAT = HexFormat.ofDelimiter(" ");
    private static final String NL = System.lineSeparator();

    public static String toString(String type, MemoryLayout layout, MemorySegment segment) {
        return type + "{" +
                "segment = " + segment + ", " + NL +
                render(1, layout, 0, 1, segment, 0L) +
                "}";
    }

    private static String render(int indent,
                                 MemoryLayout layout,
                                 int labelLen,
                                 int valueLen,
                                 MemorySegment segment,
                                 long offset) {
        return indent(indent) + labelAndPad(layout, labelLen) + " = " + switch (layout) {
            case AddressLayout al -> {
                MemorySegment s = segment.asSlice(offset, al.byteSize());
                long value = ADDRESS.byteSize() == 4
                        ? Integer.toUnsignedLong(s.get(JAVA_INT, 0))
                        : s.get(JAVA_LONG, 0);

                // Magic layout that is being rendered as a real string
                String suffix = (value == 0)
                        ? " [null]"
                        : isCString(al)
                                ? " '" + getUtf8StringOrNull(segment, offset) + "'"
                                : "";

                yield "0x" + HEX_FORMAT.toHexDigits(value) + " (" + value + ")" + suffix;
            }
            case ValueLayout.OfByte ob -> {
                byte value = segment.get(ob, offset);
                yield leftAdjust(ob, valueLen) + "0x" + HEX_FORMAT.toHexDigits(value) + " (" + value + ")";
            }
            case ValueLayout.OfBoolean __ -> {
                byte value = segment.get(JAVA_BYTE, offset);
                yield leftAdjust(JAVA_BYTE, valueLen) + "0x" + HEX_FORMAT.toHexDigits(value) + " (" + (value != 0) + ")";
            }
            case ValueLayout.OfShort os -> {
                short value = segment.get(os, offset);
                yield leftAdjust(os, valueLen) + "0x" + HEX_FORMAT.toHexDigits(value) + " (" + value + ")";
            }
            case ValueLayout.OfChar oc -> {
                char value = segment.get(oc, offset);
                yield leftAdjust(oc, valueLen) + "0x" + HEX_FORMAT.toHexDigits(value) + " (" + (Character.isAlphabetic(value) ? value : ".") + ")";
            }
            case ValueLayout.OfInt oi -> {
                int value = segment.get(oi, offset);
                yield leftAdjust(oi, valueLen) + "0x" + HEX_FORMAT.toHexDigits(value) + " (" + value + ")";
            }
            case ValueLayout.OfLong ol -> {
                long value = segment.get(ol, offset);
                yield leftAdjust(ol, valueLen) + "0x" + HEX_FORMAT.toHexDigits(value) + " (" + value + ")";
            }
            case ValueLayout.OfFloat of -> {
                float value = segment.get(of, offset);
                yield leftAdjust(of, valueLen) + "0x" + HEX_FORMAT.toHexDigits(Float.floatToIntBits(value)) + " (" + value + ")";
            }
            case ValueLayout.OfDouble od -> {
                double value = segment.get(od, offset);
                yield leftAdjust(od, valueLen) + "0x" + HEX_FORMAT.toHexDigits(Double.doubleToLongBits(value)) + " (" + value + ")";
            }
            case GroupLayout gl -> {
                StringBuilder sb = new StringBuilder(gl instanceof StructLayout ? "struct" : "union").append(" {").append(NL);
                int maxLabelLen = gl.memberLayouts().stream()
                        .map(StructUtil::label)
                        .mapToInt(String::length)
                        .max()
                        .orElse(0);

                int maxValueLayoutLen = (int) gl.memberLayouts().stream()
                        .filter(ValueLayout.class::isInstance)
                        .map(ValueLayout.class::cast)
                        .mapToLong(ValueLayout::byteSize)
                        .max()
                        .orElse(1);

                for (var member : gl.memberLayouts()) {
                    sb.append(render(indent + 1, member, maxLabelLen, maxValueLayoutLen, segment, offset));
                    offset += member.byteSize();
                }
                sb.append(indent(indent)).append('}');
                yield sb.toString();
            }
            case SequenceLayout sl -> {
                StringBuilder sb = new StringBuilder("sequence").append(" [").append(NL);
                MemoryLayout element = sl.elementLayout();
                int maxValueLayoutLen = element instanceof ValueLayout vl
                        ? (int) vl.byteSize()
                        : 1;

                for (long i = 0; i < sl.elementCount(); i++) {
                    sb.append(render(indent + 1, element, 0, maxValueLayoutLen, segment, offset));
                    offset += element.byteSize();
                }
                sb.append(indent(indent)).append(']');
                yield sb.toString();
            }
            case PaddingLayout pl -> "padding [" + pl.byteSize() + "]";
        } + NL;
    }

    private static boolean isCString(AddressLayout layout) {
        return layout.targetLayout()
                .map(l -> l.name().orElse(""))
                .filter("c_string"::equals)
                .isPresent();
    }

    private static String leftAdjust(MemoryLayout layout, int len) {
        return pad((len - (int) layout.byteSize()) * 2);
    }

    private static String labelAndPad(MemoryLayout layout, int len) {
        String label = label(layout);
        return len > 0
                ? label + pad(len - label.length())
                : label;
    }

    private static String label(MemoryLayout layout) {
        return layout.name().orElse(layout.toString());
    }

    private static String indent(int count) {
        return pad(count * 2);
    }

    private static String pad(int count) {
        return " ".repeat(count);
    }

    public static String getUtf8StringOrNull(MemorySegment segment, long offset) {
        var location = segment.get(C_STRING, offset);
        if (location.equals(MemorySegment.NULL)) {
            return null;
        }
        return location.getUtf8String(0);
    }

    record StructMapperImpl<T>(MemoryLayout layout,
                               Function<? super MemorySegment, ? extends T> constructor)
            implements StructMapper<T> {

        @Override
        public T of(MemorySegment segment) {
            if (segment.byteSize() < layout.byteSize()) {
                throw new IllegalArgumentException(
                        "Insufficient segment size: " + segment.byteSize()
                                + " but needs " + layout.byteSize() + " for " + layout);
            }
            return constructor.apply(segment);
        }

        @Override
        public T allocate(Arena arena) {
            return of(arena.allocate(layout));
        }

        public List<T> ofElements(int size, MemorySegment segment) {
            List<T> result = segment.elements(layout)
                    .limit(size)
                    .map(this::of)
                    .toList();
            if (result.size() < size) {
                throw new NoSuchElementException("Insufficient segment size: " + segment);
            }
            return result;
        }

        @Override
        public List<T> ofElements(MemorySegment segment) {
            return ofElements(Math.toIntExact(segment.byteSize() / layout.byteSize()), segment);
        }
    }

}
