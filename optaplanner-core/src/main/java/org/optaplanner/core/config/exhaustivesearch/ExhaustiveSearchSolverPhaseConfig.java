/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.core.config.exhaustivesearch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.optaplanner.core.config.heuristic.policy.HeuristicConfigPolicy;
import org.optaplanner.core.config.heuristic.selector.common.SelectionOrder;
import org.optaplanner.core.config.heuristic.selector.entity.EntitySelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.MoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.value.ValueSelectorConfig;
import org.optaplanner.core.config.phase.SolverPhaseConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.util.ConfigUtils;
import org.optaplanner.core.impl.domain.entity.descriptor.EntityDescriptor;
import org.optaplanner.core.impl.domain.solution.descriptor.SolutionDescriptor;
import org.optaplanner.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import org.optaplanner.core.impl.exhaustivesearch.DefaultExhaustiveSearchSolverPhase;
import org.optaplanner.core.impl.exhaustivesearch.ExhaustiveSearchSolverPhase;
import org.optaplanner.core.impl.exhaustivesearch.decider.ExhaustiveSearchDecider;
import org.optaplanner.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;
import org.optaplanner.core.impl.exhaustivesearch.node.bounder.FallingScoreBounder;
import org.optaplanner.core.impl.exhaustivesearch.node.bounder.ScoreBounder;
import org.optaplanner.core.impl.exhaustivesearch.node.comparator.BreadthFirstNodeComparator;
import org.optaplanner.core.impl.exhaustivesearch.node.comparator.DepthFirstNodeComparator;
import org.optaplanner.core.impl.exhaustivesearch.node.comparator.OptimisticBoundFirstNodeComparator;
import org.optaplanner.core.impl.heuristic.selector.common.SelectionCacheType;
import org.optaplanner.core.impl.heuristic.selector.entity.EntitySelector;
import org.optaplanner.core.impl.heuristic.selector.entity.mimic.ManualEntityMimicRecorder;
import org.optaplanner.core.impl.heuristic.selector.move.MoveSelector;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.termination.Termination;

@XStreamAlias("exhaustiveSearch")
public class ExhaustiveSearchSolverPhaseConfig extends SolverPhaseConfig {

    // Warning: all fields are null (and not defaulted) because they can be inherited
    // and also because the input config file should match the output config file

    protected ExhaustiveSearchType exhaustiveSearchType = null;

    @XStreamAlias("entitySelector")
    protected EntitySelectorConfig entitySelectorConfig = null;
    @XStreamAlias("moveSelector")
    protected MoveSelectorConfig moveSelectorConfig = null;

    public ExhaustiveSearchType getExhaustiveSearchType() {
        return exhaustiveSearchType;
    }

    public void setExhaustiveSearchType(ExhaustiveSearchType exhaustiveSearchType) {
        this.exhaustiveSearchType = exhaustiveSearchType;
    }

    public EntitySelectorConfig getEntitySelectorConfig() {
        return entitySelectorConfig;
    }

    public void setEntitySelectorConfig(EntitySelectorConfig entitySelectorConfig) {
        this.entitySelectorConfig = entitySelectorConfig;
    }

    public MoveSelectorConfig getMoveSelectorConfig() {
        return moveSelectorConfig;
    }

    public void setMoveSelectorConfig(MoveSelectorConfig moveSelectorConfig) {
        this.moveSelectorConfig = moveSelectorConfig;
    }

    // ************************************************************************
    // Builder methods
    // ************************************************************************


