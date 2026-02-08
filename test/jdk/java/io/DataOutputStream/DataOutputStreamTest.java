import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.io.*;


/*
* @test
* @bug 8373660
* @summary Test that writeBytes throws NullPointerException with clear message
* @run junit DataOutputStreamTest
*/
public class DataOutputStreamTest {
    /**
     * Test that writeBytes throws NullPointerException with clear message
     * when null is passed.
     */
    @Test
    public void testWriteBytesWithNull() throws IOException {
        DataOutputStream dos = new DataOutputStream(
            new ByteArrayOutputStream());
        NullPointerException npe = assertThrows(
            NullPointerException.class,
            () -> dos.writeBytes(null),
            "writeBytes should throw NullPointerException when null is passed");
        assertEquals("s", npe.getMessage(),
            "Exception message should be 's' (parameter name)");
    }
}