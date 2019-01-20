package io.eroshenkoam.idea;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.jvm.JvmNamedElement;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import io.eroshenkoam.idea.util.PsiUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
        if (element instanceof PsiClass) {
            Arrays.stream(((PsiClass) element).getMethods())
                    .filter(m -> m.hasAnnotation(SERENITY_WITH_TAGS_ANNOTATION))
                    .forEach(this::migrateTestAnnotations);
        }
    }

    private void migrateTestAnnotations(final PsiMethod testMethod) {
        migrateTags(testMethod);
        migrateDisplayName(testMethod);
//        migrateStoriesAnnotation(testMethod);
//        migrateStepAnnotation(testMethod);
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

    private void migrateStoriesAnnotation(final PsiMethod testMethod) {
        Optional.ofNullable(testMethod.getAnnotation(ALLURE1_STORIES_ANNOTATION)).ifPresent(oldStoriesAnnotation -> {
            final PsiArrayInitializerMemberValue value = (PsiArrayInitializerMemberValue) oldStoriesAnnotation
                    .findDeclaredAttributeValue("value");

            final List<String> stories = Arrays.stream(value.getInitializers())
                    .map(PsiAnnotationMemberValue::getText)
                    .collect(Collectors.toList());

            final String storiesText = getStoriesAnnotationText(stories);
            final PsiAnnotation storiesAnnotation = createAnnotation(storiesText, testMethod);

            final Project project = testMethod.getProject();
            CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
                PsiUtils.addImport(testMethod.getContainingFile(), ALLURE2_STORY_ANNOTATION);
                PsiUtils.addImport(testMethod.getContainingFile(), ALLURE2_STORIES_ANNOTATION);

                testMethod.getModifierList().addAfter(storiesAnnotation, oldStoriesAnnotation);
                oldStoriesAnnotation.delete();

                PsiUtils.optimizeImports(testMethod.getContainingFile());
            }), "Migrate Allure Stories", null);
        });
    }

    private void migrateStepAnnotation(final PsiMethod testMethod) {
        Optional.ofNullable(testMethod.getAnnotation(ALLURE1_STEP_ANNOTATION)).ifPresent(oldStepAnnotation -> {
            final String oldStepValue = AnnotationUtil.getDeclaredStringAttributeValue(oldStepAnnotation, "value");

            final String[] params = Arrays.stream(testMethod.getParameters())
                    .map(JvmNamedElement::getName)
                    .toArray(String[]::new);

            final String stepValue = convert(oldStepValue, params);
            final String stepText = String.format("@%s(\"%s\")", ALLURE2_STEP_ANNOTATION, stepValue);

            final PsiAnnotation stepAnnotation = createAnnotation(stepText, testMethod);

            final Project project = testMethod.getProject();
            CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
                PsiUtils.addImport(testMethod.getContainingFile(), ALLURE2_STEP_ANNOTATION);
                testMethod.getModifierList().addAfter(stepAnnotation, oldStepAnnotation);
                oldStepAnnotation.delete();
                PsiUtils.optimizeImports(testMethod.getContainingFile());
            }), "Migrate Allure Steps", null);
        });
    }

    private String getFeaturesAnnotationText(final List<String> features) {
        final String body = features.stream()
                .map(label -> String.format("@%s(%s)", JUNIT_5_DISPLAY_NAME_ANNOTATION, label))
                .collect(Collectors.joining(","));
        return String.format("@%s({%s})", ALLURE2_FEATURES_ANNOTATION, body);
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
