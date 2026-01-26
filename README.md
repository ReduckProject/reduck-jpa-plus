# Reduck JPA Plus

## 项目概述

Reduck JPA Plus 是一个基于 Spring Boot 3.x 的增强库，通过注解驱动的查询规范构建，简化了 Spring Data JPA 的查询构建和数据访问操作。

**核心特性：**
- 注解驱动的动态查询构建
- 增强的 Repository 接口
- 简化的分页查询
- 数据更新差异分析
- 原生 SQL 查询支持

**技术栈：**
- Java 17+
- Spring Boot 3.3.5
- Jakarta Persistence (Spring Data JPA)
- Hibernate 6.x

---

## 核心注解详解

### @AttributeIgnore
**作用**：标记字段或方法在查询时被忽略

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AttributeIgnore {
}
```

**使用场景**：用于分页参数、排序参数等不应作为查询查询条件的字段

### @AttributeProjection
**作用**：自定义查询条件注解，支持复杂查询配置

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface AttributeProjection {
    /**
     * 实体属性名称
     */
    String[] property() default {};

    /**
     * 关联表（支持多级关联）
     * 示例：join = {"b", "c"} 表示 A.b.c 的关联关系
     */
    String[] join() default {};

    /**
     * 关联查询类型（默认左连接）
     */
    JoinType joinType() default JoinType.LEFT;

    /**
     * 类型转换方法（无参方法）
     */
    String castMethod() default "";

    /**
     * 动态获取忽略大小写的方法（无参方法，返回boolean）
     */
    String ignoreCaseMethod() default "";

    /**
     * 是否忽略大小写（可被ignoreCaseMethod覆盖）
     */
    boolean ignoreCase() default false;

    /**
     * 比较操作符（默认EQUALS）
     */
    CompareOperator compare() default CompareOperator.EQUALS;

    /**
     * 外部字段查询关系（AND/OR，默认AND）
     */
    CombineOperator combine() default CombineOperator.AND;

    /**
     * 内部字段查询关系（AND/OR，默认OR）
     */
    CombineOperator innerCombine() default CombineOperator.OR;

    /**
     * 参数有效性检测策略（默认NOT_EMPTY）
     */
    MatchType match() default MatchType.NOT_EMPTY;

    /**
     * 属性转换器
     */
    Class<? extends AttributeTransformer> transformer() default AttributeTransformer.class;
}
```

### @ColumnProjection
**作用**：用于查询结果列的投影配置

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ColumnProjection {
    /**
     * 列名
     */
    String name() default "";

    /**
     * 关联表
     */
    String[] join() default {};

    /**
     * 关联查询类型（默认左连接）
     */
    JoinType joinType() default JoinType.LEFT;

    /**
     * 列转换器
     */
    Class<? extends ColumnTransformer> transformer() default ColumnTransformer.class;
}
```

### @Date
**作用**：日期格式转换注解，将日期字符串转换为时间戳

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Date {
    /**
     * 日期格式（默认yyyy-MM-dd）
     */
    String pattern() default "yyyy-MM-dd";

    /**
     * 时差（正数）
     */
    int difference() default 0;

    /**
     * 时间单位（默认天）
     */
    TimeUnit timeUnit() default TimeUnit.DAYS;
}
```

### @Distinct
**作用**：设置查询结果去重

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Distinct {
    /**
     * 是否去重（默认true）
     */
    boolean value() default true;

    /**
     * 动态判断是否去重的方法名（返回boolean）
     */
    String distinctMethod() default "";
}
```

### @Subquery
**作用**：将查询类的所有条件作为子查询

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Subquery {
    /**
     * 指定触发子查询的属性列表
     */
    String[] properties() default {};
}
```

**使用效果**：生成 SQL 类似 `SELECT * FROM A WHERE id IN (SELECT id FROM A WHERE ...)`

### @GroupBy（已废弃）
**作用**：分组查询（JPA 对分组查询的分页支持不友好，已废弃）

---

## 核心接口与类

### BaseEntityInterface
**作用**：所有实体类的基础接口，提供统一的生命周期字段

```java
public interface BaseEntityInterface {
    long getId();

    long getCreateTime();
    void setCreateTime(long createTime);

    long getUpdateTime();
    void setUpdateTime(long updateTime);

    boolean isDeleted();
}
```

### JpaRepositoryExtend
**作用**：增强的 Repository 接口，扩展了 Spring Data JPA 的功能

