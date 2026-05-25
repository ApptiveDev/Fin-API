package apptive.fin.apicollector.bank;

import java.util.List;

public interface ProductLinkDiscoverer {
    BankCode bankCode();

    List<ProductCandidate> discover(ProductSearchKeyword keyword);
}
