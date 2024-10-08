package net.reduck.jpa.plus.entity.transformer;

/**
 * @author Gin
 * @since 2023/9/7 11:25
 */
@FunctionalInterface
public interface ColumnTransformer<T, R> {

     R toAttribute(T attribute);
}
