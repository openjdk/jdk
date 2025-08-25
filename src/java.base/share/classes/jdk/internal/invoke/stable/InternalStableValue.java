package jdk.internal.invoke.stable;

import java.lang.invoke.StableValue;

// Wrapper interface to allow internal implementations that are not public
public non-sealed interface InternalStableValue<T> extends StableValue<T> { }