    public ExhaustiveSearchSolverPhase buildSolverPhase(int phaseIndex, HeuristicConfigPolicy solverConfigPolicy,
            BestSolutionRecaller bestSolutionRecaller, Termination solverTermination) {
        HeuristicConfigPolicy phaseConfigPolicy = solverConfigPolicy.createPhaseConfigPolicy();
        phaseConfigPolicy.setInitializedChainedValueFilterEnabled(true);
        ExhaustiveSearchType exhaustiveSearchType_ = exhaustiveSearchType == null
                ? ExhaustiveSearchType.DEPTH_FIRST_BRANCH_AND_BOUND : exhaustiveSearchType;
        phaseConfigPolicy.setSortEntitiesByDecreasingDifficultyEnabled(
                exhaustiveSearchType_.isSortEntitiesByDecreasingDifficulty());
        phaseConfigPolicy.setSortValuesByIncreasingStrengthEnabled(
                exhaustiveSearchType_.isSortValuesByIncreasingStrength());
        DefaultExhaustiveSearchSolverPhase phase = new DefaultExhaustiveSearchSolverPhase();
        configureSolverPhase(phase, phaseIndex, phaseConfigPolicy, bestSolutionRecaller, solverTermination);
        phase.setNodeComparator(exhaustiveSearchType_.buildNodeComparator());
        EntitySelectorConfig entitySelectorConfig_ = buildEntitySelectorConfig(phaseConfigPolicy);
        EntitySelector entitySelector = entitySelectorConfig_.buildEntitySelector(phaseConfigPolicy,
                SelectionCacheType.PHASE, SelectionOrder.ORIGINAL);
        phase.setEntitySelector(entitySelector);
        phase.setDecider(buildDecider(phaseConfigPolicy, entitySelector, bestSolutionRecaller, phase.getTermination()));
        EnvironmentMode environmentMode = phaseConfigPolicy.getEnvironmentMode();
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            phase.setAssertWorkingSolutionScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            phase.setAssertExpectedWorkingSolutionScore(true);
        }
        return phase;
    }

    private EntitySelectorConfig buildEntitySelectorConfig(HeuristicConfigPolicy configPolicy) {
        EntitySelectorConfig entitySelectorConfig_;
        if (entitySelectorConfig == null) {
            entitySelectorConfig_ = new EntitySelectorConfig();
            EntityDescriptor entityDescriptor = deduceEntityDescriptor(configPolicy.getSolutionDescriptor());
            entitySelectorConfig_.setEntityClass(entityDescriptor.getEntityClass());
            if (configPolicy.isSortEntitiesByDecreasingDifficultyEnabled()) {
                entitySelectorConfig_.setCacheType(SelectionCacheType.PHASE);
                entitySelectorConfig_.setSelectionOrder(SelectionOrder.SORTED);
                entitySelectorConfig_.setSorterManner(EntitySelectorConfig.EntitySorterManner.DECREASING_DIFFICULTY);
            }
        } else {
            entitySelectorConfig_ = entitySelectorConfig;
        }
        if (entitySelectorConfig_.getCacheType() != null
                && entitySelectorConfig_.getCacheType().compareTo(SelectionCacheType.PHASE) < 0) {
            throw new IllegalArgumentException("The solverPhaseConfig (" + this
                    + ") cannot have an entitySelectorConfig ("  + entitySelectorConfig_
                    + ") with a cacheType (" + entitySelectorConfig_.getCacheType()
                    + ") lower than " + SelectionCacheType.PHASE + ".");
        }
        return entitySelectorConfig_;
    }

    protected EntityDescriptor deduceEntityDescriptor(SolutionDescriptor solutionDescriptor) {
        Collection<EntityDescriptor> entityDescriptors = solutionDescriptor.getGenuineEntityDescriptors();
        if (entityDescriptors.size() != 1) {
            throw new IllegalArgumentException("The solverPhaseConfig (" + this
                    + ") has no entitySelector configured"
                    + " and because there are multiple in the planningEntityClassSet ("
                    + solutionDescriptor.getEntityClassSet()
                    + "), it can not be deducted automatically.");
        }
        return entityDescriptors.iterator().next();
    }

    private ExhaustiveSearchDecider buildDecider(HeuristicConfigPolicy configPolicy,
            EntitySelector sourceEntitySelector,
            BestSolutionRecaller bestSolutionRecaller, Termination termination) {
        ManualEntityMimicRecorder manualEntityMimicRecorder = new ManualEntityMimicRecorder(sourceEntitySelector);
        String mimicSelectorId = sourceEntitySelector.getEntityDescriptor().getEntityClass().getName(); // TODO mimicSelectorId must be a field
        configPolicy.addEntityMimicRecorder(mimicSelectorId, manualEntityMimicRecorder);
        MoveSelectorConfig moveSelectorConfig_ = buildMoveSelectorConfig(configPolicy,
                sourceEntitySelector, mimicSelectorId);
        MoveSelector moveSelector = moveSelectorConfig_.buildMoveSelector(configPolicy,
                SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL);
        ScoreBounder scoreBounder = new FallingScoreBounder(configPolicy.getScoreDefinition()); // TODO make flexible
        ExhaustiveSearchDecider decider = new ExhaustiveSearchDecider(bestSolutionRecaller, termination,
                manualEntityMimicRecorder, moveSelector, scoreBounder);
        EnvironmentMode environmentMode = configPolicy.getEnvironmentMode();
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            decider.setAssertMoveScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            decider.setAssertExpectedUndoMoveScore(true);
        }
        return decider;
    }

    private MoveSelectorConfig buildMoveSelectorConfig(HeuristicConfigPolicy configPolicy,
            EntitySelector entitySelector, String mimicSelectorId) {
        MoveSelectorConfig moveSelectorConfig_;
        if (moveSelectorConfig == null) {
            EntityDescriptor entityDescriptor = entitySelector.getEntityDescriptor();
            Collection<GenuineVariableDescriptor> variableDescriptors = entityDescriptor.getVariableDescriptors();
            List<MoveSelectorConfig> subMoveSelectorConfigList = new ArrayList<MoveSelectorConfig>(
                    variableDescriptors.size());
            for (GenuineVariableDescriptor variableDescriptor : variableDescriptors) {
                ChangeMoveSelectorConfig changeMoveSelectorConfig = new ChangeMoveSelectorConfig();
                EntitySelectorConfig changeEntitySelectorConfig = new EntitySelectorConfig();
                changeEntitySelectorConfig.setMimicSelectorRef(mimicSelectorId);
                changeMoveSelectorConfig.setEntitySelectorConfig(changeEntitySelectorConfig);
                ValueSelectorConfig changeValueSelectorConfig = new ValueSelectorConfig();
                changeValueSelectorConfig.setVariableName(variableDescriptor.getVariableName());
                if (configPolicy.isSortValuesByIncreasingStrengthEnabled()) {
                    if (variableDescriptor.getValueRangeDescriptor().isEntityIndependent()) {
                        changeValueSelectorConfig.setCacheType(SelectionCacheType.PHASE);
                    } else {
                        changeValueSelectorConfig.setCacheType(SelectionCacheType.STEP);
                    }
                    changeValueSelectorConfig.setSelectionOrder(SelectionOrder.SORTED);
                    changeValueSelectorConfig.setSorterManner(ValueSelectorConfig.ValueSorterManner.INCREASING_STRENGTH);
                }
                changeMoveSelectorConfig.setValueSelectorConfig(changeValueSelectorConfig);
                subMoveSelectorConfigList.add(changeMoveSelectorConfig);
            }
            if (subMoveSelectorConfigList.size() > 1) {
                moveSelectorConfig_ = new CartesianProductMoveSelectorConfig(subMoveSelectorConfigList);
            } else {
                moveSelectorConfig_ = subMoveSelectorConfigList.get(0);
            }
        } else {
            moveSelectorConfig_ = moveSelectorConfig;
        }
        return moveSelectorConfig_;
    }

    public void inherit(ExhaustiveSearchSolverPhaseConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        exhaustiveSearchType = ConfigUtils.inheritOverwritableProperty(exhaustiveSearchType,
                inheritedConfig.getExhaustiveSearchType());
        if (entitySelectorConfig == null) {
            entitySelectorConfig = inheritedConfig.getEntitySelectorConfig();
        } else if (inheritedConfig.getEntitySelectorConfig() != null) {
            entitySelectorConfig.inherit(inheritedConfig.getEntitySelectorConfig());
        }
        if (moveSelectorConfig == null) {
            moveSelectorConfig = inheritedConfig.getMoveSelectorConfig();
        } else if (inheritedConfig.getMoveSelectorConfig() != null) {
            moveSelectorConfig.inherit(inheritedConfig.getMoveSelectorConfig());
        }
    }

    public static enum ExhaustiveSearchType {
        BREADTH_FIRST_BRANCH_AND_BOUND,
        DEPTH_FIRST_BRANCH_AND_BOUND,
        OPTIMISTIC_BOUND_FIRST_BRANCH_AND_BOUND;

        public boolean isSortEntitiesByDecreasingDifficulty() {
            switch (this) {
                case BREADTH_FIRST_BRANCH_AND_BOUND:
                case DEPTH_FIRST_BRANCH_AND_BOUND:
                case OPTIMISTIC_BOUND_FIRST_BRANCH_AND_BOUND:
                    return true;
                default:
                    throw new IllegalStateException("The exhaustiveSearchType ("
                            + this + ") is not implemented.");
            }
        }

        public boolean isSortValuesByIncreasingStrength() {
            switch (this) {
                case BREADTH_FIRST_BRANCH_AND_BOUND:
                case DEPTH_FIRST_BRANCH_AND_BOUND:
                case OPTIMISTIC_BOUND_FIRST_BRANCH_AND_BOUND:
                    return true;
                default:
                    throw new IllegalStateException("The exhaustiveSearchType ("
                            + this + ") is not implemented.");
            }
        }

        public Comparator<ExhaustiveSearchNode> buildNodeComparator() {
            switch (this) {
                case BREADTH_FIRST_BRANCH_AND_BOUND:
                    return new BreadthFirstNodeComparator();
                case DEPTH_FIRST_BRANCH_AND_BOUND:
                    return new DepthFirstNodeComparator();
                case OPTIMISTIC_BOUND_FIRST_BRANCH_AND_BOUND:
                    return new OptimisticBoundFirstNodeComparator();
                default:
                    throw new IllegalStateException("The exhaustiveSearchType ("
                            + this + ") is not implemented.");
            }
        }
    }

}
