package club.ttg.moduleregistry.submission;

import club.ttg.moduleregistry.system.GameSystem;
import club.ttg.moduleregistry.system.GameSystemRepository;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет разбор JPQL-запросов репозиториев без подключения к базе. */
class ModuleSubmissionRepositoryQueryTest {

    @Test
    void repositoryQueriesCompile() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(ModuleSubmission.class)
                .addAnnotatedClass(GameSystem.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none");

        try (var sessionFactory = configuration.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            JpaRepositoryFactory factory = new JpaRepositoryFactory(session);
            assertThat(factory.getRepository(ModuleSubmissionRepository.class)).isNotNull();
            assertThat(factory.getRepository(GameSystemRepository.class)).isNotNull();
        }
    }
}
