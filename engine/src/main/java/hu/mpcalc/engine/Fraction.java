package hu.mpcalc.engine;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Exact rational number (reduced, denominator > 0). Used for all MP arithmetic; nothing is ever rounded. */
public final class Fraction implements Comparable<Fraction> {
    public static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);
    private final BigInteger num;
    private final BigInteger den;

    private Fraction(BigInteger n, BigInteger d) {
        if (d.signum() == 0) throw new ArithmeticException("zero denominator");
        if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
        BigInteger g = n.gcd(d);
        if (g.signum() != 0 && !g.equals(BigInteger.ONE)) { n = n.divide(g); d = d.divide(g); }
        this.num = n; this.den = d;
    }
    public static Fraction of(long n) { return new Fraction(BigInteger.valueOf(n), BigInteger.ONE); }
    public static Fraction of(BigDecimal b) {
        BigInteger unscaled = b.unscaledValue();
        int scale = b.scale();
        return scale >= 0 ? new Fraction(unscaled, BigInteger.TEN.pow(scale)) : new Fraction(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
    }
    public static Fraction of(String decimal) { return of(new BigDecimal(decimal)); }

    public Fraction add(Fraction o) { return new Fraction(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den)); }
    public Fraction subtract(Fraction o) { return add(o.negate()); }
    public Fraction negate() { return new Fraction(num.negate(), den); }
    public Fraction multiply(Fraction o) { return new Fraction(num.multiply(o.num), den.multiply(o.den)); }
    public Fraction divide(Fraction o) { return new Fraction(num.multiply(o.den), den.multiply(o.num)); }
    public int signum() { return num.signum(); }
    public boolean isWhole() { return den.equals(BigInteger.ONE); }
    public BigInteger numerator() { return num; }
    public BigInteger denominator() { return den; }

    /** Exact decimal value, or null if the decimal expansion does not terminate. */
    public BigDecimal toExactDecimal() {
        BigInteger d = den;
        while (d.mod(BigInteger.TWO).signum() == 0) d = d.divide(BigInteger.TWO);
        BigInteger five = BigInteger.valueOf(5);
        while (d.mod(five).signum() == 0) d = d.divide(five);
        if (!d.equals(BigInteger.ONE)) return null;
        return new BigDecimal(num).divide(new BigDecimal(den));
    }
    @Override public int compareTo(Fraction o) { return num.multiply(o.den).compareTo(o.num.multiply(den)); }
    @Override public boolean equals(Object o) { return o instanceof Fraction f && num.equals(f.num) && den.equals(f.den); }
    @Override public int hashCode() { return num.hashCode() * 31 + den.hashCode(); }
    @Override public String toString() {
        if (isWhole()) return num.toString();
        BigDecimal d = toExactDecimal();
        return d != null ? d.stripTrailingZeros().toPlainString() : num + "/" + den;
    }
}
