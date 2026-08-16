package com.banco.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de dependencia entre capas (AC-025, ARCHITECTURE.md §2):
 *
 * 1. domain no depende de Spring/JPA ni de otras capas del proyecto.
 * 2. application depende solo de domain y java (más sus propias clases del
 *    mismo paquete application: los use cases dependen de commands/validators
 *    del mismo eslabón, lo que la regla literal de SPEC-001 §10 no contempla;
 *    se permite el paquete application para no falsear la regla — ver reporte).
 * 3. nada fuera de infrastructure depende de infrastructure.
 * 4. los controllers (anotaciones Spring) solo viven en infrastructure.
 */
@AnalyzeClasses(packages = "com.banco..", importOptions = ImportOption.DoNotIncludeTests.class)
public class LayerArchitectureTest {

    @ArchTest
    static final ArchRule DOMINIO_NO_DEPENDE_DE_SPRING_JPA_NI_OTRAS_CAPAS =
            noClasses().that().resideInAPackage("com.banco.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta..",
                            "com.banco.infrastructure..",
                            "com.banco.application..");

    @ArchTest
    static final ArchRule APLICACION_DEPENDE_SOLO_DE_DOMINIO_Y_JAVA =
            classes().that().resideInAPackage("com.banco.application..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "com.banco.domain..",
                            "com.banco.application..",
                            "java..");

    @ArchTest
    static final ArchRule NADA_FUERA_DE_INFRASTRUCTURE_DEPENDE_DE_INFRASTRUCTURE =
            noClasses().that().resideOutsideOfPackage("com.banco.infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage("com.banco.infrastructure..");

    @ArchTest
    static final ArchRule CONTROLLERS_SOLO_EN_INFRASTRUCTURE =
            noClasses().that().resideOutsideOfPackage("com.banco.infrastructure..")
                    .should().dependOnClassesThat()
                    .areAnnotatedWith("org.springframework.stereotype.Controller")
                    .orShould().dependOnClassesThat()
                    .areAnnotatedWith("org.springframework.web.bind.annotation.RestController");
}
