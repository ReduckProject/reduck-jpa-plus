package net.reduck.jpa.plus.specification.annotation;

import java.lang.annotation.*;

/**
 * 忽略查询条件
 *
 * @author Reduck
 * @since 2019/4/13 13:43
 */
@Target({ElementType.FIELD,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AttributeIgnore {
}
