package com.caiocodes.crbap.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra de dependência da arquitetura, como teste.
 *
 * <p>Sem isto, "o domínio não conhece Spring" depende de todo mundo lembrar
 * disso em todo code review — e a primeira violação passa despercebida num PR
 * de sexta-feira. Com isto, o build quebra em segundos.
 *
 * <p>Existe desde a fase 1 de propósito: adicionado no fim, um teste destes
 * acusa 200 violações de uma vez e a reação natural é desligá-lo.
 *
 * <p><b>Por que @Test comum e não @ArchTest:</b> o motor JUnit5 do ArchUnit não
 * é acionado pelo Surefire nesta configuração — a classe rodava com
 * "Tests run: 0" e o build passava sem checar nada. Regra de arquitetura que
 * não roda é pior que regra nenhuma, porque parece que existe proteção.
 * Como @Test comum, o Jupiter executa e uma violação reprova o build.
 *
 * <p>O {@code allowEmptyShould(true)} que existia nas regras da fase 1 foi
 * <b>removido na fase 4</b>: todo alvo ({@code @Entity}, controller,
 * {@code *UseCase}) já existe. Se uma regra voltar a ficar sem alvo, o build
 * quebra — e é isso que se quer, porque regra vazia é regra que parou de valer
 * sem ninguém perceber.
 */
class ArchitectureTest {

    private static JavaClasses classesDoProjeto;

    @BeforeAll
    static void importar() {
        classesDoProjeto = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.caiocodes.crbap");
    }

    @Test
    @DisplayName("o import encontrou classes — senão toda regra passaria vazia")
    void deve_ter_importado_o_projeto() {
        // Esta é a defesa contra o modo de falha silencioso: se o importer não
        // achar nada, todas as regras abaixo "passam" sem verificar coisa alguma.
        assertThat(classesDoProjeto).isNotEmpty();
    }

    @Test
    @DisplayName("o domínio não depende de framework nenhum")
    void dominio_nao_depende_de_framework() {
        ArchRule regra = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson..")
                .because("o domínio precisa ser testável sem subir contexto nenhum");

        regra.check(classesDoProjeto);
    }

    @Test
    @DisplayName("o domínio não depende de infraestrutura nem da web")
    void dominio_nao_depende_da_borda() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..web..")
                .because("a dependência aponta para dentro, nunca para fora")
                .check(classesDoProjeto);
    }

    @Test
    @DisplayName("o caso de uso fala com portas, não com adaptadores")
    void aplicacao_nao_depende_da_borda() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..web..")
                .check(classesDoProjeto);
    }

    @Test
    @DisplayName("controller não acessa repositório direto")
    void web_nao_acessa_persistencia() {
        noClasses()
                .that().resideInAPackage("..crbap.*.web..")
                .should().dependOnClassesThat().resideInAPackage("..persistence..")
                .check(classesDoProjeto);
    }

    @Test
    @DisplayName("@Entity só existe dentro de infrastructure.persistence")
    void entidades_jpa_ficam_na_persistencia() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("..infrastructure.persistence..")
                .because("@Entity é detalhe de banco, não modelo de negócio")
                .check(classesDoProjeto);
    }

    @Test
    @DisplayName("todo @RestController se chama *Controller e mora em web")
    void controllers_bem_nomeados() {
        classes()
                .that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .andShould().resideInAPackage("..web..")
                .check(classesDoProjeto);
    }

    @Test
    @DisplayName("toda classe *UseCase é um @Service em application")
    void casos_de_uso_sao_servicos() {
        classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .and().areNotInterfaces()
                .should().beAnnotatedWith(Service.class)
                .andShould().resideInAPackage("..application..")
                .check(classesDoProjeto);
    }

    @Test
    @DisplayName("não há ciclo entre os módulos")
    void sem_ciclos_entre_modulos() {
        slices()
                .matching("com.caiocodes.crbap.(*)..")
                .should().beFreeOfCycles()
                .check(classesDoProjeto);
    }

    @Test
    @DisplayName("ninguém lê o relógio pela estática — o Clock é injetado")
    void nada_de_relogio_estatico() {
        noClasses()
                .should().callMethod(System.class, "currentTimeMillis")
                .because("com Clock injetado, testar data é passar um argumento")
                .check(classesDoProjeto);
    }
}
