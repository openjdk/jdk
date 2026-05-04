/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package javax.security.auth.callback;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.lang.ref.Cleaner;
import java.util.Arrays;

import jdk.internal.ref.CleanerFactory;

/**
 * <p> Underlying security services instantiate and pass a
 * {@code PasswordCallback} to the {@code handle}
 * method of a {@code CallbackHandler} to retrieve password information.
 *
 * @since 1.4
 * @see javax.security.auth.callback.CallbackHandler
 */
public class PasswordCallback implements Callback, java.io.Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 2267422647454909926L;

    private transient Cleaner.Cleanable cleanable;

    /**
     * @serial
     * @since 1.4
     */
    private final String prompt;

    /**
     * @serial
     * @since 1.4
     */
    private final boolean echoOn;

    /**
     * @serial
     * @since 1.4
     */
    private char[] inputPassword;

    /**
     * Construct a {@code PasswordCallback} with a prompt
     * and a boolean specifying whether the password should be displayed
     * as it is being typed.
     *
     * @param prompt the prompt used to request the password.
     *
     * @param echoOn true if the password should be displayed
     *                  as it is being typed.
     *
     * @exception IllegalArgumentException if {@code prompt} is null or
     *                  if {@code prompt} has a length of 0.
     */
    public PasswordCallback(String prompt, boolean echoOn) {
        if (prompt == null || prompt.isEmpty())
            throw new IllegalArgumentException();

        this.prompt = prompt;
        this.echoOn = echoOn;
    }

    /**
     * Get the prompt.
     *
     * @return the prompt.
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Return whether the password
     * should be displayed as it is being typed.
     *
     * @return the whether the password
     *          should be displayed as it is being typed.
     */
    public boolean isEchoOn() {
        return echoOn;
    }

    /**
     * Set the retrieved password.
     *
     * <p> This method makes a copy of the input {@code password}
     * before storing it.
     *
     * @param password the retrieved password, which may be null.
     *
     * @see #getPassword
     */
    public void setPassword(char[] password) {
        // Cleanup the last buffered password copy.
        if (cleanable != null) {
            cleanable.clean();
            cleanable = null;
        }

        // Set the retrieved password.
        this.inputPassword = (password == null ? null : password.clone());

        if (this.inputPassword != null) {
            cleanable = CleanerFactory.cleaner().register(
                    this, cleanerFor(inputPassword));
        }
    }

    /**
     * Get the retrieved password.
     *
     * <p> This method returns a copy of the retrieved password.
     *
     * @return the retrieved password, which may be null.
     *
     * @see #setPassword
     */
    public char[] getPassword() {
        return (inputPassword == null ? null : inputPassword.clone());
    }

    /**
     * Clear the retrieved password.
     */
    public void clearPassword() {
        // Cleanup the last retrieved password copy.
        if (cleanable != null) {
            cleanable.clean();
            cleanable = null;
        }
    }

    private static Runnable cleanerFor(char[] password) {
        return () -> Arrays.fill(password, ' ');
    }

    /**
     * Restores the state of this object from the stream.
     *
     * @param  stream the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();

        if (prompt == null || prompt.isEmpty()) {
            throw new InvalidObjectException("Missing prompt");
        }

        if (inputPassword != null) {
            char[] temp = inputPassword;
            inputPassword = temp.clone();
            Arrays.fill(temp, '0');
            cleanable = CleanerFactory.cleaner().register(
                    this, cleanerFor(inputPassword));
        }
    }
}
