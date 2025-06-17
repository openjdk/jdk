/* /nodynamiccopyright/ */

package java.lang;

public class RequiresIdentityHelper<@jdk.internal.RequiresIdentity T> {
    public RequiresIdentityHelper() {}
    public <@jdk.internal.RequiresIdentity TT> RequiresIdentityHelper(@jdk.internal.RequiresIdentity Object o) {}

    class RequiresIdentity2<TT> {
        public RequiresIdentity2() {}
        public void foo(@jdk.internal.RequiresIdentity Object o) {}
        public void bar(@jdk.internal.RequiresIdentity Object... o) {}
        public void gg(@jdk.internal.RequiresIdentity TT ri) {}
    }

    interface RequiresIdentityInt<@jdk.internal.RequiresIdentity T> {}

    interface MyIntFunction<@jdk.internal.RequiresIdentity R> {
        R apply(int value);
    }
}
