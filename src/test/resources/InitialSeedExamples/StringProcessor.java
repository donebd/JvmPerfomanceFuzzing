package benchmark;

public class StringProcessor {

    public String processString(String input) {
        String result = input.toUpperCase();
        result = result.replaceAll("[AEIOU]", "#");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            if (i % 2 == 0) {
                sb.append(result.charAt(i));
            }
        }

        return sb.reverse().toString();
    }

    public static void main(String[] args) {
        StringProcessor sp = new StringProcessor();

        String test1 = sp.processString("HelloWorld");
        String test2 = sp.processString("JavaPerformance");

        System.out.println("Test 1: " + test1);
        System.out.println("Test 2: " + test2);
    }
}