```java
@NoRepositoryBean
public interface JpaRepositoryExtend<T extends BaseEntityInterface, ID>
        extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    // 根据ID查询未删除的实体
    Optional<T> findByIdAndDeletedFalse(ID id);

    // 根据ID集合查询（允许集合为空）
    List<T> findAllWithIdsNullable(Iterable<ID> ids);

    // 根据ID查询未删除的实体（返回非Optional）
    T getOneByDeletedFalse(ID id);

    // 字段映射查询（不分页）
    List<T> findAllWith(Object o);

    // 字段映射查询并转换
    <R> List<R> findAllWith(Object o, Function<T, R> transfer);

    // 字段映射查询并返回指定类型
    <R> List<R> findAllWith(Object o, Class<R> returnType);

    // 分页查询
    <X extends PageRequest> Page<T> findPagedWith(X pageRequest);

    // 分页查询并转换
    <X extends PageRequest, R> PaginationResult<R> findPagedWith(X query, Function<T, R> transfer);

    // 分页查询并返回指定类型
    <X extends PageRequest, R> PaginationResult<R> findPagedWith(X query, Class<R> returnType);

    // 批量插入
    <S extends T> Iterable<S> batchInsert(Iterable<S> var1);

    // 持久化单个实体
    <S extends T> S persist(S entity);

    // 持久化多个实体
    <S extends T> Iterable<S> persistAll(Iterable<S> entities);

    // 使用构建器查询
    <T> List<T> findAllByBuilder(SpecificationQueryBuilder builder);

    // 执行原生SQL查询
    <R> List<R> executeNativeSql(String sql, Class<R> returnType);
}
```

### PageRequest
**作用**：自定义分页请求类

```java
public class PageRequest {
    @AttributeIgnore
    private int rows = 15; // 每页行数（默认15）

    @AttributeIgnore
    private int page = 1; // 页码（默认1）

    @AttributeIgnore
    private String sortProperty = "id"; // 排序字段（默认id）

    @AttributeIgnore
    private Sort.Direction sortDirection = Sort.Direction.DESC; // 排序方向（默认降序）

    // 转换为Spring Data的Pageable
    public org.springframework.data.domain.PageRequest toPageable();

    // 标记查询已删除的记录（默认false）
    public Boolean getDeleted();
}
```

### PaginationResult
**作用**：分页结果包装类

```java
public class PaginationResult<R> {
    private long total; // 总记录数
    private int totalPages; // 总页数
    private List<R> rows; // 数据列表

    // 从Spring Data Page转换
    <T> PaginationResult<R> of(Page<T> page, Function<T, R> transferData);

    // 静态初始化方法
    public static <R> PaginationResult<R> init(Page<?> page, List<R> rows);
    public static <R> PaginationResult<R> init(Page<R> page);
    public static <R> PaginationResult<R> init(long total, int totalPages, List<R> rows);
}
```

### SpecificationQueryBuilder
**作用**：动态规范查询构建器（Fluent API）

```java
public class SpecificationQueryBuilder {
    public static SpecificationQueryBuilder newInstance();

    // 创建AND条件匹配器
    public Matcher and(String property);

    // 创建OR条件匹配器
    public Matcher or(String property);

    // 构建Specification
    <T> Specification<T> build(Class<T> domainClass);

    // 内部匹配器类
    public static class Matcher {
        // 设置操作符
        public Matcher operate(CompareOperator type);

        // 设置属性名
        public Matcher property(String property);

        // 设置值
        public Matcher value(Object value);

        // 设置关联表
        public Matcher joins(String... join);

        // 完成匹配
        public SpecificationQueryBuilder match();
    }
}
```

**使用示例：**
```java
SpecificationQueryBuilder.newInstance()
    .and("username").operate(CompareOperator.CONTAINS).value("test").match()
    .or("email").value("example.com").match()
    .build(User.class);
```

### DataUpdater
**作用**：数据更新差异分析器，比较新旧数据列表的差异

```java
public class DataUpdater<K, V> {
    // 构造函数
    public DataUpdater(List<V> oldData, List<V> newData, Function<V, K> keyFunction);
    public DataUpdater(List<V> oldData, List<V> newData, Function<V, K> keyFunction, Comparator<V> comparator);

    // 获取新增数据
    public List<V> getAddList();

    // 获取删除数据
    public List<V> getDeleteList();

    // 获取更新数据
    public List<V> getUpdateList();
}
```

**使用示例：**
```java
DataUpdater<String, Test> updater = new DataUpdater<>(
    oldDataList,
    newDataList,
    Test::getName1
);

List<Test> addList = updater.getAddList();
List<Test> deleteList = updater.getDeleteList();
List<Test> updateList = updater.getUpdateList();
```

---

## 枚举类详解

### CompareOperator（比较操作符）

| 操作符 | SQL 表达式 | 中文描述 |
|--------|-----------|---------|
| EQUALS | = | 等于 |
| NOT_EQUALS | != | 不等于 |
| GRATER_THAN | > | 大于 |
| GRATER_THAN_OR_EQUAL | >= | 大于等于 |
| LESS_THAN | < | 小于 |
| LESS_THAN_OR_EQUAL | <= | 小于等于 |
| CONTAINS | LIKE '%value%' | 包含 |
| NOT_CONTAINS | NOT LIKE '%value%' | 不包含 |
| STARTS_WITH | LIKE 'value%' | 起始包含 |
| ENDS_WITH | LIKE '%value' | 结束包含 |
| IN | IN() | 任一 |
| NOT_IN | NOT IN() | 都不是 |
| NULL | IS NULL | 为空 |
| NOT_NULL | IS NOT NULL | 不为空 |

