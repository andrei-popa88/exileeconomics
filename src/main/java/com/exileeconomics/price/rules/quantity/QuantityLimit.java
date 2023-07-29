package com.exileeconomics.price.rules.quantity;

import java.util.Objects;

public class QuantityLimit extends AbstractQuantityLimit {
    public QuantityLimit(int quantityLowerLimit, int quantityUpperLimit) {
        super(quantityLowerLimit, quantityUpperLimit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuantityLimit that)) return false;
        return getQuantityLowerLimit() == that.getQuantityLowerLimit() && getQuantityUpperLimit() == that.getQuantityUpperLimit();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getQuantityLowerLimit(), getQuantityUpperLimit());
    }
}
