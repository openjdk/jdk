/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.keymap;

import java.io.IOError;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

import jdk.internal.org.jline.reader.EndOfFileException;
import jdk.internal.org.jline.utils.ClosedException;
import jdk.internal.org.jline.utils.NonBlockingReader;

/**
 * The BindingReader transforms incoming characters into key bindings.
 * <p>
 * This class reads characters from a {@link NonBlockingReader} and maps them to
 * bindings defined in a {@link KeyMap}. It handles multi-character key sequences,
 * such as escape sequences for function keys, by reading ahead when necessary.
 * <p>
 * Key features include:
 * <ul>
 *   <li>Support for timeout-based disambiguation of key sequences</li>
 *   <li>Buffering of input to handle multi-character sequences</li>
 *   <li>Support for running macros (predefined sequences of keystrokes)</li>
 *   <li>Access to the current input buffer and last binding read</li>
 * </ul>
 * <p>
 * This class is used by the {@link org.jline.reader.LineReader} to read key bindings
 * for line editing operations.
 */
public class BindingReader {

    /** The non-blocking reader used to read input characters */
    protected final NonBlockingReader reader;
    /** Buffer for storing characters during key sequence processing */
    protected final StringBuilder opBuffer = new StringBuilder();
    /** Stack for pushing back characters that need to be re-read */
    protected final Deque<Integer> pushBackChar = new ArrayDeque<>();
    /** The last key binding that was matched */
    protected String lastBinding;

    /**
     * Creates a new BindingReader that reads from the specified NonBlockingReader.
     *
     * @param reader the NonBlockingReader to read characters from
     */
    public BindingReader(NonBlockingReader reader) {
        this.reader = reader;
    }

    /**
     * Read from the input stream and decode an operation from the key map.
     *
     * The input stream will be read character by character until a matching
     * binding can be found.  Characters that can't possibly be matched to
     * any binding will be send with the {@link KeyMap#getNomatch()} binding.
     * Unicode (&gt;= 128) characters will be matched to {@link KeyMap#getUnicode()}.
     * <p>
     * If the current key sequence is ambiguous, i.e. the sequence is bound but
     * it's also a prefix to other sequences, then the {@link KeyMap#getAmbiguousTimeout()}
     * timeout will be used to wait for another incoming character.
     * If a character comes, the disambiguation will be done. If the timeout elapses
     * and no character came in, or if the timeout is &lt;= 0, the current bound operation
     * will be returned.
     * <p>
     * This method blocks until a complete key binding is read or the end of the
     * stream is reached. If a binding is found in the KeyMap, it is returned.
     * Otherwise, if the Unicode fallback is set in the KeyMap and a Unicode
     * character is read, the Unicode fallback binding is returned.
     *
     * @param <T> the type of bindings in the KeyMap
     * @param keys the KeyMap to use for mapping input to bindings
     * @return the binding for the read key sequence, or null if the end of stream is reached
     */
    public <T> T readBinding(KeyMap<T> keys) {
        return readBinding(keys, null, true);
    }

    /**
     * Reads a key binding from the input stream using the specified KeyMaps.
     * <p>
     * This method works like {@link #readBinding(KeyMap)}, but it first checks
     * the local KeyMap for a binding before falling back to the main KeyMap.
     *
     * @param <T> the type of bindings in the KeyMaps
     * @param keys the main KeyMap to use for mapping input to bindings
     * @param local the local KeyMap to check first for bindings
     * @return the binding for the read key sequence, or null if the end of stream is reached
     */
    public <T> T readBinding(KeyMap<T> keys, KeyMap<T> local) {
        return readBinding(keys, local, true);
    }

    /**
     * Reads a key binding from the input stream using the specified KeyMaps.
     * <p>
     * This method works like {@link #readBinding(KeyMap, KeyMap)}, but it allows
     * specifying whether to block waiting for input or return immediately if no
     * input is available.
     *
     * @param <T> the type of bindings in the KeyMaps
     * @param keys the main KeyMap to use for mapping input to bindings
     * @param local the local KeyMap to check first for bindings, or null if none
     * @param block whether to block waiting for input
     * @return the binding for the read key sequence, or null if no input is available or the end of stream is reached
     */
    public <T> T readBinding(KeyMap<T> keys, KeyMap<T> local, boolean block) {
        lastBinding = null;
        T o = null;
        int[] remaining = new int[1];
        boolean hasRead = false;
        for (; ; ) {
            if (local != null) {
                o = local.getBound(opBuffer, remaining);
            }
            if (o == null && (local == null || remaining[0] >= 0)) {
                o = keys.getBound(opBuffer, remaining);
            }
            // We have a binding and additional chars
            if (o != null) {
                if (remaining[0] >= 0) {
                    runMacro(opBuffer.substring(opBuffer.length() - remaining[0]));
                    opBuffer.setLength(opBuffer.length() - remaining[0]);
                } else {
                    long ambiguousTimeout = keys.getAmbiguousTimeout();
                    if (ambiguousTimeout > 0 && peekCharacter(ambiguousTimeout) != NonBlockingReader.READ_EXPIRED) {
                        o = null;
                    }
                }
                if (o != null) {
                    lastBinding = opBuffer.toString();
                    opBuffer.setLength(0);
                    return o;
                }
                // We don't match anything
            } else if (remaining[0] > 0) {
                int cp = opBuffer.codePointAt(0);
                String rem = opBuffer.substring(Character.charCount(cp));
                lastBinding = opBuffer.substring(0, Character.charCount(cp));
                // Unicode character
                o = (cp >= KeyMap.KEYMAP_LENGTH) ? keys.getUnicode() : keys.getNomatch();
                opBuffer.setLength(0);
                opBuffer.append(rem);
                if (o != null) {
                    return o;
                }
            }

            if (!block && hasRead) {
                break;
            }
            int c = readCharacter();
            if (c == -1) {
                return null;
            }
            opBuffer.appendCodePoint(c);
            hasRead = true;
        }
        return null;
    }

