package validation.jdk8037819;

import com.sun.org.apache.xerces.internal.dom.PSVIElementNSImpl;
import com.sun.org.apache.xerces.internal.xs.ItemPSVI;
import validation.BaseTest;

public class BasicTest1 extends BaseTest {
    public static void main(String[] args) throws Exception {
        BasicTest1 test = new BasicTest1();
        test.setUp();
        test.testSimpleValidation();
        test.testSimpleValidationWithTrivialXSIType();
        test.tearDown();
    }

    protected String getXMLDocument() {
        return "base.xml";
    }

    protected String getSchemaFile() {
        return "base.xsd";
    }

    public BasicTest1() {
        super("BasicTest1");
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSimpleValidation() {
        try {
            reset();
            validateDocument();
        } catch (Exception e) {
            fail("Validation failed: " + e.getMessage());
        }
        doValidityAsserts();
    }

    public void testSimpleValidationWithTrivialXSIType() {
        try {
            reset();
            ((PSVIElementNSImpl) fRootNode).setAttributeNS(
                "http://www.w3.org/2001/XMLSchema-instance", "type", "X");
            validateDocument();
        } catch (Exception e) {
            fail("Validation failed: " + e.getMessage());
        }
        doValidityAsserts();
    }

    private void doValidityAsserts() {
        assertValidity(ItemPSVI.VALIDITY_VALID, fRootNode.getValidity());
        assertValidationAttempted(ItemPSVI.VALIDATION_FULL, fRootNode
                .getValidationAttempted());
        assertElementName("A", fRootNode.getElementDeclaration().getName());
        assertElementNamespaceNull(fRootNode.getElementDeclaration()
                .getNamespace());
        assertTypeName("X", fRootNode.getTypeDefinition().getName());
        assertTypeNamespaceNull(fRootNode.getTypeDefinition().getNamespace());
    }
}
