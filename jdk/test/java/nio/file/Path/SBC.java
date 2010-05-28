/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313887
 * @summary Unit test for java.nio.file.Path.newByteChannel
 * @library ..
 */

import java.nio.ByteBuffer;
import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import static com.sun.nio.file.ExtendedOpenOption.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.channels.*;
import java.io.IOException;
import java.util.*;

public class SBC {

    static boolean supportsLinks;

    public static void main(String[] args) throws Exception {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            supportsLinks = TestUtil.supportsLinks(dir);

            // open options
            createTests(dir);
            appendTests(dir);
            truncateExistingTests(dir);
            noFollowLinksTests(dir);

            // SeekableByteChannel methods
            sizeTruncatePositionTests(dir);

            // platform specific
            if (System.getProperty("os.name").startsWith("Windows"))
                dosSharingOptionTests(dir);

            // misc. tests
            badCombinations(dir);
            unsupportedOptions(dir);
            nullTests(dir);

        } finally {
            TestUtil.removeAll(dir);
        }
    }

    // test CREATE and CREATE_NEW options
    static void createTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");

        // CREATE
        try {
            // create file (no existing file)
            file.newByteChannel(CREATE, WRITE).close();
            if (file.notExists())
                throw new RuntimeException("File not created");

            // create file (existing file)
            file.newByteChannel(CREATE, WRITE).close();

            // create file where existing file is a sym link
            if (supportsLinks) {
                Path link = dir.resolve("link").createSymbolicLink(file);
                try {
                    // file already exists
                    link.newByteChannel(CREATE, WRITE).close();

                    // file does not exist
                    file.delete();
                    link.newByteChannel(CREATE, WRITE).close();
                    if (file.notExists())
                        throw new RuntimeException("File not created");

                } finally {
                    TestUtil.deleteUnchecked(link);
                }
            }

        } finally {
            TestUtil.deleteUnchecked(file);
        }

        // CREATE_NEW
        try {
            // create file
            file.newByteChannel(CREATE_NEW, WRITE).close();
            if (file.notExists())
                throw new RuntimeException("File not created");

            // create should fail
            try {
                SeekableByteChannel sbc =
                    file.newByteChannel(CREATE_NEW, WRITE);
                sbc.close();
                throw new RuntimeException("FileAlreadyExistsException not thrown");
            } catch (FileAlreadyExistsException x) { }

            // create should fail
            if (supportsLinks) {
                Path link = dir.resolve("link");
                Path target = dir.resolve("thisDoesNotExist");
                link.createSymbolicLink(target);
                try {

                    try {
                        SeekableByteChannel sbc =
                            file.newByteChannel(CREATE_NEW, WRITE);
                        sbc.close();
                        throw new RuntimeException("FileAlreadyExistsException not thrown");
                    } catch (FileAlreadyExistsException x) { }

                } finally {
                    TestUtil.deleteUnchecked(link);
                }
            }


        } finally {
            TestUtil.deleteUnchecked(file);
        }

        // CREATE_NEW + SPARSE
        try {
            SeekableByteChannel sbc = file
                .newByteChannel(CREATE_NEW, WRITE, SPARSE);
            try {
                final long hole = 2L * 1024L * 1024L * 1024L;
                sbc.position(hole);
                write(sbc, "hello");
                long size = sbc.size();
                if (size != (hole + 5))
                    throw new RuntimeException("Unexpected size");
            } finally {
                sbc.close();
            }
        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }

    // test APPEND option
    static void appendTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");
        try {
            // "hello there" should be written to file
            SeekableByteChannel sbc = file
                .newByteChannel(CREATE_NEW, WRITE, APPEND);
            try {
                write(sbc, "hello ");
                sbc.position(0L);
                write(sbc, "there");
            } finally {
                sbc.close();
            }

            // check file
            Scanner s = new Scanner(file);
            try {
                String line = s.nextLine();
                if (!line.equals("hello there"))
                    throw new RuntimeException("Unexpected file contents");
            } finally {
                s.close();
            }

            // check that read is not allowed
            sbc = file.newByteChannel(APPEND);
            try {
                sbc.read(ByteBuffer.allocate(100));
            } catch (NonReadableChannelException x) {
            } finally {
                sbc.close();
            }
        } finally {
            // clean-up
            TestUtil.deleteUnchecked(file);
        }
    }

    // test TRUNCATE_EXISTING option
    static void truncateExistingTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");
        try {
            SeekableByteChannel sbc =
                file.newByteChannel(CREATE_NEW, WRITE);
            try {
                write(sbc, "Have a nice day!");
            } finally {
                sbc.close();
            }

            // re-open with truncate option
            // write short message and check
            sbc = file.newByteChannel(WRITE, TRUNCATE_EXISTING);
            try {
                write(sbc, "Hello there!");
            } finally {
                sbc.close();
            }
            Scanner s = new Scanner(file);
            try {
                String line = s.nextLine();
                if (!line.equals("Hello there!"))
                    throw new RuntimeException("Unexpected file contents");
            } finally {
                s.close();
            }

            // re-open with create + truncate option
            // check file is of size 0L
            sbc = file.newByteChannel(WRITE, CREATE, TRUNCATE_EXISTING);
            try {
                long size = ((FileChannel)sbc).size();
                if (size != 0L)
                    throw new RuntimeException("File not truncated");
            } finally {
                sbc.close();
            }

        } finally {
            // clean-up
            TestUtil.deleteUnchecked(file);
        }

    }

    // test NOFOLLOW_LINKS option
    static void noFollowLinksTests(Path dir) throws Exception {
        if (!supportsLinks)
            return;
        Path file = dir.resolve("foo").createFile();
        try {
            // ln -s foo link
            Path link = dir.resolve("link").createSymbolicLink(file);

            // open with NOFOLLOW_LINKS option
            try {
                link.newByteChannel(READ, LinkOption.NOFOLLOW_LINKS);
                throw new RuntimeException();
            } catch (IOException x) {
            } finally {
                TestUtil.deleteUnchecked(link);
            }

        } finally {
            // clean-up
            TestUtil.deleteUnchecked(file);
        }
    }

    // test size/truncate/position methods
    static void sizeTruncatePositionTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");
        try {
            SeekableByteChannel sbc = file
                .newByteChannel(CREATE_NEW, READ, WRITE);
            try {
                if (sbc.size() != 0L)
                    throw new RuntimeException("Unexpected size");

                // check size
                write(sbc, "hello");
                if (sbc.size() != 5L)
                    throw new RuntimeException("Unexpected size");

                // truncate (size and position should change)
                sbc.truncate(4L);
                if (sbc.size() != 4L)
                    throw new RuntimeException("Unexpected size");
                if (sbc.position() != 4L)
                    throw new RuntimeException("Unexpected position");

                // truncate (position should not change)
                sbc.position(2L).truncate(3L);
                if (sbc.size() != 3L)
                    throw new RuntimeException("Unexpected size");
                if (sbc.position() != 2L)
                    throw new RuntimeException("Unexpected position");
            } finally {
                sbc.close();
            }
        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }

    // Windows specific options for the use by applications that really want
    // to use legacy DOS sharing options
    static void dosSharingOptionTests(Path dir) throws Exception {
        Path file = dir.resolve("foo").createFile();
        try {
            SeekableByteChannel ch;

            // no sharing
            ch = file.newByteChannel(READ,
                NOSHARE_READ, NOSHARE_WRITE, NOSHARE_DELETE);
            try {
                try {
                    file.newByteChannel(READ);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                try {
                    file.newByteChannel(WRITE);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                try {
                    file.delete();
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
            } finally {
                ch.close();
            }

            // read allowed
            ch = file.newByteChannel(READ, NOSHARE_WRITE, NOSHARE_DELETE);
            try {
                file.newByteChannel(READ).close();
                try {
                    file.newByteChannel(WRITE);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                try {
                    file.delete();
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
            } finally {
                ch.close();
            }

            // write allowed
            ch = file.newByteChannel(READ, NOSHARE_READ, NOSHARE_DELETE);
            try {
                try {
                    file.newByteChannel(READ);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                file.newByteChannel(WRITE).close();
                try {
                    file.delete();
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
            } finally {
                ch.close();
            }

            // delete allowed
            ch = file.newByteChannel(READ, NOSHARE_READ, NOSHARE_WRITE);
            try {
                try {
                    file.newByteChannel(READ);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                try {
                    file.newByteChannel(WRITE);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                file.delete();
            } finally {
                ch.close();
            }

        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }

    // invalid combinations of options
    static void badCombinations(Path dir) throws Exception {
        Path file = dir.resolve("bad");

        try {
            file.newByteChannel(READ, APPEND);
            throw new RuntimeException("IllegalArgumentException expected");
        } catch (IllegalArgumentException x) { }

        try {
            file.newByteChannel(WRITE, APPEND, TRUNCATE_EXISTING);
            throw new RuntimeException("IllegalArgumentException expected");
        } catch (IllegalArgumentException x) { }
    }

    // unsupported operations
    static void unsupportedOptions(Path dir) throws Exception {
        Path file = dir.resolve("bad");

        OpenOption badOption = new OpenOption() { };
        try {
            file.newByteChannel(badOption);
            throw new RuntimeException("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException e) { }
        try {
            file.newByteChannel(READ, WRITE, badOption);
            throw new RuntimeException("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException e) { }
    }

    // null handling
    static void nullTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");

        try {
            file.newByteChannel((OpenOption[])null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            OpenOption[] opts = { READ, null };
            file.newByteChannel(opts);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            file.newByteChannel((Set<OpenOption>)null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            Set<OpenOption> opts = new HashSet<OpenOption>();
            opts.add(READ);
            opts.add(null);
            file.newByteChannel(opts);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            EnumSet<StandardOpenOption> opts = EnumSet.of(READ);
            file.newByteChannel(opts, (FileAttribute[])null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            EnumSet<StandardOpenOption> opts = EnumSet.of(READ);
            FileAttribute[] attrs = { null };
            file.newByteChannel(opts, attrs);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }
    }

    static void write(WritableByteChannel wbc, String msg) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(msg.getBytes());
        while (buf.hasRemaining())
            wbc.write(buf);
    }
}
