package benchmark;

public class MethodInliningTest {
    public int smallMethod(int a, int b) {
        return a + b;
    }

    public int mediumMethod(int a, int b, int c) {
        int result = a * b;
        if (result > 1000) {
            result = result / 2;
        }
        return result + c;
    }

    public int testInlining(int iterations) {
        int sum = 0;
        for (int i = 0; i < iterations; i++) {
            sum += smallMethod(i, i+1);
            sum += mediumMethod(i, i+2, i+3);
        }
        return sum;
    }

    public static void main(String[] args) {
        MethodInliningTest test = new MethodInliningTest();
        int result = test.testInlining(1000);
        System.out.println("Result: " + result);
    }
}
