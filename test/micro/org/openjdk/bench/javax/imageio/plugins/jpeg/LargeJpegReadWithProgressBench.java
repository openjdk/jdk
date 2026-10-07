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
package org.openjdk.bench.javax.imageio.plugins.jpeg;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;

/**
 * Measure time taken to read large jpeg image
 * make test TEST="micro:javax.imageio.plugins.jpeg.LargeJpegReadWithProgressBench"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Benchmark)
public class LargeJpegReadWithProgressBench {

    private static final File pwd = new File(".");
    private static ImageReader reader;

    @Setup
    public void setup() throws IOException {
        BufferedImage src = createSource();
        ImageInputStream iis = prepareInput(src);
        reader = null;
        Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("jpeg");
        if (it.hasNext()) {
            reader = (ImageReader)it.next();
        } else {
            throw new RuntimeException("Could not find JPEG reader");
        }
        reader.setInput(iis);
        ImageReadProgressListener listener = new ImageReadProgressListener();
        reader.addIIOReadProgressListener(listener);
    }

    @Benchmark
    public void readLargeJpegImage(Blackhole bh) throws IOException {
        reader.read(0);
    }

    private static BufferedImage createSource() {
        int width = 2000;
        int height = 2000;
        int squareSize = 20;

        Color red = Color.RED;
        Color green = Color.GREEN;
        BufferedImage image = new BufferedImage(width, height,
            BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((x / squareSize) + (y / squareSize)) % 2 == 0) {
                    image.setRGB(x, y, red.getRGB());
                } else {
                    image.setRGB(x, y, green.getRGB());
                }
            }
        }
        return image;
    }

    private static ImageInputStream prepareInput(BufferedImage src)
        throws IOException {
        File f = File.createTempFile("src_", ".jpeg", pwd);
        if (ImageIO.write(src, "jpeg", f)) {
            ImageInputStream iis = ImageIO.createImageInputStream(f);
            f.deleteOnExit();
            return iis;
        } else {
            throw new RuntimeException("Unable to write jpeg image");
        }
    }
}

class ImageReadProgressListener implements IIOReadProgressListener {
    // This class is a no-op, it is added just to have a progress listener
    @Override
    public void sequenceStarted(ImageReader source, int minIndex) {

    }

    @Override
    public void sequenceComplete(ImageReader source) {

    }

    @Override
    public void imageStarted(ImageReader source, int imageIndex) {

    }

    @Override
    public void imageProgress(ImageReader source, float percentageDone) {

    }

    @Override
    public void imageComplete(ImageReader source) {

    }

    @Override
    public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {

    }

    @Override
    public void thumbnailProgress(ImageReader source, float percentageDone) {

    }

    @Override
    public void thumbnailComplete(ImageReader source) {

    }

    @Override
    public void readAborted(ImageReader source) {

    }
}
