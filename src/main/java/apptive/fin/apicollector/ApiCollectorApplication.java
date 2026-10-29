package apptive.fin.apicollector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ConfigurationPropertiesScan
public class ApiCollectorApplication {

    public static void main(String[] args) {
        // 배치가 FAILED로 끝나면 0이 아닌 종료 코드를 내도록 한다(JobExecutionExitCodeGenerator). 수집 워크플로가 실패를 감지하는 근거.
        System.exit(SpringApplication.exit(SpringApplication.run(ApiCollectorApplication.class, args)));
    }

}
