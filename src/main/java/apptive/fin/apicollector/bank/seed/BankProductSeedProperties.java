package apptive.fin.apicollector.bank.seed;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "collector.bank-product")
public record BankProductSeedProperties(
        List<Seed> seeds
) {
    public BankProductSeedProperties {
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
    }

    public record Seed(
            BankCode bankCode,
            String keyword,
            List<String> aliases,
            String title,
            String url,
            boolean enabled
    ) {
        public Seed {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
