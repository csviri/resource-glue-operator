package io.csviri.operator.workflow.dependent;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.stream.Collectors;

import io.csviri.operator.workflow.Utils;
import io.csviri.operator.workflow.customresource.workflow.Workflow;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;

import static io.csviri.operator.workflow.WorkflowOperatorReconciler.*;

public class GenericDependentResource
    extends GenericKubernetesDependentResource<Workflow> implements GarbageCollected<Workflow>,
    Updater<GenericKubernetesResource, Workflow>,
    Creator<GenericKubernetesResource, Workflow> {

  private static ObjectMapper objectMapper = new ObjectMapper();
  private static MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  private GenericKubernetesResource desired;

  public GenericDependentResource(GenericKubernetesResource desired) {
    super(new GroupVersionKind(desired.getApiVersion(), desired.getKind()));
    this.desired = desired;
  }

  @Override
  protected GenericKubernetesResource desired(Workflow primary,
      Context<Workflow> context) {

    // todo how do I know if a resource is namespaced or not => explicit flag / and check fabric8

    if (desired.getMetadata().getNamespace() == null) {
      desired.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    }


    var template = Serialization.asYaml(desired);
    // this can be precompiled
    var mustache = mustacheFactory.compile(new StringReader(template), "desired");
    // convert GKR to Map for better access ?
    var actualResourcesByName = Utils.getActualResourcesByName(context, primary);
    var mustacheContext = actualResourcesByName.entrySet().stream().collect(Collectors
        .toMap(Map.Entry::getKey, e -> objectMapper.convertValue(e.getValue(), Map.class)));
    addPrimaryResourceOfOperatorIfAvailable(context, primary, mustacheContext);

    var res = mustache.execute(new StringWriter(), mustacheContext);

    var resultDesired = Serialization.unmarshal(res.toString(), GenericKubernetesResource.class);
    return resultDesired;

    // return desired;
  }

  private void addPrimaryResourceOfOperatorIfAvailable(Context<Workflow> context,
      Workflow primary,
      Map<String, Map> mustacheContext) {
    var annotations = primary.getMetadata().getAnnotations();
    if (!annotations.containsKey(WATCH_GROUP)) {
      return;
    }

    GroupVersionKind gvk =
        new GroupVersionKind(annotations.get(WATCH_GROUP),
            annotations.get(WATCH_VERSION), annotations.get(WATCH_KIND));

    InformerEventSource<GenericKubernetesResource, Workflow> is = null;
    try {
      is = (InformerEventSource<GenericKubernetesResource, Workflow>) context.eventSourceRetriever()
          .getResourceEventSourceFor(GenericKubernetesResource.class, gvk.toString());
    } catch (IllegalArgumentException e) {
      // was not able to find es
    }
    if (is != null) {
      var resource =
          is.get(new ResourceID(annotations.get(WATCH_NAME), annotations.get(WATCH_NAMESPACE)));
      resource.ifPresent(r -> {
        mustacheContext.put("primary", objectMapper.convertValue(r, Map.class));
      });

      // GroupVersionKind gvk =
      // new GroupVersionKind(annotations.get(WATCH_GROUP),
      // annotations.get(WATCH_VERSION), annotations.get(WATCH_KIND));
      // var secondaryResources = context.getSecondaryResources(GenericKubernetesResource.class);
      // var target = secondaryResources.stream().filter(r ->
      // r.getApiVersion().equals(gvk.apiVersion())
      // && r.getKind().equals(gvk.getKind())
      // && Objects.equals(r.getMetadata().getName(), annotations.get(WATCH_NAME))
      // && Objects.equals(r.getMetadata().getNamespace(), annotations.get(WATCH_NAMESPACE)))
      // .findFirst();
    }
  }

  @Override
  public Result<GenericKubernetesResource> match(GenericKubernetesResource actualResource,
      Workflow primary, Context<Workflow> context) {
    return Result.nonComputed(false);
  }
}