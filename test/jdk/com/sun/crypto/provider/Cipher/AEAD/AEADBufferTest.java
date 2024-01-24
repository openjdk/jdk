/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @summary Use Cipher update and doFinal with a mixture of byte[], bytebuffer,
 * and offset while verifying return values.  Also using different and
 * in-place buffers.
 *
 * in-place is not tested with different buffer types as it is not a logical
 * scenario and is complicated by getOutputSize calculations.
 */

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;

public class AEADBufferTest implements Cloneable {

    // Data type for the operation
    enum dtype { BYTE, HEAP, DIRECT };
    // Data map
    static HashMap<String, List<Data>> datamap = new HashMap<>();
    // List of enum values for order of operation
    List<dtype> ops;

    static final String AES = "AES";
    // The remaining input data length is inserted at the particular index
    // in sizes[] during execution.
    static final int REMAINDER = -1;

    String algo;
    boolean same = true;
    int[] sizes;
    boolean incremental = false;
    // In some cases the theoretical check is too complicated to verify
    boolean theoreticalCheck;
    List<Data> dataSet;
    int inOfs = 0, outOfs = 0;
    static HexFormat hex = HexFormat.of();

    static class Data {
        int id;
        SecretKey key;
        byte[] iv;
        int counter;  // for CC20
        byte[] pt;
        byte[] aad;
        byte[] ct;
        byte[] tag;
        int blockSize;  // 16 for GCM, 0 for CC20

        Data(String keyalgo, int id, String key, String iv, byte[] pt, String aad,
            String ct, String tag) {
            this(keyalgo, id, key, iv, 0,pt, aad, ct,tag);
        }

        Data(String keyalgo, int id, String key, String iv, String pt, String aad,
            String ct, String tag) {
            this(keyalgo, id, key, iv, HexToBytes(pt), aad, ct, tag);
        }

        Data(String keyalgo, int id, String key, String iv, int counter, byte[] pt, String aad,
            String ct, String tag) {
            this.id = id;
            this.key = new SecretKeySpec(HexToBytes(key), keyalgo);
            this.iv = HexToBytes(iv);
            this.counter = counter;
            this.pt = pt;
            this.aad = HexToBytes(aad);
            this.ct = HexToBytes(ct);
            this.tag = HexToBytes(tag);
            this.blockSize = (keyalgo.equals(AES) ? 16 : 0);
        }

        Data(String keyalgo, int id, String key, String iv, int counter, String pt, String aad,
            String ct, String tag) {
            this(keyalgo, id, key, iv, counter, HexToBytes(pt), aad, ct, tag);
        }

        Data(String keyalgo, int id, String key, int ivLen, int ptlen) {
            this.id = id;
            this.key = new SecretKeySpec(HexToBytes(key), keyalgo);
            iv = new byte[ivLen];
            counter = 0;
            pt = new byte[ptlen];
            tag = new byte[16];
            aad = new byte[0];
            boolean isGCM = keyalgo.equals(AES);
            this.blockSize = (isGCM ? 16 : 0);
            byte[] tct = null;
            try {
                SecureRandom r = new SecureRandom();
                r.nextBytes(iv);
                r.nextBytes(pt);
                Cipher c = Cipher.getInstance(isGCM ? "AES/GCM/NoPadding": "ChaCha20-Poly1305");
                c.init(Cipher.ENCRYPT_MODE, this.key,
                    getAPS(keyalgo, tag.length, iv));
                tct = c.doFinal(pt);
            } catch (Exception e) {
                throw new RuntimeException("Error in generating data for length " +
                    ptlen, e);
            }
            ct = new byte[ptlen];
            System.arraycopy(tct, 0, ct, 0, ct.length);
            System.arraycopy(tct, ct.length, tag, 0, tag.length);
        }

        private static byte[] HexToBytes(String hexVal) {
            if (hexVal == null) {
                return new byte[0];
            }
            return hex.parseHex(hexVal);
        }

    }

    /**
     * Construct a test with an algorithm and a list of dtype.
     * @param algo Algorithm string
     * @param ops List of dtypes.  If only one dtype is specified, only a
     *            doFinal operation will occur.  If multiple dtypes are
     *            specified, the last is a doFinal, the others are updates.
     */
    AEADBufferTest(String algo, List<dtype> ops) {
        this.algo = algo;
        this.ops = ops;
        theoreticalCheck = true;
        dataSet = datamap.get(algo);
    }

