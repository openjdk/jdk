/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.misc;
import java.util.Hashtable;

/**
 * This class provides ANSI/ISO C signal support. A Java program can register
 * signal handlers for the current process. There are two restrictions:
 * <ul>
 * <li>
 * Java code cannot register a handler for signals that are already used
 * by the Java VM implementation. The <code>Signal.handle</code>
 * function raises an <code>IllegalArgumentException</code> if such an attempt
 * is made.
 * <li>
 * When <code>Signal.handle</code> is called, the VM internally registers a
 * special C signal handler. There is no way to force the Java signal handler
 * to run synchronously before the C signal handler returns. Instead, when the
 * VM receives a signal, the special C signal handler creates a new thread
 * (at priority <code>Thread.MAX_PRIORITY</code>) to
 * run the registered Java signal handler. The C signal handler immediately
 * returns. Note that because the Java signal handler runs in a newly created
 * thread, it may not actually be executed until some time after the C signal
 * handler returns.
 * </ul>
 * <p>
 * Signal objects are created based on their names. For example:
 * <blockquote><pre>
 * new Signal("INT");
 * </blockquote></pre>
 * constructs a signal object corresponding to <code>SIGINT</code>, which is
 * typically produced when the user presses <code>Ctrl-C</code> at the command line.
 * The <code>Signal</code> constructor throws <code>IllegalArgumentException</code>
 * when it is passed an unknown signal.
 * <p>
 * This is an example of how Java code handles <code>SIGINT</code>:
 * <blockquote><pre>
 * SignalHandler handler = new SignalHandler () {
 *     public void handle(Signal sig) {
 *       ... // handle SIGINT
 *     }
 * };
 * Signal.handle(new Signal("INT"), handler);
 * </blockquote></pre>
 *
 * @author   Sheng Liang
 * @author   Bill Shannon
 * @see      sun.misc.SignalHandler
 * @since    1.2
 */
public final class Signal {
    private static Hashtable handlers = new Hashtable(4);
    private static Hashtable signals = new Hashtable(4);

    private int number;
    private String name;

    /* Returns the signal number */
    public int getNumber() {
        return number;
    }

    /**
     * Returns the signal name.
     *
     * @return the name of the signal.
     * @see sun.misc.Signal#Signal(String name)
     */
    public String getName() {
        return name;
    }

    /**
     * Compares the equality of two <code>Signal</code> objects.
     *
     * @param other the object to compare with.
     * @return whether two <code>Signal</code> objects are equal.
     */
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !(other instanceof Signal)) {
            return false;
        }
        Signal other1 = (Signal)other;
        return name.equals(other1.name) && (number == other1.number);
    }

    /**
     * Returns a hashcode for this Signal.
     *
     * @return  a hash code value for this object.
     */
    public int hashCode() {
        return number;
    }

    /**
     * Returns a string representation of this signal. For example, "SIGINT"
     * for an object constructed using <code>new Signal ("INT")</code>.
     *
     * @return a string representation of the signal
     */
    public String toString() {
        return "SIG" + name;
    }

    /**
     * Constructs a signal from its name.
     *
     * @param name the name of the signal.
     * @exception IllegalArgumentException unknown signal
     * @see sun.misc.Signal#getName()
     */
    public Signal(String name) {
        number = findSignal(name);
        this.name = name;
        if (number < 0) {
            throw new IllegalArgumentException("Unknown signal: " + name);
        }
    }

    /**
     * Registers a signal handler.
     *
     * @param sig a signal
     * @param handler the handler to be registered with the given signal.
     * @result the old handler
     * @exception IllegalArgumentException the signal is in use by the VM
     * @see sun.misc.Signal#raise(Signal sig)
     * @see sun.misc.SignalHandler
     * @see sun.misc.SignalHandler#SIG_DFL
     * @see sun.misc.SignalHandler#SIG_IGN
     */
    public static synchronized SignalHandler handle(Signal sig,
                                                    SignalHandler handler)
        throws IllegalArgumentException {
        long newH = (handler instanceof NativeSignalHandler) ?
                      ((NativeSignalHandler)handler).getHandler() : 2;
        long oldH = handle0(sig.number, newH);
        if (oldH == -1) {
            throw new IllegalArgumentException
                ("Signal already used by VM or OS: " + sig);
        }
        signals.put(new Integer(sig.number), sig);
        synchronized (handlers) {
            SignalHandler oldHandler = (SignalHandler)handlers.get(sig);
            handlers.remove(sig);
            if (newH == 2) {
                handlers.put(sig, handler);
            }
            if (oldH == 0) {
                return SignalHandler.SIG_DFL;
            } else if (oldH == 1) {
                return SignalHandler.SIG_IGN;
            } else if (oldH == 2) {
                return oldHandler;
            } else {
                return new NativeSignalHandler(oldH);
            }
        }
    }

    /**
     * Raises a signal in the current process.
     *
     * @param sig a signal
     * @see sun.misc.Signal#handle(Signal sig, SignalHandler handler)
     */
    public static void raise(Signal sig) throws IllegalArgumentException {
        if (handlers.get(sig) == null) {
            throw new IllegalArgumentException("Unhandled signal: " + sig);
        }
        raise0(sig.number);
    }

    /* Called by the VM to execute Java signal handlers. */
    private static void dispatch(final int number) {
        final Signal sig = (Signal)signals.get(new Integer(number));
        final SignalHandler handler = (SignalHandler)handlers.get(sig);

        Runnable runnable = new Runnable () {
            public void run() {
              // Don't bother to reset the priority. Signal handler will
              // run at maximum priority inherited from the VM signal
              // dispatch thread.
              // Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
                handler.handle(sig);
            }
        };
        if (handler != null) {
            new Thread(runnable, sig + " handler").start();
        }
    }

    /* Find the signal number, given a name. Returns -1 for unknown signals. */
    private static native int findSignal(String sigName);
    /* Registers a native signal handler, and returns the old handler.
     * Handler values:
     *   0     default handler
     *   1     ignore the signal
     *   2     call back to Signal.dispatch
     *   other arbitrary native signal handlers
     */
    private static native long handle0(int sig, long nativeH);
    /* Raise a given signal number */
    private static native void raise0(int sig);
}
