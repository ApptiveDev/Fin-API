package apptive.fin.apicollector.product.repository;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.entity.ProductSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySourceAndProductCode(ProductSource source, String productCode);

    @Query("""
        select p
        from Product p
        join fetch p.source s
        where s.code = :sourceCode
          and p.type = :type
        order by p.id asc
    """)
    List<Product> findAllBySourceCodeAndType(
            @Param("sourceCode") String sourceCode,
            @Param("type") ProductType type
    );

    @Query("""
        update ProductProperty pp
            set pp.isJoinable = false
           where pp.product.source = :productSource
           and exists(
               select pr.id
                   from ProductRaw pr
                   where pr.source = :source
                       and pr.externalId = pp.product.productCode
                       and pr.lastSeenAt < :lastSeen
               )
    """)
    @Modifying
    int disableBySourceAndLastSeenBefore(ProductSource productSource, Source source, Instant lastSeen);
}
