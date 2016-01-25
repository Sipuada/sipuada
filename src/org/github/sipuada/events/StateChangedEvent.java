package org.github.sipuada.events;

import org.github.sipuada.State;

public class StateChangedEvent {

	private State oldState;
	private State newState;
	
	public StateChangedEvent(State old, State brandNew) {
		oldState = old;
		newState = brandNew;
	}

	public State getOldState() {
		return oldState;
	}

	public State getNewState() {
		return newState;
	}

}
