package compiler.jsr292;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/*
 * @test
 * @bug 8166110
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 *
 * @run main/bootclasspath/othervm -Xbatch -XX:-TieredCompilation compiler.jsr292.MHDeoptTest
 *
 */
public class MHDeoptTest {

    static int xx = 0;

    public static void main(String[] args) throws Throwable {
        MethodHandle mh1 = MethodHandles.lookup().findStatic(MHDeoptTest.class, "body1", MethodType.methodType(int.class));
        MethodHandle mh2 = MethodHandles.lookup().findStatic(MHDeoptTest.class, "body2", MethodType.methodType(int.class));
        MethodHandle[] arr = new MethodHandle[] {mh2, mh1};

        for (MethodHandle mh : arr) {
            for (int i = 1; i < 50_000; i++) {
                xx = i;
                mainLink(mh);
            }
        }

    }

    static int mainLink(MethodHandle mh) throws Throwable {
        return (int)mh.invokeExact();
    }

    static int cnt = 1000;

    static int body1() {
        int limit = 0x7fff;
        // uncommon trap
        if (xx == limit) {
            // OSR
            for (int i = 0; i < 50_000; i++) {
            }
            ++cnt;
            ++xx;
        }
        if (xx == limit + 1) {
            return cnt + 1;
        }
        return cnt;
    }

    static int body2() {
        int limit = 0x7fff;
        int dummy = 0;
        // uncommon trap
        if (xx == limit) {
            // OSR
            for (int i = 0; i < 50_000; i++) {
            }
            ++cnt;
            ++xx;
        }
        if (xx == limit + 1) {
            return cnt + 1;
        }
        return cnt;
    }

}
