class Foo {
    public static void main(String args[]) {
       System.out.println("HelloWorld");
       if (args.length > 0) {
         new Bar();
       }
    }
    Foo doit() {
        return new Bar();
    }
    static class Bar extends Foo {
          class Bam extends Bar {}

          Bar doit() {
              return new Bam();
          }
    }
}