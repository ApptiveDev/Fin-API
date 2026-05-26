package apptive.fin.apicollector.bank;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;

import java.util.List;

public interface ProductLinkDiscoverer {
    BankCode bankCode();

    List<ProductCandidate> discover(ProductSearchKeyword keyword);
}