### CombineOperator（逻辑运算符）

| 操作符 | SQL 表达式 | 描述 |
|--------|-----------|------|
| AND | AND | 与操作（默认） |
| OR | OR | 或操作 |

### MatchType（匹配策略）

| 策略 | 描述 |
|------|------|
| NOT_EMPTY | 非空匹配（默认） |
| NOT_NULL | 非null匹配 |
| NOT_ZERO | 非零匹配 |
| ALWAYS | 始终匹配 |

---

## 使用示例

### 基础使用

**步骤1：定义实体类**
```java
@Entity
@Data
public class User implements BaseEntityInterface {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String email;

    private int age;

    private boolean deleted;

    private long createTime;

    private long updateTime;
}
```

**步骤2：定义查询对象**
```java
@Data
public class UserQuery extends PageRequest {
    @AttributeProjection(property = "username", compare = CompareOperator.CONTAINS)
    private String username;

    @AttributeProjection(property = "email", compare = CompareOperator.ENDS_WITH)
    private String emailDomain;

    @AttributeProjection(property = "age", compare = CompareOperator.GRATER_THAN_OR_EQUAL)
    private Integer minAge;

    @AttributeProjection(property = "age", compare = CompareOperator.LESS_THAN)
    private Integer maxAge;
}
```

**步骤3：定义 Repository**
```java
public interface UserRepository extends JpaRepositoryExtend<User, Long> {
}
```

**步骤4：使用查询**
```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public PaginationResult<User> findUsers(UserQuery query) {
        return userRepository.findPagedWith(query, User.class);
    }

    public List<User> findActiveUsers(String username) {
        UserQuery query = new UserQuery();
        query.setUsername(username);
        return userRepository.findAllWith(query);
    }
}
```

### 高级查询

**关联查询示例：**
```java
@Data
public class OrderQuery extends PageRequest {
    @AttributeProjection(property = "user.username", compare = CompareOperator.CONTAINS, join = {"user"})
    private String username;

    @AttributeProjection(property = "product.name", compare = CompareOperator.EQUALS, join = {"product"})
    private String productName;

    @AttributeProjection(property = "status", compare = CompareOperator.IN)
    private List<OrderStatus> statuses;
}
```

**子查询示例：**
```java
@Subquery
@Data
public class UserSubquery extends PageRequest {
    @AttributeProjection(property = "age", compare = CompareOperator.GRATER_THAN)
    private Integer minAge;
}
```

**使用 SpecificationQueryBuilder：**
```java
public List<User> findUsersByBuilder(String username, String email) {
    SpecificationQueryBuilder builder = SpecificationQueryBuilder.newInstance();

    if (StringUtils.hasText(username)) {
        builder.and("username").operate(CompareOperator.CONTAINS).value(username).match();
    }

    if (StringUtils.hasText(email)) {
        builder.and("email").operate(CompareOperator.CONTAINS).value(email).match();
    }

    return userRepository.findAllByBuilder(builder);
}
```

---

## 项目构建

```bash
# 编译和测试
./mvnw clean compile
./mvnw test

# 打包
./mvnw package

# 安装到本地仓库
./mvnw install
```

---

## 版本迁移

从 Spring Boot 2.x 迁移到 3.x：

1. **包名变更**：`javax.persistence` → `jakarta.persistence`
2. **Hibernate 版本**：5.x → 6.x
3. **Java 版本**：8+ → 17+
4. **Spring Data JPA**：相应更新

---

## 注意事项

1. **Java 版本**：需要 Java 17+
2. **Spring Boot 版本**：需要 Spring Boot 3.x
3. **实体类要求**：必须实现 BaseEntityInterface
4. **查询对象继承**：查询对象通常应继承 PageRequest 以支持分页
5. **关联查询深度**：支持多级关联查询，但应避免过深的关联
6. **性能考虑**：复杂查询可能需要优化数据库索引

---

## 扩展与定制

### 自定义属性转换器

```java
public class CustomAttributeTransformer implements AttributeTransformer {
    @Override
    public Object transform(Object value) {
        // 自定义转换逻辑
        return value;
    }
}

// 使用
@AttributeProjection(transformer = CustomAttributeTransformer.class)
private String customField;
```

### 自定义列转换器

```java
public class CustomColumnTransformer implements ColumnTransformer {
    @Override
    public Object transform(Object value) {
        // 自定义列转换逻辑
        return value;
    }
}

// 使用
@ColumnProjection(transformer = CustomColumnTransformer.class)
private String customColumn;
```

---

Reduck JPA Plus 提供了一种简洁而强大的方式来处理数据库查询，通过注解驱动和动态查询构建，显著减少了开发时间和代码复杂度。
