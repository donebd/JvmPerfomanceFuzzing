package benchmark;

public class MatrixMultiplier {

    public double[][] multiply(double[][] a, double[][] b) {
        int n = a.length;
        double[][] result = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0.0;
                for (int k = 0; k < n; k++) {
                    sum += a[i][k] * b[k][j];
                }
                result[i][j] = sum;
            }
        }
        return result;
    }

    public static void main(String[] args) {
        MatrixMultiplier mm = new MatrixMultiplier();

        double[][] matrixA = {{1.5, 2.0}, {3.0, 4.5}};
        double[][] matrixB = {{5.0, 6.0}, {7.5, 8.0}};

        double[][] result = mm.multiply(matrixA, matrixB);

        System.out.println("Result matrix:");
        for (double[] row : result) {
            for (double val : row) {
                System.out.printf("%.2f ", val);
            }
            System.out.println();
        }
    }
}
