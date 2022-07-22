package org.matsim.contrib.freight.controler;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scoring.ScoringFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * This keeps track of a scheduledTour during simulation and can thus be seen as the driver of the vehicle that runs the tour.
 *
 * <p>In addition, the driver knows which planElement is associated to a shipment and service, respectively.
 *
 * @author mzilske, sschroeder
 */
final class CarrierDriverAgent{
	private static final Logger log = Logger.getLogger( CarrierDriverAgent.class );

	private Leg currentLeg;

	private Activity currentActivity;

	private List<Id<Link>> currentRoute;

	private final Id<Person> driverId;

	private final ScheduledTour scheduledTour;

	private int activityCounter = 0;
	private final ScoringFunction scoringFunction;
	private final CarrierAgentTracker lspTracker;
	private final Carrier carrier;

	CarrierDriverAgent( Id<Person> driverId, ScheduledTour tour, ScoringFunction scoringFunction, CarrierAgentTracker lspTracker, Carrier carrier ){
		this.scoringFunction = scoringFunction;
		this.lspTracker = lspTracker;
		this.carrier = carrier;
		log.debug( "creating CarrierDriverAgent with driverId=" + driverId );
		this.driverId = driverId;
		this.scheduledTour = tour;
	}

	void handleAnEvent( Event event ){
		// the event comes to here from CarrierAgent#handleEvent only for events concerning this driver

		if( event instanceof PersonArrivalEvent ){
			handleEvent( (PersonArrivalEvent) event );
		} else if( event instanceof PersonDepartureEvent ){
			handleEvent( (PersonDepartureEvent) event );
		} else if( event instanceof LinkEnterEvent ){
			handleEvent( (LinkEnterEvent) event );
		} else if( event instanceof ActivityEndEvent ){
			handleEvent( (ActivityEndEvent) event );
		} else if( event instanceof ActivityStartEvent ){
			handleEvent( (ActivityStartEvent) event );
		} else{
			notifyEventHappened( event, null, scheduledTour, driverId, activityCounter );
		}
	}

	private void handleEvent( PersonArrivalEvent event ){
		currentLeg.setTravelTime( event.getTime() - currentLeg.getDepartureTime().seconds() );
		double travelTime = currentLeg.getDepartureTime().seconds()
						    + currentLeg.getTravelTime().seconds() - currentLeg.getDepartureTime().seconds();
		currentLeg.setTravelTime( travelTime );
		if( currentRoute.size() > 1 ){
			NetworkRoute networkRoute = RouteUtils.createNetworkRoute( currentRoute );
			networkRoute.setTravelTime( travelTime );
			networkRoute.setVehicleId( getVehicle().getId() );
			currentLeg.setRoute( networkRoute );
			currentRoute = null;
		} else{
			Id<Link> startLink;
			if( currentRoute.size() != 0 ){
				startLink = currentRoute.get( 0 );
			} else{
				startLink = event.getLinkId();
			}
			Route genericRoute = RouteUtils.createGenericRouteImpl( startLink, event.getLinkId() );
			genericRoute.setDistance( 0.0 );
			currentLeg.setRoute( genericRoute );
		}
		if( scoringFunction != null ){
			scoringFunction.handleLeg( currentLeg );
		}
		notifyEventHappened( event, null, scheduledTour, driverId, activityCounter );
	}

	private void handleEvent( PersonDepartureEvent event ){
		Leg leg = PopulationUtils.createLeg( event.getLegMode() );
		leg.setDepartureTime( event.getTime() );
		currentLeg = leg;
		currentRoute = new ArrayList<>();
		notifyEventHappened( event, null, scheduledTour, driverId, activityCounter );
	}

	private void handleEvent( LinkEnterEvent event ){
		if( scoringFunction != null ){
			scoringFunction.handleEvent( new LinkEnterEvent( event.getTime(), getVehicle().getId(), event.getLinkId() ) );
		}
		currentRoute.add( event.getLinkId() );
		notifyEventHappened( event, null, scheduledTour, driverId, activityCounter );
	}

	private void handleEvent( ActivityEndEvent event ){
		if( currentActivity == null ){
			Activity firstActivity = PopulationUtils.createActivityFromLinkId( event.getActType(), event.getLinkId() );
			firstActivity.setFacilityId( event.getFacilityId() );
			currentActivity = firstActivity;
		}
		currentActivity.setEndTime( event.getTime() );
		if( scoringFunction != null ){
			scoringFunction.handleActivity( currentActivity );
		}

		notifyEventHappened( event, currentActivity, scheduledTour, driverId, activityCounter );

		log.debug( "handling activity end event=" + event );
		if( FreightConstants.START.equals( event.getActType() ) ){
			activityCounter += 1;
			return;
		}
		if( FreightConstants.END.equals( event.getActType() ) ) return;
		if( FreightConstants.PICKUP.equals( event.getActType() ) ){
			activityCounter += 2;
		} else if( FreightConstants.DELIVERY.equals( event.getActType() ) ){
			activityCounter += 2;
		} else{
			activityCounter += 2;
		}
	}

	private void handleEvent( ActivityStartEvent event ){
		Activity activity = PopulationUtils.createActivityFromLinkId( event.getActType(), event.getLinkId() );
		activity.setFacilityId( event.getFacilityId() );
		activity.setStartTime( event.getTime() );
		if( event.getActType().equals( FreightConstants.END ) ){
			activity.setEndTimeUndefined();
			if( scoringFunction != null ){
				scoringFunction.handleActivity( activity );
			}
		} else{
			Tour.TourActivity tourActivity = getTourActivity();
			if( !activity.getLinkId().toString().equals( tourActivity.getLocation().toString() ) )
				throw new AssertionError( "linkId of activity is not equal to linkId of tourActivity. This must not be." );
			currentActivity = new FreightActivity( activity, tourActivity.getTimeWindow() );
		}
		notifyEventHappened( event, currentActivity, scheduledTour, driverId, activityCounter );
	}

	/**
	 * {@link CarrierAgentTracker} is an event handler, and it passes events related to individual carriers (but only those) to them.
	 * Here, they are send back to the tracker. The main (only) reason why this is necessary is some fields such as "activity" or
	 * "scheduledTour" are not filled in from the event itself, and one needs the agent to figure it out.
	 */
	private void notifyEventHappened( Event event, Activity activity, ScheduledTour scheduledTour, Id<Person> driverId, int activityCounter ){
		if( scoringFunction == null ){
			lspTracker.notifyEventHappened( event, carrier, activity, scheduledTour, driverId, activityCounter );
		}
	}

	private Tour.TourActivity getTourActivity(){
		return (Tour.TourActivity) this.scheduledTour.getTour().getTourElements().get( activityCounter );
	}

	CarrierVehicle getVehicle(){
		return scheduledTour.getVehicle();
	}

	Tour.TourElement getPlannedTourElement( int elementIndex ){
		int index = elementIndex - 1;
		int elementsSize = scheduledTour.getTour().getTourElements().size();
		if( index < 0 ) return scheduledTour.getTour().getStart();
		else if( index == elementsSize ) return scheduledTour.getTour().getEnd();
		else if( index < elementsSize ){
			return scheduledTour.getTour().getTourElements().get( index );
		} else throw new IllegalStateException( "index out of bounds" );
	}
}