package sun.net.www.http;

import org.testng.annotations.Test;

@Test
public class KeepAliveStreamCleanerTest {

    /*
    Tests that KeepAliveStreamCleaner run does not throw an
    IllegalMonitorState Exception.
     */
    @Test
    public void keepAliveStreamCleanerTest() {
        KeepAliveStreamCleaner kase = new KeepAliveStreamCleaner();
        kase.run();
    }
}