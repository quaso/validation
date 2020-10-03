package com.quaso.validation.utils;

import com.quaso.validation.exception.UnknownFieldException;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.LinkedList;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ReflectionUtils {

    private static final String PREFIX_PARENT = "parent.";
    private static final String PREFIX_ANY = "any.";
    private static final String PREFIX_TOP = "top.";

    public Object getFieldValue(final Object object, final Field field) {
        final boolean canAccess = field.canAccess(object);
        if (!canAccess) {
            field.setAccessible(true);
        }
        try {
            return field.get(object);
        } catch (final IllegalAccessException e) {
            return null;
        } finally {
            if (!canAccess) {
                field.setAccessible(canAccess);
            }
        }
    }

    public Object getFieldValue(final Object object, final String fieldName) {
        final Field field = org.springframework.util.ReflectionUtils.findField(object.getClass(), fieldName);
        if (field == null) {
            throw new UnknownFieldException(fieldName, object.getClass());
        }
        return getFieldValue(object, field);
    }

    public Object getFieldValue(final String fieldName, final Deque hierarchyDeque, final boolean cloneStack) {
        Deque deque = hierarchyDeque;
        if (cloneStack) {
            deque = new LinkedList<>();
            deque.addAll(hierarchyDeque);
        }

        if (fieldName.startsWith(PREFIX_TOP)) {
            // remove all but first element from stack
            while (deque.size() > 1) {
                deque.pop();
            }
            return getFieldValue(fieldName.substring(PREFIX_TOP.length()), deque, false);
        } else if (fieldName.startsWith(PREFIX_ANY)) {
            for (; ; ) {
                try {
                    return getFieldValue(deque.peek(), fieldName.substring(PREFIX_ANY.length()));
                } catch (final UnknownFieldException ex) {
                    log.debug(ex.getMessage());
                    // remove current element from stack, so parent is new current
                    deque.pop();
                    if (deque.isEmpty()) {
                        throw new UnknownFieldException(fieldName.substring(PREFIX_ANY.length()));
                    }
                    return getFieldValue(fieldName, deque, false);
                }
            }
        } else if (fieldName.startsWith(PREFIX_PARENT)) {
            // remove current element from stack, so parent is new current
            deque.pop();
            return getFieldValue(fieldName.substring(PREFIX_PARENT.length()), deque, false);
        }
        return getFieldValue(deque.peek(), fieldName);
    }
}
