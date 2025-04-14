package benchmark;

public class PrimeChecker {

    public boolean isPrime(int number) {
        if (number <= 1) return false;
        if (number <= 3) return true;

        if (number % 2 == 0 || number % 3 == 0) return false;

        for (int i = 5; i * i <= number; i += 6) {
            if (number % i == 0 || number % (i + 2) == 0) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        PrimeChecker pc = new PrimeChecker();

        boolean test1 = pc.isPrime(104729);
        boolean test2 = pc.isPrime(104725);

        System.out.println("Test 1: " + test1);
        System.out.println("Test 2: " + test2);
    }
}
