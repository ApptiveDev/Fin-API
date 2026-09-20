# api-collector

금융상품 수집·정규화 배치 (origin: ApptiveDev/Fin-API).

## DB 스키마 / 마이그레이션

DB 스키마와 Flyway 마이그레이션은 **Fin-BE가 전담**하고, 이 앱은 거기에 의존한다.
프로덕션/dev classpath에는 Flyway가 없고(`ddl-auto: validate`로 기존 스키마 검증만 함),
Flyway는 **test scope에만** 존재해 Testcontainers Postgres에 스키마를 만든다.

### Fin-BE submodule

마이그레이션 SQL(`V1~V5`)은 `fin-be` 서브모듈의
`fin-be/src/main/resources/db/migration/`에 있고, test용 Flyway가 이 경로를 참조한다
(`src/test/resources/application-test.yml`의 `spring.flyway.locations`).

- **최초 clone 후**: `git submodule update --init --recursive` (또는 `git clone --recursive`).
- **테스트가 스키마 관련으로 깨지면** 서브모듈이 체크아웃됐는지부터 확인.

#### 스키마를 바꾸는 개발 중 (Fin-BE 미머지 상태)

테스트는 `fin-be/`에 **현재 체크아웃된 내용**을 읽는다(포인터가 아니라 작업트리 기준).
그래서 아직 머지 안 된 스키마도 로컬에서 바로 검증할 수 있다:

```bash
cd fin-be && git fetch && git checkout <Fin-BE 작업브랜치> && cd ..
./gradlew test    # 그 스키마로 엔티티 검증
```

#### 스키마 변경이 확정됐을 때 (포인터 갱신)

api-collector main에 박히는 포인터는 **Fin-BE main에 머지된 커밋**을 가리켜야 한다
(미머지/ephemeral 커밋은 squash·브랜치 삭제로 사라져 포인터가 깨진다). 순서:

1. Fin-BE 스키마 변경을 main에 머지
2. `git submodule update --remote fin-be` → 포인터 bump 후 커밋
