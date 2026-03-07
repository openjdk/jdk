/*
 * @test /nodynamiccopyright/
 * @bug 8019340
 * @summary Verify compile errors derived from bootstrap method annotations
 *
 * @compile/fail/ref=BadBootstrapAnnotations.out -XDrawDiagnostics BadBootstrapAnnotations.java
 */

import java.lang.constant.ConstantDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.CallSiteBootstrap;
import java.lang.invoke.ConstantBootstrap;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.TypeDescriptor;

public class BadBootstrapAnnotations {
    @ConstantBootstrap
    @CallSiteBootstrap
    int a() { return 42; }

    @ConstantBootstrap
    @CallSiteBootstrap
    public BadBootstrapAnnotations() {}

    @CallSiteBootstrap @ConstantBootstrap CallSite nonStaticBoot(MethodHandles.Lookup caller, String name, MethodType type) { return null; }
    @CallSiteBootstrap static String badIndyBoot(MethodHandles.Lookup caller, String name, MethodType type) { return null; }
    @CallSiteBootstrap static CallSite badIndyBoot(String caller, String name, MethodType type) { return null; }
    @CallSiteBootstrap static CallSite badIndyBoot(MethodHandles.Lookup caller, int name, MethodType type) { return null; }
    @CallSiteBootstrap static CallSite badIndyBoot(MethodHandles.Lookup caller, String name, ConstantDesc type) { return null; }

    class MySite extends ConstantCallSite {
        @CallSiteBootstrap
        MySite(MethodHandles.Lookup caller, String name, String type) {
            super(MethodHandles.empty((MethodType) (Object) type));
        }

        @ConstantBootstrap
        @CallSiteBootstrap
        MySite(MethodHandles.Lookup caller, int name, TypeDescriptor type) {
            super(MethodHandles.empty((MethodType) type));
        }
    }

    @ConstantBootstrap static String badCondyBoot(Object caller, String name, MethodType type) { return null; }
}
