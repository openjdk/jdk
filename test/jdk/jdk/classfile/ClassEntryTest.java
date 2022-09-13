/*
 * @test
 * @summary Testing Classfile ClassEntry lists methods.
 * @run testng ClassEntryTest
 */
import jdk.classfile.constantpool.ClassEntry;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.*;

public class ClassEntryTest {

    static final List<ClassEntry> additionCE = List.copyOf(ClassEntry.addingSymbols(List.of(), new ClassDesc[] {ConstantDescs.CD_void, ConstantDescs.CD_Enum, ConstantDescs.CD_Class}));
    static final List<ClassDesc> additionCD = List.of(ConstantDescs.CD_void, ConstantDescs.CD_Enum, ConstantDescs.CD_Class);
    static final List<ClassEntry> base = List.copyOf(additionCE);

    @Test
    public void testNPECombos() {
        // NPE on first param
        try {
            ClassEntry.adding(null, additionCE);
            fail("NPE expected");
        } catch(NullPointerException e) { }
        try {
            ClassEntry.adding(null, additionCE.get(1), additionCE.get(2));
            fail("NPE expected");
        } catch(NullPointerException e) { }
        try {
            ClassEntry.addingSymbols(null, additionCD);
            fail("NPE expected");
        } catch(NullPointerException e) { }
        try {
            ClassEntry.addingSymbols(null, additionCD.get(1), additionCD.get(2));
            fail("NPE expected");
        } catch(NullPointerException e) { }
        // NPE on second param
        try {
            ClassEntry.adding(base, (List<ClassEntry>)null);
            fail("NPE expected");
        } catch(NullPointerException e) { }
        try {
            ClassEntry.adding(base, (ClassEntry[])null);
            fail("NPE expected");
        } catch(NullPointerException e) { }
        try {
            ClassEntry.addingSymbols(base, (List<ClassDesc>)null);
            fail("NPE expected");
        } catch(NullPointerException e) { }
        try {
            ClassEntry.addingSymbols(base, (ClassDesc[])null);
            fail("NPE expected");
        } catch(NullPointerException e) { }
    }

    @Test
    void combine() {
        List<ClassEntry> expected = new ArrayList<>(base);
        expected.addAll(additionCE);
        expected = List.copyOf(expected);
        // Ensure inputs are equivalent before using 'expected' as a common result
        Assert.assertTrue(listCompare(additionCE, ClassEntry.addingSymbols(List.<ClassEntry>of(), additionCD)));
        Assert.assertTrue(listCompare(expected, ClassEntry.adding(base, additionCE)));
        Assert.assertTrue(listCompare(expected, ClassEntry.adding(base, additionCE.toArray(new ClassEntry[0]))));
        Assert.assertTrue(listCompare(expected, ClassEntry.addingSymbols(base, additionCD)));
        Assert.assertTrue(listCompare(expected, ClassEntry.addingSymbols(base, additionCD.toArray(new ClassDesc[0]))));
    }

    boolean listCompare(List<ClassEntry> a, List<ClassEntry> b) {
        if (a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            ClassEntry ca = a.get(i);
            ClassEntry cb = b.get(i);
            if (!ca.asSymbol().equals(cb.asSymbol())) {
                return false;
            }
        }
        return true;
    }

    @Test
    void throwOnNullAdditions() {
        // NPE when adding a null element
        ArrayList<ClassEntry> withNullElement = new ArrayList<ClassEntry>(additionCE);
        withNullElement.add(null);
        try {
            ClassEntry.adding(base, withNullElement);
            fail("NPE expected");
        } catch(NullPointerException e) { }
        try {
            ClassEntry.adding(base, withNullElement.toArray(new ClassEntry[0]));
            fail("NPE expected");
        } catch(NullPointerException e) { }
        ArrayList<ClassDesc> withNullElementCD = new ArrayList<ClassDesc>(additionCD);
        withNullElementCD.add(null);
        try {
            ClassEntry.addingSymbols(base, withNullElementCD);
            fail("NPE expected");
        } catch(NullPointerException e) { }
        try {
            ClassEntry.addingSymbols(base, withNullElementCD.toArray(new ClassDesc[0]));
            fail("NPE expected");
        } catch(NullPointerException e) { }
    }

    @Test
    void addEmpty() {
        Assert.assertEquals(base, ClassEntry.adding(base, List.of()));
        Assert.assertEquals(base, ClassEntry.adding(base, new ClassEntry[0]));
        Assert.assertEquals(base, ClassEntry.addingSymbols(base, List.of()));
        Assert.assertEquals(base, ClassEntry.addingSymbols(base, new ClassDesc[0]));
    }

    @Test
    void dedup() {
        {
            List<ClassEntry> duplicates = ClassEntry.adding(base, base);
            List<ClassEntry> dedup = ClassEntry.deduplicate(duplicates);
            boolean result = listCompare(base, dedup);
            if (!result) {
                fail("Different: " + Arrays.toString(base.toArray())+ " : " + Arrays.toString(dedup.toArray()));
            }
            Assert.assertTrue(result);
        }
        {
            List<ClassEntry> duplicates = ClassEntry.addingSymbols(List.of(), additionCD);
            duplicates = ClassEntry.addingSymbols(duplicates, additionCD);
            List<ClassEntry> dedup = ClassEntry.deduplicate(duplicates);
            boolean result = listCompare(base, dedup);
            if (!result) {
                fail("Different: " + Arrays.toString(base.toArray())+ " : " + Arrays.toString(dedup.toArray()));
            }
            Assert.assertTrue(result);
        }

    }

}
