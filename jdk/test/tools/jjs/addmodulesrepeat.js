/*
 * This is the test JavaScript program used in jjs-modulepathTest.sh
 */

print("--module-path passed: " + $OPTIONS._module_path);
print("--add-modules passed: " + $OPTIONS._add_modules);

if ($OPTIONS._add_modules != "java.base,com.greetings") {
    throw new Error("--add-modules values are not merged!");
}

var Hello = com.greetings.Hello;

var moduleName = Hello.class.module.name;
if (moduleName != "com.greetings") {
    throw new Error("Expected module name to be com.greetings");
}
