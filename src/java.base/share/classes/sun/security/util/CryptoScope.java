package sun.security.util;

import java.security.CryptoPrimitive;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Crypto scope corresponds to one or more crypto primitives.
 */
enum CryptoScope {

    KX("kx", new HashSet<>(List.of(CryptoPrimitive.KEY_AGREEMENT))),      // Key Exchange
    AUTHN("authn", new HashSet<>(List.of(CryptoPrimitive.SIGNATURE)));       // Authentication

    private final Set<CryptoPrimitive> cryptoPrimitives;
    private final String name;

    CryptoScope(String name, Set<CryptoPrimitive> cryptoPrimitives) {
        this.name = name;
        this.cryptoPrimitives = cryptoPrimitives;
    }

    Set<CryptoPrimitive> getCryptoPrimitives() {
        return cryptoPrimitives;
    }

    String getName() {
        return name;
    }
}
