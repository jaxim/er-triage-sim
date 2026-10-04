package edu.hospital.triage.engine;

import edu.hospital.triage.model.Patient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Priority queue implemented via a sorted ArrayList and Iterator, as required.
 * Patients are ordered by severity (desc), then arrival time — NOT by mere
 * arrival order. The queue dynamically re-sorts whenever a background
 * vital-sign thread changes a patient's severity score.
 *
 * All public methods are synchronized: patient simulator threads, the GUI
 * (EDT) and the dispatcher all touch this structure concurrently.
 */
public class TriageQueue {

    private final ArrayList<Patient> queue = new ArrayList<>();

    public synchronized void enqueue(Patient p) {
        queue.add(p);
        resort();
    }

    /** Re-sort the backing ArrayList (Patient implements Comparable). */
    public synchronized void resort() {
        Collections.sort(queue);
    }

    /** Highest-priority patient, or null if the room is empty. */
    public synchronized Patient peek() {
        return queue.isEmpty() ? null : queue.get(0);
    }

    /** Removes and returns the highest-priority patient. */
    public synchronized Patient dequeue() {
        return queue.isEmpty() ? null : queue.remove(0);
    }

    /**
     * Removes a specific patient using an explicit Iterator — the required
     * Iterator-based traversal, and the safe way to remove mid-iteration.
     */
    public synchronized boolean remove(Patient target) {
        Iterator<Patient> it = queue.iterator();
        while (it.hasNext()) {
            Patient p = it.next();
            if (p.getPatientId().equals(target.getPatientId())) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Snapshot copy for the GUI so the EDT never iterates live state. */
    public synchronized List<Patient> snapshot() {
        return new ArrayList<>(queue);
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}
