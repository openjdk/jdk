import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.MethodParameters_attribute;
import com.sun.tools.javac.code.Flags;
import toolbox.Assert;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

/*
 * @test
 * @summary check that implicit parameter flags are available by default
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.jdeps/com.sun.tools.classfile
 * @run main ImplicitParameters
 */
public class ImplicitParameters extends TestRunner {
    public ImplicitParameters() {
        super(System.err);
    }

    public static void main(String[] args) throws Exception {
        new ImplicitParameters().runTests();
    }

    @Override
    protected void runTests() throws Exception {
        Path base = Path.of(".").toAbsolutePath();
        compileCLasses(base);
        runTests(method -> new Object[]{ readClassFile(base.resolve("classes"), method) });
    }

    private void compileCLasses(Path base) throws IOException {
        String outer = """
                class Outer {
                    class Inner {
                        public Inner(Inner notMandated) {}
                    }
                                
                    Inner anonymousInner = this.new Inner(null) {};
                                
                    enum MyEnum {}
                                
                    record MyRecord(int a, Object b) {
                        MyRecord {}
                    }
                }
                """;
        Path src = base.resolve("src");
        ToolBox tb = new ToolBox();
        tb.writeJavaFiles(src, outer);
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    private ClassFile readClassFile(Path classes, Method method) {
        String className = method.getAnnotation(ClassName.class).value();
        try {
            return ClassFile.read(classes.resolve("Outer$" + className + ".class"));
        } catch (IOException | ConstantPoolException e) {
            throw new RuntimeException(e);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface ClassName {
        String value();
    }

    @Test
    @ClassName("Inner")
    public void testInnerClassConstructor(ClassFile classFile) {
        MethodParameters_attribute methodParameters = (MethodParameters_attribute) classFile.methods[0].attributes.get("MethodParameters");
        Assert.checkNonNull(methodParameters, "MethodParameters attribute must be present");
        MethodParameters_attribute.Entry[] table = methodParameters.method_parameter_table;
        Assert.check((table[0].flags & Flags.MANDATED) != 0, "mandated flag must be set for implicit parameter");
        Assert.check((table[1].flags & Flags.MANDATED) == 0, "mandated flag must not be set for explicit parameter");
    }

    @Test
    @ClassName("1")
    public void testAnonymousClassExtendingInnerClassConstructor(ClassFile classFile) {
        MethodParameters_attribute methodParameters = (MethodParameters_attribute) classFile.methods[0].attributes.get("MethodParameters");
        Assert.checkNonNull(methodParameters, "MethodParameters attribute must be present");
        MethodParameters_attribute.Entry[] table = methodParameters.method_parameter_table;
        Assert.check((table[0].flags & Flags.MANDATED) != 0, "mandated flag must be set for implicit parameter");
        Assert.check((table[1].flags & Flags.MANDATED) == 0, "mandated flag must not be set for explicit parameter");
    }

    @Test
    @ClassName("MyEnum")
    public void testValueOfInEnum(ClassFile classFile) throws ConstantPoolException {
        for (com.sun.tools.classfile.Method method : classFile.methods) {
            if (method.getName(classFile.constant_pool).equals("valueOf")) {
                MethodParameters_attribute methodParameters = (MethodParameters_attribute) method.attributes.get("MethodParameters");
                Assert.checkNonNull(methodParameters, "MethodParameters attribute must be present");
                MethodParameters_attribute.Entry[] table = methodParameters.method_parameter_table;
                Assert.check((table[0].flags & Flags.MANDATED) != 0, "mandated flag must be set for implicit parameter");
            }
        }
    }

    @Test
    @ClassName("MyRecord")
    public void testCompactConstructor(ClassFile classFile) {
        MethodParameters_attribute methodParameters = (MethodParameters_attribute) classFile.methods[0].attributes.get("MethodParameters");
        Assert.checkNonNull(methodParameters, "MethodParameters attribute must be present");
        MethodParameters_attribute.Entry[] table = methodParameters.method_parameter_table;
        for (int i = 0; i < methodParameters.method_parameter_table_length; i++) {
            Assert.check((table[i].flags & Flags.MANDATED) != 0, "mandated flag must be set for implicit parameter");
        }
    }
}
