package jdk.nashorn.internal.runtime.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import jdk.nashorn.internal.objects.NativeSymbol;
import jdk.nashorn.internal.runtime.Symbol;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @bug 8142924
 * @summary ES6 symbols created with Symbol.for should deserialize to canonical instances
 */
@SuppressWarnings("javadoc")
public class JDK_8142924_Test {
    @Test
    public static void testNonGlobal() throws Exception {
        final String name = "testNonGlobal";
        final Symbol symbol1 = (Symbol)NativeSymbol.constructor(false, null, name);
        final Symbol symbol2 = serializeRoundTrip(symbol1);
        Assert.assertNotSame(symbol1, symbol2);
        Assert.assertEquals(symbol2.getName(), name);
        Assert.assertNotSame(symbol1, NativeSymbol._for(null, name));
    }

    @Test
    public static void testGlobal() throws Exception {
        final String name = "testGlobal";
        final Symbol symbol1 = (Symbol)NativeSymbol._for(null, name);
        final Symbol symbol2 = serializeRoundTrip(symbol1);
        Assert.assertSame(symbol1, symbol2);
    }

    private static Symbol serializeRoundTrip(final Symbol symbol) throws Exception {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(bout);
        out.writeObject(symbol);
        out.close();
        return (Symbol) new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray())).readObject();
    }
}