    public AEADBufferTest clone() throws CloneNotSupportedException{
        return (AEADBufferTest)super.clone();
    }

    /**
     * Define particular data sizes to be tested.  "REMAINDER", which has a
     * value of -1, can be used to insert the remaining input text length at
     * that index during execution.
     * @param sizes Data sizes for each dtype in the list.
     */
    AEADBufferTest dataSegments(int[] sizes) {
        this.sizes = sizes;
        return this;
    }

    /**
     * Do not perform in-place operations
     */
    AEADBufferTest differentBufferOnly() {
        this.same = false;
        return this;
    }

    /**
     * Enable incrementing through each data size available.  This can only be
     * used when the List has more than one dtype entry.
     */
    AEADBufferTest incrementalSegments() {
        this.incremental = true;
        return this;
    }

    /**
     * Specify a particular test dataset.
     *
     * @param id id value for the test data to used in this test.
     */
    AEADBufferTest dataSet(int id) throws Exception {
        for (Data d : datamap.get(algo)) {
            if (d.id == id) {
                dataSet = List.of(d);
                return this;
            }
        }
        throw new Exception("Unable to find dataSet id = " + id);
    }

    /**
     * Set both input and output offsets to the same offset
     * @param offset value for inOfs and outOfs
     * @return
     */
    AEADBufferTest offset(int offset) {
        this.inOfs = offset;
        this.outOfs = offset;
        return this;
    }

    /**
     * Set the input offset
     * @param offset value for input offset
     */
    AEADBufferTest inOfs(int offset) {
        this.inOfs = offset;
        return this;
    }

    /**
     * Set the output offset
     * @param offset value for output offset
     */
    AEADBufferTest outOfs(int offset) {
        this.outOfs = offset;
        return this;
    }

    /**
     * Reverse recursive loop that starts at the end-1 index, going to 0, in
     * the size array to calculate all the possible sizes.
     * It returns the remaining data size not used in the loop.  This remainder
     * is used for the end index which is the doFinal op.
     */
    int inc(int index, int max, int total) {
        if (sizes[index] == max - total) {
            sizes[index + 1]++;
            total++;
            sizes[index] = 0;
        } else if (index == 0) {
            sizes[index]++;
        }

        total += sizes[index];
        if (index > 0) {
            return inc(index - 1, max, total);
        }
        return total;
    }

    // Call recursive loop and take returned remainder value for last index
    boolean incrementSizes(int max) {
        sizes[ops.size() - 1] = max - inc(ops.size() - 2, max, 0);
        if (sizes[ops.size() - 2] == max) {
            // We are at the end, exit test loop
            return false;
        }
        return true;
    }

    void test() throws Exception {
        int i = 1;
        System.err.println("Algo: " + algo + " \tOps: " + ops.toString());
        for (Data data : dataSet) {

            // If incrementalSegments is enabled, run through that test only
            if (incremental) {
                if (ops.size() < 2) {
                    throw new Exception("To do incrementalSegments you must " +
                        "have more that 1 dtype in the list");
                }
                sizes = new int[ops.size()];

                while (incrementSizes(data.pt.length)) {
                    System.err.print("Encrypt:  Data Index: " + i + " \tSizes[ ");
                    for (int v : sizes) {
                        System.err.print(v + " ");
                    }
                    System.err.println("]");
                    encrypt(data);
                }
                Arrays.fill(sizes, 0);

                while (incrementSizes(data.ct.length + data.tag.length)) {
                    System.err.print("Decrypt:  Data Index: " + i + " \tSizes[ ");
                    for (int v : sizes) {
                        System.err.print(v + " ");
                    }
                    System.err.println("]");
                    decrypt(data);
                }

            } else {
                // Default test of 0 and 2 offset doing in place and different
                // i/o buffers
                System.err.println("Encrypt:  Data Index: " + i);
                encrypt(data);

                System.err.println("Decrypt:  Data Index: " + i);
                decrypt(data);
            }
            i++;
        }
    }

