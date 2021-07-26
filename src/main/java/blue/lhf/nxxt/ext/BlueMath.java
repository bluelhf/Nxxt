package blue.lhf.nxxt.ext;

import com.google.inject.internal.Nullable;

public class BlueMath {

    @SuppressWarnings("unused")
    public static class Random {
        private final OpenSimplexNoise openSimplex = new OpenSimplexNoise();
        private final java.util.Random random = new java.util.Random();
        public Random() {
            random.setSeed((long) (System.currentTimeMillis() / random.nextDouble()));
        }

        /**
         * @return Random double between 0 and 1 (inclusive)
         * */
        public double randomDouble() {
            return randomDouble(new Boundary());
        }

        /**
         * @param bounds The inclusive bound(s) of the random double
         * @return Random double between the lower bound and the upper bound (inclusive)
         * */
        public double randomDouble(Boundary bounds) {
            double zeroOne = random.nextDouble() / Math.nextDown(1D);
            return zeroOne * (bounds.getUpper() - bounds.getLower()) + bounds.getLower();
        }

        public double randomDouble(double lower, double upper) {
            Boundary boundary = new Boundary(lower, upper);
            return randomDouble(boundary);
        }

        /**
         * Returns a one-dimensional OpenSimplex noise value between the specified boundary or 0 and 1
         * @param x The x-coordinate of the OpenSimplex noise value
         * @param boundary The inclusive boundary of the output value, 0 -> 1 if null.
         * @return The open-simplex noise value at (x) mapped to the input boundary.
         * */
        public double simplex(double x, @Nullable Boundary boundary) {
            double low = boundary != null ? boundary.getLower() : 0;
            double high = boundary != null ? boundary.getUpper() : 1;
            return openSimplex.eval(x, 0) * (high - low) + low;
        }
        /**
         * Returns a two-dimensional OpenSimplex noise value between the specified boundary or 0 and 1
         * @param x The x-coordinate of the OpenSimplex noise value
         * @param y The y-coordinate of the OpenSimplex noise value
         * @param boundary The inclusive boundary of the output value, 0 -> 1 if null.
         * @return The open-simplex noise value at (x) mapped to the input boundary.
         * */
        public double simplex(double x, double y, @Nullable Boundary boundary) {
            double low = boundary != null ? boundary.getLower() : 0;
            double high = boundary != null ? boundary.getUpper() : 1;
            return openSimplex.eval(x, y) * (high - low) + low;
        }
        /**
         * Returns a three-dimensional OpenSimplex noise value between the specified boundary or 0 and 1
         * @param x The x-coordinate of the OpenSimplex noise value
         * @param y The y-coordinate of the OpenSimplex noise value
         * @param z The z-coordinate of the OpenSimplex noise value
         * @param boundary The inclusive boundary of the output value, 0 -> 1 if null.
         * @return The open-simplex noise value at (x) mapped to the input boundary.
         * */
        public double simplex(double x, double y, double z, @Nullable Boundary boundary) {
            double low = boundary != null ? boundary.getLower() : 0;
            double high = boundary != null ? boundary.getUpper() : 1;
            return openSimplex.eval(x, y, z) * (high - low) + low;
        }
        /**
         * Returns a four-dimensional OpenSimplex noise value between the specified boundary or 0 and 1
         * @param x The x-coordinate of the OpenSimplex noise value
         * @param y The y-coordinate of the OpenSimplex noise value
         * @param z The z-coordinate of the OpenSimplex noise value
         * @param w The fourth-dimension coordinate of the OpenSimplex noise value
         * @param boundary The inclusive boundary of the output value, 0 -> 1 if null.
         * @return The open-simplex noise value at (x) mapped to the input boundary.
         * */
        public double simplex(double x, double y, double z, double w, @Nullable Boundary boundary) {
            double low = boundary != null ? boundary.getLower() : 0;
            double high = boundary != null ? boundary.getUpper() : 1;
            return openSimplex.eval(x, y, z, w) * (high - low) + low;
        }

    }

    /**
     * Represents a one-dimensional boundary
     * */
    @SuppressWarnings("unused")
    public static class Boundary {
        private double low = 0;
        private double high = 1;
        /**
         * @param lower The lower bound of the Boundary
         * @param upper The upper bound of the Boundary
         * */
        public Boundary(double lower, double upper) {
            this.low = lower;
            this.high = upper;
        }
        /**
         * Represents a Boundary between 0 and 1
         * */
        public Boundary() { }
        /**
         * Represents a Boundary with a lower bound of 0.
         * @param upper The upper bound of the Boundary
         * */
        public Boundary(double upper) {
            this.high = upper;
        }

        /**
         * @return The lower bound of the Boundary
         * */
        public double getLower() {
            return low;
        }

        /**
         * Sets the lower bound of the Boundary
         * @return Itself with the new lower bound
         * */
        public Boundary setLower(double low) {
            this.low = low;
            return this;
        }

        /**
         * @return The upper bound of the Boundary
         * */
        public double getUpper() {
            return high;
        }

        /**
         * Sets the upper bound of the Boundary
         * @return Itself with the new lower bound
         * */
        public Boundary setUpper(double upper) {
            this.high = upper;
            return this;
        }

        /**
         * Constrains a number between the lower and upper bounds of this Boundary (inclusive)
         * @param val The value to constrain
         * @return The constrained value
         * */
        public double constrain(double val) {
            return Math.max(low, Math.min(high, val));
        }
        /**
         * Maps a number from a given range to the lower and upper bounds of the Boundary<br/>
         * If val is 0.5, lower is 0, and upper is 1, and the lower and upper bounds of this Boundary are 0 and 2, the output is 1.
         * @param val The value to map
         * @param lower The value's old lower bound
         * @param upper The value's old upper bound
         * @return The mapped value
         * */
        public double map(double val, double lower, double upper) {
            return (val - lower) * (high - low) / (upper - lower) + low;
        }
    }
}
