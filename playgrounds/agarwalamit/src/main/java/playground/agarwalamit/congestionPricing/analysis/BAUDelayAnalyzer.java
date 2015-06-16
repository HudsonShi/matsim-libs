/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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
 * *********************************************************************** */
package playground.agarwalamit.congestionPricing.analysis;

import java.io.BufferedWriter;
import java.util.Map;
import java.util.SortedMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.io.IOUtils;

import playground.agarwalamit.analysis.congestion.CausedDelayAnalyzer;
import playground.agarwalamit.analysis.congestion.CongestionPersonAnalyzer;
import playground.agarwalamit.analysis.congestion.CrossMarginalCongestionEventsWriter;
import playground.agarwalamit.utils.LoadMyScenarios;
import playground.benjamin.scenarios.munich.analysis.filter.PersonFilter;
import playground.benjamin.scenarios.munich.analysis.filter.UserGroup;

/**
 * (1) Read events file and write congestion events for desired implementation.
 * (2) From congestion events file write various analyses files.
 * @author amit
 */

public class BAUDelayAnalyzer {

	
	public BAUDelayAnalyzer(String congestionImpl) {
		scenario = LoadMyScenarios.loadScenarioFromOutputDir(runDir);
		simulationEndTime = scenario.getConfig().qsim().getEndTime();
		int lastIt = scenario.getConfig().controler().getLastIteration();
		eventsFile = runDir+"ITERS/it."+lastIt+"/"+lastIt+".events.xml.gz";
		congestionEventsFile = runDir+"/ITERS/it."+lastIt+"/"+lastIt+".events_"+congestionImpl+".xml.gz";
		this.congestionImpl = congestionImpl;
	}

	private int noOfTimeBins = 30;
	private String eventsFile ;
	private double simulationEndTime;
	private String runDir = "../../../repos/runs-svn/detEval/emissionCongestionInternalization/output/1pct/run11/policies/bau/";
	private String congestionEventsFile;
	private Scenario scenario;
	private String congestionImpl;
	

	private final PersonFilter pf = new PersonFilter();
	
	public static void main(String[] args) {
		BAUDelayAnalyzer analyzer = new BAUDelayAnalyzer("implV3");
		analyzer.writeCongestionEvents();
		analyzer.writeExperiecedAndCausingPersonDelay();
		analyzer.writeAverageLinkDelays();
		analyzer.writeHourlyCausedDelayForEachPerson();
	}
	
	private void writeCongestionEvents(){
		new CrossMarginalCongestionEventsWriter(scenario).readAndWrite(congestionImpl);
	}
	
	/**
	 * Writes timeBin2PersonId2UserGroup2CausedDelay
	 */
	private void writeHourlyCausedDelayForEachPerson(){
		SortedMap<Double, Map<Id<Person>, Double>> timeBin2CausingPerson2Delay = getCausingPersonDelay(noOfTimeBins);
		BufferedWriter writer = IOUtils.getBufferedWriter(runDir+"/analysis/timeBin2Person2UserGroup2CausedDelay_"+congestionImpl+".txt");
		try {
			writer.write("timeBin \t personId \t userGroup \t delayInHr \n");
			for (double d : timeBin2CausingPerson2Delay.keySet()){
				for (Id<Person> personId : timeBin2CausingPerson2Delay.get(d).keySet()){
					writer.write(d+"\t"+personId+"\t"+getUserGroupFromPersonId(personId)+"\t"+timeBin2CausingPerson2Delay.get(d).get(personId) / 3600+"\n");
				}
			}
			writer.close();
		} catch (Exception e) {
			throw new RuntimeException("Data is not written in file. Reason: "
					+ e);
		}
	}
	
