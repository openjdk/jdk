package jdk.internal.foreign;

/**
 * A record that can be used to represent the result of native calls.
 *
 * @param result the result returned from the native call
 * @param errno  the errno (if result <= 0) or 0
 */
public record ResultErrno(int result, int errno) {
}
