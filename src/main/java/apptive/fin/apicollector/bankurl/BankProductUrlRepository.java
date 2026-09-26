package apptive.fin.apicollector.bankurl;

import apptive.fin.apicollector.product.entity.ProductProperty;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BankProductUrlRepository extends Repository<ProductProperty, Long> {

    @Query("""
        select distinct new apptive.fin.apicollector.bankurl.BankProductUrlTarget(
            p.id,
            p.productCode,
            p.originalName,
            p.type,
            provider.code,
            provider.name
        )
        from ProductProperty property
        join property.product p
        join property.provider provider
        where p.source.code in :sourceCodes
          and property.isJoinable = true
          and provider.code is not null
        order by provider.code, p.originalName, p.id
        """)
    List<BankProductUrlTarget> findActiveTargets(@Param("sourceCodes") Collection<String> sourceCodes);

    @Modifying(clearAutomatically = true)
    @Query("""
        update ProductProperty property
           set property.applyUrl = :url
         where property.product.id = :productId
           and property.provider.code = :providerCode
           and property.isJoinable = true
           and (property.applyUrl is null or property.applyUrl <> :url)
        """)
    int updateActiveProductUrl(
            @Param("productId") Long productId,
            @Param("providerCode") String providerCode,
            @Param("url") String url
    );
}
