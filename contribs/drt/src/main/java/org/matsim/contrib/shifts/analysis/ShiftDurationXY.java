package org.matsim.contrib.shifts.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.shifts.events.*;
import org.matsim.contrib.shifts.shift.DrtShift;
import org.matsim.contrib.shifts.shift.DrtShiftUtils;
import org.matsim.contrib.shifts.shift.DrtShifts;
import org.matsim.contrib.shifts.shift.ShiftBreak;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class ShiftDurationXY implements DrtShiftStartedEventHandler, DrtShiftEndedEventHandler,
		DrtShiftBreakStartedEventHandler, DrtShiftBreakEndedEventHandler {

    private final DrtShifts shifts;
    private final Map<Id<DrtShift>, Double> shift2StartTime = new HashMap<>();
    private final Map<Id<DrtShift>, Double> shift2BreakStartTime = new HashMap<>();

    private final Map<Id<DrtShift>, Tuple<Double,Double>> shift2plannedVsActualDuration = new HashMap<>();
    private final Map<Id<DrtShift>, Tuple<Double,Double>> shift2plannedVsActualBreakDuration = new HashMap<>();

    @Inject
    ShiftDurationXY(Scenario scenario, EventsManager eventsManager) {
        this(DrtShiftUtils.getShifts(scenario));
        eventsManager.addHandler(this);
    }

    public ShiftDurationXY(DrtShifts shifts) {
        super();
        this.shifts = shifts;
        reset(0);
    }

    /* Implementation of EventHandler-Interfaces */

    @Override
    public void handleEvent(final DrtShiftStartedEvent event) {
        shift2StartTime.put(event.getShiftId(), event.getTime());
    }

    @Override
    public void handleEvent(DrtShiftBreakStartedEvent event) {
        shift2BreakStartTime.put(event.getShiftId(), event.getTime());
    }

    @Override
    public void handleEvent(final DrtShiftEndedEvent event) {
        final Double start = shift2StartTime.get(event.getShiftId());
        double duration = event.getTime() - start;
        final DrtShift drtShift = shifts.getShifts().get(event.getShiftId());
        double plannedDuration = drtShift.getEndTime() - drtShift.getStartTime();
        shift2plannedVsActualDuration.put(event.getShiftId(), new Tuple<>(plannedDuration, duration));
    }

    @Override
    public void handleEvent(DrtShiftBreakEndedEvent event) {
        final Double start = shift2BreakStartTime.get(event.getShiftId());
        double duration = event.getTime() - start;
        final ShiftBreak drtShift = shifts.getShifts().get(event.getShiftId()).getBreak();
        double plannedDuration = drtShift.getDuration();
        shift2plannedVsActualBreakDuration.put(event.getShiftId(), new Tuple<>(plannedDuration, duration));
    }




    @Override
    public void reset(final int iter) {
        this.shift2plannedVsActualDuration.clear();
        this.shift2StartTime.clear();
    }

    /**
     * Writes the gathered data tab-separated into a text file.
     *
     * @param filename The name of a file where to write the gathered data.
     */
    public void writeShiftDuration(final String filename) {
        try (OutputStream stream = IOUtils.getOutputStream(IOUtils.getFileUrl(filename), false)) {
            writeShiftDuration(new PrintStream(stream));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes the gathered data tab-separated into a text stream.
     *
     * @param stream The data stream where to write the gathered data.
     */
    public void writeShiftDuration(final PrintStream stream) {
        stream.print("shift\tplanned duration\tactual duration");
        stream.print("\n");
        for (Map.Entry<Id<DrtShift>, Tuple<Double, Double>> entry: shift2plannedVsActualDuration.entrySet()) {
            // data about all modes
            stream.print(entry.getKey() + "\t" + entry.getValue().getFirst() + "\t" + entry.getValue().getSecond());
            // new line
            stream.print("\n");
        }
    }

    /**
     * Writes the gathered data tab-separated into a text file.
     *
     * @param filename The name of a file where to write the gathered data.
     */
    public void writeBreakDuration(final String filename) {
        try (OutputStream stream = IOUtils.getOutputStream(IOUtils.getFileUrl(filename), false)) {
            writeBreakDuration(new PrintStream(stream));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes the gathered data tab-separated into a text stream.
     *
     * @param stream The data stream where to write the gathered data.
     */
    public void writeBreakDuration(final PrintStream stream) {
        stream.print("break of shift\tplanned duration\tactual duration");
        stream.print("\n");
        for (Map.Entry<Id<DrtShift>, Tuple<Double, Double>> entry: shift2plannedVsActualBreakDuration.entrySet()) {
            // data about all modes
            stream.print(entry.getKey() + "\t" + entry.getValue().getFirst() + "\t" + entry.getValue().getSecond());
            // new line
            stream.print("\n");
        }
    }

    public Map<Id<DrtShift>, Tuple<Double, Double>> getShift2plannedVsActualDuration() {
        return shift2plannedVsActualDuration;
    }

    public Map<Id<DrtShift>, Tuple<Double, Double>> getShift2plannedVsActualBreakDuration() {
        return shift2plannedVsActualBreakDuration;
    }
}

