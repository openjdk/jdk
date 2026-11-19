/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.awt.Canvas;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @test
 * @bug 8394262
 * @summary The AWT JPEG decoder must not write past the end of its input
 *          buffer when it appends a synthetic EOI marker to a truncated
 *          progressive image.
 * @run main/othervm -Xcheck:jni SyntheticEOIMarkerBufferTest
 */

public class SyntheticEOIMarkerBufferTest {

    private static final int INPUT_BUFFER_SIZE = 1024;
    private static final int FIRST_SOS_END = INPUT_BUFFER_SIZE + 1; // 1025
    private static final int TOTAL_LENGTH = 2 * INPUT_BUFFER_SIZE;  // 2048

    public static void main(String[] args) throws Exception {
        byte[] jpeg = truncatedProgressiveJpeg();

        // Decode through the public AWT API using the crafted stream.
        URL url = URL.of(URI.create("testjpeg:/truncated.jpg"),
                new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override
                            public void connect() {
                            }

                            @Override
                            public String getContentType() {
                                return "image/jpeg";
                            }

                            @Override
                            public InputStream getInputStream() {
                                return new TruncatedStream(jpeg);
                            }
                        };
                    }
                });

        Image image = Toolkit.getDefaultToolkit().createImage(url);
        MediaTracker tracker = new MediaTracker(new Canvas());
        tracker.addImage(image, 0);

        try {
            tracker.waitForID(0, 30_000);

            int status = tracker.statusID(0, false);
            if ((status & MediaTracker.ERRORED) == 0) {
                throw new RuntimeException(
                        "Expected a decoding error, status: " + status);
            }

            System.out.println("Rejected as expected");

        } finally {
            tracker.removeImage(image, 0);
            image.flush();
        }
    }

    private static byte[] truncatedProgressiveJpeg() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(TOTAL_LENGTH);

        out.write(0xFF); out.write(0xD8);

        byte[] dqt = new byte[65];
        dqt[0] = 0x00;
        Arrays.fill(dqt, 1, dqt.length, (byte) 0x01);
        segment(out, 0xDB, dqt);

        segment(out, 0xC2, new byte[] {
            0x08,
            0x00, 0x20, // height 32
            0x00, 0x20, // width 32
            0x01,
            0x01, 0x11, 0x00 // id, 1x1, qtable
        });

        byte[] dht = new byte[18];
        dht[1] = 0x01;
        segment(out, 0xC4, dht);

        // SOI(2) + DQT(69) + SOF2(13) + DHT(22) = 106 bytes so far.
        // Pad with a COM segment so that the first SOS header, which is
        // 10 bytes long, ends exactly at FIRST_SOS_END.
        int comPayload = FIRST_SOS_END - 10 - 106 - 4;
        segment(out, 0xFE, new byte[comPayload]);

        segment(out, 0xDA, new byte[] {
            0x01,
            0x01, 0x00,
            0x00, 0x00,
            0x00
        });

        out.write(new byte[TOTAL_LENGTH - FIRST_SOS_END], 0,
                  TOTAL_LENGTH - FIRST_SOS_END);

        byte[] jpeg = out.toByteArray();
        if (jpeg.length != TOTAL_LENGTH
                || (jpeg[FIRST_SOS_END - 10] & 0xFF) != 0xFF
                || (jpeg[FIRST_SOS_END - 9] & 0xFF) != 0xDA) {
            throw new RuntimeException("unexpected crafted image layout");
        }
        return jpeg;
    }

    private static void segment(ByteArrayOutputStream out, int marker,
                                byte[] payload) {
        out.write(0xFF);
        out.write(marker);
        out.write((payload.length + 2) >>> 8);
        out.write((payload.length + 2) & 0xFF);
        out.writeBytes(payload);
    }

    /*
     * A truncated stream that claims data is still available after EOF,
     * the way a stream behind a network connection or a decoder can do.
     */
    private static final class TruncatedStream extends InputStream {
        private final byte[] data;
        private int pos;

        TruncatedStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            return pos < data.length ? (data[pos++] & 0xFF) : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len == 0) {
                return 0;
            }
            if (pos >= data.length) {
                return -1;
            }
            int n = Math.min(len, data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }

        @Override
        public int available() {
            return 1;
        }
    }
}
