/**
 * @test /nodynamiccopyright/
 * @bug 8361570
 * @summary Verify there's no fake sealed not allowed here error when sealed
 *          and requires-identity Flags clash
 * @modules java.base/jdk.internal
 * @compile NoFalseSealedError.java
 */

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

void main(String[] args) {
    new RequiresIdentity(null) {};
    new WeakReference<>(null) {};
    new WeakHashMap<>() {};
}

static class RequiresIdentity {
    RequiresIdentity(@jdk.internal.RequiresIdentity Object o) {}
}