    // Setup data for encryption
    void encrypt(Data data) throws Exception {
        byte[] input, output;

        input = data.pt;
        output = new byte[data.ct.length + data.tag.length];
        System.arraycopy(data.ct, 0, output, 0, data.ct.length);
        System.arraycopy(data.tag, 0, output, data.ct.length,
            data.tag.length);

        // Test different input/output buffers
        System.err.println("\tinput len: " + input.length + "  inOfs " +
            inOfs + "  outOfs " + outOfs + "  in/out buffer: different");
        crypto(true, data, input, output);

        // Test with in-place buffers
        if (same) {
            System.err.println("\tinput len: " + input.length + "  inOfs " +
                inOfs + "  outOfs " + outOfs + "  in/out buffer: in-place");
            cryptoSameBuffer(true, data, input, output);
        }
    }

    // Setup data for decryption
    void decrypt(Data data) throws Exception {
        byte[] input, output;

        input = new byte[data.ct.length + data.tag.length];
        System.arraycopy(data.ct, 0, input, 0, data.ct.length);
        System.arraycopy(data.tag, 0, input, data.ct.length, data.tag.length);
        output = data.pt;

        // Test different input/output buffers
        System.err.println("\tinput len: " + input.length + "  inOfs " +
            inOfs + "  outOfs " + outOfs + "  in/out buffer: different");
        crypto(false, data, input, output);

        // Test with in-place buffers
        if (same) {
            System.err.println("\tinput len: " + input.length + "  inOfs " +
                inOfs + "  outOfs " + outOfs + "  in-place: same");
            cryptoSameBuffer(false, data, input, output);
        }
    }

    static AlgorithmParameterSpec getAPS(String algo, int tLen, byte[] iv) {
        return switch (algo) {
            case "AES", "AES/GCM/NoPadding" ->
                new GCMParameterSpec(tLen * 8, iv);
            case "CC20", "ChaCha20-Poly1305" -> new IvParameterSpec(iv);
            default -> null;
        };
    }

