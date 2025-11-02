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
 * You should have received a copy of the GNU General Public License version 2
 * along with this work; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8371091
 * @summary Verify that POST, PUT, and method() throw NullPointerException with a message when BodyPublisher is null.
 * @run main RequestBuilderNullBodyTest
 */

import java.net.URI;
import java.net.http.HttpRequest;

public class RequestBuilderNullBodyTest {

	public static void main(String[] args) {
		test(() -> HttpRequest.newBuilder(URI.create("https://example.com"))
			.POST(null)
			.build(), "POST");
		test(() -> HttpRequest.newBuilder(URI.create("https://example.com"))
			.PUT(null)
			.build(), "PUT");
		test(() -> HttpRequest.newBuilder(URI.create("https://example.com"))
			.method("PATCH", null)
			.build(), "method");
	}

	private static void test(Runnable r, String label) {
		try {
			r.run();
			throw new AssertionError(label + " should have thrown NullPointerException");
		} catch (NullPointerException e) {
			if (e.getMessage() == null || !e.getMessage().contains("BodyPublisher")) {
				throw new AssertionError(label + " NPE message missing or incorrect: " + e.getMessage());
			}
			System.out.println(label + " threw expected NPE with message: " + e.getMessage());
		}
	}
}
