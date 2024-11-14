/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * @test
 * @bug 8059767
 * @summary tests that FileHandler can accept a long limit.
 * @modules java.logging/java.util.logging:open
 * @run main/othervm FileHandlerLongLimit
 * @author danielfuchs
 * @key randomness
 */
public class FileHandlerLongLimit {

     // We will test handling of limit and overflow of MeteredStream.written
    public static void run(Properties propertyFile) throws Exception {
        setUp(propertyFile);
        test(propertyFile.getProperty("test.name"), propertyFile,
                Long.parseLong(propertyFile.getProperty(FileHandler.class.getName()+".limit")));
    }


    private static final String PREFIX =
            "FileHandler-" + UUID.randomUUID() + ".log";
    private static final String userDir = System.getProperty("user.dir", ".");
    private static final boolean userDirWritable = Files.isWritable(Paths.get(userDir));
    private static final Field limitField;
    private static final Field meterField;
    private static final Field writtenField;
    private static final Field outField;

    private static final List<Properties> properties;
    static {
        Properties props1 = new Properties();
        Properties props2 = new Properties();
        Properties props3 = new Properties();
        props1.setProperty("test.name", "with limit=Integer.MAX_VALUE");
        props1.setProperty(FileHandler.class.getName() + ".pattern", PREFIX);
        props1.setProperty(FileHandler.class.getName() + ".limit", String.valueOf(Integer.MAX_VALUE));
        props2.setProperty("test.name", "with limit=Integer.MAX_VALUE*4");
        props2.setProperty(FileHandler.class.getName() + ".pattern", PREFIX);
        props2.setProperty(FileHandler.class.getName() + ".limit", String.valueOf(((long)Integer.MAX_VALUE)*4));
        props3.setProperty("test.name", "with limit=Long.MAX_VALUE - 1024");
        props3.setProperty(FileHandler.class.getName() + ".pattern", PREFIX);
        props3.setProperty(FileHandler.class.getName() + ".limit", String.valueOf(Long.MAX_VALUE - 1024));
        properties = Collections.unmodifiableList(Arrays.asList(
                    props1,
                    props2,
                    props3));
        try {
            Class<?> metteredStreamClass = Class.forName(FileHandler.class.getName()+"$MeteredStream");
            limitField = FileHandler.class.getDeclaredField("limit");
            limitField.setAccessible(true);
            meterField = FileHandler.class.getDeclaredField("meter");
            meterField.setAccessible(true);
            writtenField = metteredStreamClass.getDeclaredField("written");
            writtenField.setAccessible(true);
            outField = metteredStreamClass.getDeclaredField("out");
            outField.setAccessible(true);

        } catch (NoSuchFieldException | ClassNotFoundException x) {
            throw new ExceptionInInitializerError(x);
         }
    }

