// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.analysis.util;

import static com.google.devtools.build.lib.packages.Attribute.ConfigurationTransition.HOST;
import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.packages.BuildType.NODEP_LABEL_LIST;
import static com.google.devtools.build.lib.syntax.Type.BOOLEAN;
import static com.google.devtools.build.lib.syntax.Type.STRING;
import static com.google.devtools.build.lib.syntax.Type.STRING_LIST;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.ConfiguredAspect;
import com.google.devtools.build.lib.analysis.ConfiguredAspectFactory;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetBuilder;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetFactory;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.analysis.Runfiles;
import com.google.devtools.build.lib.analysis.RunfilesProvider;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.configuredtargets.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.LabelSyntaxException;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.AspectDefinition;
import com.google.devtools.build.lib.packages.AspectParameters;
import com.google.devtools.build.lib.packages.Attribute.LateBoundDefault;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.packages.NativeAspectClass;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.packages.SkylarkProviderIdentifier;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.FileTypeSet;
import java.util.List;

/**
 * Various rule and aspect classes that aid in testing the aspect machinery.
 *
 * <p>These are mostly used in {@link com.google.devtools.build.lib.analysis.DependencyResolverTest}
 * and {@link com.google.devtools.build.lib.analysis.AspectTest}.
 */
public class TestAspects {

  /**
   * A transitive info provider for collecting aspects in the transitive closure. Created by
   * aspects.
   */
  @Immutable
  public static final class AspectInfo implements TransitiveInfoProvider {
    private final NestedSet<String> data;

    public AspectInfo(NestedSet<String> data) {
      this.data = data;
    }

    public NestedSet<String> getData() {
      return data;
    }
  }

  /**
   * A transitive info provider used as sentinel. Created by aspects.
   */
  @Immutable
  public static final class FooProvider implements TransitiveInfoProvider {
  }

  /**
   * A transitive info provider used as sentinel. Created by aspects.
   */
  @Immutable
  public static final class BarProvider implements TransitiveInfoProvider {
  }

  /**
   * A transitive info provider for collecting aspects in the transitive closure. Created by
   * rules.
   */
  @Immutable
  public static final class RuleInfo implements TransitiveInfoProvider {
    private final NestedSet<String> data;

    public RuleInfo(NestedSet<String> data) {
      this.data = data;
    }

    public NestedSet<String> getData() {
      return data;
    }
  }

  /**
   * A very simple provider used in tests that check whether the logic that attaches aspects
   * depending on whether a configured target has a provider works or not.
   */
  @Immutable
  public static final class RequiredProvider implements TransitiveInfoProvider {
  }

  /**
   * Another very simple provider used in tests that check whether the logic that attaches aspects
   * depending on whether a configured target has a provider works or not.
   */
  @Immutable
  public static final class RequiredProvider2 implements TransitiveInfoProvider {
  }

  private static NestedSet<String> collectAspectData(String me, RuleContext ruleContext) {
    NestedSetBuilder<String> result = new NestedSetBuilder<>(Order.STABLE_ORDER);
    result.add(me);

    Iterable<String> attributeNames = ruleContext.attributes().getAttributeNames();
    for (String attributeName : attributeNames) {
      Type<?> attributeType = ruleContext.attributes().getAttributeType(attributeName);
      if (!LABEL.equals(attributeType) && !LABEL_LIST.equals(attributeType)) {
        continue;
      }
      Iterable<AspectInfo> prerequisites = ruleContext
          .getPrerequisites(attributeName, Mode.DONT_CHECK, AspectInfo.class);
      for (AspectInfo prerequisite : prerequisites) {
        result.addTransitive(prerequisite.getData());
      }
    }
    return result.build();
  }

