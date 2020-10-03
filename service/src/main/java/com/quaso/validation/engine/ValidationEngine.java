package com.quaso.validation.engine;

import com.quaso.validation.engine.config.ApiScannerConfiguration;
import com.quaso.validation.engine.config.ClassDetails;
import com.quaso.validation.engine.config.FieldDetails;
import com.quaso.validation.engine.config.ValidationConfigService;
import com.quaso.validation.engine.config.ValidationContext;
import com.quaso.validation.engine.config.model.ClassConfiguration;
import com.quaso.validation.engine.config.model.FieldConfiguration;
import com.quaso.validation.engine.config.model.RuleConfiguration;
import com.quaso.validation.engine.config.properties.ValidationProperties;
import com.quaso.validation.exception.NoConfigException;
import com.quaso.validation.exception.ValidationFailFastException;
import com.quaso.validation.utils.ReflectionUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationEngine {

    private final ValidationProperties validationProperties;
    private final ValidationConfigService validationConfigService;
    private final ApiScannerConfiguration apiScannerConfiguration;

    public List<ValidationErrorResponse> validate(final Object request, final HttpMethod httpMethod,
        final String allianceCode, final String partnerCode) {
        final Class<?> requestType = request.getClass();
        final ClassDetails classDetails = apiScannerConfiguration.findConfigByRequestType(requestType);
        if (classDetails == null) {
            // request class is not recognized in ValidationApi
            throw new IllegalArgumentException(
                "Request type " + requestType.getCanonicalName() + " is not known and cannot be validated");
        }
        try {
            final List<ClassConfiguration> classConfigurationList = validationConfigService
                .findClassConfigurationList(request.getClass().getSimpleName(), httpMethod, allianceCode, partnerCode);
            // iterate through request and identify classes
            final ValidationContext validationContext = new ValidationContext(httpMethod, request);
            return validate(validationContext, classDetails, null, classConfigurationList);
        } catch (final NoConfigException ex) {
            return Collections.emptyList();
        } catch (final ValidationFailFastException ex) {
            if (ex.getValidationErrorResponse().getError().getCode() == ValidationErrorCode.UnexpectedError) {
                log.error("Unexpected validation error", ex);
            }
            return Collections.singletonList(ex.getValidationErrorResponse());
        }
    }

    private List<ValidationErrorResponse> validate(final ValidationContext validationContext,
        final ClassDetails classDetails, final FieldDetails fieldDetails,
        final List<ClassConfiguration> classConfigurationList) throws ValidationFailFastException {
        final Object classInstance = validationContext.getHierarchyStack().peek();
        if (classInstance == null) {
            return Collections.emptyList();
        }
        final List<ValidationErrorResponse> result = new ArrayList<>();

        log.debug("{}Validating class {}", indentLog(validationContext.size() - 1),
            classInstance.getClass().getSimpleName());

        try {
            // find class validation configuration
            final ClassConfiguration classConfiguration;
            if (fieldDetails == null) {
                classConfiguration = validationConfigService
                    .findClassConfiguration(classInstance.getClass(), classConfigurationList);
            } else {
                classConfiguration = validationConfigService
                    .findClassConfiguration(fieldDetails, classConfigurationList);
            }

            // validate rules for the class
            validateClassRules(validationContext, classInstance, result, classConfiguration);

            // validate rule for the fields in class
            validateFieldRules(validationContext, classInstance, result, classConfiguration);
        } catch (final NoConfigException ex) {
            // ignore
            log.debug("{}No config", indentLog(validationContext.size()));
        }

        // validate sub-classes
        validateSubclasses(validationContext, classDetails, classConfigurationList, classInstance, result);

        log.debug("{}Validation class {} finished", indentLog(validationContext.size() - 1, true),
            classInstance.getClass().getSimpleName());

        return result;
    }

    private void validateClassRules(final ValidationContext validationContext, final Object classInstance,
        final List<ValidationErrorResponse> result, final ClassConfiguration classConfiguration)
        throws ValidationFailFastException {
        final List<RuleConfiguration> classRuleConfigurationList = classConfiguration
            .getClassRuleConfigurationList();
        if (classRuleConfigurationList != null) {
            for (int i = 0; i < classRuleConfigurationList.size(); i++) {
                final RuleConfiguration ruleConfiguration = classRuleConfigurationList.get(i);
                log.debug("{}Validating class rule {}: {}", indentLog(validationContext.size() + 2), i + 1,
                    ruleConfiguration.getLogString());
                final Optional<ValidationErrorResponse> validationResult = validateRule(ruleConfiguration,
                    classInstance, validationContext, classInstance, null);
                if (validationResult.isPresent()) {
                    result.add(validationResult.get());
                }
            }
        }
    }

    private void validateFieldRules(final ValidationContext validationContext, final Object classInstance,
        final List<ValidationErrorResponse> result, final ClassConfiguration classConfiguration)
        throws ValidationFailFastException {
        final List<FieldConfiguration> fieldConfigurationList = classConfiguration.getFieldConfigurationList();
        if (fieldConfigurationList != null) {
            // validate fields in class
            for (final FieldConfiguration fieldConfiguration : fieldConfigurationList) {
                final Object fieldValue = ReflectionUtils
                    .getFieldValue(classInstance, fieldConfiguration.getName());
                log.debug("{}Validating field {}", indentLog(validationContext.size()),
                    fieldConfiguration.getName());
                for (int i = 0; i < fieldConfiguration.getRuleConfigurationList().size(); i++) {
                    final RuleConfiguration ruleConfiguration = fieldConfiguration.getRuleConfigurationList()
                        .get(i);
                    if (StringUtils.isNotEmpty(ruleConfiguration.getInput())) {
                        throw new IllegalStateException(
                            "Rule configuration mismatch. Cannot define 'input' field here");
                    }
                    log.debug("{}Validating rule {}: {}", indentLog(validationContext.size() + 2), i + 1,
                        ruleConfiguration.getLogString());
                    final Optional<ValidationErrorResponse> validationResult = validateRule(ruleConfiguration,
                        fieldValue, validationContext, classInstance, fieldConfiguration);
                    if (validationResult.isPresent()) {
                        result.add(validationResult.get());
                    }
                }
            }
        }
    }

    private void validateSubclasses(final ValidationContext validationContext, final ClassDetails classDetails,
        final List<ClassConfiguration> classConfigurationList, final Object classInstance,
        final List<ValidationErrorResponse> result) throws ValidationFailFastException {
        for (final Entry<FieldDetails, ClassDetails> entry : classDetails.getFieldIds().entrySet()) {
            final Object value = ReflectionUtils.getFieldValue(classInstance, entry.getKey().getField());
            final List<?> collection;
            if (value instanceof List) {
                collection = (List<?>) value;
            } else {
                collection = Collections.singletonList(value);
            }
            for (int i = 0; i < collection.size(); i++) {
                final Integer positionInList = (value instanceof List) ? (i + 1) : null;
                if (positionInList != null) {
                    log.debug("{}Field {} element {}", indentLog(validationContext.size() + 1),
                        entry.getKey().getField().getName(), positionInList);
                }
                validationContext.getHierarchyStack().push(collection.get(i));
                try {
                    final List<ValidationErrorResponse> validationResult = validate(validationContext, entry.getValue(),
                        entry.getKey(), classConfigurationList);
                    validationResult.forEach(ver -> ver.setFieldPath(
                        enhanceValidationErrorMessage(classInstance, ver.getFieldPath(), positionInList)));
                    result.addAll(validationResult);
                } catch (final ValidationFailFastException ex) {
                    ex.getValidationErrorResponse().setFieldPath(
                        enhanceValidationErrorMessage(classInstance, ex.getValidationErrorResponse().getFieldPath(),
                            positionInList));
                    throw ex;
                } finally {
                    validationContext.getHierarchyStack().pop();
                }
            }
        }
    }

    private Optional<ValidationErrorResponse> validateRule(final RuleConfiguration ruleConfiguration,
        final Object value, final ValidationContext validationContext, final Object classInstance,
        final FieldConfiguration fieldConfiguration) throws ValidationFailFastException {
        final Optional<ValidationErrorResponse> validationResult = ruleConfiguration.validate(value, validationContext);
        log.trace("{}Validation result is present: {}", indentLog(validationContext.size() + 2),
            validationResult.isPresent());
        ValidationErrorResponse result = null;
        if (validationResult.isPresent()) {
            result = validationResult.get();
            final String fieldPath;
            if (fieldConfiguration == null) {
                fieldPath = classInstance.getClass().getSimpleName();
            } else {
                fieldPath = String
                    .format("%s.%s", classInstance.getClass().getSimpleName(), fieldConfiguration.getName());
            }
            log.debug("{}Validation failure message: {} {}", indentLog(validationContext.size() + 2),
                fieldPath, validationResult.get().getFullMessage());

            result.setFieldPath(fieldPath);
            if (validationProperties.isFailFast()
                || validationResult.get().getError().getCode() == ValidationErrorCode.UnexpectedError) {
                throw new ValidationFailFastException(result);
            }
        }
        return Optional.ofNullable(result);
    }

    private String enhanceValidationErrorMessage(final Object obj, final String msg, final Integer positionInList) {
        if (positionInList == null) {
            return String.format("%s.%s", obj.getClass().getSimpleName(), msg);
        } else {
            return String.format("%s.[%d]%s", obj.getClass().getSimpleName(), positionInList, msg);
        }
    }

    private String indentLog(final int size) {
        return indentLog(size, false);
    }

    private String indentLog(final int size, final boolean finishLevel) {
        final String result;
        if (finishLevel) {
            result = String.format("%s\\- ", StringUtils.repeat("|  ", size));
        } else {
            result = String.format("%s+- ", StringUtils.repeat("|  ", size));
        }
        return result;
    }
}
