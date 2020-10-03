package com.quaso.validation.utils;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.Comparator;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ComparatorUtils {

    public <T> Comparator<T> compare(final Function<T, String> toValue) {
        return (o1, o2) -> {
            final String value1 = toValue.apply(o1);
            final String value2 = toValue.apply(o2);
            if (isEmpty(value1) && !isEmpty(value2)) {
                return 1;
            }
            if (isEmpty(value2)) {
                return -1;
            }
            return value1.compareTo(value2);
        };
    }
}