  /**
   * A simple rule configured target factory that is used in all the mock rules in this class.
   */
  public static class DummyRuleFactory implements RuleConfiguredTargetFactory {
    @Override
    public ConfiguredTarget create(RuleContext ruleContext) throws InterruptedException {

      RuleConfiguredTargetBuilder builder =
          new RuleConfiguredTargetBuilder(ruleContext)
              .addProvider(
                  new RuleInfo(collectAspectData("rule " + ruleContext.getLabel(), ruleContext)))
              .setFilesToBuild(NestedSetBuilder.<Artifact>create(Order.STABLE_ORDER))
              .setRunfilesSupport(null, null)
              .add(RunfilesProvider.class, RunfilesProvider.simple(Runfiles.EMPTY));

      if (ruleContext.getRule().getRuleClassObject().getName().equals("honest")) {
        builder.addProvider(new RequiredProvider());
      }

      return builder.build();
    }
  }

  /**
   * A simple rule configured target factory that exports provider {@link RequiredProvider2}.
   */
  public static class DummyRuleFactory2 implements RuleConfiguredTargetFactory {
    @Override
    public ConfiguredTarget create(RuleContext ruleContext) throws InterruptedException {
      return new RuleConfiguredTargetBuilder(ruleContext)
              .addProvider(
                  new RuleInfo(collectAspectData("rule " + ruleContext.getLabel(), ruleContext)))
              .setFilesToBuild(NestedSetBuilder.<Artifact>create(Order.STABLE_ORDER))
              .setRunfilesSupport(null, null)
              .add(RunfilesProvider.class, RunfilesProvider.simple(Runfiles.EMPTY))
              .addProvider(new RequiredProvider())
              .addProvider(new RequiredProvider2())
              .build();
    }
  }

  /**
   * A simple rule configured target factory that expects different providers added through
   * different aspects.
   */
  public static class MultiAspectRuleFactory implements RuleConfiguredTargetFactory {
    @Override
    public ConfiguredTarget create(RuleContext ruleContext) throws InterruptedException {
      TransitiveInfoCollection fooAttribute = ruleContext.getPrerequisite("foo", Mode.DONT_CHECK);
      TransitiveInfoCollection barAttribute = ruleContext.getPrerequisite("bar", Mode.DONT_CHECK);

      NestedSetBuilder<String> infoBuilder = NestedSetBuilder.<String>stableOrder();

      if (fooAttribute.getProvider(FooProvider.class) != null) {
        infoBuilder.add("foo");
      }
      if (barAttribute.getProvider(BarProvider.class) != null) {
        infoBuilder.add("bar");
      }

      RuleConfiguredTargetBuilder builder =
          new RuleConfiguredTargetBuilder(ruleContext)
              .addProvider(
                  new RuleInfo(infoBuilder.build()))
              .setFilesToBuild(NestedSetBuilder.<Artifact>create(Order.STABLE_ORDER))
              .setRunfilesSupport(null, null)
              .add(RunfilesProvider.class, RunfilesProvider.simple(Runfiles.EMPTY));

      return builder.build();
    }
  }

  /**
   * A base class for mock aspects to reduce boilerplate.
   */
  public abstract static class BaseAspect extends NativeAspectClass
    implements ConfiguredAspectFactory {
    @Override
    public ConfiguredAspect create(
        ConfiguredTarget base, RuleContext ruleContext, AspectParameters parameters) {
      String information = parameters.isEmpty()
          ? ""
          : " data " + Iterables.getFirst(parameters.getAttribute("baz"), null);
      return new ConfiguredAspect.Builder(this, parameters, ruleContext)
          .addProvider(
              new AspectInfo(
                  collectAspectData("aspect " + ruleContext.getLabel() + information, ruleContext)))
          .build();
    }
  }

  public static final SimpleAspect SIMPLE_ASPECT = new SimpleAspect();
  public static final FooProviderAspect FOO_PROVIDER_ASPECT = new FooProviderAspect();
  public static final BarProviderAspect BAR_PROVIDER_ASPECT = new BarProviderAspect();

