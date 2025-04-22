/* /nodynamiccopyright/ */

package java.lang;

public class RequiresIdentityHelper<@jdk.internal.RequiresIdentity T> {
    public RequiresIdentityHelper() {}
    public RequiresIdentityHelper(@jdk.internal.RequiresIdentity Object o) {}
}

class RequiresIdentity2<T> {
    public RequiresIdentity2() {}
    public void foo(@jdk.internal.RequiresIdentity Object o) {}
    public void bar(@jdk.internal.RequiresIdentity Object... o) {}
    public void gg(@jdk.internal.RequiresIdentity T ri) {}
}
