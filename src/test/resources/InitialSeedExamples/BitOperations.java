package benchmark;

public class BitOperations {
    public int countBits(int n) {
        int count = 0;
        while (n != 0) {
            count += n & 1;
            n >>>= 1;
        }
        return count;
    }

    public int reverseBits(int n) {
        int result = 0;
        for (int i = 0; i < 32; i++) {
            result = (result << 1) | (n & 1);
            n >>>= 1;
        }
        return result;
    }

    public int[] bitOperationsArray(int[] array) {
        int[] result = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            // Комбинация битовых операций
            result[i] = (array[i] << 2) ^ (array[i] >>> 3) & (~array[i] | 0xFF);
        }
        return result;
    }

    public static void main(String[] args) {
        BitOperations processor = new BitOperations();
        int bits = processor.countBits(255);
        int reversed = processor.reverseBits(123456);
        int[] result = processor.bitOperationsArray(new int[]{1, 2, 3, 4, 5});

        System.out.println("Bits in 255: " + bits);
        System.out.println("Reversed 123456: " + reversed);
        System.out.println("First element result: " + result[0]);
    }
}
