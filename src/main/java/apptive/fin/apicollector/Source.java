package apptive.fin.apicollector;

public enum Source {
    ALL,
    ONTONG, // 온통청년 API
    FSS, // 금감원 API
    KFB, // 은행연합회 소비자포털 비교공시(입출금자유예금)
    YOUTH_POLICY // 온통청년 청년정책 → B1 캘린더(youth_policy). 상품이 아니라 product에는 쓰지 않는다
}