    /**
     * Reads characters from the input until a specific sequence is encountered.
     * <p>
     * This method reads characters one by one and accumulates them in a buffer
     * until the specified terminating sequence is found.
     * </p>
     *
     * @param sequence the terminating sequence to look for
     * @return the string read up to but not including the terminating sequence,
     *         or null if the end of the stream is reached before the sequence is found
     */
    public String readStringUntil(String sequence) {
        StringBuilder sb = new StringBuilder();
        if (!pushBackChar.isEmpty()) {
            pushBackChar.forEach(sb::appendCodePoint);
        }
        try {
            char[] buf = new char[64];
            while (true) {
                int idx = sb.indexOf(sequence, Math.max(0, sb.length() - buf.length - sequence.length()));
                if (idx >= 0) {
                    String rem = sb.substring(idx + sequence.length());
                    runMacro(rem);
                    return sb.substring(0, idx);
                }
                int l = reader.readBuffered(buf);
                if (l < 0) {
                    throw new ClosedException();
                }
                sb.append(buf, 0, l);
            }
        } catch (ClosedException e) {
            throw new EndOfFileException(e);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    /**
     * Reads a single character (Unicode code point) from the input stream.
     * <p>
     * This method blocks until a character is available or the end of the stream
     * is reached. It properly handles surrogate pairs for Unicode characters
     * outside the BMP (Basic Multilingual Plane).
     *
     * @return the character read, or -1 if the end of the stream is reached
     * @throws EndOfFileException if the stream is closed while reading
     * @throws IOError if an I/O error occurs
     */
    public int readCharacter() {
        if (!pushBackChar.isEmpty()) {
            return pushBackChar.pop();
        }
        try {
            int c = NonBlockingReader.READ_EXPIRED;
            int s = 0;
            while (c == NonBlockingReader.READ_EXPIRED) {
                c = reader.read(100L);
                if (c >= 0 && Character.isHighSurrogate((char) c)) {
                    s = c;
                    c = NonBlockingReader.READ_EXPIRED;
                }
            }
            return s != 0 ? Character.toCodePoint((char) s, (char) c) : c;
        } catch (ClosedException e) {
            throw new EndOfFileException(e);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    /**
     * Reads a single character (Unicode code point) from the input stream with buffering.
     * <p>
     * This method attempts to read multiple characters at once for efficiency,
     * storing them in an internal buffer. It properly handles surrogate pairs for
     * Unicode characters outside the BMP (Basic Multilingual Plane).
     *
     * @return the character read, or -1 if the end of the stream is reached
     * @throws EndOfFileException if the stream is closed while reading
     * @throws IOError if an I/O error occurs
     */
    public int readCharacterBuffered() {
        try {
            if (pushBackChar.isEmpty()) {
                char[] buf = new char[32];
                int l = reader.readBuffered(buf);
                if (l <= 0) {
                    return -1;
                }
                int s = 0;
                for (int i = 0; i < l; ) {
                    int c = buf[i++];
                    if (Character.isHighSurrogate((char) c)) {
                        s = c;
                        if (i < l) {
                            c = buf[i++];
                            pushBackChar.addLast(Character.toCodePoint((char) s, (char) c));
                        } else {
                            break;
                        }
                    } else {
                        s = 0;
                        pushBackChar.addLast(c);
                    }
                }
                if (s != 0) {
                    int c = reader.read();
                    if (c >= 0) {
                        pushBackChar.addLast(Character.toCodePoint((char) s, (char) c));
                    } else {
                        return -1;
                    }
                }
            }
            return pushBackChar.pop();
        } catch (ClosedException e) {
            throw new EndOfFileException(e);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    /**
     * Peeks at the next character in the input stream without consuming it.
     * <p>
     * This method waits up to the specified timeout for a character to become
     * available. If a character is available, it is returned but not consumed.
     * If no character is available within the timeout, -2 is returned.
     *
     * @param timeout the maximum time to wait in milliseconds
     * @return the next character, -1 if the end of the stream is reached, or -2 if the timeout expires
     * @throws IOError if an I/O error occurs
     */
    public int peekCharacter(long timeout) {
        if (!pushBackChar.isEmpty()) {
            return pushBackChar.peek();
        }
        try {
            return reader.peek(timeout);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    /**
     * Runs a macro by pushing its characters into the input buffer.
     * <p>
     * This method allows simulating keystrokes by pushing the characters of the
     * macro string into the input buffer. These characters will be read before
     * any actual input from the underlying reader.
     *
     * @param macro the string of characters to push into the input buffer
     */
    public void runMacro(String macro) {
        macro.codePoints().forEachOrdered(pushBackChar::addLast);
    }

    /**
     * Returns the current contents of the operation buffer.
     * <p>
     * The operation buffer contains the characters of the current key sequence
     * being processed. This is useful for debugging or displaying the current
     * input state.
     *
     * @return the current operation buffer as a string
     */
    public String getCurrentBuffer() {
        return opBuffer.toString();
    }

    /**
     * Returns the last key binding that was successfully read.
     * <p>
     * This method returns the string representation of the last key sequence
     * that was successfully mapped to a binding. This can be useful for displaying
     * the binding to the user or for implementing key sequence chaining.
     *
     * @return the last key binding, or null if no binding has been read yet
     */
    public String getLastBinding() {
        return lastBinding;
    }
}
