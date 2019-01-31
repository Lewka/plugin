package io.eroshenkoam.idea;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import io.eroshenkoam.idea.util.PsiUtils;

import java.util.*;
import java.util.stream.Collectors;

import static io.eroshenkoam.idea.Annotations.*;
import static io.eroshenkoam.idea.util.PsiUtils.createAnnotation;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
public class MigrateToJunit5 extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        final PsiElement element = event.getData(PlatformDataKeys.PSI_ELEMENT);

        for (Module module : ModuleManager.getInstance(element.getProject()).getSortedModules()) {
            List<PsiClass> testClasses = AllClassesSearch.search(GlobalSearchScope.moduleScope(module), element.getProject()).findAll()
                    .stream()
                    .filter(psiClass -> {
                        try {
                            return psiClass.getName().endsWith("Test");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            for (PsiClass testClass : testClasses) {
                if (testClass != null) {
                    Arrays.stream(testClass.getMethods())
                            .filter(m -> m.hasAnnotation(SERENITY_WITH_TAGS_ANNOTATION))
                            .forEach(this::migrateTestAnnotations);
                }
            }
        }
    }

    private void migrateTestAnnotations(final PsiMethod testMethod) {
        System.out.println("Working on : " + testMethod.getName());
        migrateTags(testMethod);
        migrateDisplayName(testMethod);
        migrateTmsLink(testMethod);
        System.out.println("Done with : " + testMethod.getName());
    }

    private void migrateTags(final PsiMethod testMethod) {
        Optional.ofNullable(testMethod.getAnnotation(SERENITY_WITH_TAGS_ANNOTATION)).ifPresent(serenityAnnotation -> {
            final PsiArrayInitializerMemberValue value = (PsiArrayInitializerMemberValue) serenityAnnotation
                    .findDeclaredAttributeValue("value");

            final List<String> tags = Arrays.stream(value.getInitializers())
                    .map(PsiAnnotationMemberValue::getText)
                    .collect(Collectors.toList());

            String formattedTags = tags.stream()
                    .map(tag -> String.format("@%s(%s)", JUNIT_5_TAG_ANNOTATION, tag))
                    .collect(Collectors.joining(", "));

            final String tmsLinkText = String.format("@%s({%s})", JUNIT_5_TAGS_ANNOTATION, formattedTags);
            final PsiAnnotation junit5TagsAnnotation = createAnnotation(tmsLinkText, testMethod);
            final Project project = testMethod.getProject();
            CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
                PsiUtils.addImport(testMethod.getContainingFile(), JUNIT_5_TAGS_ANNOTATION);

                testMethod.getModifierList().addAfter(junit5TagsAnnotation, serenityAnnotation);
                serenityAnnotation.delete();

                PsiUtils.optimizeImports(testMethod.getContainingFile());
            }), "Migrate Junit5 tags", null);
        });
    }

    private void migrateDisplayName(final PsiMethod testMethod) {
        Optional.ofNullable(testMethod.getAnnotation(SERENITY_TITLE_ANNOTATION)).ifPresent(serenityTitleAnnotation -> {
            String value = serenityTitleAnnotation.findDeclaredAttributeValue("value").getText();

            final String featuresText = String.format("@%s(%s)", JUNIT_5_DISPLAY_NAME_ANNOTATION, value);
            final PsiAnnotation junitDisplayAnnotation = createAnnotation(featuresText, testMethod);

            final Project project = testMethod.getProject();
            CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
                PsiUtils.addImport(testMethod.getContainingFile(), JUNIT_5_DISPLAY_NAME_ANNOTATION);

                testMethod.getModifierList().addAfter(junitDisplayAnnotation, serenityTitleAnnotation);
                serenityTitleAnnotation.delete();

                PsiUtils.optimizeImports(testMethod.getContainingFile());
            }), "Migrate Junit5 display name", null);
        });
    }

    private void migrateTmsLink(final PsiMethod testMethod) {
        Optional.ofNullable(testMethod.getAnnotation(SERENITY_ISSUE_ANNOTATION)).ifPresent(serenityIssueAnnotation -> {
            String value = serenityIssueAnnotation.findDeclaredAttributeValue("value").getText();

            final String featuresText = String.format("@%s(%s)", ALLURE2_TMS_LINK_ANNOTATION, value);
            final PsiAnnotation junitDisplayAnnotation = createAnnotation(featuresText, testMethod);

            final Project project = testMethod.getProject();
            CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
                PsiUtils.addImport(testMethod.getContainingFile(), ALLURE2_TMS_LINK_ANNOTATION);

                testMethod.getModifierList().addAfter(junitDisplayAnnotation, serenityIssueAnnotation);
                serenityIssueAnnotation.delete();

                PsiUtils.optimizeImports(testMethod.getContainingFile());
            }), "Migrate Allure TMSLink annotation", null);
        });
    }

    private String getStoriesAnnotationText(final List<String> features) {
        final String body = features.stream()
                .map(label -> String.format("@%s(%s)", ALLURE2_STORY_ANNOTATION, label))
                .collect(Collectors.joining(","));
        return String.format("@%s({%s})", ALLURE2_STORIES_ANNOTATION, body);
    }

    private String convert(final String stepValue, final String[] params) {
        String result = stepValue;
        for (int i = 0; i < params.length; i++) {
            result = result.replace("{" + i + "}", "{" + params[i] + "}");
        }
        return result;
    }
}