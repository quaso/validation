package com.quaso.validation.engine.config;

import com.quaso.validation.engine.config.properties.ValidationProperties;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * This class is responsible for scanning classpath for detecting request type and classes to be validated.
 */
@Component
@RequiredArgsConstructor
public class ApiScannerConfiguration {

    private final ValidationProperties validationProperties;

    // key is request type
    private final Map<Class<?>, ClassDetails> knownClasses = new HashMap<>();

    @PostConstruct
    public void scanValidationApi() {
        final Method[] declaredMethods = ValidationApi.class.getDeclaredMethods();
        for (final Method declaredMethod : declaredMethods) {
            if (declaredMethod.getAnnotation(RequestMapping.class) == null) {
                continue;
            }
            final Class parameterType = Arrays.stream(declaredMethod.getParameters())
                .filter(p -> p.getAnnotation(RequestBody.class) != null)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                    "Method " + declaredMethod.getName() + " does not have parameter with RequestBody"))
                .getType();
            scanRequestType(parameterType);
        }
    }

    private void scanRequestType(final Class<?> requestType) {
        final ClassDetails classDetails = scanClass(requestType, new HashMap<>());
        knownClasses.put(requestType, classDetails);
    }

    private ClassDetails scanClass(final Class<?> classType, Map<Class<?>, ClassDetails> scannedClasses) {
        final ClassDetails result = new ClassDetails();
        scannedClasses.put(classType, result);
        // use recursion to scan fields
        Arrays.stream(classType.getDeclaredFields())
            .map(field -> FieldDetails.builder()
                .field(field)
                .parentFieldName(field.getName())
                .build())
            .filter(fieldId -> isPackageToScan(fieldId.getFieldGenericClass()))
            .forEach(fieldId -> {
                Class<?> scanCandidate = fieldId.getFieldGenericClass();
                if (scannedClasses.containsKey(scanCandidate)) {
                    result.registerField(fieldId, scannedClasses.get(scanCandidate));
                } else {
                    final ClassDetails fieldClassDetails = scanClass(scanCandidate, scannedClasses);
                    result.registerField(fieldId, fieldClassDetails);
                }
            });
        return result;
    }


    private boolean isPackageToScan(final Class<?> type) {
        final String packageName = type.getPackage().getName();
        for (final String pckg : validationProperties.getPackagesToScan()) {
            if (packageName.startsWith(pckg)) {
                return true;
            }
        }
        return false;
    }

    public ClassDetails findConfigByRequestType(final Class<?> type) {
        return knownClasses.get(type);
    }

    public Map<Class<?>, ClassDetails> getKnownClasses() {
        return knownClasses;
    }
}
