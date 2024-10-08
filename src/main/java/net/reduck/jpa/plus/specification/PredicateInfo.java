package net.reduck.jpa.plus.specification;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Gin
 * @since 2023/2/8 09:27
 */
@AllArgsConstructor
@Getter
public class PredicateInfo {

    private final AttributeProjectionDescriptor[] descriptors;

    private final boolean distinct;
}
