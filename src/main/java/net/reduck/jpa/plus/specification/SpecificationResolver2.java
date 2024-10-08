package net.reduck.jpa.plus.specification;

import net.reduck.jpa.plus.specification.annotation.Distinct;
import net.reduck.jpa.plus.specification.annotation.Subquery;
import net.reduck.jpa.plus.specification.enums.CombineOperator;
import net.reduck.jpa.plus.specification.enums.CompareOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.*;
import java.lang.reflect.Method;
import java.util.*;

import static net.reduck.jpa.plus.specification.SpecificationAnnotationIntrospector.getPropertiesAndGetter;
import static net.reduck.jpa.plus.specification.SpecificationAnnotationIntrospector.getQueryDescriptors;

/**
 * @author Reduck
 * @since 2019/4/2 19:06
 */
@SuppressWarnings({"unchecked", "rawtypes", "cast", "AlibabaMethodTooLong", "Duplicates"})
class SpecificationResolver2<T> implements Specification<T> {
    private static final long serialVersionUID = -1L;

    private static Logger log = LoggerFactory.getLogger(SpecificationResolver2.class);

    private final Class targetClass;
    private final Map<String, Method> entityReadMethods;
    private final List<AttributeProjectionDescriptor> conditions;
    private final Map<String, Join> joinCache = new HashMap<>();
    private final String id = "id";
    private final Object target;

    private Predicate predicateResult = null;

    private EscapeCharacter escape = EscapeCharacter.DEFAULT;

    public SpecificationResolver2(Object queryCondition, Class<T> entityClass) {
        this.targetClass = queryCondition.getClass();
        this.entityReadMethods = getPropertiesAndGetter(entityClass);
        this.conditions = new ArrayList<>(getQueryDescriptors(queryCondition));
        this.target = queryCondition;
    }

    public SpecificationResolver2(List<AttributeProjectionDescriptor> queryCondition, Class<T> entityClass) {
        this.targetClass = Void.class;
        this.entityReadMethods = getPropertiesAndGetter(entityClass);
        this.conditions = queryCondition;
        this.target = Void.class;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    @Nullable
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        processDistinct(query);

        if (AnnotationUtils.findAnnotation(targetClass, Subquery.class) != null) {
            javax.persistence.criteria.Subquery subquery = query.subquery(root.getJavaType());
            Root<T> subRoot = subquery.from(root.getJavaType());
            subquery.select(subRoot.get(id));
            subquery.where(process(subRoot, criteriaBuilder)).alias("subquery");
            predicateResult = criteriaBuilder.and(root.get(id).in(subquery));
        } else {
            predicateResult = criteriaBuilder.and(process(root, criteriaBuilder));
        }

        // 清除缓存，避免多次调用异常
        joinCache.clear();
        return predicateResult;
    }

    private Predicate process(Root root, CriteriaBuilder criteriaBuilder) {

        Predicate predicate = criteriaBuilder.conjunction();
        // 自定义条件转换
        if (conditions.size() > 0) {
            for (AttributeProjectionDescriptor condition : conditions) {
                if ((condition.joinName == null || condition.joinName.length == 0) && !entityReadMethods.containsKey(condition.columnName)) {
                    throw new RuntimeException("未知字段:" + condition.columnName);
                }
                log.debug("查询字段：{}={}", condition.columnName, condition.value);

                // 处理 like 多值
                Predicate nextPredicate = getMultipleValuePredicate(condition, criteriaBuilder, root);
                // 处理非like多值
                if (nextPredicate == null) {
                    nextPredicate = processCondition(condition, criteriaBuilder, root);
                    nextPredicate = getInPredicate(nextPredicate, condition, criteriaBuilder, root);
                }

                switch (condition.combine) {
                    case AND: {
                        predicate = criteriaBuilder.and(predicate, nextPredicate);
                        break;
                    }

                    case OR: {
                        predicate = criteriaBuilder.or(predicate, nextPredicate);
                        break;
                    }

                    default:
                        break;
                }
            }
        }

        return predicate;
    }

    /**
     * 根据操作符组合查询语句
     *
     * @param condition
     * @param criteriaBuilder
     * @param root
     *
     * @return
     */
    private Predicate processCondition(AttributeProjectionDescriptor condition, CriteriaBuilder criteriaBuilder, Root<T> root) {
        Predicate predicate;
        switch (condition.operatorType) {
            // 小于等于
            case LESS_THAN_OR_EQUAL:
                predicate = criteriaBuilder.lessThanOrEqualTo(root.get(condition.columnName), (Comparable) condition.value);
                break;

            // 包含
            case CONTAINS:
                if (condition.ignoreCase) {
                    predicate = (criteriaBuilder.like(criteriaBuilder.lower(getPath(condition, root)), "%" + escape(((String) condition.value).toLowerCase()) + "%"));
                } else {
                    predicate = criteriaBuilder.like(getPath(condition, root), "%" + escape(String.valueOf(condition.value)) + "%");
                }
                break;

            // 小于
            case LESS_THAN:
                predicate = criteriaBuilder.lessThan(getPath(condition, root), (Comparable) condition.value);
                break;

            // 大于
            case GRATER_THAN:
                predicate = criteriaBuilder.greaterThan(getPath(condition, root), (Comparable) condition.value);
                break;

            // 大于等于
            case GRATER_THAN_OR_EQUAL:
                predicate = criteriaBuilder.greaterThanOrEqualTo(getPath(condition, root), (Comparable) condition.value);
                break;

            // 不等于
            case NOT_EQUALS:
                predicate = criteriaBuilder.notEqual(getPath(condition, root), condition.value);
                break;

            case STARTS_WITH:
                predicate = criteriaBuilder.notLike(getPath(condition, root), condition.value + "%");
                break;

            case ENDS_WITH:
                predicate = criteriaBuilder.notLike(getPath(condition, root), "%" + condition.value);
                break;

            case NULL:
                predicate = criteriaBuilder.isNull(getPath(condition, root));
                break;

            case NOT_NULL:
                predicate = criteriaBuilder.isNotNull(getPath(condition, root));
                break;

            case IN:
                predicate = criteriaBuilder.in(getPath(condition, root));
                if (condition.value instanceof Collection) {
                    Collection collection = (Collection) condition.value;
                    for (Object o : collection) {
                        ((CriteriaBuilder.In) predicate).value(o);
                    }
                }
                break;

            case NOT_IN:
                predicate = criteriaBuilder.in(getPath(condition, root));
                if (condition.value instanceof Collection) {
                    Collection collection = (Collection) condition.value;
                    for (Object o : collection) {
                        ((CriteriaBuilder.In) predicate).value(o);
                    }
                }

                predicate = predicate.not();
                break;

            // 默认等于
            case EQUALS:
            default:
                predicate = criteriaBuilder.equal(getPath(condition, root), condition.value);
                break;
        }

        return predicate;
    }

