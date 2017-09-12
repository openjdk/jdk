/*
 * This is the test JavaScript program used in jjs-fileTest.sh
 */

// good old 'hello world'!
print('hello');

// basic number manipulation
var v = 2 + 5;
v *= 5;
v.doubleValue();
v = v + " is the value";
if (v != 0) {
    print('yes v != 0');
}

// basic java access
java.lang.System.out.println('hello world from script');

// basic stream manipulation
var al = new java.util.ArrayList();
al.add("hello");
al.add("world");
// script functions for lambas
al.stream().map(function(s) s.toUpperCase()).forEach(print);

// interface implementation
new java.lang.Runnable() {
    run: function() {
        print('I am runnable');
    }
}.run();

// java class extension
var MyList = Java.extend(java.util.ArrayList);
var m = new MyList() {
    size: function() {
        print("size called");
        // call super.size()
        return Java.super(m).size();
    }
};

print("is m an ArrayList? " + (m instanceof java.util.ArrayList));
m.add("hello");
m.add("world");
print(m.size());
