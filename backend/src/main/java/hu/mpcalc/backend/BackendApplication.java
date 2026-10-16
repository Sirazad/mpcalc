package hu.mpcalc.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
// The repositories are nested interfaces of hu.mpcalc.backend.repo.Repositories.
@EnableJpaRepositories(considerNestedRepositories = true)
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
