/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package runtime.valhalla.inlinetypes.field_layout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import jdk.test.lib.Asserts;

public class ValueClassGenerator {

    final int NUM_PREDEFINED_VALUES;
    final Random random;
    ArrayList<PrimitiveDesc> primitiveTypes = new ArrayList<>();
    ArrayList<ConcreteValueClassDesc> valueTypes = new ArrayList<>();
    ArrayList<AbstractValueClassDesc> abstractValueTypes = new ArrayList<>();
    ArrayList<TypeDesc> referenceTypes = new ArrayList<>();
    static String classTemplate;
    static String abstractValueClassTemplate;
    Path workDir = null;

    static {
        try {
            String path = System.getProperty("test.src");
            classTemplate = Files.readString(Path.of(path, "TestValueTemplate.java.template"));
            abstractValueClassTemplate = Files.readString(Path.of(path + File.separator + "TestAbstractValueTemplate.java.template"));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public ArrayList<String> getValueClassesNames() {
        ArrayList<String> res  = new ArrayList<>(valueTypes.size());
        for (ConcreteValueClassDesc cd : valueTypes) {
            res.add(cd.typeName);
        }
        return res;
    }

    public static abstract class TypeDesc {
        String typeName;
        // Some types simply don't have a value set large enough to allow generating of precomputed
        // values without duplicates, or it would be too complex to try to determine if their value set
        // is large enough. For those types we allow duplicates in the precomputed values, and we record
        // the information in the generated class for the substitutability test.
        boolean allowDuplicates;

        public TypeDesc(String name, boolean duplicates) {
            typeName = name;
            allowDuplicates = duplicates;
        }

        public abstract String getPrecomputedValueAsString(int i);
        public abstract boolean isPrimitiveType();
        public boolean allowDuplicates() { return allowDuplicates; }
    }

    public static class PrimitiveDesc extends TypeDesc {
        String[] precomputedValues;

        public PrimitiveDesc(String name, boolean allowDuplicates, String[] values) {
            super(name, allowDuplicates);
            precomputedValues = values;
        }

        public String getPrecomputedValueAsString(int i) {
            return precomputedValues[i];
        }
        public boolean isPrimitiveType() { return true; }
    }


    String[] getPregeneratedValues(Supplier<String> gen, boolean allowDuplicates, String[] startingVals) {
        String[] vals = new String[NUM_PREDEFINED_VALUES];
        System.arraycopy(startingVals, 0, vals, 0, startingVals.length);
        for (int i = startingVals.length; i < NUM_PREDEFINED_VALUES; i++) {
            boolean foundNewVal = false;
            String s = null;
            while (!foundNewVal) {
                s = gen.get();
                if (allowDuplicates) {
                    break;
                }
                boolean alreadyExist = false;
                for (int j = 0; j < i; j++) {
                    if (s.compareTo(vals[j]) == 0) {
                        alreadyExist = true;
                        break;
                    }
                }
                foundNewVal = !alreadyExist;
            }
            vals[i] = s;
        }
        return vals;
    }

    void generatePrimitiveTypes() {
      String[] boolVals = getPregeneratedValues(() -> Boolean.toString(random.nextBoolean()),
                              true, new String[] {"true", "false"});
      primitiveTypes.add(new PrimitiveDesc("boolean", true, boolVals));
      String[] byteVals = getPregeneratedValues(() -> "(byte)"+Byte.toString((byte)random.nextInt()),
                              true, new String[] {"(byte)0", "(byte)-1", "(byte)1", "Byte.MAX_VALUE", "Byte.MIN_VALUE"});
      primitiveTypes.add(new PrimitiveDesc("byte", true, byteVals));
      String[] charVals = getPregeneratedValues(() -> {
                                                    char c = 0;
                                                    do {
                                                        c = (char)random.nextInt(Character.MAX_VALUE+1);

                                                    } while (c == Character.MAX_VALUE || c == Character.MIN_VALUE);
                                                    return "(char)"+Integer.toString(c);
                                                  },
                              false, new String[] {"(char)1", "Character.MAX_VALUE", "Character.MIN_VALUE"});
      primitiveTypes.add(new PrimitiveDesc("char", false, charVals));
      String[] shortVals = getPregeneratedValues(() -> {
                                                    short v = 0;
                                                    do {
                                                        v = (short)random.nextInt();
                                                    } while (v == Short.MAX_VALUE || v == Short.MIN_VALUE ||
                                                             v == 0 || v == -1 || v == 1);
                                                    return "(short)"+Integer.toString(v);
                                                  },
                                false, new String[] {"(short)0", "(short)-1", "(short)1", "Short.MAX_VALUE", "Short.MIN_VALUE"});
      primitiveTypes.add(new PrimitiveDesc("short", false, shortVals));
      String[] intVals = getPregeneratedValues(() -> {
                                                    int v = 0;
                                                    do {
                                                        v = random.nextInt();
                                                    } while (v == Integer.MAX_VALUE || v == Integer.MIN_VALUE ||
                                                             v == 0 || v == -1 || v == 1);
                                                    return Integer.toString(v);
                                                  },
                              false, new String[] {"0", "-1", "1", "Integer.MAX_VALUE", "Integer.MIN_VALUE"});
      primitiveTypes.add(new PrimitiveDesc("int", false, intVals));
      String[] longVals = getPregeneratedValues(() -> {
                                                    long v = 0;
                                                    do {
                                                        v = random.nextLong();
                                                    } while (v == Long.MAX_VALUE || v == Long.MIN_VALUE ||
                                                             v == 0 || v == -1 || v == 1);
                                                    return Long.toString(v)+"L";
                                                  },
                              false, new String[] {"0L", "-1L", "1L", "Long.MAX_VALUE", "Long.MIN_VALUE"});
      primitiveTypes.add(new PrimitiveDesc("long", false, longVals));
      String[] floatVals = getPregeneratedValues(() -> {
                                                    float v = 0;
                                                    do {
                                                        v = random.nextFloat();
                                                    } while (v == Float.MAX_VALUE || v == Float.MIN_NORMAL || v == Float.MIN_VALUE ||
                                                             v == Float.NEGATIVE_INFINITY || v == Float.POSITIVE_INFINITY ||
                                                             v == 0.0f || v == -1.0f || v == 1.0f);
                                                    return Float.toString(v)+"f";
                                                  },
                              false, new String[] {"0.0f", "-1.0f", "1.0f", "Float.MAX_VALUE", "Float.MIN_VALUE",
                                                   "Float.MIN_NORMAL", "Float.NEGATIVE_INFINITY", "Float.POSITIVE_INFINITY"});
      primitiveTypes.add(new PrimitiveDesc("float", false, floatVals));
      String[] doubleVals = getPregeneratedValues(() -> {
                                                    double v = 0;
                                                    do {
                                                        v = random.nextDouble();
                                                    } while (v == Double.MAX_VALUE || v == Double.MIN_NORMAL || v == Double.MIN_VALUE ||
                                                             v == Double.NEGATIVE_INFINITY || v == Double.POSITIVE_INFINITY ||
                                                             v == 0.0 || v == -1.0 || v == 1.0);
                                                    return Double.toString(v);
                                                  },
                              false, new String[] {"0.0", "-1.0", "1.0", "Double.MAX_VALUE", "Double.MIN_VALUE",
                                                   "Double.MIN_NORMAL", "Double.NEGATIVE_INFINITY", "Double.POSITIVE_INFINITY"});
      primitiveTypes.add(new PrimitiveDesc("double", false, doubleVals));
    }

    void printPredefinedPrimitiveValues() {
        for (PrimitiveDesc desc : primitiveTypes) {
            System.out.print(desc.typeName + ": ");
            for (String s : desc.precomputedValues) {
                System.out.print(s + " ");
            }
            System.out.println("");
        }
    }

    // We need at least one identity class to test identity fields
    // We just use String for that, no need to store pregenerated values
    public class StringClassDesc extends TypeDesc {
        StringClassDesc() {
            super("String", false);
        }

        public String getPrecomputedValueAsString(int i) {
            return "\"" + Integer.toString(i) + "\"";
        }

        public boolean isPrimitiveType() { return false; }
    }

    void generateStringClass() {
        referenceTypes.add(new StringClassDesc());
    }

    public static class FieldDesc {
        String name;
        TypeDesc type;
        String initVal;

        FieldDesc(String name, TypeDesc type, String initval) {
            this.name = name;
            this.type = type;
            this.initVal = initval;
        }
    }

    public abstract class ValueClassDesc extends TypeDesc {
        AbstractValueClassDesc superClass;
        ArrayList<FieldDesc> fields;

        ValueClassDesc(String name, boolean allowDuplicates, AbstractValueClassDesc superClass, ArrayList<FieldDesc> fields) {
            super(name, allowDuplicates);
            this.superClass = superClass;
            this.fields = fields;
        }

        public boolean isPrimitiveType() { return false; }

        String generateFieldsDeclarations() {
            StringBuilder sb = new StringBuilder();
            int counter = 0;
            for (FieldDesc fd : fields) {
                sb.append("\t").append(fd.type.typeName).append(" ").append(fd.name).append(";\n");
            }
            return sb.toString();
        }

        String generateConstructorArgumentsWithTypes() {
            StringBuilder sb = new StringBuilder();
            if (superClass != null) {
                sb.append(superClass.generateConstructorArgumentsWithTypes());
                if (fields.size() != 0) sb.append(", ");
            }
            int offset = superClass != null ? superClass.getNumberOfConstructorArguments() : 0;
            for (int i = 0; i < fields.size(); i++) {
                FieldDesc fd = fields.get(i);
                sb.append(fd.type.typeName).append(" a").append(i + offset);
                if (i != fields.size() - 1) sb.append(", ");
            }
            return sb.toString();
        }

        String generateConstructorArguments() {
            StringBuilder sb = new StringBuilder();
            if (superClass != null) {
                sb.append(superClass.generateConstructorArguments());
                if (fields.size() != 0) sb.append(", ");
            }
            int offset = superClass != null ? superClass.getNumberOfConstructorArguments() : 0;
            for (int i = 0; i < fields.size(); i++) {
                FieldDesc fd = fields.get(i);
                sb.append("a").append(i + offset);
                if (i != fields.size() - 1) sb.append(", ");
            }
            return sb.toString();
        }

        String generateFieldInitialization() {
            StringBuilder sb = new StringBuilder();
            int offset = superClass != null ? superClass.getNumberOfConstructorArguments() : 0;
            for (int i = 0; i < fields.size(); i++) {
                FieldDesc fd = fields.get(i);
                sb.append("\t").append(fd.name).append(" = a").append(i + offset).append(";\n");
            }
            return sb.toString();
        }

        public String getRandomConstructorArgumentAsString() {
            StringBuilder sb = new StringBuilder();
            if (superClass != null) {
                sb.append(superClass.getRandomConstructorArgumentAsString());
                if (fields.size() != 0) sb.append(", ");
            }
            for (int i = 0; i < fields.size(); i++) {
                FieldDesc fd = fields.get(i);
                sb.append(fd.type.getPrecomputedValueAsString(random.nextInt(NUM_PREDEFINED_VALUES)));
                if (i != fields.size() - 1) sb.append(", ");
            }
            return sb.toString();
        }

        int getNumberOfConstructorArguments() {
            int res = fields.size();
            if (superClass != null) {
                res += superClass.getNumberOfConstructorArguments();
            }
            return res;
        }
    }

    public class AbstractValueClassDesc extends ValueClassDesc {
        AbstractValueClassDesc(String name, boolean allowDuplicates, AbstractValueClassDesc superClass, ArrayList<FieldDesc> fields) {
            super(name, allowDuplicates, superClass, fields);
        }

        public String getPrecomputedValueAsString(int i) {
            throw new RuntimeException("Abstract value classes don't have predefined values");
        }

        String generateSource() {
            String src = abstractValueClassTemplate.replace("<class_modifiers>", "public abstract value class");
            src = src.replace("<class_name>", typeName);
            if (superClass != null) {
                src = src.replace("<super_class>", "extends " + superClass.typeName);
            } else {
                src = src.replace("<super_class>", "");
            }
            src = src.replace("<super_class>", superClass != null ? superClass.typeName : "Object");
            src = src.replace("<fields_declarations>", generateFieldsDeclarations());
            src = src.replace("<constructor_args>", generateConstructorArgumentsWithTypes());
            src = src.replace("<fields_initialization>", generateFieldInitialization());
            src = src.replace("<super_constructor_args>", superClass != null ? superClass.generateConstructorArguments() : "");
            return src;
         }
    }

    public class ConcreteValueClassDesc extends ValueClassDesc {

        public ConcreteValueClassDesc(String name, boolean allowDuplicates, AbstractValueClassDesc superClass, ArrayList<FieldDesc> fields) {
            super(name, allowDuplicates, superClass, fields);
        }

        public String getPrecomputedValueAsString(int i) {
            return typeName+".getPredefinedValue("+i+")";
        }

        String generatePrecomputedValues() {
            String[] rndVal = new String[NUM_PREDEFINED_VALUES];
            int nfields = fields.size();
            StringBuilder sb = new StringBuilder();
            // While null is not strictly speaking part of the value set of the class, inserting
            // it in the list of precomputed values makes the code more streamlined when picking up
            // values later
            sb.append("\tpredefined[0] = null;\n");
            rndVal[0] = "null";
            for (int i = 1; i < NUM_PREDEFINED_VALUES; i++) {
                sb.append("\tpredefined[").append(i).append("] = new ").append(typeName).append("(");
                boolean validNewVal = false;
                while (!validNewVal) {
                    StringBuffer sb2 = new StringBuffer();
                    sb2.append(getRandomConstructorArgumentAsString());
                    rndVal[i] = sb2.toString();
                    if (allowDuplicates()) {
                        validNewVal = true;
                    } else {
                        boolean alreadyUsed = false;
                        for (int k = 0; k < i; k++) {
                            if (rndVal[i].compareTo(rndVal[k]) == 0) alreadyUsed = true;
                        }
                        validNewVal = !alreadyUsed;
                    }
                }
                sb.append(rndVal[i]);
                sb.append(");\n");
            }
            return sb.toString();
        }

        String generateSource() {
            String src = classTemplate.replace("<class_modifiers>", "public value class");
            src = src.replace("<class_name>", typeName);
            if (superClass != null) {
                src = src.replace("<super_class>", "extends " + superClass.typeName);
            } else {
                src = src.replace("<super_class>", "");
            }
            src = src.replace("<allow_duplicates>", allowDuplicates() ? "true" : "false");
            src = src.replace("<fields_declarations>", generateFieldsDeclarations());
            src = src.replace("<NUM_PREDEFINED>", Integer.toString(NUM_PREDEFINED_VALUES));
            src = src.replace("<predefined_values_generation>", generatePrecomputedValues());
            src = src.replace("<constructor_args>", generateConstructorArgumentsWithTypes());
            src = src.replace("<fields_initialization>", generateFieldInitialization());
            src = src.replace("<super_constructor_args>", superClass != null ? superClass.generateConstructorArguments() : "");
            return src;
         }
    }

    boolean selectFields(ArrayList<FieldDesc> fields, int nfields) {
      int nPrimitive = 0;
      int nValues = 0;
      if (nfields != 0) {
          nPrimitive = random.nextInt(nfields+1);
          nValues = nfields - nPrimitive;
      }
      boolean allowDuplicates = nfields == 0 ? true : false;
      for (int i = 0; i < nPrimitive; i++) {
          String fieldName = "field"+i;
          TypeDesc fieldType = primitiveTypes.get(random.nextInt(primitiveTypes.size()));
          if (fieldType.allowDuplicates) allowDuplicates = true;
          String initval = fieldType.getPrecomputedValueAsString(random.nextInt(NUM_PREDEFINED_VALUES));
          FieldDesc fd = new FieldDesc(fieldName, fieldType, initval);
          fields.add(fd);
      }
      for (int i = nPrimitive; i < nfields; i++) {
          String fieldName = "field"+i;
          TypeDesc fieldType = referenceTypes.get(random.nextInt(referenceTypes.size()));
          if (fieldType.allowDuplicates) allowDuplicates = true;
          String initval = fieldType.getPrecomputedValueAsString(random.nextInt(NUM_PREDEFINED_VALUES));
          FieldDesc fd = new FieldDesc(fieldName, fieldType, initval);
          fields.add(fd);
      }
      return allowDuplicates;
    }

    ValueClassDesc generateValueClass(int n, int total) {
        boolean isAbstract;
        boolean hasSuper;
        int nfields;
        if (n == 0) { // always create the empty value as Value0
            nfields = 0;
            isAbstract = false;
            hasSuper = false;
        } else {
            // generate at least one abstract value if there are none
            // by halfway through the generation of all classes
            boolean middle = n == total / 2;
            isAbstract = (middle && abstractValueTypes.isEmpty()) || random.nextInt(16) == 1;
            hasSuper = !abstractValueTypes.isEmpty() && (random.nextInt(16) == 1);
            nfields = randomFieldNumber();
        }
        AbstractValueClassDesc superClass = null;
        if (hasSuper) {
            superClass = abstractValueTypes.get(random.nextInt(abstractValueTypes.size()));
        }
        ArrayList<FieldDesc> fields = new ArrayList<>(nfields);
        boolean allowDuplicates = selectFields(fields, nfields);
        String name = (isAbstract ? "AbstractValueClass" : "ValueClass") + n;
        ValueClassDesc cd;
        if (isAbstract) {
            cd = new AbstractValueClassDesc(name, allowDuplicates, superClass, fields);
        } else {
            cd = new ConcreteValueClassDesc(name, allowDuplicates, superClass, fields);
        }
        return cd;
    }

    ConcreteValueClassDesc generateEmptyValueClass() {
        return (ConcreteValueClassDesc)generateValueClass(0, 1);
    }

    void generateValueClasses(int n) {
        ConcreteValueClassDesc empty = generateEmptyValueClass();
        valueTypes.add(empty);
        referenceTypes.add(empty);
        for (int i = 1; i < n; i++) {
            ValueClassDesc c = generateValueClass(i, n);
            if (c instanceof AbstractValueClassDesc) {
                abstractValueTypes.add((AbstractValueClassDesc)c);
            } else {
                valueTypes.add((ConcreteValueClassDesc)c);
                referenceTypes.add(c);
            }
        }
    }

    int randomFieldNumber() {
        return (int)Math.ceil(Math.exp(random.nextInt(16)/7.0));
    }

    void printStatistics(ArrayList<? extends ValueClassDesc> list) {
        int nClasses = list.size();
        System.out.println("Number of value classes generated: " + nClasses);
        final int FIELDS_BUCKETS = 16;
        int maxFields = 0;
        int[] numberOfFields = new int[FIELDS_BUCKETS];
        int[] primOnly = new int[FIELDS_BUCKETS];
        int[] valOnly = new int[FIELDS_BUCKETS];
        int[] mixed = new int[FIELDS_BUCKETS];
        int nWithSuperClass = 0;
        for (ValueClassDesc cd : list) {
            int n = cd.fields.size();
            nWithSuperClass += cd.superClass != null ? 1 : 0;
            if (n > FIELDS_BUCKETS - 1) n = FIELDS_BUCKETS - 1;
            numberOfFields[n]++;
            if (n > maxFields) maxFields = n;
            int nPrim = 0;
            int nVal = 0;
            for (FieldDesc fd : cd.fields) {
                if (fd.type.isPrimitiveType()) {
                    nPrim++;
                } else {
                    nVal++;
                }
            }
            if (nPrim == 0) primOnly[n]++;
            else if (nVal == 0) valOnly[n]++;
            else mixed[n]++;
        }
        System.out.println("Number of classes with super class: " + nWithSuperClass + " (" + (nWithSuperClass*100/nClasses) + "%)");
        System.out.println("Number of fields distribution:");
        for (int i = 0; i <= maxFields; i++) {
            System.out.print(i + " fields: " + numberOfFields[i] + " (" + (numberOfFields[i]*100/nClasses) + "%)");
            if (numberOfFields[i] != 0) {
                System.out.print(" : primOnly: " + primOnly[i] + " (" + primOnly[i]*100/numberOfFields[i] + "%)");
                System.out.print(" valOnly: " + valOnly[i] + " (" + valOnly[i]*100/numberOfFields[i] + "%)");
                System.out.print(" mixed: " + mixed[i] + " (" + mixed[i]*100/numberOfFields[i] + "%)");
            }
            System.out.println("");
        }
    }

    void writeValueClasses() {
        for (ConcreteValueClassDesc cd : valueTypes) {
            File file = new File(workDir.toFile(), cd.typeName + ".java" );
            try (PrintWriter out = new PrintWriter(file)) {
                out.println(cd.generateSource());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        for (AbstractValueClassDesc cd : abstractValueTypes) {
            File file = new File(workDir.toFile(), cd.typeName + ".java" );
            try (PrintWriter out = new PrintWriter(file)) {
                out.println(cd.generateSource());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    void compileValueClasses() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        File[] files = new File[valueTypes.size() + abstractValueTypes.size()];
        for (int i = 0; i < valueTypes.size(); i++) {
            files[i] = new File(workDir.toFile(), valueTypes.get(i).typeName + ".java");
        }
        for (int i = 0; i < abstractValueTypes.size(); i++) {
            files[valueTypes.size() + i] = new File(workDir.toFile(), abstractValueTypes.get(i).typeName + ".java");
        }
        ArrayList<String> optionList = new ArrayList<>();
        optionList.addAll(Arrays.asList("-source", Integer.toString(Runtime.version().feature())));
        optionList.addAll(Arrays.asList("--enable-preview"));
        optionList.addAll(Arrays.asList("-d", workDir.toString()));
        StandardJavaFileManager sjfm = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> fileObjects = sjfm.getJavaFileObjects(files);
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, optionList, null, fileObjects);
        if (!task.call()) {
            throw new AssertionError("test failed due to a compilation error");
        }
        try {
            sjfm.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void generateAll(int numClasses, Path workDir) {
        this.workDir = workDir;
        generatePrimitiveTypes();
        generateStringClass();
        generateValueClasses(numClasses);
        System.out.println("Generated " + valueTypes.size() + " concrete value classes and " + abstractValueTypes.size() + " abstract value classes");
        System.out.println("Abstract value classes statistics:");
        printStatistics(abstractValueTypes);
        System.out.println("Concrete value classes statistics:");
        printStatistics(valueTypes);
        writeValueClasses();
        compileValueClasses();
    }

    public ValueClassGenerator(long seed, int nPredefined) {
        random = new Random(seed);
        NUM_PREDEFINED_VALUES = nPredefined;
    }
}
