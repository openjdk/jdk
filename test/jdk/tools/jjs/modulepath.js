/*
 * This is the test JavaScript program used in jjs-modulepathTest.sh
 */

print("--module-path passed: " + $OPTIONS._module_path);
print("--add-modules passed: " + $OPTIONS._add_modules);

var Hello = com.greetings.Hello;
var moduleName = Hello.class.module.name;
if (moduleName != "com.greetings") {
    throw new Error("Expected module name to be com.greetings");
} else {
    print("Module name is " + moduleName);
}

var msg = Hello.greet();
if (msg != "Hello World!") {
    throw new Error("Expected 'Hello World!'");
} else {
    print(msg);
}
