package apptive.fin.apicollector;

public enum Source {
    ALL,
    ONTONG, // 온통청년 API
    FSS, // 금감원 API
    KFB, // 은행연합회 소비자포털 비교공시(입출금자유예금)
    CALENDAR // B1 캘린더: 정책상품 회차(manual-product-rounds.json → policy_product_round)와 온통청년 청년정책(youth_policy)
}
