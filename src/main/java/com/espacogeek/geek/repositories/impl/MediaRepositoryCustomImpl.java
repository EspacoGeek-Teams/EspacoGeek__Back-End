package com.espacogeek.geek.repositories.impl;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Repository;

import com.espacogeek.geek.models.MediaModel;
import com.espacogeek.geek.repositories.MediaRepositoryCustom;
import com.espacogeek.geek.utils.Utils;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.TypedQuery;

@Repository
public class MediaRepositoryCustomImpl implements MediaRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @see MediaRepositoryCustom#findMediaByNameOrAlternativeTitleAndMediaCategory(String,
     *      String, Integer, Map)
     */
    @Override
    public Page<MediaModel> findMediaByNameOrAlternativeTitleAndMediaCategory(
            String name,
            String alternativeTitle,
            Integer category,
            Map<String, List<String>> requestedFields,
            @PageableDefault(size = 10, page = 0) Pageable pageable) {

        Class<MediaModel> entityClass = MediaModel.class;
        String table = getTableName(entityClass);
        String alias = "m";

        // 1) Determine selected scalar fields (always include id and name)
        Set<String> fieldNames = new LinkedHashSet<>();
        fieldNames.add("id");
        if (hasField(entityClass, "name")) fieldNames.add("name");

        if (requestedFields != null) {
            requestedFields.forEach((field, subFields) -> {
                // include only scalar fields belonging to MediaModel (ignore associations)
                if (isScalarField(entityClass, field)) fieldNames.add(field);
            });
        }

        // Ensure selected fields exist
        fieldNames.removeIf(f -> !hasField(entityClass, f));

        // Resolve column list with aliases matching Java field names
        List<String> selectCols = new ArrayList<>();
        for (String f : fieldNames) {
            Field fld = getField(entityClass, f);
            String col = getColumnName(fld);
            selectCols.add(alias + "." + col + " AS " + f);
        }

        // Build FROM and JOINs
        StringBuilder from = new StringBuilder()
                .append(" FROM ").append(table).append(" ").append(alias).append(" ");

        // Try to resolve alternativeTitles join metadata via reflection
        JoinInfo altJoin = resolveAlternativeTitlesJoin(entityClass);
        if (altJoin != null) {
            from.append(" LEFT JOIN ").append(altJoin.table).append(" at")
                .append(" ON at.").append(altJoin.fkToMediaColumn)
                .append(" = ").append(alias).append(".").append(altJoin.mediaIdColumn)
                .append(" ");
        }

        // Build WHERE
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        // name filter
        if (name != null && !name.isBlank() && hasField(entityClass, "name")) {
            String nameCol = getColumnName(getField(entityClass, "name"));
            where.add("LOWER(" + alias + "." + nameCol + ") LIKE :name");
            params.put("name", "%" + name.toLowerCase(Locale.ROOT) + "%");
        }

        // category filter (resolve FK column from @JoinColumn on mediaCategory if possible)
        if (category != null) {
            String catFkColumn = getJoinColumnName(entityClass, "mediaCategory", "media_category_id");
            where.add(alias + "." + catFkColumn + " = :category");
            params.put("category", category);
        }

        // alternativeTitle filter (only if join resolved)
        if (alternativeTitle != null && !alternativeTitle.isBlank() && altJoin != null && altJoin.altNameColumn != null) {
            where.add("LOWER(at." + altJoin.altNameColumn + ") LIKE :altTitle");
            params.put("altTitle", "%" + alternativeTitle.toLowerCase(Locale.ROOT) + "%");
        }

        String whereSql = where.isEmpty() ? "" : (" WHERE " + String.join(" AND ", where));

        // Count query (DISTINCT by id)
        String idColumn = altJoin != null ? altJoin.mediaIdColumn : getIdColumnName(entityClass);
        String countSql = "SELECT COUNT(DISTINCT " + alias + "." + idColumn + ") " + from + whereSql;

        jakarta.persistence.Query countQuery = entityManager.createNativeQuery(countSql)
                .unwrap(jakarta.persistence.Query.class);
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                countQuery.setParameter(e.getKey(), e.getValue());
            }
        }
        Number total = ((Number) countQuery.getSingleResult());

        long totalElements = total == null ? 0L : total.longValue();

        if (totalElements == 0L) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Main SELECT
        String sql = "SELECT " + String.join(", ", selectCols) + from + whereSql
                + " ORDER BY " + alias + "." + idColumn + " ASC";

        jakarta.persistence.Query q = entityManager.createNativeQuery(sql);
        // bind params
        for (Map.Entry<String, Object> e : params.entrySet()) {
            q.setParameter(e.getKey(), e.getValue());
        }
        q.setFirstResult((int) pageable.getOffset());
        q.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Object> rows = q.getResultList();

        // Map to entities
        List<MediaModel> result = new ArrayList<>(rows.size());
        List<String> orderedFields = new ArrayList<>(fieldNames); // matches selectCols order
        for (Object row : rows) {
            Object[] cols = (orderedFields.size() == 1) ? new Object[]{ row } : (Object[]) row;
            MediaModel entity = mapRowToEntity(entityClass, orderedFields, cols);
            result.add(entity);
        }

        return new PageImpl<>(result, pageable, totalElements);
    }

    // ---- Reflection helpers ----

    private static boolean hasField(Class<?> type, String fieldName) {
        return getField(type, fieldName) != null;
    }

    private static Field getField(Class<?> type, String fieldName) {
        Class<?> t = type;
        while (t != null && t != Object.class) {
            try {
                Field f = t.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
            t = t.getSuperclass();
        }
        return null;
    }

    private static String getTableName(Class<?> entityClass) {
        Table t = entityClass.getAnnotation(Table.class);
        if (t != null && !t.name().isBlank()) return t.name();
        // fallback: simple snake_case pluralization is project-specific; keep simple
        return camelToSnake(entityClass.getSimpleName());
    }

    private static String getColumnName(Field field) {
        if (field == null) return null;
        Column c = field.getAnnotation(Column.class);
        if (c != null && !c.name().isBlank()) return c.name();
        // Fallback for @Id without @Column
        if (field.isAnnotationPresent(Id.class)) return camelToSnake(field.getName());
        return camelToSnake(field.getName());
    }

    private static String getIdColumnName(Class<?> entityClass) {
        for (Field f : entityClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) {
                return getColumnName(f);
            }
        }
        // naive fallback
        return "id";
    }

    private static String getJoinColumnName(Class<?> entityClass, String fieldName, String defaultName) {
        Field f = getField(entityClass, fieldName);
        if (f != null) {
            JoinColumn jc = f.getAnnotation(JoinColumn.class);
            if (jc != null && !jc.name().isBlank()) return jc.name();
        }
        return defaultName;
    }

    private static boolean isScalarField(Class<?> entityClass, String fieldName) {
        Field f = getField(entityClass, fieldName);
        if (f == null) return false;
        Class<?> t = f.getType();
        if (f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToMany.class)) return false;
        return t.isPrimitive()
                || Number.class.isAssignableFrom(t)
                || CharSequence.class.isAssignableFrom(t)
                || Boolean.class.isAssignableFrom(t)
                || java.util.Date.class.isAssignableFrom(t)
                || java.time.temporal.Temporal.class.isAssignableFrom(t)
                || java.time.LocalDate.class.isAssignableFrom(t)
                || java.time.LocalDateTime.class.isAssignableFrom(t)
                || java.time.OffsetDateTime.class.isAssignableFrom(t);
    }

    private static String camelToSnake(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) out.append('_');
            out.append(Character.toLowerCase(ch));
        }
        return out.toString();
    }

    private static class JoinInfo {
        String table;
        String fkToMediaColumn;
        String mediaIdColumn;
        String altNameColumn;
    }

    // Try to find: alternativeTitles -> elementType table, FK back to media (join column), and 'name' column on element
    private static JoinInfo resolveAlternativeTitlesJoin(Class<?> mediaClass) {
        Field alt = getField(mediaClass, "alternativeTitles");
        if (alt == null) return null;
        try {
            ParameterizedType pt = (ParameterizedType) alt.getGenericType();
            Class<?> altType = (Class<?>) pt.getActualTypeArguments()[0];

            JoinInfo ji = new JoinInfo();
            ji.table = getTableName(altType);
            ji.mediaIdColumn = getIdColumnName(mediaClass);

            // Find ManyToOne back-ref to media
            for (Field f : altType.getDeclaredFields()) {
                if (f.isAnnotationPresent(ManyToOne.class) && f.getType().equals(mediaClass)) {
                    JoinColumn jc = f.getAnnotation(JoinColumn.class);
                    ji.fkToMediaColumn = (jc != null && !jc.name().isBlank()) ? jc.name() : camelToSnake(f.getName()) + "_id";
                }
                if ("name".equals(f.getName())) {
                    ji.altNameColumn = getColumnName(f);
                }
            }

            if (ji.table == null || ji.fkToMediaColumn == null) return null;
            if (ji.altNameColumn == null) ji.altNameColumn = "name";
            return ji;
        } catch (Exception e) {
            return null;
        }
    }

    private static MediaModel mapRowToEntity(Class<MediaModel> entityClass, List<String> fields, Object[] values) {
        try {
            MediaModel instance = entityClass.getDeclaredConstructor().newInstance();
            for (int i = 0; i < fields.size(); i++) {
                String fname = fields.get(i);
                Object val = (i < values.length) ? values[i] : null;
                Field f = getField(entityClass, fname);
                if (f == null) continue;
                Object converted = convertValue(val, f.getType());
                try {
                    f.setAccessible(true);
                    f.set(instance, converted);
                } catch (IllegalAccessException ignored) {}
            }
            return instance;
        } catch (Exception e) {
            // Fallback when mapping fails
            return null;
        }
    }

    private static Object convertValue(Object val, Class<?> targetType) {
        if (val == null) return null;
        if (targetType.isInstance(val)) return val;

        if (targetType == Integer.class || targetType == int.class) {
            if (val instanceof Number n) return n.intValue();
            if (val instanceof String s) return Integer.parseInt(s);
        }
        if (targetType == Long.class || targetType == long.class) {
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) return Long.parseLong(s);
        }
        if (targetType == Double.class || targetType == double.class) {
            if (val instanceof Number n) return n.doubleValue();
            if (val instanceof String s) return Double.parseDouble(s);
        }
        if (targetType == Float.class || targetType == float.class) {
            if (val instanceof Number n) return n.floatValue();
            if (val instanceof String s) return Float.parseFloat(s);
        }
        if (targetType == BigInteger.class && val instanceof Number n) {
            return BigInteger.valueOf(n.longValue());
        }
        if (targetType == BigDecimal.class && val instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (CharSequence.class.isAssignableFrom(targetType)) {
            return String.valueOf(val);
        }
        // Simple temporal handling (leave to JPA/JDBC types, otherwise best-effort)
        if (Temporal.class.isAssignableFrom(targetType)) {
            return val;
        }
        return null;
    }
}