  private static final AspectDefinition SIMPLE_ASPECT_DEFINITION =
      new AspectDefinition.Builder(SIMPLE_ASPECT).build();

  private static final AspectDefinition FOO_PROVIDER_ASPECT_DEFINITION =
      new AspectDefinition.Builder(FOO_PROVIDER_ASPECT).build();
  private static final AspectDefinition BAR_PROVIDER_ASPECT_DEFINITION =
      new AspectDefinition.Builder(BAR_PROVIDER_ASPECT).build();

  /**
   * A very simple aspect.
   */
  public static class SimpleAspect extends BaseAspect {
    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return SIMPLE_ASPECT_DEFINITION;
    }
  }

  /**
   * A simple aspect that propagates a FooProvider provider.
   */
  public static class FooProviderAspect extends NativeAspectClass
      implements ConfiguredAspectFactory {
    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return FOO_PROVIDER_ASPECT_DEFINITION;
    }

    @Override
    public ConfiguredAspect create(
        ConfiguredTarget base, RuleContext ruleContext, AspectParameters parameters) {
      return new ConfiguredAspect.Builder(this, parameters, ruleContext)
          .addProvider(new FooProvider())
          .build();
    }
  }

  /**
   * A simple aspect that propagates a BarProvider provider.
   */
  public static class BarProviderAspect extends NativeAspectClass
      implements ConfiguredAspectFactory{
    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return BAR_PROVIDER_ASPECT_DEFINITION;
    }

    @Override
    public ConfiguredAspect create(
        ConfiguredTarget base, RuleContext ruleContext, AspectParameters parameters) {
      return new ConfiguredAspect.Builder(this, parameters, ruleContext)
          .addProvider(new BarProvider())
          .build();
    }
  }

  public static final ExtraAttributeAspect EXTRA_ATTRIBUTE_ASPECT = new ExtraAttributeAspect();
  private static final AspectDefinition EXTRA_ATTRIBUTE_ASPECT_DEFINITION =
      new AspectDefinition.Builder(EXTRA_ATTRIBUTE_ASPECT)
          .add(attr("$dep", LABEL).value(Label.parseAbsoluteUnchecked("//extra:extra")))
          .build();

  private static final ExtraAttributeAspectRequiringProvider
    EXTRA_ATTRIBUTE_ASPECT_REQUIRING_PROVIDER = new ExtraAttributeAspectRequiringProvider();
  private static final AspectDefinition EXTRA_ATTRIBUTE_ASPECT_REQUIRING_PROVIDER_DEFINITION =
      new AspectDefinition.Builder(EXTRA_ATTRIBUTE_ASPECT_REQUIRING_PROVIDER)
          .add(attr("$dep", LABEL).value(Label.parseAbsoluteUnchecked("//extra:extra")))
          .requireProviders(RequiredProvider.class)
          .build();

  /**
   * An aspect that defines its own implicit attribute.
   */
  public static class ExtraAttributeAspect extends BaseAspect {
    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return EXTRA_ATTRIBUTE_ASPECT_DEFINITION;
    }
  }

  public static final AttributeAspect ATTRIBUTE_ASPECT = new AttributeAspect();
  private static final AspectDefinition ATTRIBUTE_ASPECT_DEFINITION =
      new AspectDefinition.Builder(ATTRIBUTE_ASPECT)
      .propagateAlongAttribute("foo")
      .build();

  /**
   * An aspect that propagates along all attributes.
   */
  public static class AllAttributesAspect extends BaseAspect {

    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return ALL_ATTRIBUTES_ASPECT_DEFINITION;
    }
  }
  public static final NativeAspectClass ALL_ATTRIBUTES_ASPECT = new AllAttributesAspect();
  private static final AspectDefinition ALL_ATTRIBUTES_ASPECT_DEFINITION =
      new AspectDefinition.Builder(ALL_ATTRIBUTES_ASPECT)
          .propagateAlongAllAttributes()
          .build();

  /** An aspect that propagates along all attributes and has a tool dependency. */
  public static class AllAttributesWithToolAspect extends BaseAspect {

    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return ALL_ATTRIBUTES_WITH_TOOL_ASPECT_DEFINITION;
    }
  }

  public static final NativeAspectClass ALL_ATTRIBUTES_WITH_TOOL_ASPECT =
      new AllAttributesWithToolAspect();
  private static final AspectDefinition ALL_ATTRIBUTES_WITH_TOOL_ASPECT_DEFINITION =
      new AspectDefinition.Builder(ALL_ATTRIBUTES_WITH_TOOL_ASPECT)
          .propagateAlongAllAttributes()
          .add(
              attr("$tool", BuildType.LABEL)
                  .allowedFileTypes(FileTypeSet.ANY_FILE)
                  .value(Label.parseAbsoluteUnchecked("//a:tool")))
          .build();

  /**
   * An aspect that requires aspects on the attributes of rules it attaches to.
   */
  public static class AttributeAspect extends BaseAspect {
    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return ATTRIBUTE_ASPECT_DEFINITION;
    }
  }

  /**
   * An aspect that defines its own implicit attribute and requires provider.
   */
  public static class ExtraAttributeAspectRequiringProvider extends BaseAspect {
    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return EXTRA_ATTRIBUTE_ASPECT_REQUIRING_PROVIDER_DEFINITION;
    }
  }

  public static class AspectRequiringProvider extends BaseAspect {
    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return ASPECT_REQUIRING_PROVIDER_DEFINITION;
    }
  }

  /**
   * An aspect that requires provider sets {{@link RequiredProvider}} and
   * {{@link RequiredProvider2}}.
   */
  public static class AspectRequiringProviderSets extends BaseAspect {
    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return ASPECT_REQUIRING_PROVIDER_SETS_DEFINITION;
    }
  }

  /**
   * An aspect that has a definition depending on parameters provided by originating rule.
   */
  public static class ParametrizedDefinitionAspect extends NativeAspectClass
    implements ConfiguredAspectFactory {

    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      AspectDefinition.Builder builder =
          new AspectDefinition.Builder(PARAMETRIZED_DEFINITION_ASPECT)
              .propagateAlongAttribute("foo");
      ImmutableCollection<String> baz = aspectParameters.getAttribute("baz");
      if (baz != null) {
        try {
          builder.add(attr("$dep", LABEL).value(Label.parseAbsolute(baz.iterator().next())));
        } catch (LabelSyntaxException e) {
          throw new IllegalStateException();
        }
      }
      return builder.build();
    }

    @Override
    public ConfiguredAspect create(
        ConfiguredTarget base, RuleContext ruleContext, AspectParameters parameters) {
      StringBuilder information = new StringBuilder("aspect " + ruleContext.getLabel());
      if (!parameters.isEmpty()) {
        information.append(" data " + Iterables.getFirst(parameters.getAttribute("baz"), null));
        information.append(" ");
      }
      List<? extends TransitiveInfoCollection> deps =
          ruleContext.getPrerequisites("$dep", Mode.TARGET);
      information.append("$dep:[");
      for (TransitiveInfoCollection dep : deps) {
        information.append(" ");
        information.append(dep.getLabel());
      }
      information.append("]");
      return new ConfiguredAspect.Builder(this, parameters, ruleContext)
          .addProvider(new AspectInfo(collectAspectData(information.toString(), ruleContext)))
          .build();
    }
  }

  private static final ParametrizedDefinitionAspect PARAMETRIZED_DEFINITION_ASPECT =
      new ParametrizedDefinitionAspect();

  private static final AspectRequiringProvider ASPECT_REQUIRING_PROVIDER =
      new AspectRequiringProvider();
  private static final AspectRequiringProviderSets ASPECT_REQUIRING_PROVIDER_SETS =
      new AspectRequiringProviderSets();
  private static final AspectDefinition ASPECT_REQUIRING_PROVIDER_DEFINITION =
      new AspectDefinition.Builder(ASPECT_REQUIRING_PROVIDER)
          .requireProviders(RequiredProvider.class)
          .propagateAlongAttribute("foo")
          .build();
  private static final AspectDefinition ASPECT_REQUIRING_PROVIDER_SETS_DEFINITION =
      new AspectDefinition.Builder(ASPECT_REQUIRING_PROVIDER_SETS)
          .requireProviderSets(
              ImmutableList.of(
                  ImmutableSet.<Class<?>>of(RequiredProvider.class),
                  ImmutableSet.<Class<?>>of(RequiredProvider2.class)))
          .build();

  /**
   * An aspect that prints a warning.
   */
  public static class WarningAspect extends NativeAspectClass
    implements ConfiguredAspectFactory {

    @Override
    public ConfiguredAspect create(
        ConfiguredTarget base, RuleContext ruleContext, AspectParameters parameters) {
      ruleContext.ruleWarning("Aspect warning on " + base.getTarget().getLabel());
      return new ConfiguredAspect.Builder(this, parameters, ruleContext).build();
    }

    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return WARNING_ASPECT_DEFINITION;
    }
  }

  public static final WarningAspect WARNING_ASPECT = new WarningAspect();
  private static final AspectDefinition WARNING_ASPECT_DEFINITION =
      new AspectDefinition.Builder(WARNING_ASPECT)
      .propagateAlongAttribute("bar")
      .build();

  /**
   * An aspect that raises an error.
   */
  public static class ErrorAspect extends NativeAspectClass
    implements ConfiguredAspectFactory {

    @Override
    public ConfiguredAspect create(
        ConfiguredTarget base, RuleContext ruleContext, AspectParameters parameters) {
      ruleContext.ruleError("Aspect error");
      return null;
    }

    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return ERROR_ASPECT_DEFINITION;
    }
  }

  public static final ErrorAspect ERROR_ASPECT = new ErrorAspect();
  private static final AspectDefinition ERROR_ASPECT_DEFINITION =
      new AspectDefinition.Builder(ERROR_ASPECT)
      .propagateAlongAttribute("bar")
      .build();

  /**
   * An aspect that advertises but fails to provide providers.
   */
  public static class FalseAdvertisementAspect extends NativeAspectClass
    implements ConfiguredAspectFactory {

    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return FALSE_ADVERTISEMENT_DEFINITION;
    }

    @Override
    public ConfiguredAspect create(ConfiguredTarget base, RuleContext context,
        AspectParameters parameters) throws InterruptedException {
      return new ConfiguredAspect.Builder(this, parameters, context).build();
    }
  }
  public static final FalseAdvertisementAspect FALSE_ADVERTISEMENT_ASPECT
      = new FalseAdvertisementAspect();
  private static final AspectDefinition FALSE_ADVERTISEMENT_DEFINITION =
      new AspectDefinition.Builder(FALSE_ADVERTISEMENT_ASPECT)
        .advertiseProvider(RequiredProvider.class)
        .advertiseProvider(
            ImmutableList.of(SkylarkProviderIdentifier.forLegacy("advertised_provider")))
        .build();


  /**
   * A common base rule for mock rules in this class to reduce boilerplate.
   *
   * <p>It has a few common attributes because internal Blaze machinery assumes the presence of
   * these.
   */
  public static class BaseRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("testonly", BOOLEAN).nonconfigurable("test").value(false))
          .add(attr("deprecation", STRING).nonconfigurable("test").value((String) null))
          .add(attr("tags", STRING_LIST))
          .add(attr("visibility", NODEP_LABEL_LIST).orderIndependent().cfg(HOST)
              .nonconfigurable("test"))
          .add(attr(RuleClass.COMPATIBLE_ENVIRONMENT_ATTR, LABEL_LIST)
              .allowedFileTypes(FileTypeSet.NO_FILE))
          .add(attr(RuleClass.RESTRICTED_ENVIRONMENT_ATTR, LABEL_LIST)
              .allowedFileTypes(FileTypeSet.NO_FILE))
          .build();

    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("base")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRuleClasses.RootRule.class)
          .build();
    }
  }

  /**
   * A rule that defines an aspect on one of its attributes.
   */
  public static class AspectRequiringRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(SIMPLE_ASPECT))
          .add(attr("bar", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(SIMPLE_ASPECT))
          .build();

    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("aspect")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines different aspects on different attributes.
   */
  public static class MultiAspectRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL).allowedFileTypes(FileTypeSet.ANY_FILE)
              .mandatory()
              .aspect(FOO_PROVIDER_ASPECT))
          .add(attr("bar", LABEL).allowedFileTypes(FileTypeSet.ANY_FILE)
              .mandatory()
              .aspect(BAR_PROVIDER_ASPECT))
          .build();

    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("multi_aspect")
          .factoryClass(MultiAspectRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines an {@link AspectRequiringProvider} on one of its attributes.
   */
  public static class AspectRequiringProviderRule implements RuleDefinition {

    private static final class TestAspectParametersExtractor implements
        Function<Rule, AspectParameters> {
      @Override
      public AspectParameters apply(Rule rule) {
        if (rule.isAttrDefined("baz", STRING)) {
          String value = rule.getAttributeContainer().getAttr("baz").toString();
          if (!value.equals("")) {
            return new AspectParameters.Builder().addAttribute("baz", value).build();
          }
        }
        return AspectParameters.EMPTY;
      }
    }

    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(ASPECT_REQUIRING_PROVIDER, new TestAspectParametersExtractor()))
          .add(attr("baz", STRING))
          .build();

    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("aspect_requiring_provider")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines an {@link AspectRequiringProviderSets} on one of its attributes.
   */
  public static class AspectRequiringProviderSetsRule implements RuleDefinition {

    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(ASPECT_REQUIRING_PROVIDER_SETS))
          .add(attr("baz", STRING))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("aspect_requiring_provider_sets")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines an {@link ExtraAttributeAspect} on one of its attributes.
   */
  public static class ExtraAttributeAspectRule implements RuleDefinition {

    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(EXTRA_ATTRIBUTE_ASPECT))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("rule_with_extra_deps_aspect")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines an {@link ParametrizedDefinitionAspect} on one of its attributes.
   */
  public static class ParametrizedDefinitionAspectRule implements RuleDefinition {

    private static final class TestAspectParametersExtractor
        implements Function<Rule, AspectParameters> {
      @Override
      public AspectParameters apply(Rule rule) {
        if (rule.isAttrDefined("baz", STRING)) {
          String value = rule.getAttributeContainer().getAttr("baz").toString();
          if (!value.equals("")) {
            return new AspectParameters.Builder().addAttribute("baz", value).build();
          }
        }
        return AspectParameters.EMPTY;
      }
    }

    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(
              attr("foo", LABEL_LIST)
                  .allowedFileTypes(FileTypeSet.ANY_FILE)
                  .aspect(PARAMETRIZED_DEFINITION_ASPECT, new TestAspectParametersExtractor()))
          .add(attr("baz", STRING))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("parametrized_definition_aspect")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines an {@link ExtraAttributeAspectRequiringProvider} on one of its attributes.
   */
  public static class ExtraAttributeAspectRequiringProviderRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(EXTRA_ATTRIBUTE_ASPECT_REQUIRING_PROVIDER))
          .build();

    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("extra_attribute_aspect_requiring_provider")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines an {@link AllAttributesAspect} on one of its attributes.
   */
  public static class AllAttributesAspectRule implements RuleDefinition {

    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(ALL_ATTRIBUTES_ASPECT))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("all_attributes_aspect")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /** A rule that defines an {@link AllAttributesWithToolAspect} on one of its attributes. */
  public static class AllAttributesWithToolAspectRule implements RuleDefinition {

    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(
              attr("foo", LABEL_LIST)
                  .allowedFileTypes(FileTypeSet.ANY_FILE)
                  .aspect(ALL_ATTRIBUTES_WITH_TOOL_ASPECT))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("all_attributes_with_tool_aspect")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines a {@link WarningAspect} on one of its attributes.
   */
  public static class WarningAspectRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(WARNING_ASPECT))
          .add(attr("bar", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("warning_aspect")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that defines an {@link ErrorAspect} on one of its attributes.
   */
  public static class ErrorAspectRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE)
              .aspect(ERROR_ASPECT))
          .add(attr("bar", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("error_aspect")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A simple rule that has an attribute.
   */
  public static class SimpleRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE))
          .add(attr("foo1", LABEL).allowedFileTypes(FileTypeSet.ANY_FILE))
          .add(attr("txt", STRING))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("simple")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that advertises a provider but doesn't implement it.
   */
  public static class LiarRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE))
          .advertiseProvider(RequiredProvider.class)
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("liar")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that advertises a provider and implements it.
   */
  public static class HonestRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE))
          .advertiseProvider(RequiredProvider.class)
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("honest")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * A rule that advertises another, different provider and implements it.
   */
  public static class HonestRule2 implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("foo", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE))
          .advertiseProvider(RequiredProvider2.class)
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("honest2")
          .factoryClass(DummyRuleFactory2.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }


  /**
   * Rule with an implcit dependency.
   */
  public static class ImplicitDepRule implements RuleDefinition {
    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("$dep", LABEL).value(Label.parseAbsoluteUnchecked("//extra:extra")))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("implicit_dep")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * Rule with a late-bound dependency.
   */
  public static class LateBoundDepRule implements RuleDefinition {
    // TODO(b/65746853): provide a way to do this without passing the entire configuration
    private static final LateBoundDefault<?, List<Label>> PLUGINS_LABEL_LIST =
        LateBoundDefault.fromTargetConfiguration(
            BuildConfiguration.class,
            ImmutableList.of(),
            (rule, attributes, configuration) -> configuration.getPlugins());

    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr(":plugins", LABEL_LIST).value(PLUGINS_LABEL_LIST))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("late_bound_dep")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /**
   * Rule with {@link FalseAdvertisementAspect}
   */
  public static final class FalseAdvertisementAspectRule implements RuleDefinition {

    @Override
    public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          .add(attr("deps", LABEL_LIST).allowedFileTypes().aspect(FALSE_ADVERTISEMENT_ASPECT))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return  RuleDefinition.Metadata.builder()
          .name("false_advertisement_aspect")
          .factoryClass(DummyRuleFactory.class)
          .ancestors(BaseRule.class)
          .build();
    }
  }

  /** Aspect that propagates over rule outputs. */
  public static class AspectApplyingToFiles extends NativeAspectClass
      implements ConfiguredAspectFactory {

    /** Simple provider for testing */
    @Immutable
    public static final class Provider implements TransitiveInfoProvider {
      private final Label label;

      private Provider(Label label) {
        this.label = label;
      }

      public Label getLabel() {
        return label;
      }
    }

    @Override
    public AspectDefinition getDefinition(AspectParameters aspectParameters) {
      return AspectDefinition.builder(this).applyToFiles(true).build();
    }

    @Override
    public ConfiguredAspect create(ConfiguredTarget base, RuleContext context,
        AspectParameters parameters) throws InterruptedException {
      return ConfiguredAspect.builder(this, parameters, context)
          .addProvider(Provider.class, new Provider(base.getLabel()))
          .build();
    }
  }
}
