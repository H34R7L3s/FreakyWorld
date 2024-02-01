package h34r7l3s.freakyworld;

public class AttributeData {
    private final double value;
    private final int count;

    public AttributeData(double value, int count) {
        this.value = value;
        this.count = count;
    }

    // Getter für value und count
    public double getValue() {
        return value;
    }

    public int getCount() {
        return count;
    }
}
