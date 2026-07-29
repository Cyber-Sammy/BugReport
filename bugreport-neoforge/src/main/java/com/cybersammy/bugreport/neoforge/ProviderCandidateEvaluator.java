package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.BugReportProvider;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Objects;
import net.neoforged.neoforgespi.language.IModInfo;

final class ProviderCandidateEvaluator {
    private final ClassLoader apiClassLoader;

    ProviderCandidateEvaluator(ClassLoader apiClassLoader) {
        this.apiClassLoader = Objects.requireNonNull(apiClassLoader);
    }

    Evaluation evaluate(IModInfo modInfo, String className) {
        Class<?> type;
        try {
            type = Class.forName(className, false, apiClassLoader);
        } catch (ClassNotFoundException exception) {
            return rejected(
                    ProviderDiagnosticCode.MISSING_CLASS,
                    modInfo,
                    className);
        } catch (LinkageError | RuntimeException exception) {
            return rejected(
                    ProviderDiagnosticCode.CLASS_LOAD_FAILED,
                    modInfo,
                    className);
        }

        if (!Objects.equals(
                modInfo.getOwningFile().moduleName(),
                type.getModule().getName())) {
            return rejected(
                    ProviderDiagnosticCode.OWNERSHIP_MISMATCH,
                    modInfo,
                    className);
        }
        if (!BugReportProvider.class.isAssignableFrom(type)) {
            return rejected(
                    ProviderDiagnosticCode.INVALID_TYPE,
                    modInfo,
                    className);
        }
        if (!Modifier.isPublic(type.getModifiers())) {
            return rejected(
                    ProviderDiagnosticCode.INACCESSIBLE_CLASS,
                    modInfo,
                    className);
        }
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return rejected(
                    ProviderDiagnosticCode.ABSTRACT_TYPE,
                    modInfo,
                    className);
        }

        Class<? extends BugReportProvider> providerType =
                type.asSubclass(BugReportProvider.class);
        Constructor<? extends BugReportProvider> constructor;
        try {
            constructor = providerType.getConstructor();
        } catch (NoSuchMethodException | SecurityException exception) {
            return rejected(
                    ProviderDiagnosticCode.MISSING_CONSTRUCTOR,
                    modInfo,
                    className);
        }

        return Evaluation.accepted(
                new ProviderCandidate(
                        modInfo.getModId(),
                        className,
                        constructor));
    }

    private static Evaluation rejected(
            ProviderDiagnosticCode code,
            IModInfo modInfo,
            String className) {
        return Evaluation.rejected(
                ProviderDiagnostic.forClass(code, modInfo.getModId(), className));
    }

    record Evaluation(
            ProviderCandidate candidate,
            ProviderDiagnostic diagnostic) {
        Evaluation {
            if ((candidate == null) == (diagnostic == null)) {
                throw new IllegalArgumentException(
                        "Exactly one evaluation outcome is required");
            }
        }

        static Evaluation accepted(ProviderCandidate candidate) {
            return new Evaluation(Objects.requireNonNull(candidate), null);
        }

        static Evaluation rejected(ProviderDiagnostic diagnostic) {
            return new Evaluation(null, Objects.requireNonNull(diagnostic));
        }
    }
}
