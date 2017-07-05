#!/bin/sh

#
# Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

#  @test
#  @bug 5002251 6407335 6412391
#  @summary Redefine a class that has an annotation and verify that the
#    new annotation is returned.
#
#  @run shell RedefineAnnotation.sh

compileOptions=-g

# Uncomment this to see the JDI trace
#jdbOptions=-dbgtrace

createJavaFile()
{
    cat <<EOF > $1.java.1

import java.lang.annotation.*;
import java.lang.reflect.*;

/**
 */
@Foo(Constants.class_annotation)  // @1 commentout
// @1 uncomment @Foo(Constants.new_class_annotation)
public class $1 {
@Foo(Constants.field_annotation)  // @1 commentout
// @1 uncomment @Foo(Constants.new_field_annotation)
    public int dummy_field;

    public static void main(String[] args) {
        MySubClass sub = new MySubClass();
        MySubSubClass subsub = new MySubSubClass();
        new $1().hi(false);
        new $1().hi(true);  // @1 breakpoint
        sub.hi(true);
        subsub.hi(true);
    }

@Foo(Constants.method_annotation)  // @1 commentout
// @1 uncomment @Foo(Constants.new_method_annotation)
    public void hi(
@Foo(Constants.method_parameter_annotation)  // @1 commentout
// @1 uncomment @Foo(Constants.new_method_parameter_annotation)
                   boolean isNewVersion) {

        if (isNewVersion) {
            System.out.println("Checking for NEW versions of annotations in "
                + getClass());
        }

        // class annotations check:
        Foo foo = getClass().getAnnotation(Foo.class);
        if (foo == null) {
          throw new Error("FAIL: cannot get class_annotation from "
                        + getClass());
        }

        String class_annotation = foo.value();
        System.out.println("class annotation is: " + class_annotation);
        if (isNewVersion) {
            if (class_annotation.equals(Constants.new_class_annotation)) {
                System.out.println("PASS: class_annotation was changed.");
            } else {
                System.out.println("FAIL: class_annotation was NOT changed.");
            }
        }
    
        // field annotations check:
        try {
            Field my_field = getClass().getField("dummy_field");
            foo = my_field.getAnnotation(Foo.class);
            if (foo == null) {
              throw new Error("FAIL: cannot get field_annotation from "
                            + getClass() + ".dummy_field");
            }
            String field_annotation = foo.value();
            System.out.println("field annotation is: " + field_annotation);
            if (isNewVersion) {
                if (field_annotation.equals(Constants.new_field_annotation)) {
                    System.out.println("PASS: field_annotation was changed.");
                } else {
                    System.out.println(
                        "FAIL: field_annotation was NOT changed.");
                }
        }
        } catch (NoSuchFieldException nsfe) {
            throw new Error("FAIL: cannot find field 'dummy_field' in "
                          + getClass());
        }
    
        // method annotations check:
        try {
            Class params[] = new Class[1];
            params[0] = Boolean.TYPE;
            Method my_method = getClass().getMethod("hi", params);
            foo = my_method.getAnnotation(Foo.class);
            if (foo == null) {
              throw new Error("FAIL: cannot get field_annotation from "
                            + getClass() + ".hi()");
            }
            String method_annotation = foo.value();
            System.out.println("method annotation is: " + method_annotation);
            if (isNewVersion) {
                if (method_annotation.equals(Constants.new_method_annotation)) {
                    System.out.println("PASS: method_annotation was changed.");
                } else {
                    System.out.println(
                        "FAIL: method_annotation was NOT changed.");
                }
            }
        } catch (NoSuchMethodException nsme) {
            throw new Error("FAIL: cannot find method 'hi' in " + getClass());
        }
    
        // method parameter annotations check:
        try {
            Class params[] = new Class[1];
            params[0] = Boolean.TYPE;
            Method my_method = getClass().getMethod("hi", params);
            Annotation my_annotations[][] = my_method.getParameterAnnotations();
            if (my_annotations.length != 1) {
                throw new Error("FAIL: unexpected my_annotations.length ("
                              + my_annotations.length);
            }
            Annotation my_annotation[] = my_annotations[0];
            if (my_annotation.length != 1) {
                throw new Error("FAIL: unexpected my_annotation.length ("
                              + my_annotation.length);
            }
            foo = (Foo)my_annotation[0];
            String method_parameter_annotation = foo.value();
            System.out.println("method parameter annotation is: "
                + method_parameter_annotation);
            if (isNewVersion) {
                if (method_parameter_annotation.equals(
                    Constants.new_method_parameter_annotation)) {
                    System.out.println(
                        "PASS: method_parameter_annotation was changed.");
                } else {
                    System.out.println(
                        "FAIL: method_parameter_annotation was NOT changed.");
                }
            }
        } catch (NoSuchMethodException nsme) {
            throw new Error("FAIL: cannot find method 'hi' in " + getClass());
        }
    }
}

// this subclass exists just to make the RedefineClasses() code do a
// subclass walk to update the counter
class MySubClass extends $1 {
  int my_int_field_makes_me_different;
}

// this subclass exists just to make the RedefineClasses() code do a
// sub-subclass walk to update the counter
class MySubSubClass extends MySubClass {
  float my_float_field_makes_me_different;
}

class Constants {
    static final String class_annotation     = "Patrick's class comment";
    static final String new_class_annotation = "*NEW* Patrick's class comment";

    static final String field_annotation     = "dummy_field comment";
    static final String new_field_annotation = "*NEW* dummy_field comment";

    static final String method_annotation     = "method hi() comment";
    static final String new_method_annotation = "*NEW* method hi() comment";

    static final String method_parameter_annotation     =
        "param isNewVersion comment";
    static final String new_method_parameter_annotation =
        "*NEW* param isNewVersion comment";
}


/**
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@interface Foo {
    String value();
}

EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1
    redefineClass @1
    cmd cont
}


mysetup()
{
    if [ -z "$TESTSRC" ] ; then
        TESTSRC=.
    fi

    for ii in . $TESTSRC $TESTSRC/.. ; do
        if [ -r "$ii/ShellScaffold.sh" ] ; then
            . $ii/ShellScaffold.sh 
            break
        fi
    done
}

# You could replace this next line with the contents
# of ShellScaffold.sh and this script will run just the same.
mysetup

runit

debuggeeFailIfPresent 'FAIL:'
pass
