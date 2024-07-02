/*
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
 */
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.LongStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;

/**
 * @test
 * @summary Test zip archive stored in another zip archive.
 * @compile EmbeddedZipFileTest.java
 * @run testng EmbeddedZipFileTest
 */
public class EmbeddedZipFileTest
{
    // ZIP file to create
    private static final String ZIP_FILE_NAME = "EmbeddedZipFile.zip";

    private static final String OUTER_FILE_NAME = "archive.jar";
    private static final String INNER_FILE_NAME = "inner.txt";
    // List of files to be added to the ZIP file
    private static final List<String> ZIP_ENTRIES = List.of(INNER_FILE_NAME);
    private static final long INNER_FILE_SIZE = 0x100000L; // 1024L x 1024L;
    private static byte[] INNER_FILE_CONTENT = LongStream.range(0, INNER_FILE_SIZE)
            .mapToInt(i -> RandomGenerator.getDefault().nextInt('A', 'Z' + 1))
            .collect(ByteArrayOutputStream::new, ByteArrayOutputStream::write, (a, b) -> { throw new UnsupportedOperationException(); })
            .toByteArray();


    /**
     * Validate that if the size of a ZIP entry exceeds 0xFFFFFFFF, that the
     * correct size is returned from the ZIP64 Extended information.
     * @throws IOException
     */
    @Test
    public static void openNestedZipFile() throws IOException
    {
        createZipFile();
        System.out.println("Validating Zip Entry Sizes");
        try (ZipFile zip = new ZipFile(ZIP_FILE_NAME)) {
            ZipEntry ze = zip.getEntry(OUTER_FILE_NAME);
            System.out.printf("Entry: %s, size= %s position=%d %n", ze.getName(), ze.getSize(), zip.getEntryDataOffset(ze));
            try (ZipFile nestedArchive = new ZipFile(subFileChannel(zip.getFileChannel(), zip.getEntryDataOffset(ze), ze.getSize()), StandardCharsets.UTF_8)) {
                ZipEntry inner = nestedArchive.getEntry(INNER_FILE_NAME);
                assertEquals(inner.getSize(), INNER_FILE_SIZE);
                System.out.printf("Inner entry: %s, size= %s%n", inner.getName(), inner.getSize());
                try (InputStream stream = nestedArchive.getInputStream(inner)) {
                    byte[] content = stream.readAllBytes();
                    ArrayAsserts.assertArrayEquals(content, INNER_FILE_CONTENT);
                }
            }

        }
    }

    /**
     * Delete the files created for use by the test
     * @throws IOException if an error occurs deleting the files
     */
    private static void deleteFiles() throws IOException
    {
        Files.deleteIfExists(Path.of(ZIP_FILE_NAME));
    }

    /**
     * Create the ZIP file adding an entry whose size exceeds 0xFFFFFFFF
     * @throws IOException if an error occurs creating the ZIP File
     */
    private static void createZipFile() throws IOException
    {
        try (FileOutputStream fos = new FileOutputStream(ZIP_FILE_NAME);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            System.out.printf("Creating Zip file: %s%n", ZIP_FILE_NAME);
            byte[] outerContent = createInnerZipFile();
            ZipEntry entry = new ZipEntry(OUTER_FILE_NAME);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(outerContent.length);
            entry.setCompressedSize(outerContent.length);
            entry.setCrc(calculateCrc(outerContent));
            zos.putNextEntry(entry);
            zos.write(outerContent);
        }
    }

    private static byte[] createInnerZipFile() throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(output)) {
            ZipEntry entry = new ZipEntry(INNER_FILE_NAME);
            entry.setSize(INNER_FILE_SIZE);
            zos.putNextEntry(entry);
            zos.write(INNER_FILE_CONTENT);
        }
        return output.toByteArray();
    }

    private static long calculateCrc(byte[] content)
    {
        CRC32 calculator = new CRC32();
        calculator.update(content);
        return calculator.getValue();
    }

    private static FileChannel subFileChannel(FileChannel parentChannel, long base, long size)
    {
        return new FileChannel()
        {
            long position = 0;

            @Override
            public synchronized int read(ByteBuffer dst) throws IOException
            {
                ByteBuffer dup = dst.duplicate();
                if (dup.remaining() > size - position) {
                    dup.limit((int) (dup.position() + size - position));
                }
                int read = parentChannel.read(dup, base + position);
                if (read > 0) {
                    position += read;
                    dst.position(dup.position());
                }
                return read;
            }

            @Override
            public long read(ByteBuffer[] dsts, int offset, int length) throws IOException
            {
                if (length > 0) {
                    return read(dsts[offset]);
                }
                return 0;
            }

            @Override
            public int write(ByteBuffer src) throws IOException
            {
                throw new UnsupportedOperationException("read only channel");
            }

            @Override
            public long write(ByteBuffer[] srcs, int offset, int length) throws IOException
            {
                throw new UnsupportedOperationException("read only channel");
            }

            @Override
            public long position() throws IOException
            {
                return position;
            }

            @Override
            public FileChannel position(long newPosition) throws IOException
            {
                position = newPosition;
                return this;
            }

            @Override
            public long size() throws IOException
            {
                return size;
            }

            @Override
            public FileChannel truncate(long size) throws IOException
            {
                throw new UnsupportedOperationException("read only channel");
            }

            @Override
            public void force(boolean metaData) throws IOException
            {
                throw new UnsupportedOperationException("read only channel");
            }

            @Override
            public long transferTo(long position, long count, WritableByteChannel target) throws IOException
            {
                throw new UnsupportedOperationException("TODO");
            }

            @Override
            public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException
            {
                throw new UnsupportedOperationException("read only channel");
            }

            @Override
            public int read(ByteBuffer dst, long position) throws IOException
            {
                ByteBuffer dup = dst.duplicate();
                if (dup.remaining() > size - position) {
                    dup.limit(dup.position() + (int) (size - position));
                }
                int read = parentChannel.read(dup, base + position);
                if (read > 0) {
                    dst.position(dup.position());
                }
                return read;
            }

            @Override
            public int write(ByteBuffer src, long position) throws IOException
            {
                return parentChannel.write(src, base + position);
            }

            @Override
            public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException
            {
                throw new UnsupportedOperationException("TODO");
            }

            @Override
            public FileLock lock(long position, long size, boolean shared) throws IOException
            {
                throw new UnsupportedOperationException("TODO");
            }

            @Override
            public FileLock tryLock(long position, long size, boolean shared) throws IOException
            {
                throw new UnsupportedOperationException("TODO");
            }

            @Override
            protected void implCloseChannel() throws IOException
            {
            }
        };
    }

    /**
     * Make sure the needed test files do not exist prior to executing the test
     * @throws IOException
     */
    @BeforeMethod
    public void setUp() throws IOException {
        deleteFiles();
    }

    /**
     * Remove the files created for the test
     * @throws IOException
     */
    @AfterMethod
    public void tearDown() throws IOException {
        deleteFiles();
    }
}
