/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

/**
 * Helper class for managing timeouts during I/O operations.
 *
 * <p>
 * The Timeout class provides a simple mechanism for tracking timeouts during I/O
 * operations. It helps with implementing operations that should complete within
 * a specified time limit, such as non-blocking reads with timeouts.
 * </p>
 *
 * <p>
 * This class supports both finite timeouts (specified in milliseconds) and infinite
 * timeouts (indicated by zero or negative timeout values). It provides methods for
 * starting the timeout countdown, checking if the timeout has expired, and calculating
 * the remaining time.
 * </p>
 *
 * <p>
 * The class is designed to be used in scenarios where multiple I/O operations need
 * to share a single timeout, ensuring that the total time for all operations does
 * not exceed the specified limit.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * // Create a timeout of 5 seconds
 * Timeout timeout = new Timeout(5000);
 *
 * // Start the timeout countdown
 * timeout.start();
 *
 * // Perform I/O operations, checking for timeout
 * while (!timeout.isExpired() &amp;&amp; !operationComplete()) {
 *     // Perform a partial operation with the remaining time
 *     long remaining = timeout.remaining();
 *     performPartialOperation(remaining);
 * }
 * </pre>
 */
public class Timeout {

    private final long timeout;
    private long cur = 0;
    private long end = Long.MAX_VALUE;

    public Timeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isInfinite() {
        return timeout <= 0;
    }

    public boolean isFinite() {
        return timeout > 0;
    }

    public boolean elapsed() {
        if (timeout > 0) {
            cur = System.currentTimeMillis();
            if (end == Long.MAX_VALUE) {
                end = cur + timeout;
            }
            return cur >= end;
        } else {
            return false;
        }
    }

    public long timeout() {
        return timeout > 0 ? Math.max(1, end - cur) : timeout;
    }
}
