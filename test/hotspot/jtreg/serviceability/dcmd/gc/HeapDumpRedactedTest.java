import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.hprof.parser.Reader;
import jdk.test.lib.hprof.model.JavaHeapObject;
import jdk.test.lib.hprof.model.*;
import java.util.Enumeration;
import jdk.test.lib.Asserts;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;



/*
 * @test 
 * @summary  Test that primitive values in the heap dump are 0
 * @library /test/lib
 * @run main/othervm HeapDumpRedactedTest
 */

 
 // test class with nonzero fields
 class PrimitiveTestTarget {
    byte byteTest = 0x1;
    char character = 'A';
    short shortInt = Short.MAX_VALUE;
    int integer = Integer.MAX_VALUE;
    long longInt = Long.MAX_VALUE;
    float floatValue = 123.456f;
    double doubleValue = 987.654;
    boolean bool = true;
    
    // Array of primitive types
    byte[] byteArray = {0x1,0x2,0x3,0x4,0x5};
    char[] charArray = {'a','b','c','d','e'};
    short[] shortArray = {1,2,3,4,5};
    int[] intArray = {1,2,3,4,5};
    float[] floatArray = {1.0f,2.0f,3.0f,4.0f,5.0f};
    double[] doubleArray = {1.0,2.0,3.0,4.0,Double.NaN};
    long[] longArray = {1,2,3,4,5};
    boolean[] boolArray = {true,false,true,false,true};

    static int staticInt = 100;
    String str = "test_string";
}

public class HeapDumpRedactedTest {
    public static void main(String[] args) throws Exception {
        PrimitiveTestTarget target = new PrimitiveTestTarget();
        PidJcmdExecutor executor = new PidJcmdExecutor();
        File dump = new File("jcmd.gc.heap_dump." + System.currentTimeMillis() + ".hprof");

        if (dump.exists()) {
            dump.delete();
        }

        executor.execute("GC.heap_dump " + dump.getAbsolutePath() + " -redact");
        Snapshot snapshot = Reader.readFile(dump.getAbsolutePath(), true, 0);
        snapshot.resolve(false);
        Enumeration<JavaHeapObject> things = snapshot.getThings();
        
        // Find the test object from heap dump
        String className = PrimitiveTestTarget.class.getName();
        JavaClass testClass = snapshot.findClass(className);
        if (testClass == null) {
            throw new RuntimeException("Class '" + className + "' not found");
        }
        int instanceCount = testClass.getInstancesCount(false);
        if (instanceCount < 1) {
            throw new RuntimeException("No instances of '" + className + "' found");
        }
        JavaObject targetObject = (JavaObject) testClass.getInstances(false).nextElement();

        // primitives
        testPrimitive("byteTest", targetObject, "0x0");
        testPrimitive("character", targetObject , ""+'\u0000');
        testPrimitive("shortInt", targetObject, "0");
        testPrimitive("integer", targetObject, "0");
        testPrimitive("longInt", targetObject, "0");
        testPrimitive("floatValue", targetObject, "0.0");
        testPrimitive("doubleValue", targetObject, "0.0");
        testPrimitive("bool", targetObject, "false");

        // arrays
        testPrimitiveArray("byteArray", targetObject, "0x0");
        testPrimitiveArray("charArray", targetObject , ""+'\u0000');
        testPrimitiveArray("shortArray", targetObject, "0");
        testPrimitiveArray("intArray", targetObject, "0");
        testPrimitiveArray("longArray", targetObject, "0");
        testPrimitiveArray("floatArray", targetObject, "0.0");
        testPrimitiveArray("doubleArray", targetObject, "0.0");
        testPrimitiveArray("boolArray", targetObject, "false");

        //static field
        JavaThing staticInt =  testClass.getStaticField("staticInt");
        Asserts.assertTrue(staticInt instanceof JavaValue);
        JavaValue value = (JavaValue) staticInt;
        Asserts.assertEquals(value.toString(), "0");

        // Object (String)
        JavaObject str = getObject("str", targetObject);
        testPrimitiveArray("value", str, "0x0"); // String.value is byte array

        
        HprofParser.parseAndVerify(dump);
        dump.delete();
    }

    private static void testPrimitive(String fieldName, JavaObject obj, String expected) {
        JavaThing field = obj.getField(fieldName);
        Asserts.assertTrue(field instanceof JavaValue);
        JavaValue value = (JavaValue) field;
        Asserts.assertEquals(expected, value.toString());
    }

    private static void testPrimitiveArray(String fieldName, JavaObject obj, String expected) {
        JavaHeapObject field = (JavaHeapObject) obj.getField(fieldName);
        Asserts.assertTrue(field instanceof JavaValueArray);
        JavaValueArray array = (JavaValueArray) field;
        JavaThing[] elements = array.getElements(); 
        for (JavaThing element : elements) {
            Asserts.assertTrue(element instanceof JavaValue);
            JavaValue value = (JavaValue) element;
            Asserts.assertEquals(expected, value.toString());
        }
    }

    private static JavaObject getObject(String fieldName, JavaObject obj) {
        JavaHeapObject f = (JavaHeapObject) obj.getField(fieldName);
        Asserts.assertTrue(f instanceof JavaObject);
        return (JavaObject) f;
    }
}
