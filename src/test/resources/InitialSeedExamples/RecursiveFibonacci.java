package benchmark;

public class RecursiveFibonacci {

    public long calculate(int n) {
        if (n <= 1) return n;
        return calculate(n-1) + calculate(n-2);
    }

    public static void main(String[] args) {
        RecursiveFibonacci fib = new RecursiveFibonacci();

        long result1 = fib.calculate(20);
        long result2 = fib.calculate(25);

        System.out.println("Fibonacci 20: " + result1);
        System.out.println("Fibonacci 25: " + result2);
    }
}