    private Path getPath(AttributeProjectionDescriptor condition, Root<T> root) {
        if (condition.joinName == null || condition.joinName.length == 0) {
            return root.get(condition.columnName);
        }

        return getJoin(root, condition.joinName, condition.joinType).get(condition.columnName);
    }

    /**
     * 获取关联表
     * <p>
     * root.getJoins().add(...)
     * 后续可使用 root本身 不用新建一个Map,解析 查询可能会比较麻烦
     *
     * @param root
     * @param joinNames
     *
     * @return
     */
    private Join getJoin(Root root, String[] joinNames, JoinType joinType) {
        StringBuilder sb = new StringBuilder();
        Join join = null;

        for (String joinName : joinNames) {
            sb.append(joinName).append(".");
            if (!joinCache.containsKey(sb.toString())) {
                if (join == null) {
                    joinCache.put(sb.toString(), root.join(joinName, joinType));
                } else {
                    joinCache.put(sb.toString(), join.join(joinName, joinType));
                }
            }
            join = joinCache.get(sb.toString());
        }
        return join;
    }

    private Predicate getMultipleValuePredicate(AttributeProjectionDescriptor condition, CriteriaBuilder criteriaBuilder, Root<T> root) {
        if (condition.operatorType != CompareOperator.ENDS_WITH
                && condition.operatorType != CompareOperator.CONTAINS
                && condition.operatorType != CompareOperator.STARTS_WITH
        ) {
            return null;
        }

        if (!(condition.value instanceof Collection)) {
            return null;
        }

        List<Predicate> predicates = new ArrayList<>();
        for (Object values : (Collection) condition.value) {
            AttributeProjectionDescriptor newCondition = new AttributeProjectionDescriptor(condition.name, condition.name, values, condition.operatorType);
            newCondition.setJoinName(condition.joinName);
            newCondition.setJoinType(condition.joinType);
            predicates.add(processCondition(newCondition, criteriaBuilder, root));
        }

        return condition.innerCombine == CombineOperator.OR
                ? criteriaBuilder.or(criteriaBuilder.or(predicates.toArray(new Predicate[]{})))
                : criteriaBuilder.and(criteriaBuilder.and(predicates.toArray(new Predicate[]{})));
    }

    private Predicate getInPredicate(Predicate predicate, AttributeProjectionDescriptor condition, CriteriaBuilder criteriaBuilder, Root<T> root) {
        if (condition.innerNames.size() == 0) {
            return predicate;
        }

        List<Predicate> predicates = new ArrayList<>();
        for (String name : condition.innerNames) {
            AttributeProjectionDescriptor newCondition = new AttributeProjectionDescriptor(name, name, condition.value, condition.operatorType);
            newCondition.setJoinName(condition.joinName);
            newCondition.setJoinType(condition.joinType);
            predicates.add(processCondition(newCondition, criteriaBuilder, root));
        }

        predicates.add(predicate);

        switch (condition.innerCombine) {
            case AND:
                return criteriaBuilder.and(criteriaBuilder.and(predicates.toArray(new Predicate[]{})));

            case OR:
                return criteriaBuilder.or(criteriaBuilder.or(predicates.toArray(new Predicate[]{})));

            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * 模糊查询时进行字符转义
     *
     * @param value 待转译字符
     *
     * @return
     */
    private String escape(String value) {
        return StringUtils.hasText(value) ? escape.escape(value) : value;
    }

    private void processDistinct(CriteriaQuery<?> query) {
        Distinct distinct = AnnotationUtils.findAnnotation(targetClass, Distinct.class);

        if (distinct == null) {
            return;
        }

        if (!"".equals(distinct.distinctMethod())) {
            Method method = BeanUtils.findMethod(targetClass, distinct.distinctMethod());
            if (method == null) {
                throw new DynamicBuildJpaQueryException("Distinct method does not exist : " + targetClass.getName() + "#" + distinct.distinctMethod() + "()");
            }

            if ((Boolean) SpecificationAnnotationIntrospector.invoke(method, target)) {
                query.distinct(true);
            }
        } else {
            if (distinct.value()) {
                query.distinct(true);
            }
        }
    }

}
