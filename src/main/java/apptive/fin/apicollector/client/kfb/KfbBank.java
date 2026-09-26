package apptive.fin.apicollector.client.kfb;

/**
 * 은행연합회 비교공시의 은행. code는 FSS fin_co_no와 같은 체계다(0010001 우리은행 등).
 */
public record KfbBank(
        String code,
        String name
) {
}