	/**
	 * Writes
	 *<p> personId userGroup delayInHr
	 */
	private void writeExperiecedAndCausingPersonDelay(){
		SortedMap<Double, Map<Id<Person>, Double>> timeBin2AffectedPerson2Delay = getExperiencedPersonDelay(1);
		SortedMap<Double, Map<Id<Person>, Double>> timeBin2CausingPerson2Delay = getCausingPersonDelay(1);
		
		if (timeBin2AffectedPerson2Delay.size()!=1) throw new RuntimeException("Delay is not summed up for all time bins.");
		Map<Id<Person>, Double> affectedperson2Delay = timeBin2AffectedPerson2Delay.get(simulationEndTime);
		Map<Id<Person>, Double> causedPerson2Delay = timeBin2CausingPerson2Delay.get(simulationEndTime);
		
		BufferedWriter writer = IOUtils.getBufferedWriter(runDir+"/analysis/affectedAndCausedDelay_"+congestionImpl+".txt");
		
		try {
			writer.write("personId\tuserGroup\taffectedDelayInHr\tcausedDelayInHr\n");
			for (Id<Person> id :causedPerson2Delay.keySet()){
				writer.write(id+"\t"+getUserGroupFromPersonId(id)+"\t"+affectedperson2Delay.get(id) / 3600+"\t"+causedPerson2Delay.get(id) / 3600+"\n");
			}
			writer.close();
		} catch (Exception e) {
			throw new RuntimeException("Data is not written in file. Reason: "
					+ e);
		}
	}
	
	/**
	 * Writes
	 * <p> timeBin LinkId avgTollPerPerson.
	 * <p> Since, these link delays are evaluated from congestion events, implementation varies.
	 */
	private void writeAverageLinkDelays (){

		CausedDelayAnalyzer delayAnalyzer = new CausedDelayAnalyzer(congestionEventsFile, scenario, noOfTimeBins);
		delayAnalyzer.run();
		
		SortedMap<Double, Map<Id<Link>, Double>> timeBin2LinkId2Delay = delayAnalyzer.getTimeBin2LinkId2Delay();
		SortedMap<Double, Map<Id<Link>, Integer>> timeBin2LinkCount = delayAnalyzer.getTimeBin2Link2PersonCount();
		
		BufferedWriter writer = IOUtils.getBufferedWriter(runDir+"/analysis/linkId2Delay"+congestionImpl+".txt");
		
		try {
			writer.write("timeBin\tlinkId\tavgLinkDelayInHr\n");
			for (double d:timeBin2LinkId2Delay.keySet()){
				for(Id<Link> linkId : timeBin2LinkId2Delay.get(d).keySet()){
					double delay = timeBin2LinkId2Delay.get(d).get(linkId);
					double count = timeBin2LinkCount.get(d).get(linkId);
					double avgDelay  = 0;
					/*
					 *  if delay ==0 and count != 0 --> noone delayed
					 *  else if delay ==0 and count ==0 --> noone traveled on the link
					 *  else if delay!=0 and count ==0 --> cant happen
					 *  elst if delay!=0 and count !=0 --> get avg delay
					 */
					if(delay!=0 && count==0) throw new RuntimeException("Delay is not zero whereas person count is zero. Can not happen. Aborting...");
					else if(delay !=0 && count !=0 )  {
						avgDelay = ( delay / count) / 3600.;
					}
					writer.write(d+"\t"+linkId+"\t"+avgDelay+"\n");	
				}
			}
			writer.close();
		} catch (Exception e) {
			throw new RuntimeException("Data is not written in file. Reason: "
					+ e);
		}
	}

	private SortedMap<Double, Map<Id<Person>, Double>> getExperiencedPersonDelay(int noOfTimeBin){
		CongestionPersonAnalyzer personAnalyzer = new CongestionPersonAnalyzer(eventsFile, noOfTimeBin);
		personAnalyzer.init(scenario);
		personAnalyzer.preProcessData();
		personAnalyzer.postProcessData();
		return personAnalyzer.getCongestionPerPersonTimeInterval();
	}
	
	private SortedMap<Double, Map<Id<Person>, Double>> getCausingPersonDelay(int noOfTimeBin){
		CausedDelayAnalyzer delayAnalyzer = new CausedDelayAnalyzer(congestionEventsFile, scenario, noOfTimeBin);
		delayAnalyzer.run();
		return delayAnalyzer.getTimeBin2CausingPersonId2Delay();
	}
	
	private UserGroup getUserGroupFromPersonId(Id<Person> presonId){
		for(UserGroup ug :UserGroup.values()){
			if(pf.isPersonIdFromUserGroup(presonId, ug)){
				return ug;
			}
		}
		return null;
	}
}