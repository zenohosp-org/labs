package com.labs.server.repository;

import com.labs.server.entity.LabServiceCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Read access to the global LOINC master catalog ({@code lab_service_catalog}).
 * The admin's "add from catalog" picker is the only caller of {@link #search}.
 */
public interface LabServiceCatalogRepository extends JpaRepository<LabServiceCatalog, Long> {

    Optional<LabServiceCatalog> findByLoincCode(String loincCode);

    /**
     * Typeahead search for the picker. Native + {@code ILIKE} on purpose: the
     * V20 {@code gin (name gin_trgm_ops)} / {@code (aliases gin_trgm_ops)}
     * indexes support ILIKE directly, so a '%q%' match over ~60k rows is an
     * index probe rather than a full scan. JPQL has no ILIKE and its
     * {@code LOWER(col) LIKE} form would not hit the trigram index.
     *
     * Ranking mirrors {@code LabServiceRepository.searchByHospital}: exact LOINC
     * first, then name-prefix, then anything else; panels rise within a tier.
     * {@code :lim} caps the result so the dropdown never pulls the catalog.
     */
    @Query(value = """
            SELECT * FROM lab_service_catalog c
            WHERE c.name       ILIKE ('%' || :q || '%')
               OR c.aliases    ILIKE ('%' || :q || '%')
               OR c.loinc_code ILIKE (:q || '%')
               OR c.test_code  ILIKE ('%' || :q || '%')
            ORDER BY
              CASE WHEN c.loinc_code ILIKE :q          THEN 0
                   WHEN c.name       ILIKE (:q || '%') THEN 1
                   ELSE 2 END,
              c.is_panel DESC,
              c.name ASC
            LIMIT :lim
            """, nativeQuery = true)
    List<LabServiceCatalog> search(@Param("q") String q, @Param("lim") int lim);
}
