/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** *
 */

package org.matsim.contrib.drt.optimizer.rebalancing.demandestimator;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.function.ToDoubleFunction;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystemParams;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingParams;
import org.matsim.contrib.drt.optimizer.rebalancing.mincostflow.MinCostFlowRebalancingStrategyParams;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModes;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

public class PreviousIterationDRTDemandEstimatorTest {

	//TODO write test with service area !!
	// (with an service area, demand estimation zones are not spread over the entire network but restricted to the service are (plus a little surrounding))

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	private void assertDemand(ToDoubleFunction<DrtZone> demandFunction, DrtZonalSystem zonalSystem, String zoneId,
			double time, int expectedValue) {
		DrtZone zone = zonalSystem.getZones().get(zoneId);
		assertEquals("wrong estimation of demand at time=" + (time + 60) + " in zone " + zoneId, expectedValue,
				demandFunction.applyAsDouble(zone), MatsimTestUtils.EPSILON);
	}

	@Test
	public void estimateDemand_standardSetup() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandEstimatorType.PreviousIterationDemand, "", false);
		controler.run();
		ZonalDemandEstimator estimator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandEstimator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 1800; ii < 16 * 3600; ii += 1800) {
			ToDoubleFunction<DrtZone> demandFunction = estimator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 0);
			assertDemand(demandFunction, zonalSystem, "2", ii, 0);
			assertDemand(demandFunction, zonalSystem, "3", ii, 0);
			assertDemand(demandFunction, zonalSystem, "4", ii, 0);
			assertDemand(demandFunction, zonalSystem, "5", ii, 0);
			assertDemand(demandFunction, zonalSystem, "6", ii, 0);
			assertDemand(demandFunction, zonalSystem, "7", ii, 0);
			assertDemand(demandFunction, zonalSystem, "8", ii, 3);
		}
	}

	@Test
	public void estimateDemand_withSpeedUpMode() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandEstimatorType.PreviousIterationDemand,
				"drt_teleportation", false);
		controler.run();
		ZonalDemandEstimator estimator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandEstimator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 1800; ii < 16 * 3600; ii += 1800) {
			ToDoubleFunction<DrtZone> demandFunction = estimator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 0);
			assertDemand(demandFunction, zonalSystem, "2", ii, 0);
			assertDemand(demandFunction, zonalSystem, "3", ii, 0);
			assertDemand(demandFunction, zonalSystem, "4", ii, 3);
			assertDemand(demandFunction, zonalSystem, "5", ii, 0);
			assertDemand(demandFunction, zonalSystem, "6", ii, 0);
			assertDemand(demandFunction, zonalSystem, "7", ii, 0);
			assertDemand(demandFunction, zonalSystem, "8", ii, 3);
		}
	}

	private Controler setupControler(MinCostFlowRebalancingStrategyParams.ZonalDemandEstimatorType estimatorType,
			String drtSpeedUpModeForRebalancingConfiguration, boolean useServiceArea) {
		URL configUrl = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("dvrp-grid"),
				"eight_shared_taxi_config.xml");
		Config config = ConfigUtils.loadConfig(configUrl, new MultiModeDrtConfigGroup(), new DvrpConfigGroup(),
				new OTFVisConfigGroup());

		DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(config);
		drtCfg.setDrtSpeedUpMode(drtSpeedUpModeForRebalancingConfiguration);

		if (useServiceArea) {
			throw new IllegalArgumentException("about to get implemented...");
			//			drtCfg.setOperationalScheme(DrtConfigGroup.OperationalScheme.serviceAreaBased);
			//			drtCfg.setDrtServiceAreaShapeFile("");
		}

		MinCostFlowRebalancingStrategyParams rebalancingStrategyParams = new MinCostFlowRebalancingStrategyParams();
		rebalancingStrategyParams.setTargetAlpha(1);
		rebalancingStrategyParams.setTargetBeta(0);
		rebalancingStrategyParams.setZonalDemandEstimatorType(estimatorType);

		RebalancingParams rebalancingParams = new RebalancingParams();
		rebalancingParams.addParameterSet(rebalancingStrategyParams);
		drtCfg.addParameterSet(rebalancingParams);

		DrtZonalSystemParams zonalSystemParams = new DrtZonalSystemParams();
		zonalSystemParams.setZonesGeneration(DrtZonalSystemParams.ZoneGeneration.GridFromNetwork);
		zonalSystemParams.setCellSize(500.);
		drtCfg.addParameterSet(zonalSystemParams);

		drtCfg.setChangeStartLinkToLastLinkInSchedule(false); //do not take result from last iteration...

		config.controler().setLastIteration(1);
		config.qsim().setStartTime(0.);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());

		PlansCalcRouteConfigGroup.ModeRoutingParams pseudoDrtSpeedUpModeRoutingParams = new PlansCalcRouteConfigGroup.ModeRoutingParams(
				"drt_teleportation");
		pseudoDrtSpeedUpModeRoutingParams.setBeelineDistanceFactor(1.3);
		pseudoDrtSpeedUpModeRoutingParams.setTeleportedModeSpeed(8.0);
		config.plansCalcRoute().addModeRoutingParams(pseudoDrtSpeedUpModeRoutingParams);

		// if adding a new mode (drtSpeedUpMode), some default modes are deleted, so re-insert them...
		PlansCalcRouteConfigGroup.ModeRoutingParams walkRoutingParams = new PlansCalcRouteConfigGroup.ModeRoutingParams(
				TransportMode.walk);
		walkRoutingParams.setBeelineDistanceFactor(1.3);
		walkRoutingParams.setTeleportedModeSpeed(3.0 / 3.6);
		config.plansCalcRoute().addModeRoutingParams(walkRoutingParams);

		PlanCalcScoreConfigGroup.ModeParams pseudoDrtSpeedUpModeScoreParams = new PlanCalcScoreConfigGroup.ModeParams(
				"drt_teleportation");
		config.planCalcScore().addModeParams(pseudoDrtSpeedUpModeScoreParams);

		//this is the wrong way around (create controler before manipulating scenario...
		Controler controler = DrtControlerCreator.createControler(config, false);
		setupPopulation(controler.getScenario().getPopulation());
		return controler;
	}

	/**
	 * we have eight zones, 2 rows 4 columns.
	 * order of zones:
	 * 2	4	6	8
	 * 1	3	5	7
	 * <p>
	 * 1) in the left column, there are half of the people, performing dummy - > car -> dummy
	 * That should lead to half of the drt vehicles rebalanced to the left column when using FleetSizeWeightedByActivityEndsDemandEstimator.
	 * 2) in the right column, the other half of the people perform dummy -> drt -> dummy from top row to bottom row.
	 * That should lead to all drt vehicles rebalanced to the right column when using PreviousIterationDRTDemandEstimator.
	 * 3) in the center, there is nothing happening.
	 * But, when using EqualVehicleDensityZonalDemandEstimator, one vehicle should get sent to every zone..
	 */
	private void setupPopulation(Population population) {
		//delete what's there
		population.getPersons().clear();

		PopulationFactory factory = population.getFactory();

		Id<Link> left1 = Id.createLinkId(344);
		Id<Link> left2 = Id.createLinkId(112);

		for (int i = 1; i < 100; i++) {
			Person person = factory.createPerson(Id.createPersonId("leftColumn_" + i));

			Plan plan = factory.createPlan();
			Activity dummy1 = factory.createActivityFromLinkId("dummy", left1);
			dummy1.setEndTime(i * 10 * 60);
			plan.addActivity(dummy1);

			plan.addLeg(factory.createLeg(TransportMode.car));
			plan.addActivity(factory.createActivityFromLinkId("dummy", left2));

			person.addPlan(plan);
			population.addPerson(person);
		}

		Id<Link> right1 = Id.createLinkId(151);
		Id<Link> right2 = Id.createLinkId(319);

		for (int i = 1; i < 100; i++) {
			Person person = factory.createPerson(Id.createPersonId("rightColumn_" + i));

			Plan plan = factory.createPlan();
			Activity dummy1 = factory.createActivityFromLinkId("dummy", right1);
			dummy1.setEndTime(i * 10 * 60);
			plan.addActivity(dummy1);

			plan.addLeg(factory.createLeg(TransportMode.drt));
			plan.addActivity(factory.createActivityFromLinkId("dummy", right2));

			person.addPlan(plan);
			population.addPerson(person);
		}

		Id<Link> center1 = Id.createLinkId(147);
		Id<Link> center2 = Id.createLinkId(315);

		for (int i = 1; i < 100; i++) {
			Person person = factory.createPerson(Id.createPersonId("centerColumn_" + i));

			Plan plan = factory.createPlan();
			Activity dummy1 = factory.createActivityFromLinkId("dummy", center1);
			dummy1.setEndTime(i * 10 * 60);
			plan.addActivity(dummy1);

			plan.addLeg(factory.createLeg("drt_teleportation"));
			plan.addActivity(factory.createActivityFromLinkId("dummy", center2));

			person.addPlan(plan);
			population.addPerson(person);
		}
	}
}