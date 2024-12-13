package net.reduck.jpa.plus.specification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * @author Gin
 * @since 2023/2/8 10:10
 */
public class JpaExecutor {
    private final EntityManager em;

    public JpaExecutor(EntityManager em) {
        this.em = em;
    }

    public <T> void execute(PredicateInfo info, Class<T> domainType) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(domainType);
        Root<T> root = query.from(domainType);
        query.select(root);
    }
}
