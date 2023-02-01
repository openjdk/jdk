class Str {
    public String strBuilderRepro() {
        return new StringBuilder(10).toString();
    }

    public static void main(String[] args)  {
        Str s = new Str();
        String foo = "";
        for (int i = 0; i < 20000; ++i) {
            foo = s.strBuilderRepro();
        }
        System.out.println(foo);
    }
}
