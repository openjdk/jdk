/*
 * @test
 * @bug 8300487
 * @summary Test import/export constructors and methods
 * @author Martin Buchholz
 * @key randomness
 */

import java.nio.*;
import java.util.*;

public class ImportExport {
    final Random rnd = new Random();

    void equal(byte[] x, byte[] y) {
        check(Arrays.equals(x, y));
    }

    void equal(long[] x, long[] y) {
        check(Arrays.equals(x, y));
    }

    void equal(byte[] bytes, NaturalsBitSet s) {
        equal(s, NaturalsBitSet.valueOf(bytes));
        equal(s, NaturalsBitSet.valueOf(ByteBuffer.wrap(bytes)));
        equal(s, NaturalsBitSet.valueOf(
                  ByteBuffer.wrap(
                      Arrays.copyOf(bytes, bytes.length + 8 + rnd.nextInt(8)))
                  .order(ByteOrder.LITTLE_ENDIAN)
                  .asLongBuffer()));
    }

    void checkEmptyBitSet(NaturalsBitSet s) {
        equal(s.toByteArray(), new byte[0]);
        equal(s.toLongArray(), new long[0]);
        check(s.isEmpty());
    }

    void test(String[] args) throws Throwable {
        for (int i = 0; i < 17; i++) {
            byte[] bytes = new byte[i];
            NaturalsBitSet s = new NaturalsBitSet();
            equal(bytes, s);
            equal(NaturalsBitSet.valueOf(bytes).toByteArray(), new byte[0]);
            if (i > 0) {
                int k = rnd.nextInt(i);
                for (int j = 0; j < 8; j++) {
                    bytes[k] |= 1 << j;
                    s.set(8*k+j);
                    equal(bytes, s);
                    byte[] expected = new byte[k+1]; expected[k] = bytes[k];
                    equal(NaturalsBitSet.valueOf(bytes).toByteArray(), expected);
                    ByteBuffer bb = ByteBuffer.wrap(bytes);
                    bb.position(k);
                    equal(NaturalsBitSet.valueOf(bb).toByteArray(),
                          new byte[]{bytes[k]});
                }
            }
        }
        for (int i = 0; i < 100; i++) {
            byte[] bytes = new byte[rnd.nextInt(17)];
            for (int j = 0; j < bytes.length; j++)
                bytes[j] = (byte) rnd.nextInt(0x100);
            NaturalsBitSet s = NaturalsBitSet.valueOf(bytes);
            byte[] expected = s.toByteArray();
            equal(expected.length, (s.length()+7)/8);
            if (bytes.length == 0)
                continue;
            if (expected.length > 0)
                check(expected[expected.length-1] != 0);
            if (bytes[bytes.length-1] != 0)
                equal(bytes, expected);
            int n = rnd.nextInt(8 * bytes.length);
            equal(s.get(n), ((bytes[n/8] & (1<<(n%8))) != 0));
        }

        for (int i = 0; i < 3; i++) {
            checkEmptyBitSet(NaturalsBitSet.valueOf(new byte[i]));
            checkEmptyBitSet(NaturalsBitSet.valueOf(ByteBuffer.wrap(new byte[i])));
            checkEmptyBitSet(NaturalsBitSet.valueOf(new byte[i*64]));
            checkEmptyBitSet(NaturalsBitSet.valueOf(ByteBuffer.wrap(new byte[i*64])));
            checkEmptyBitSet(NaturalsBitSet.valueOf(new long[i]));
            checkEmptyBitSet(NaturalsBitSet.valueOf(LongBuffer.wrap(new long[i])));
        }

        {
            long[] longs = new long[rnd.nextInt(10)];
            for (int i = 0; i < longs.length; i++)
                longs[i] = rnd.nextLong();
            LongBuffer b1 = LongBuffer.wrap(longs);
            LongBuffer b2 = LongBuffer.allocate(longs.length + 10);
            for (int i = 0; i < b2.limit(); i++)
                b2.put(i, rnd.nextLong());
            int beg = rnd.nextInt(10);
            b2.position(beg);
            b2.put(longs);
            b2.limit(b2.position());
            b2.position(beg);
            NaturalsBitSet s1 = NaturalsBitSet.valueOf(longs);
            NaturalsBitSet s2 = NaturalsBitSet.valueOf(b1);
            NaturalsBitSet s3 = NaturalsBitSet.valueOf(b2);
            equal(s1, s2);
            equal(s1, s3);
            if (longs.length > 0 && longs[longs.length -1] != 0) {
                equal(longs, s1.toLongArray());
                equal(longs, s2.toLongArray());
                equal(longs, s3.toLongArray());
            }
            for (int i = 0; i < 64 * longs.length; i++) {
                equal(s1.get(i), ((longs [i/64] & (1L<<(i%64))) != 0));
                equal(s2.get(i), ((b1.get(i/64) & (1L<<(i%64))) != 0));
                equal(s3.get(i), ((b2.get(b2.position()+i/64) & (1L<<(i%64))) != 0));
            }
        }
    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new ImportExport().instanceMain(args);}
    void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
