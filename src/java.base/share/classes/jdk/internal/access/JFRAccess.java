package jdk.internal.access;

public interface JFRAccess {
    JFRAccess NONE = other -> 0;

    long swapContext(long other);
}