    /**
     * Perform cipher operation using different input and output buffers.
     *   This method allows mixing of data types (byte, heap, direct).
     */
    void crypto(boolean encrypt, Data d, byte[] input, byte[] output)
        throws Exception {
        byte[] pt = new byte[input.length + inOfs];
        System.arraycopy(input, 0, pt, inOfs, input.length);
        byte[] expectedOut = new byte[output.length + outOfs];
        System.arraycopy(output, 0, expectedOut, outOfs, output.length);
        int plen = input.length / ops.size(); // partial input length
        int theoreticallen;// expected output length
        int dataoffset = 0; // offset of unconsumed data in pt
        int index = 0; // index of which op we are on
        int rlen; // result length
        int pbuflen = 0; // plen remaining in the internal buffers

        Cipher cipher = Cipher.getInstance(algo);
        cipher.init((encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE),
            d.key, getAPS(algo, d.tag.length, d.iv));
        cipher.updateAAD(d.aad);

        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        ba.write(new byte[outOfs], 0, outOfs);
        for (dtype v : ops) {
            if (index < ops.size() - 1) {
                if (sizes != null && input.length > 0) {
                    if (sizes[index] == -1) {
                        plen = input.length - dataoffset;
                    } else {
                        if (sizes[index] > input.length) {
                            plen = input.length;
                        } else {
                            plen = sizes[index];
                        }
                    }
                }

                int olen = cipher.getOutputSize(plen) + outOfs;

                /*
                 * The theoretical limit is the length of the data sent to
                 * update() + any data might be setting in CipherCore or AEAD
                 * internal buffers % the block size.
                 */
                theoreticallen = (plen + pbuflen) - (d.blockSize > 0 ?
                    (plen + pbuflen) % d.blockSize : 0);

                // Update operations
                switch (v) {
                    case BYTE -> {
                        byte[] out = new byte[olen];
                        rlen = cipher.update(pt, dataoffset + inOfs, plen, out,
                            outOfs);
                        ba.write(out, outOfs, rlen);
                    }
                    case HEAP -> {
                        ByteBuffer b = ByteBuffer.allocate(plen + outOfs);
                        b.position(outOfs);
                        b.put(pt, dataoffset + inOfs, plen);
                        b.flip();
                        b.position(outOfs);
                        ByteBuffer out = ByteBuffer.allocate(olen);
                        out.position(outOfs);
                        rlen = cipher.update(b, out);
                        ba.write(out.array(), outOfs, rlen);
                    }
                    case DIRECT -> {
                        ByteBuffer b = ByteBuffer.allocateDirect(plen + outOfs);
                        b.position(outOfs);
                        b.put(pt, dataoffset + inOfs, plen);
                        b.flip();
                        b.position(outOfs);
                        ByteBuffer out = ByteBuffer.allocateDirect(olen);
                        out.position(outOfs);
                        rlen = cipher.update(b, out);
                        byte[] o = new byte[rlen];
                        out.flip();
                        out.position(outOfs);
                        out.get(o, 0, rlen);
                        ba.write(o);
                    }
                    default -> throw new Exception("Unknown op: " + v.name());
                }

                if (theoreticalCheck) {
                    pbuflen += plen - rlen;
                    if (encrypt && rlen != theoreticallen) {
                        throw new Exception("Wrong update return len (" +
                            v.name() + "):  " + "rlen=" + rlen +
                            ", expected output len=" + theoreticallen);
                    }
                }

                dataoffset += plen;
                index++;

            } else {
                // doFinal operation
                plen = input.length - dataoffset;

                int olen = cipher.getOutputSize(plen) + outOfs;
                switch (v) {
                    case BYTE -> {
                        byte[] out = new byte[olen];
                        rlen = cipher.doFinal(pt, dataoffset + inOfs,
                            plen, out, outOfs);
                        ba.write(out, outOfs, rlen);
                    }
                    case HEAP -> {
                        ByteBuffer b = ByteBuffer.allocate(plen + inOfs);
                        b.limit(b.capacity());
                        b.position(inOfs);
                        b.put(pt, dataoffset + inOfs, plen);
                        b.flip();
                        b.position(inOfs);
                        ByteBuffer out = ByteBuffer.allocate(olen);
                        out.limit(out.capacity());
                        out.position(outOfs);
                        rlen = cipher.doFinal(b, out);
                        ba.write(out.array(), outOfs, rlen);
                    }
                    case DIRECT -> {
                        ByteBuffer b = ByteBuffer.allocateDirect(plen + inOfs);
                        b.limit(b.capacity());
                        b.position(inOfs);
                        b.put(pt, dataoffset + inOfs, plen);
                        b.flip();
                        b.position(inOfs);
                        ByteBuffer out = ByteBuffer.allocateDirect(olen);
                        out.limit(out.capacity());
                        out.position(outOfs);
                        rlen = cipher.doFinal(b, out);
                        byte[] o = new byte[rlen];
                        out.flip();
                        out.position(outOfs);
                        out.get(o, 0, rlen);
                        ba.write(o);
                    }
                    default -> throw new Exception("Unknown op: " + v.name());
                }

                if (theoreticalCheck && rlen != olen - outOfs) {
                    throw new Exception("Wrong doFinal return len (" +
                        v.name() + "):  " + "rlen=" + rlen +
                        ", expected output len=" + (olen - outOfs));
                }

                // Verify results
                byte[] ctresult = ba.toByteArray();
                if (ctresult.length != expectedOut.length ||
                    Arrays.compare(ctresult, expectedOut) != 0) {
                    String s = "Ciphertext mismatch (" + v.name() +
                        "):\nresult   (len=" + ctresult.length + "): " +
                        hex.formatHex(ctresult) +
                        "\nexpected (len=" + output.length + "): " +
                        hex.formatHex(output);
                    System.err.println(s);
                    throw new Exception(s);

                }
            }
        }
    }

