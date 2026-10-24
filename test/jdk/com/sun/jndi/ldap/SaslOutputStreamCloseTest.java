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
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8377486
 * @summary Verify that SaslOutputStream.write() methods throw an IOException, if invoked
 *          when the stream is closed
 * @modules java.security.sasl/com.sun.security.sasl
 *          java.naming/com.sun.jndi.ldap.sasl:+open
 * @run junit ${test.main.class}
 */
class SaslOutputStreamCloseTest {

    /*
     * Verifies that SaslOutputStream.write(...) throws IOException if the SaslOutputStream
     * is closed.
     */
    @Test
    void testWriteThrowsIOExceptionOnClose() throws Exception {
        try (final OutputStream os = createSaslOutputStream(new ByteArrayOutputStream())) {
            os.write(new byte[]{0x42, 0x42});
            os.close();
            try {
                os.write(new byte[]{0x42}, 0, 1);
                fail("OutputStream.write(...) on closed " + os + " did not throw IOException");
            } catch (IOException ioe) {
                // verify it was thrown for right reason
                if (!"stream closed".equals(ioe.getMessage())) {
                    throw ioe; // propagate original exception
                }
                // expected
                System.err.println("received expected IOException: " + ioe);
            }
        }
    }

    // reflectively construct an instance of
    // (package private) com.sun.jndi.ldap.sasl.SaslOutputStream class
    private static OutputStream createSaslOutputStream(final OutputStream underlying) throws Exception {
        final Constructor<?> constructor = Class.forName("com.sun.jndi.ldap.sasl.SaslOutputStream")
                .getDeclaredConstructor(new Class[]{SaslClient.class, OutputStream.class});
        constructor.setAccessible(true);
        return (OutputStream) constructor.newInstance(new DummySaslClient(), underlying);
    }


    private static final class DummySaslClient implements SaslClient {
        private boolean closed;

        @Override
        public String getMechanismName() {
            return "DUMMY";
        }

        @Override
        public boolean hasInitialResponse() {
            return false;
        }

        @Override
        public byte[] evaluateChallenge(byte[] challenge) throws SaslException {
            return new byte[0];
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
            if (closed) {
                // intentionally throw something other than a IOException
                throw new IllegalStateException(this + " is closed");
            }
            return incoming;
        }

        @Override
        public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
            if (closed) {
                // intentionally throw something other than a IOException
                throw new IllegalStateException(this + " is closed");
            }
            return outgoing;
        }

        @Override
        public Object getNegotiatedProperty(String propName) {
            return null;
        }

        @Override
        public void dispose() {
            this.closed = true;
        }
    }
}
