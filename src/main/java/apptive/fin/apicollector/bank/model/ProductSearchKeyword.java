package apptive.fin.apicollector.bank.model;

public record ProductSearchKeyword(
        String value,
        String canonicalName
) {
    public ProductSearchKeyword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("keyword value is required");
        }
        value = value.trim();
        canonicalName = canonicalName == null || canonicalName.isBlank()
                ? value
                : canonicalName.trim();
    }

    public ProductSearchKeyword(String value) {
        this(value, value);
    }

    public String compact() {
        return value.replaceAll("\\s+", "");
    }

    public String compactCanonical() {
        return canonicalName.replaceAll("\\s+", "");
    }
}
