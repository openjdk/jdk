package sun.nio.ch;
/*
 * Copyright (c) 2014, 2021 Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.String.format;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Random;

/*
 * @test
 * @bug 8066867
 * @summary tests whether sun.nio.ChannelInputStream.transferTo conforms to the
 *          InputStream.transferTo contract defined in the javadoc
 * @key randomness
 */
public class TransferTo {

	public static void main(final String[] args) throws IOException {
		test(defaultInput(), defaultOutput());
		test(fileChannelInput(), defaultOutput());
	}

	private static void test(final InputStreamProvider inputStreamProvider, final OutputStreamProvider outputStreamProvider) throws IOException {
		ifOutIsNullThenNpeIsThrown(inputStreamProvider, outputStreamProvider);
		ifExceptionInInputNeitherStreamIsClosed(inputStreamProvider, outputStreamProvider);
		ifExceptionInOutputNeitherStreamIsClosed(inputStreamProvider, outputStreamProvider);
		onReturnNeitherStreamIsClosed(inputStreamProvider, outputStreamProvider);
		onReturnInputIsAtEnd(inputStreamProvider, outputStreamProvider);
		contents(inputStreamProvider, outputStreamProvider);
	}

	private static void ifOutIsNullThenNpeIsThrown(final InputStreamProvider inputStreamProvider, final OutputStreamProvider outputStreamProvider)
			throws IOException {
		try (InputStream in = inputStreamProvider.input()) {
			assertThrowsNPE(() -> in.transferTo(null), "out");
		}

		try (InputStream in = inputStreamProvider.input((byte) 1)) {
			assertThrowsNPE(() -> in.transferTo(null), "out");
		}

		try (InputStream in = inputStreamProvider.input((byte) 1, (byte) 2)) {
			assertThrowsNPE(() -> in.transferTo(null), "out");
		}

		InputStream in = null;
		try {
			final InputStream fin = in = inputStreamProvider.newThrowingInputStream();
			// null check should precede everything else:
			// InputStream shouldn't be touched if OutputStream is null
			assertThrowsNPE(() -> fin.transferTo(null), "out");
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (final IOException ignored) {
				}
		}
	}

	private static void ifExceptionInInputNeitherStreamIsClosed(final InputStreamProvider inputStreamProvider, final OutputStreamProvider outputStreamProvider)
			throws IOException {
		transferToThenCheckIfAnyClosed(inputStreamProvider.input(0, new byte[] { 1, 2, 3 }), outputStreamProvider.output());
		transferToThenCheckIfAnyClosed(inputStreamProvider.input(1, new byte[] { 1, 2, 3 }), outputStreamProvider.output());
		transferToThenCheckIfAnyClosed(inputStreamProvider.input(2, new byte[] { 1, 2, 3 }), outputStreamProvider.output());
	}

	private static void ifExceptionInOutputNeitherStreamIsClosed(final InputStreamProvider inputStreamProvider, final OutputStreamProvider outputStreamProvider)
			throws IOException {
		transferToThenCheckIfAnyClosed(inputStreamProvider.input(new byte[] { 1, 2, 3 }), outputStreamProvider.output(0));
		transferToThenCheckIfAnyClosed(inputStreamProvider.input(new byte[] { 1, 2, 3 }), outputStreamProvider.output(1));
		transferToThenCheckIfAnyClosed(inputStreamProvider.input(new byte[] { 1, 2, 3 }), outputStreamProvider.output(2));
	}

	private static void transferToThenCheckIfAnyClosed(final InputStream input, final OutputStream output) throws IOException {
		try (CloseLoggingInputStream in = new CloseLoggingInputStream(input); CloseLoggingOutputStream out = new CloseLoggingOutputStream(output)) {
			boolean thrown = false;
			try {
				in.transferTo(out);
			} catch (final IOException ignored) {
				thrown = true;
			}
			if (!thrown)
				throw new AssertionError();

			if (in.wasClosed() || out.wasClosed())
				throw new AssertionError();
		}
	}