    /**
     * Perform cipher operation using in-place buffers.  This method does not
     * allow mixing of data types (byte, heap, direct).
     *
     * Mixing data types makes no sense for in-place operations and would
     * greatly complicate the test code.
     */
    void cryptoSameBuffer(boolean encrypt, Data d, byte[] input, byte[] output) throws Exception {

        byte[] data, out;
        if (encrypt) {
            data = new byte[output.length + Math.max(inOfs, outOfs)];
        } else {
            data = new byte[input.length + Math.max(inOfs, outOfs)];
        }

        ByteBuffer bbin = null, bbout = null;
        System.arraycopy(input, 0, data, inOfs, input.length);
        byte[] expectedOut = new byte[output.length + outOfs];
        System.arraycopy(output, 0, expectedOut, outOfs, output.length);
        int plen = input.length / ops.size(); // partial input length
        int theorticallen = plen -
            (d.blockSize > 0 ? plen % d.blockSize : 0);  // output length
        int dataoffset = 0;
        int index = 0;
        int rlen = 0; // result length
        int len = 0;

        Cipher cipher = Cipher.getInstance(algo);
        cipher.init((encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE),
            d.key, getAPS(algo, d.tag.length, d.iv));
        cipher.updateAAD(d.aad);

        // Prepare data
        switch (ops.get(0)) {
            case HEAP -> {
                bbin = ByteBuffer.wrap(data);
                bbin.limit(input.length + inOfs);
                bbout = bbin.duplicate();
            }
            case DIRECT -> {
                bbin = ByteBuffer.allocateDirect(data.length);
                bbout = bbin.duplicate();
                bbin.put(data, 0, input.length + inOfs);
                bbin.flip();
            }
        }

        // Set data limits for bytebuffers
        if (bbin != null) {
            bbin.position(inOfs);
            bbout.limit(output.length + outOfs);
            bbout.position(outOfs);
        }

        // Iterate through each operation
        for (dtype v : ops) {
            if (index < ops.size() - 1) {
                switch (v) {
                    case BYTE -> {
                        rlen = cipher.update(data, dataoffset + inOfs, plen,
                            data, len + outOfs);
                    }
                    case HEAP, DIRECT -> {
                        theorticallen = bbin.remaining() - (d.blockSize > 0 ?
                            bbin.remaining() % d.blockSize : 0);
                        rlen = cipher.update(bbin, bbout);
                    }
                    default -> throw new Exception("Unknown op: " + v.name());
                }

                // Check that the theoretical return value matches the actual.
                if (theoreticalCheck && encrypt && rlen != theorticallen) {
                    throw new Exception("Wrong update return len (" +
                        v.name() + "):  " + "rlen=" + rlen +
                        ", expected output len=" + theorticallen);
                }

                dataoffset += plen;
                len += rlen;
                index++;

            } else {
                // Run doFinal op
                plen = input.length - dataoffset;

                switch (v) {
                    case BYTE -> {
                        rlen = cipher.doFinal(data, dataoffset + inOfs,
                            plen, data, len + outOfs);
                        out = Arrays.copyOfRange(data, 0,len + rlen + outOfs);
                    }
                    case HEAP, DIRECT -> {
                        rlen = cipher.doFinal(bbin, bbout);
                        bbout.flip();
                        out = new byte[bbout.remaining()];
                        bbout.get(out);
                    }
                    default -> throw new Exception("Unknown op: " + v.name());
                }
                len += rlen;

                // Verify results
                if (len != output.length ||
                    Arrays.compare(out, 0, len, expectedOut, 0,
                        output.length) != 0) {
                    String s = "Ciphertext mismatch (" + v.name() +
                        "):\nresult (len=" + len + "):\n" +
                        hex.formatHex(out) +
                        "\nexpected (len=" + output.length + "):\n" +
                        hex.formatHex(output);
                    System.err.println(s);
                    throw new Exception(s);
                }
            }
        }
    }
    static void offsetTests(AEADBufferTest t) throws Exception {
        t.clone().offset(2).test();
        t.clone().inOfs(2).test();
        // Test not designed for overlap situations
        t.clone().outOfs(2).differentBufferOnly().test();
    }