    private static class TestOutputStream extends OutputStream {
        final OutputStream delegate;
        TestOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }
        @Override
        public void write(int b) throws IOException {
            // do nothing - we only pretend to write something...
        }
        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

    }

    public static void main(String... args) throws Exception {

        try {
            for (Properties propertyFile : properties) {
                run(propertyFile);
            }
        } finally {
            if (userDirWritable) {
                // cleanup - delete files that have been created
                try {
                    Files.list(Paths.get(userDir))
                        .filter((f) -> f.toString().contains(PREFIX))
                        .forEach((f) -> {
                            try {
                                System.out.println("deleting " + f);
                                Files.delete(f);
                            } catch(Throwable t) {
                                System.err.println("Failed to delete " + f + ": " + t);
                            }
                        });
                } catch(Throwable t) {
                    System.err.println("Cleanup failed to list files: " + t);
                    t.printStackTrace();
                }
            }
        }
    }

    static void setUp(Properties propertyFile) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            propertyFile.store(bytes, propertyFile.getProperty("test.name"));
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes.toByteArray());
            LogManager.getLogManager().readConfiguration(bais);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @FunctionalInterface
    public static interface FileHandlerSupplier {
        public FileHandler test() throws Exception;
    }

    private static void checkException(Class<? extends Exception> type, FileHandlerSupplier test) {
        Throwable t = null;
        FileHandler f = null;
        try {
            f = test.test();
        } catch (Throwable x) {
            t = x;
        }
        try {
            if (type != null && t == null) {
                throw new RuntimeException("Expected " + type.getName() + " not thrown");
            } else if (type != null && t != null) {
                if (type.isInstance(t)) {
                    System.out.println("Recieved expected exception: " + t);
                } else {
                    throw new RuntimeException("Exception type mismatch: "
                        + type.getName() + " expected, "
                        + t.getClass().getName() + " received.", t);
                }
            } else if (t != null) {
                throw new RuntimeException("Unexpected exception received: " + t, t);
            }
        } finally {
            if (f != null) {
                // f should always be null when an exception is expected,
                // but in case the test doesn't behave as expected we will
                // want to close f.
                try { f.close(); } catch (Throwable x) {};
            }
        }
    }

    static final class TestAssertException extends RuntimeException {
        TestAssertException(String msg) {
            super(msg);
        }
    }

    private static void assertEquals(long expected, long received, String msg) {
        if (expected != received) {
            throw new TestAssertException("Unexpected result for " + msg
                    + ".\n\texpected: " + expected
                    +  "\n\tactual:   " + received);
        } else {
            System.out.println("Got expected " + msg + ": " + received);
        }
    }

    private static long getLimit(FileHandler handler) throws Exception {
        return limitField.getLong(handler);
    }
    private static OutputStream getMeteredOutput(FileHandler handler) throws Exception {
        final OutputStream metered = OutputStream.class.cast(meterField.get(handler));
        return metered;
    }
    private static TestOutputStream setTestOutputStream(OutputStream metered) throws Exception {
        outField.set(metered, new TestOutputStream(OutputStream.class.cast(outField.get(metered))));
        return TestOutputStream.class.cast(outField.get(metered));
    }
    private static long getWritten(OutputStream metered) throws Exception {
        return writtenField.getLong(metered);
    }

    private static long setWritten(OutputStream metered, long newValue) throws Exception {
        writtenField.setLong(metered, newValue);
        return writtenField.getLong(metered);
    }

    public static FileHandler testFileHandlerLimit(FileHandlerSupplier supplier,
            long limit) throws Exception {
        try {
            Files.deleteIfExists(Paths.get(PREFIX));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
        final FileHandler fh = supplier.test();
        try {
            // verify we have the expected limit
            assertEquals(limit, getLimit(fh), "limit");

            // get the metered output stream
            OutputStream metered = getMeteredOutput(fh);

            // we don't want to actually write to the file, so let's
            // redirect the metered to our own TestOutputStream.
            setTestOutputStream(metered);

            // check that fh.meter.written is 0
            assertEquals(0, getWritten(metered), "written");

            // now we're going to publish a series of log records
            // we're using the same log record over and over to make
            // sure we get the same amount of bytes.
            String msg = "this is at least 10 chars long";
            LogRecord record = new LogRecord(Level.SEVERE, msg);
            fh.publish(record);
            fh.flush();
            long w = getWritten(metered);
            long offset = getWritten(metered);
            System.out.println("first offset is:  " + offset);

            fh.publish(record);
            fh.flush();
            offset = getWritten(metered) - w;
            w = getWritten(metered);
            System.out.println("second offset is: " + offset);

            fh.publish(record);
            fh.flush();
            offset = getWritten(metered) - w;
            w = getWritten(metered);
            System.out.println("third offset is:  " + offset);

            fh.publish(record);
            fh.flush();
            offset = getWritten(metered) - w;
            System.out.println("fourth offset is: " + offset);

            // Now set fh.meter.written to something close to the limit,
            // so that we can trigger log file rotation.
            assertEquals(limit-2*offset+10, setWritten(metered, limit-2*offset+10), "written");
            w = getWritten(metered);

            // publish one more log record. we should still be just beneath
            // the limit
            fh.publish(record);
            fh.flush();
            assertEquals(w+offset, getWritten(metered), "written");

            // check that fh still has the same MeteredStream - indicating
            // that the file hasn't rotated.
            if (getMeteredOutput(fh) != metered) {
                throw new RuntimeException("Log should not have rotated");
            }

            // Now publish two log record. The spec is a bit vague about when
            // exactly the log will be rotated - it could happen just after
            // writing the first log record or just before writing the next
            // one. We publich two - so we're sure that the log must have
            // rotated.
            fh.publish(record);
            fh.flush();
            fh.publish(record);
            fh.flush();

            // Check that fh.meter is a different instance of MeteredStream.
            if (getMeteredOutput(fh) == metered) {
                throw new RuntimeException("Log should have rotated");
            }
            // success!
            return fh;
        } catch (Error | Exception x) {
            // if we get an exception we need to close fh.
            // if we don't get an exception, fh will be closed by the caller.
            // (and that's why we dont use try-with-resources/finally here).
            try { fh.close(); } catch(Throwable t) {t.printStackTrace();}
            throw x;
        }
    }

    public static void test(String name, Properties props, long limit) throws Exception {
        System.out.println("Testing: " + name);
        Class<? extends Exception> expectedException = null;

        if (userDirWritable || expectedException != null) {
            // These calls will create files in user.dir.
            // The file name contain a random UUID (PREFIX) which identifies them
            // and allow us to remove them cleanly at the end (see finally block
            // in main()).
            checkException(expectedException, () -> new FileHandler());
            checkException(expectedException, () -> {
                final FileHandler fh = new FileHandler();
                assertEquals(limit, getLimit(fh), "limit");
                return fh;
            });
            checkException(expectedException, () -> testFileHandlerLimit(
                    () -> new FileHandler(),
                    limit));
            checkException(expectedException, () -> testFileHandlerLimit(
                    () -> new FileHandler(PREFIX, Long.MAX_VALUE, 1, true),
                    Long.MAX_VALUE));
        }
        System.out.println("Success for: " + name);
    }
}
