import org.testng.annotations.Test;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartDocument;

import java.io.StringReader;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @modules java.xml
 * @run testng/othervm XmlInputFactoryTest
 *
 */
@Test
public class XmlInputFactoryTest {

    @Test
    void startDocumentEvent_standaloneSet() throws XMLStreamException {
        var factory = XMLInputFactory.newInstance();
        var xml = """
                <?xml version="1.0"?>""";
        var reader = factory.createXMLEventReader(new StringReader(xml));
        var startDocumentEvent = (StartDocument) reader.nextEvent();
        assertFalse(startDocumentEvent.standaloneSet());

        xml = """
                <?xml version="1.0" standalone="yes"?>""";
        reader = factory.createXMLEventReader(new StringReader(xml));
        startDocumentEvent = (StartDocument) reader.nextEvent();
        assertTrue(startDocumentEvent.standaloneSet());
        assertTrue(startDocumentEvent.isStandalone());

        xml = """
                <?xml version="1.0" standalone="no"?>""";
        reader = factory.createXMLEventReader(new StringReader(xml));
        startDocumentEvent = (StartDocument) reader.nextEvent();
        assertTrue(startDocumentEvent.standaloneSet());
        assertFalse(startDocumentEvent.isStandalone());
    }
}
