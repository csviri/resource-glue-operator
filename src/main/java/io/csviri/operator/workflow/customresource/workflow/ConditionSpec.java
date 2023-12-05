package io.csviri.operator.workflow.customresource.workflow;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PodsReadyConditionSpec.class, name = "PodsReady"),
    @JsonSubTypes.Type(value = JavaScriptConditionSpec.class, name = "JSCondition")
})
public class ConditionSpec {


}