    public static void main(String args[]) throws Exception {
        AEADBufferTest t;

        initTest();

        // **** GCM Tests

        // Test single byte array
        new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.BYTE)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.BYTE)));
        // Test update-doFinal with byte arrays
        new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.BYTE, dtype.BYTE)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.BYTE, dtype.BYTE)));
        // Test update-update-doFinal with byte arrays
        new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.BYTE, dtype.BYTE, dtype.BYTE)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.BYTE, dtype.BYTE, dtype.BYTE)));

        // Test single heap bytebuffer
        new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.HEAP)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.HEAP)));
        // Test update-doFinal with heap bytebuffer
        new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.HEAP, dtype.HEAP)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.HEAP, dtype.HEAP)));
        // Test update-update-doFinal with heap bytebuffer
        new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.HEAP, dtype.HEAP, dtype.HEAP)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.HEAP, dtype.HEAP, dtype.HEAP)));

        // Test single direct bytebuffer
        new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.DIRECT)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding", List.of(dtype.DIRECT)));
        // Test update-doFinal with direct bytebuffer
        new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.DIRECT)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.DIRECT)));
        // Test update-update-doFinal with direct bytebuffer
        new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.DIRECT, dtype.DIRECT)).test();
        offsetTests(new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.DIRECT, dtype.DIRECT)));

        // Test update-update-doFinal with byte arrays and preset data sizes
        t = new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.BYTE, dtype.BYTE, dtype.BYTE)).dataSegments(
            new int[] { 1, 1, AEADBufferTest.REMAINDER});
        t.clone().test();
        offsetTests(t.clone());

        // Test update-doFinal with a byte array and a direct bytebuffer
        t = new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.BYTE, dtype.DIRECT)).differentBufferOnly();
        t.clone().test();
        offsetTests(t.clone());
        // Test update-doFinal with a byte array and heap and direct bytebuffer
        t = new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.BYTE, dtype.HEAP, dtype.DIRECT)).differentBufferOnly();
        t.clone().test();
        offsetTests(t.clone());

        // Test update-doFinal with a direct bytebuffer and a byte array.
        t = new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.BYTE)).differentBufferOnly();
        t.clone().test();
        offsetTests(t.clone());

        // Test update-doFinal with a direct bytebuffer and a byte array with
        // preset data sizes.
        t = new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.BYTE)).differentBufferOnly().
            dataSegments(new int[] { 20, AEADBufferTest.REMAINDER });
        t.clone().test();
        offsetTests(t.clone());
        // Test update-update-doFinal with a direct and heap bytebuffer and a
        // byte array with preset data sizes.
        t = new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.BYTE, dtype.HEAP)).
            differentBufferOnly().dataSet(5).
            dataSegments(new int[] { 5000, 1000, AEADBufferTest.REMAINDER });
        t.clone().test();
        offsetTests(t.clone());

        // Test update-update-doFinal with byte arrays, incrementing through
        // every data size combination for the Data set 0
        new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.BYTE, dtype.BYTE, dtype.BYTE)).incrementalSegments().
            dataSet(0).test();
        // Test update-update-doFinal with direct bytebuffers, incrementing through
        // every data size combination for the Data set 0
        new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.DIRECT, dtype.DIRECT)).
            incrementalSegments().dataSet(0).test();

        new AEADBufferTest("AES/GCM/NoPadding",
            List.of(dtype.DIRECT, dtype.DIRECT, dtype.DIRECT)).
            dataSegments(new int[] { 49, 0, 2 }).dataSet(0).test();

        // **** CC20P1305 Tests

        // Test single byte array
        new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.BYTE)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.BYTE)));
        // Test update-doFinal with byte arrays
        new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.BYTE, dtype.BYTE)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.BYTE, dtype.BYTE)));
        // Test update-update-doFinal with byte arrays
        new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.BYTE, dtype.BYTE, dtype.BYTE)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.BYTE, dtype.BYTE, dtype.BYTE)));

        // Test single heap bytebuffer
        new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.HEAP)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.HEAP)));
        // Test update-doFinal with heap bytebuffer
        new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.HEAP, dtype.HEAP)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.HEAP, dtype.HEAP)));
        // Test update-update-doFinal with heap bytebuffer
        new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.HEAP, dtype.HEAP, dtype.HEAP)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.HEAP, dtype.HEAP, dtype.HEAP)));

        // Test single direct bytebuffer
        new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.DIRECT)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305", List.of(dtype.DIRECT)));
        // Test update-doFinal with direct bytebuffer
        new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.DIRECT)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.DIRECT)));
        // Test update-update-doFinal with direct bytebuffer
        new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.DIRECT, dtype.DIRECT)).test();
        offsetTests(new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.DIRECT, dtype.DIRECT)));

        // Test update-update-doFinal with byte arrays and preset data sizes
        t = new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.BYTE, dtype.BYTE, dtype.BYTE)).dataSegments(
            new int[] { 1, 1, AEADBufferTest.REMAINDER});
        t.clone().test();
        offsetTests(t.clone());

        // Test update-doFinal with a byte array and a direct bytebuffer
        t = new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.BYTE, dtype.DIRECT)).differentBufferOnly();
        t.clone().test();
        offsetTests(t.clone());
        // Test update-doFinal with a byte array and heap and direct bytebuffer
        t = new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.BYTE, dtype.HEAP, dtype.DIRECT)).differentBufferOnly();
        t.clone().test();
        offsetTests(t.clone());

        // Test update-doFinal with a direct bytebuffer and a byte array.
        t = new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.BYTE)).differentBufferOnly();
        t.clone().test();
        offsetTests(t.clone());

        // Test update-doFinal with a direct bytebuffer and a byte array with
        // preset data sizes.
        t = new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.BYTE)).differentBufferOnly().
            dataSegments(new int[] { 20, AEADBufferTest.REMAINDER });
        t.clone().test();
        offsetTests(t.clone());
        // Test update-update-doFinal with a direct and heap bytebuffer and a
        // byte array with preset data sizes.
        t = new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.BYTE, dtype.HEAP)).
            differentBufferOnly().dataSet(1).
            dataSegments(new int[] { 5000, 1000, AEADBufferTest.REMAINDER });
        t.clone().test();
        offsetTests(t.clone());

        // Test update-update-doFinal with byte arrays, incrementing through
        // every data size combination for the Data set 0
        new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.BYTE, dtype.BYTE, dtype.BYTE)).incrementalSegments().
            dataSet(0).test();
        // Test update-update-doFinal with direct bytebuffers, incrementing through
        // every data size combination for the Data set 0
        new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.DIRECT, dtype.DIRECT)).
            incrementalSegments().dataSet(0).test();

        new AEADBufferTest("ChaCha20-Poly1305",
            List.of(dtype.DIRECT, dtype.DIRECT, dtype.DIRECT)).
            dataSegments(new int[] { 49, 0, 2 }).dataSet(0).test();
    }

    // Test data
    static void initTest() {

        datamap.put("AES/GCM/NoPadding", List.of(
            // GCM KAT
            new Data(AES, 0,
            "141f1ce91989b07e7eb6ae1dbd81ea5e",
                "49451da24bd6074509d3cebc2c0394c972e6934b45a1d91f3ce1d3ca69e19" +
                "4aa1958a7c21b6f21d530ce6d2cc5256a3f846b6f9d2f38df0102c4791e5" +
                "7df038f6e69085646007df999751e248e06c47245f4cd3b8004585a7470d" +
                "ee1690e9d2d63169a58d243c0b57b3e5b4a481a3e4e8c60007094ef3adea" +
                "2e8f05dd3a1396f",
            "d384305af2388699aa302f510913fed0f2cb63ba42efa8c5c9de2922a2ec" +
                "2fe87719dadf1eb0aef212b51e74c9c5b934104a43",
            "630cf18a91cc5a6481ac9eefd65c24b1a3c93396bd7294d6b8ba3239517" +
                "27666c947a21894a079ef061ee159c05beeb4",
            "f4c34e5fbe74c0297313268296cd561d59ccc95bbfcdfcdc71b0097dbd83" +
                "240446b28dc088abd42b0fc687f208190ff24c0548",
            "dbb93bbb56d0439cd09f620a57687f5d"),
            // GCM KAT
            new Data(AES, 1, "11754cd72aec309bf52f7687212e8957",
                "3c819d9a9bed087615030b65",
                new byte[0], null, null,
                "250327c674aaf477aef2675748cf6971"),
            // Randomly generated data at the time of execution.
            new Data(AES, 5, "11754cd72aec309bf52f7687212e8957",
                16, 12345)));

        datamap.put("ChaCha20-Poly1305", List.of(
            new Data("CC20", 0,
                "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f",
                "070000004041424344454647",
                1,
                "4c616469657320616e642047656e746c656d656e206f662074686520636c6173" +
                "73206f66202739393a204966204920636f756c64206f6666657220796f75206f" +
                "6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73" +
                "637265656e20776f756c642062652069742e",
                "50515253c0c1c2c3c4c5c6c7",
                "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6" +
                "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b36" +
                "92ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc" +
                "3ff4def08e4b7a9de576d26586cec64b61161ae10b59",
                "4f09e26a7e902ecbd0600691"),
                // Randomly generated data at the time of execution.
            new Data("CC20", 1,
                "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f",
                12, 12345)));
    }
}
