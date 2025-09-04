/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8366833
 * @summary Poly1305 does not always correctly update position for array-backed
 *          ByteBuffers after processMultipleBlocks
 * @run main UpdateAADTest
 */

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;

public class UpdateAADTest {
	private static final SecureRandom RAND;
	private static final KeyGenerator CC20GEN;

	static {
		try {
			RAND = new SecureRandom();
			CC20GEN = KeyGenerator.getInstance("ChaCha20");
		} catch (GeneralSecurityException gse) {
			throw new RuntimeException("Failed to init static JCE components",
					gse);
		}
	}

	public static void main(final String[] args) throws Exception {
		ByteBuffer twoKBuf = ByteBuffer.allocate(2048);
		ByteBuffer nonBABuf = ByteBuffer.allocate(1329);

		System.out.println("----- Test 1: Baseline test -----");
		System.out.println("Make an array backed buffer that is 16-byte " +
						   "aligned, treat all data as AAD and feed it to " +
						   " updateAAD.");
		new AADUpdateTest(twoKBuf, true).run();

		System.out.println("----- Test 2: Non Block Aligned Offset -----");
		System.out.println("Use the same buffer, but place the offset such " +
				           "that the remaining data is not block aligned.");
		new AADUpdateTest(twoKBuf.position(395), true).run();

		System.out.println("----- Test 3: Non Block Aligned Buf/Off -----");
		System.out.println("Make a buffer of non-block aligned size with an " +
				           "offset that keeps the remaining data non-block " +
				           "aligned.");
		new AADUpdateTest(nonBABuf.position(602), true).run();

		System.out.println("----- Test 4: Aligned Buffer Slice -----");
		System.out.println("Use a buffer of block aligned size, but slice " +
				           "the buffer such that the slice offset is part " +
				           "way into the original buffer.");
		new AADUpdateTest(twoKBuf.rewind().slice(1024,1024).position(42),
				true).run();

		// Test 5: Try the same test, this time with non-block aligned
		// buffers/slices.
		System.out.println("----- Test 5: Non-Aligned Buffer Slice -----");
		System.out.println("Try the same test as #4, this time with " +
				           "non-block aligned buffers/slices.");
		new AADUpdateTest(nonBABuf.rewind().slice(347, 347).position(86),
				true).run();
	}

	public static class AADUpdateTest implements Runnable {
		private final ByteBuffer buffer;
		private final boolean expectedPass;

		AADUpdateTest(ByteBuffer buf, boolean expPass) {
			buffer = Objects.requireNonNull(buf);
			expectedPass = expPass;
		}

		@Override
		public void run() {
			Cipher cipher;
			try {
				SecretKey key = CC20GEN.generateKey();
				byte[] nonce = new byte[12];
				RAND.nextBytes(nonce);

				cipher = Cipher.getInstance("ChaCha20-Poly1305");
				cipher.init(Cipher.ENCRYPT_MODE, key,
						new IvParameterSpec(nonce));
			} catch (GeneralSecurityException gse) {
				throw new RuntimeException("Failed during test setup", gse);
			}

			try {
				cipher.updateAAD(buffer);
				if (!expectedPass) {
					throw new RuntimeException(
							"Expected failing test did not throw exception");
				}
			} catch (Exception exc) {
				if (expectedPass) {
					throw new RuntimeException(
							"FAIL: Expected passing test failed", exc);
				}
			}
		}
	}
}