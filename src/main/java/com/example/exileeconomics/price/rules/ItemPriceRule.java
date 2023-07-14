package com.example.exileeconomics.price.rules;

import com.example.exileeconomics.definitions.ItemDefinitionEnum;
import com.example.exileeconomics.price.rules.quantity.QuantityLimitInterface;

import java.util.Objects;
import java.util.Set;

public class ItemPriceRule implements ItemPriceRuleInterface {
    private final ItemDefinitionEnum itemDefinitionEnum;
    private final int limit;
    private final int offset;
    private final Set<? extends QuantityLimitInterface> quantityLimitRules;

    public ItemPriceRule(ItemDefinitionEnum itemDefinitionEnum, int limit, int offset, Set<? extends QuantityLimitInterface> quantityLimitRules) {
        this.itemDefinitionEnum = itemDefinitionEnum;
        this.limit = limit;
        this.offset = offset;
        this.quantityLimitRules = quantityLimitRules;
    }

    public ItemDefinitionEnum getItemDefinitionEnum() {
        return itemDefinitionEnum;
    }

    @Override
    public int getLimit() {
        return limit;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public Set<? extends QuantityLimitInterface> getQuantityLimits() {
        return quantityLimitRules;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemPriceRule that)) return false;
        return getLimit() == that.getLimit() && getOffset() == that.getOffset() && getItemDefinitionEnum() == that.getItemDefinitionEnum() && Objects.equals(quantityLimitRules, that.quantityLimitRules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getItemDefinitionEnum(), getLimit(), getOffset(), quantityLimitRules);
    }
}