	private static void onReturnNeitherStreamIsClosed(final InputStreamProvider inputStreamProvider, final OutputStreamProvider outputStreamProvider)
			throws IOException {
		try (CloseLoggingInputStream in = new CloseLoggingInputStream(inputStreamProvider.input(new byte[] { 1, 2, 3 }));
				CloseLoggingOutputStream out = new CloseLoggingOutputStream(outputStreamProvider.output())) {

			in.transferTo(out);

			if (in.wasClosed() || out.wasClosed())
				throw new AssertionError();
		}
	}

	private static void onReturnInputIsAtEnd(final InputStreamProvider inputStreamProvider, final OutputStreamProvider outputStreamProvider)
			throws IOException {
		try (InputStream in = inputStreamProvider.input(new byte[] { 1, 2, 3 }); OutputStream out = outputStreamProvider.output()) {

			in.transferTo(out);

			if (in.read() != -1)
				throw new AssertionError();
		}
	}

	private static void contents(final InputStreamProvider inputStreamProvider, final OutputStreamProvider outputStreamProvider) throws IOException {
		checkTransferredContents(inputStreamProvider, outputStreamProvider, new byte[0]);
		checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(1024, 4096));
		// to span through several batches
		checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(16384, 16384));
	}

	private static void checkTransferredContents(final InputStreamProvider inputStreamProvider, final OutputStreamProvider outputStreamProvider,
			final byte[] bytes) throws IOException {
		try (InputStream in = inputStreamProvider.input(bytes); RecordingOutputStream out = outputStreamProvider.recordingOutput()) {
			in.transferTo(out);

			final byte[] outBytes = out.toByteArray();
			if (!Arrays.equals(bytes, outBytes))
				throw new AssertionError(format("bytes.length=%s, outBytes.length=%s", bytes.length, outBytes.length));
		}
	}

	private static byte[] createRandomBytes(final int min, final int maxRandomAdditive) {
		final Random rnd = new Random();
		final byte[] bytes = new byte[min + rnd.nextInt(maxRandomAdditive)];
		rnd.nextBytes(bytes);
		return bytes;
	}

	private static interface InputStreamProvider {
		InputStream input(byte... bytes);

		InputStream input(int exceptionPosition, byte... bytes);

		InputStream newThrowingInputStream();
	}

	private static abstract class RecordingOutputStream extends OutputStream {
		abstract byte[] toByteArray();
	}

	private static interface OutputStreamProvider {
		OutputStream output();

		OutputStream output(int exceptionPosition);

		RecordingOutputStream recordingOutput();
	}

	private static InputStreamProvider defaultInput() {
		return new InputStreamProvider() {

			@Override
			public InputStream input(final byte... bytes) {
				return this.input(-1, bytes);
			}

			@Override
			public InputStream input(final int exceptionPosition, final byte... bytes) {
				return new InputStream() {

					int pos;

					@Override
					public int read() throws IOException {
						if (this.pos == exceptionPosition)
							// because of the pesky IOException swallowing in
							// java.io.InputStream.read(byte[], int, int)
							// pos++;
							throw new IOException();

						if (this.pos >= bytes.length)
							return -1;
						return bytes[this.pos++] & 0xff;
					}
				};
			}

			@Override
			public InputStream newThrowingInputStream() {
				return new InputStream() {

					boolean closed;

					@Override
					public int read(final byte[] b) throws IOException {
						throw new IOException();
					}

					@Override
					public int read(final byte[] b, final int off, final int len) throws IOException {
						throw new IOException();
					}

					@Override
					public long skip(final long n) throws IOException {
						throw new IOException();
					}

					@Override
					public int available() throws IOException {
						throw new IOException();
					}

					@Override
					public void close() throws IOException {
						if (!this.closed) {
							this.closed = true;
							throw new IOException();
						}
					}

					@Override
					public void reset() throws IOException {
						throw new IOException();
					}

					@Override
					public int read() throws IOException {
						throw new IOException();
					}
				};
			}
		};
	}

	private static OutputStreamProvider defaultOutput() {
		return new OutputStreamProvider() {

			@Override
			public OutputStream output() {
				return this.output(-1);
			}

			@Override
			public OutputStream output(final int exceptionPosition) {
				return new OutputStream() {

					int pos;

					@Override
					public void write(final int b) throws IOException {
						if (this.pos++ == exceptionPosition)
							throw new IOException();
					}
				};
			}

			@Override
			public RecordingOutputStream recordingOutput() {
				return new RecordingOutputStream() {
					private final ByteArrayOutputStream recorder = new ByteArrayOutputStream();

					@Override
					public void write(final int b) {
						this.recorder.write(b);
					}

					@Override
					byte[] toByteArray() {
						return this.recorder.toByteArray();
					}
				};
			}
		};
	}

	private static InputStreamProvider fileChannelInput() {
		return new InputStreamProvider() {

			@Override
			public InputStream input(final byte... bytes) {
				return this.input(-1, bytes);
			}

			@Override
			public InputStream input(final int exceptionPosition, final byte... bytes) {
				return new ChannelInputStream(new AbstractFileChannel() {
					// Override the needed method and let it throw then
				});
			}

			@Override
			public InputStream newThrowingInputStream() {
				return new InputStream() {

					boolean closed;

					@Override
					public int read(final byte[] b) throws IOException {
						throw new IOException();
					}

					@Override
					public int read(final byte[] b, final int off, final int len) throws IOException {
						throw new IOException();
					}

					@Override
					public long skip(final long n) throws IOException {
						throw new IOException();
					}

					@Override
					public int available() throws IOException {
						throw new IOException();
					}

					@Override
					public void close() throws IOException {
						if (!this.closed) {
							this.closed = true;
							throw new IOException();
						}
					}

					@Override
					public void reset() throws IOException {
						throw new IOException();
					}

					@Override
					public int read() throws IOException {
						throw new IOException();
					}
				};
			}
		};
	}

	private static class CloseLoggingInputStream extends FilterInputStream {

		boolean closed;

		CloseLoggingInputStream(final InputStream in) {
			super(in);
		}

		@Override
		public void close() throws IOException {
			this.closed = true;
			super.close();
		}

		boolean wasClosed() {
			return this.closed;
		}
	}

	private static class CloseLoggingOutputStream extends FilterOutputStream {

		boolean closed;

		CloseLoggingOutputStream(final OutputStream out) {
			super(out);
		}

		@Override
		public void close() throws IOException {
			this.closed = true;
			super.close();
		}

		boolean wasClosed() {
			return this.closed;
		}
	}

	public interface Thrower {
		public void run() throws Throwable;
	}

	public static void assertThrowsNPE(final Thrower thrower, final String message) {
		assertThrows(thrower, NullPointerException.class, message);
	}

	public static <T extends Throwable> void assertThrows(final Thrower thrower, final Class<T> throwable, final String message) {
		Throwable thrown;
		try {
			thrower.run();
			thrown = null;
		} catch (final Throwable caught) {
			thrown = caught;
		}

		if (!throwable.isInstance(thrown)) {
			final String caught = thrown == null ? "nothing" : thrown.getClass().getCanonicalName();
			throw new AssertionError(format("Expected to catch %s, but caught %s", throwable, caught), thrown);
		}

		if (thrown != null && !message.equals(thrown.getMessage()))
			throw new AssertionError(format("Expected exception message to be '%s', but it's '%s'", message, thrown.getMessage()));
	}

	private static abstract class AbstractFileChannel extends FileChannel {

		@Override
		public int read(final ByteBuffer dst) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int write(final ByteBuffer src) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public long position() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public FileChannel position(final long newPosition) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public long size() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public FileChannel truncate(final long size) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void force(final boolean metaData) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public long transferTo(final long position, final long count, final WritableByteChannel target) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public long transferFrom(final ReadableByteChannel src, final long position, final long count) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int read(final ByteBuffer dst, final long position) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int write(final ByteBuffer src, final long position) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public MappedByteBuffer map(final MapMode mode, final long position, final long size) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public FileLock lock(final long position, final long size, final boolean shared) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public FileLock tryLock(final long position, final long size, final boolean shared) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		protected void implCloseChannel() throws IOException {
			throw new UnsupportedOperationException();
		}
	}
}
